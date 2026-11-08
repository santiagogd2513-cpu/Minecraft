package com.example.beautifuldecor.browser.audio;

import java.util.Arrays;

/**
 * Decodificador de AAC-LC en Java puro (MPEG-4 audio, objeto 2; también acepta el objeto 1 si no usa predicción).
 * Entrada: unidades de acceso AAC "crudas" (como en MP4) y el AudioSpecificConfig. Salida: PCM de 16 bits entrelazado,
 * 1024 muestras por canal por unidad. Mono se queda en mono; todo lo demás se mezcla a estéreo.
 * Incluye: M/S, estéreo de intensidad, PNS, TNS, pulsos, ventanas seno y KBD, todas las secuencias de ventana.
 * No incluye: SBR / PS (se decodifica solo el núcleo), predicción (Main), LTP, ganancia (SSR), canales de acoplamiento.
 */
public final class AacDecoder {
    /** Característica del flujo que este decodificador no soporta. */
    public static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Unsupported(String what) {
            super(what);
        }
    }

    private static final int ONLY_LONG = 0;
    private static final int LONG_START = 1;
    private static final int EIGHT_SHORT = 2;
    private static final int LONG_STOP = 3;
    private static final int ZERO_HCB = 0;
    private static final int NOISE_HCB = 13;
    private static final int INTENSITY_HCB2 = 14;
    private static final int INTENSITY_HCB = 15;

    private static final int[] SAMPLE_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};

    // ------------------------------------------------------------------ lector de bits
    private static final class Bits {
        final byte[] d;
        int pos;
        final int end;

        Bits(byte[] data) {
            this.d = data;
            this.end = data.length * 8;
        }

        int get1() {
            int p = this.pos++;
            int i = p >> 3;
            if (i >= this.d.length) {
                return 0;
            }
            return (this.d[i] >> (7 - (p & 7))) & 1;
        }

        int get(int n) {
            int v = 0;
            while (n > 0) {
                int p = this.pos;
                int i = p >> 3;
                int avail = 8 - (p & 7);
                int take = Math.min(avail, n);
                int cur = i < this.d.length ? this.d[i] & 0xff : 0;
                v = (v << take) | ((cur >> (avail - take)) & ((1 << take) - 1));
                this.pos += take;
                n -= take;
            }
            return v;
        }

        void align() {
            this.pos = (this.pos + 7) & ~7;
        }

        boolean overrun() {
            return this.pos > this.end;
        }
    }

    // ------------------------------------------------------------------ Huffman (árbol binario)
    private static final class Huff {
        final int[] t;

        Huff(int[] lens, int[] codes, int n) {
            int[] tree = new int[2 * (n + 2) * 2];
            int nodes = 1;
            for (int s = 0; s < n; s++) {
                int len = lens[s];
                if (len == 0) {
                    continue;
                }
                int node = 0;
                for (int b = len - 1; b >= 0; b--) {
                    int bit = (codes[s] >> b) & 1;
                    int idx = node * 2 + bit;
                    if (b == 0) {
                        tree[idx] = -(s + 1);
                    } else {
                        if (tree[idx] == 0) {
                            tree[idx] = nodes++;
                        }
                        node = tree[idx];
                    }
                }
            }
            this.t = Arrays.copyOf(tree, nodes * 2);
        }

        int read(Bits b) {
            int node = 0;
            for (int i = 0; i < 24; i++) {
                int v = this.t[node * 2 + b.get1()];
                if (v < 0) {
                    return -v - 1;
                }
                if (v == 0) {
                    break;
                }
                node = v;
            }
            throw new IllegalStateException("código Huffman inválido");
        }
    }

    private static final Huff[] SPEC = new Huff[12];
    private static final Huff SF_HUFF;
    private static final int[] SPEC_COUNT = {0, 81, 81, 81, 81, 81, 81, 64, 64, 169, 169, 289};
    private static final float[] POW43 = new float[8192];
    private static final float[] POW2SF = new float[256 + 64];
    private static final float[] SINE_LONG = new float[1024];
    private static final float[] SINE_SHORT = new float[128];
    private static final float[] KBD_LONG = new float[1024];
    private static final float[] KBD_SHORT = new float[128];

    static {
        for (int cb = 1; cb <= 11; cb++) {
            int[] lens = unhex(AacTables.SPEC_LEN_HEX[cb - 1], 2);
            int[] codes = unhex(AacTables.SPEC_CODE_HEX[cb - 1], 4);
            SPEC[cb] = new Huff(lens, codes, SPEC_COUNT[cb]);
        }
        SF_HUFF = new Huff(unhex(AacTables.SF_LEN_HEX, 2), unhex(AacTables.SF_CODE_HEX, 6), 121);
        for (int i = 0; i < POW43.length; i++) {
            POW43[i] = (float) Math.pow(i, 4.0 / 3.0);
        }
        for (int i = 0; i < POW2SF.length; i++) {
            POW2SF[i] = (float) Math.pow(2.0, (i - 100) / 4.0);
        }
        for (int i = 0; i < 1024; i++) {
            SINE_LONG[i] = (float) Math.sin(Math.PI / 2048.0 * (i + 0.5));
        }
        for (int i = 0; i < 128; i++) {
            SINE_SHORT[i] = (float) Math.sin(Math.PI / 256.0 * (i + 0.5));
        }
        kbd(KBD_LONG, 4.0);
        kbd(KBD_SHORT, 6.0);
    }

    private static int[] unhex(String s, int width) {
        int n = s.length() / width;
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = Integer.parseInt(s.substring(i * width, (i + 1) * width), 16);
        }
        return r;
    }

    private static double bessel0(double x) {
        double sum = 1;
        double term = 1;
        double h = x / 2;
        for (int k = 1; k < 60; k++) {
            term *= (h / k) * (h / k);
            sum += term;
            if (term < 1e-20 * sum) {
                break;
            }
        }
        return sum;
    }

    private static void kbd(float[] out, double alpha) {
        int m = out.length;
        double[] w = new double[m + 1];
        double total = 0;
        for (int p = 0; p <= m; p++) {
            double r = 2.0 * p / m - 1.0;
            w[p] = bessel0(Math.PI * alpha * Math.sqrt(Math.max(0, 1 - r * r)));
            total += w[p];
        }
        double acc = 0;
        for (int n = 0; n < m; n++) {
            acc += w[n];
            out[n] = (float) Math.sqrt(acc / total);
        }
    }

    // ------------------------------------------------------------------ configuración
    private final int sfIndex;
    private final int sampleRate;
    private final int outChannels;
    private final int channelConfig;
    private boolean sbrSeen;

    /** @param asc AudioSpecificConfig (los bytes de esds / DecoderSpecificInfo) */
    public AacDecoder(byte[] asc) {
        Bits b = new Bits(asc);
        int aot = audioObjectType(b);
        int sfi = b.get(4);
        int rate;
        if (sfi == 15) {
            rate = b.get(24);
            sfi = nearestIndex(rate);
        } else if (sfi < SAMPLE_RATES.length) {
            rate = SAMPLE_RATES[sfi];
        } else {
            throw new Unsupported("frecuencia AAC no válida");
        }
        int cfg = b.get(4);
        if (aot == 5 || aot == 29) {
            int esfi = b.get(4);
            if (esfi == 15) {
                b.get(24);
            }
            aot = audioObjectType(b);
            this.sbrSeen = true;
        }
        if (aot != 2 && aot != 1) {
            throw new Unsupported("AAC de tipo " + aot + " (solo AAC-LC)");
        }
        if (b.get1() != 0) {
            throw new Unsupported("AAC con 960 muestras por bloque");
        }
        if (b.get1() != 0) {
            b.get(14);
        }
        if (cfg < 1 || cfg > 7) {
            throw new Unsupported("AAC con configuración de canales " + cfg);
        }
        this.sfIndex = sfi;
        this.sampleRate = rate;
        this.channelConfig = cfg;
        this.outChannels = cfg == 1 ? 1 : 2;
    }

    private static int audioObjectType(Bits b) {
        int t = b.get(5);
        if (t == 31) {
            t = 32 + b.get(6);
        }
        return t;
    }

    private static int nearestIndex(int rate) {
        int best = 0;
        for (int i = 0; i < 12; i++) {
            if (Math.abs(SAMPLE_RATES[i] - rate) < Math.abs(SAMPLE_RATES[best] - rate)) {
                best = i;
            }
        }
        return best;
    }

    public int sampleRate() {
        return this.sampleRate;
    }

    public int channels() {
        return this.outChannels;
    }

    /** El flujo declara SBR (HE-AAC): solo se decodifica el núcleo, por lo que suena sin los agudos. */
    public boolean sbrDeclared() {
        return this.sbrSeen;
    }

    // ------------------------------------------------------------------ estructuras por canal
    private static final class Ics {
        int windowSequence;
        int windowShape;
        int maxSfb;
        int numGroups;
        int numWindows;
        final int[] groupLen = new int[8];
        int[] swb;
        int numSwb;
        final int[][] cb = new int[8][64];
        final int[][] sf = new int[8][64];
        int globalGain;
        boolean pulsePresent;
        int numPulse;
        int pulseStartSfb;
        final int[] pulseOff = new int[4];
        final int[] pulseAmp = new int[4];
        boolean tnsPresent;
        final int[] tnsFilt = new int[8];
        final int[] tnsRes = new int[8];
        final int[][] tnsLen = new int[8][3];
        final int[][] tnsOrder = new int[8][3];
        final int[][] tnsDir = new int[8][3];
        final float[][][] tnsCoef = new float[8][3][20];
        final float[] spec = new float[1024];
        final int[] quant = new int[1024];
        boolean hasIntensity;
        boolean hasNoise;
    }

    private static final class ChanState {
        final float[] overlap = new float[1024];
        int prevShape;
    }

    private final Ics ics0 = new Ics();
    private final Ics ics1 = new Ics();
    private final ChanState[] states = new ChanState[16];
    private final float[][] chanOut = new float[16][1024];
    private final int[] chanRole = new int[16];
    private int chanCount;
    private int noiseSeed = 0x1f2e3d4c;
    private final boolean[][] msUsed = new boolean[8][64];
    private int msPresent;

    // ------------------------------------------------------------------ decodificación de una unidad
    /** Decodifica una unidad de acceso. Devuelve 1024 * canales() muestras PCM de 16 bits entrelazadas. */
    public short[] decode(byte[] au) {
        Bits b = new Bits(au);
        this.chanCount = 0;
        int sce = 0;
        int cpe = 0;
        while (true) {
            if (b.overrun()) {
                throw new IllegalStateException("unidad AAC truncada");
            }
            int id = b.get(3);
            if (id == 7) {
                break;
            }
            switch (id) {
                case 0, 3 -> {
                    b.get(4);
                    parseIcs(b, this.ics0, false, null);
                    reconstructPair(this.ics0, null, false);
                    this.chanRole[this.chanCount - 1] = id == 3 ? 5 : (sce++ == 0 ? 0 : 6);
                }
                case 1 -> {
                    b.get(4);
                    boolean common = b.get1() != 0;
                    if (common) {
                        parseIcsInfo(b, this.ics0);
                        copyInfo(this.ics0, this.ics1);
                        this.msPresent = b.get(2);
                        if (this.msPresent == 3) {
                            throw new IllegalStateException("ms_mask_present inválido");
                        }
                        if (this.msPresent == 1) {
                            for (int g = 0; g < this.ics0.numGroups; g++) {
                                for (int s = 0; s < this.ics0.maxSfb; s++) {
                                    this.msUsed[g][s] = b.get1() != 0;
                                }
                            }
                        } else if (this.msPresent == 2) {
                            for (int g = 0; g < this.ics0.numGroups; g++) {
                                Arrays.fill(this.msUsed[g], true);
                            }
                        }
                        parseIcsBody(b, this.ics0);
                        parseIcsBody(b, this.ics1);
                    } else {
                        this.msPresent = 0;
                        parseIcs(b, this.ics0, false, null);
                        parseIcs(b, this.ics1, false, null);
                    }
                    reconstructPair(this.ics0, this.ics1, common);
                    int role = cpe++;
                    this.chanRole[this.chanCount - 2] = 10 + role * 2;
                    this.chanRole[this.chanCount - 1] = 11 + role * 2;
                }
                case 2 -> throw new Unsupported("AAC con canal de acoplamiento");
                case 4 -> skipDse(b);
                case 5 -> throw new Unsupported("AAC con PCE");
                case 6 -> skipFil(b);
                default -> throw new IllegalStateException("elemento AAC");
            }
        }
        return mix();
    }

    private void skipDse(Bits b) {
        b.get(4);
        boolean align = b.get1() != 0;
        int count = b.get(8);
        if (count == 255) {
            count += b.get(8);
        }
        if (align) {
            b.align();
        }
        b.pos += count * 8;
    }

    private void skipFil(Bits b) {
        int count = b.get(4);
        if (count == 15) {
            count += b.get(8) - 1;
        }
        if (count > 0) {
            int type = b.get(4);
            if (type == 13 || type == 14) {
                this.sbrSeen = true;
            }
            b.pos += count * 8 - 4;
        }
    }

    // ------------------------------------------------------------------ sintaxis
    private static void copyInfo(Ics a, Ics c) {
        c.windowSequence = a.windowSequence;
        c.windowShape = a.windowShape;
        c.maxSfb = a.maxSfb;
        c.numGroups = a.numGroups;
        c.numWindows = a.numWindows;
        System.arraycopy(a.groupLen, 0, c.groupLen, 0, 8);
        c.swb = a.swb;
        c.numSwb = a.numSwb;
    }

    private void parseIcsInfo(Bits b, Ics ics) {
        b.get1();
        ics.windowSequence = b.get(2);
        ics.windowShape = b.get1();
        if (ics.windowSequence == EIGHT_SHORT) {
            ics.maxSfb = b.get(4);
            int grouping = b.get(7);
            ics.numWindows = 8;
            ics.numGroups = 1;
            ics.groupLen[0] = 1;
            for (int i = 0; i < 7; i++) {
                if (((grouping >> (6 - i)) & 1) == 0) {
                    ics.groupLen[ics.numGroups++] = 1;
                } else {
                    ics.groupLen[ics.numGroups - 1]++;
                }
            }
            ics.swb = AacTables.SWB_SHORT[this.sfIndex];
        } else {
            ics.maxSfb = b.get(6);
            if (b.get1() != 0) {
                throw new Unsupported("AAC con predicción (perfil Main)");
            }
            ics.numWindows = 1;
            ics.numGroups = 1;
            ics.groupLen[0] = 1;
            ics.swb = AacTables.SWB_LONG[this.sfIndex];
        }
        ics.numSwb = ics.swb.length - 1;
        if (ics.maxSfb > ics.numSwb) {
            throw new IllegalStateException("max_sfb fuera de rango");
        }
    }

    private void parseIcs(Bits b, Ics ics, boolean common, Ics info) {
        ics.globalGain = 0;
        int gain = b.get(8);
        parseIcsInfo(b, ics);
        ics.globalGain = gain;
        parseIcsData(b, ics);
    }

    /** Cuerpo de un canal cuando la información de ventana es común (ya leída). */
    private void parseIcsBody(Bits b, Ics ics) {
        ics.globalGain = b.get(8);
        parseIcsData(b, ics);
    }

    private void parseIcsData(Bits b, Ics ics) {
        parseSections(b, ics);
        parseScaleFactors(b, ics);
        ics.pulsePresent = b.get1() != 0;
        if (ics.pulsePresent) {
            if (ics.windowSequence == EIGHT_SHORT) {
                throw new IllegalStateException("pulsos en ventana corta");
            }
            ics.numPulse = b.get(2) + 1;
            ics.pulseStartSfb = b.get(6);
            if (ics.pulseStartSfb > ics.numSwb) {
                throw new IllegalStateException("pulse_start_sfb");
            }
            for (int i = 0; i < ics.numPulse; i++) {
                ics.pulseOff[i] = b.get(5);
                ics.pulseAmp[i] = b.get(4);
            }
        }
        ics.tnsPresent = b.get1() != 0;
        if (ics.tnsPresent) {
            parseTns(b, ics);
        }
        if (b.get1() != 0) {
            throw new Unsupported("AAC con control de ganancia (SSR)");
        }
        parseSpectrum(b, ics);
    }

    private void parseSections(Bits b, Ics ics) {
        boolean shortWin = ics.windowSequence == EIGHT_SHORT;
        int bitsIncr = shortWin ? 3 : 5;
        int esc = shortWin ? 7 : 31;
        ics.hasIntensity = false;
        ics.hasNoise = false;
        for (int g = 0; g < ics.numGroups; g++) {
            int k = 0;
            while (k < ics.maxSfb) {
                int cb = b.get(4);
                if (cb == 12) {
                    throw new IllegalStateException("libro de códigos reservado");
                }
                int len = 0;
                int incr;
                while ((incr = b.get(bitsIncr)) == esc) {
                    len += esc;
                    if (b.overrun()) {
                        throw new IllegalStateException("sect_len");
                    }
                }
                len += incr;
                if (len == 0 || k + len > ics.maxSfb) {
                    throw new IllegalStateException("sect_len fuera de rango");
                }
                if (cb == NOISE_HCB) {
                    ics.hasNoise = true;
                } else if (cb >= INTENSITY_HCB2) {
                    ics.hasIntensity = true;
                }
                for (int i = k; i < k + len; i++) {
                    ics.cb[g][i] = cb;
                }
                k += len;
            }
        }
    }

    private void parseScaleFactors(Bits b, Ics ics) {
        int sf = ics.globalGain;
        int noise = ics.globalGain - 90;
        int isPos = 0;
        boolean firstNoise = true;
        for (int g = 0; g < ics.numGroups; g++) {
            for (int s = 0; s < ics.maxSfb; s++) {
                int cb = ics.cb[g][s];
                if (cb == ZERO_HCB) {
                    ics.sf[g][s] = 0;
                } else if (cb >= INTENSITY_HCB2) {
                    isPos += SF_HUFF.read(b) - 60;
                    if (isPos < -155 || isPos > 100) {
                        isPos = Math.max(-155, Math.min(100, isPos));
                    }
                    ics.sf[g][s] = isPos;
                } else if (cb == NOISE_HCB) {
                    if (firstNoise) {
                        noise += b.get(9) - 256;
                        firstNoise = false;
                    } else {
                        noise += SF_HUFF.read(b) - 60;
                    }
                    ics.sf[g][s] = noise;
                } else {
                    sf += SF_HUFF.read(b) - 60;
                    if (sf < 0 || sf > 255) {
                        throw new IllegalStateException("factor de escala fuera de rango");
                    }
                    ics.sf[g][s] = sf;
                }
            }
        }
    }

    private void parseTns(Bits b, Ics ics) {
        boolean shortWin = ics.windowSequence == EIGHT_SHORT;
        int nBitsFilt = shortWin ? 1 : 2;
        int nBitsLen = shortWin ? 4 : 6;
        int nBitsOrder = shortWin ? 3 : 5;
        int maxOrder = shortWin ? 7 : 12;
        for (int w = 0; w < ics.numWindows; w++) {
            ics.tnsFilt[w] = b.get(nBitsFilt);
            if (ics.tnsFilt[w] == 0) {
                continue;
            }
            int res = b.get1();
            ics.tnsRes[w] = res;
            int bits = res + 3;
            for (int f = 0; f < ics.tnsFilt[w]; f++) {
                ics.tnsLen[w][f] = b.get(nBitsLen);
                int order = b.get(nBitsOrder);
                if (order > maxOrder && order > 20) {
                    throw new IllegalStateException("orden TNS");
                }
                if (order > 20) {
                    throw new IllegalStateException("orden TNS");
                }
                ics.tnsOrder[w][f] = order;
                if (order > 0) {
                    ics.tnsDir[w][f] = b.get1();
                    int compress = b.get1();
                    int len = bits - compress;
                    float iqfac = (float) (((1 << (bits - 1)) - 0.5) / (Math.PI / 2));
                    float iqfacM = (float) (((1 << (bits - 1)) + 0.5) / (Math.PI / 2));
                    for (int i = 0; i < order; i++) {
                        int code = b.get(len);
                        int s = (code & (1 << (len - 1))) != 0 ? code - (1 << len) : code;
                        ics.tnsCoef[w][f][i] = (float) Math.sin(s / (s >= 0 ? iqfac : iqfacM));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ datos espectrales
    private void parseSpectrum(Bits b, Ics ics) {
        int[] q = ics.quant;
        Arrays.fill(q, 0);
        int[] swb = ics.swb;
        int win0 = 0;
        for (int g = 0; g < ics.numGroups; g++) {
            int glen = ics.groupLen[g];
            for (int s = 0; s < ics.maxSfb; s++) {
                int cb = ics.cb[g][s];
                int start = swb[s];
                int width = swb[s + 1] - start;
                if (cb == ZERO_HCB || cb >= NOISE_HCB) {
                    continue;
                }
                for (int w = 0; w < glen; w++) {
                    int base = (win0 + w) * 128 + start;
                    if (ics.windowSequence != EIGHT_SHORT) {
                        base = start;
                    }
                    readBand(b, cb, q, base, width);
                }
            }
            win0 += glen;
        }
        if (b.overrun()) {
            throw new IllegalStateException("datos espectrales truncados");
        }
        if (ics.pulsePresent) {
            int pos = swb[ics.pulseStartSfb];
            for (int i = 0; i < ics.numPulse; i++) {
                pos += ics.pulseOff[i];
                if (pos >= 1024) {
                    throw new IllegalStateException("pulso fuera de rango");
                }
                if (q[pos] > 0) {
                    q[pos] += ics.pulseAmp[i];
                } else {
                    q[pos] -= ics.pulseAmp[i];
                }
            }
        }
    }

    private void readBand(Bits b, int cb, int[] q, int base, int width) {
        Huff h = SPEC[cb];
        if (cb <= 4) {
            for (int k = 0; k < width; k += 4) {
                int sym = h.read(b);
                int w0 = sym / 27;
                int x0 = (sym / 9) % 3;
                int y0 = (sym / 3) % 3;
                int z0 = sym % 3;
                if (cb <= 2) {
                    q[base + k] = w0 - 1;
                    q[base + k + 1] = x0 - 1;
                    q[base + k + 2] = y0 - 1;
                    q[base + k + 3] = z0 - 1;
                } else {
                    q[base + k] = w0 != 0 && b.get1() != 0 ? -w0 : w0;
                    q[base + k + 1] = x0 != 0 && b.get1() != 0 ? -x0 : x0;
                    q[base + k + 2] = y0 != 0 && b.get1() != 0 ? -y0 : y0;
                    q[base + k + 3] = z0 != 0 && b.get1() != 0 ? -z0 : z0;
                }
            }
            return;
        }
        int mod = cb <= 6 ? 9 : cb <= 8 ? 8 : cb <= 10 ? 13 : 17;
        for (int k = 0; k < width; k += 2) {
            int sym = h.read(b);
            int a = sym / mod;
            int c = sym % mod;
            if (cb <= 6) {
                q[base + k] = a - 4;
                q[base + k + 1] = c - 4;
            } else {
                int sa = a != 0 && b.get1() != 0 ? -1 : 1;
                int sc = c != 0 && b.get1() != 0 ? -1 : 1;
                if (cb == 11) {
                    if (a == 16) {
                        a = escape(b);
                    }
                    if (c == 16) {
                        c = escape(b);
                    }
                }
                q[base + k] = sa * a;
                q[base + k + 1] = sc * c;
            }
        }
    }

    private static int escape(Bits b) {
        int n = 4;
        while (b.get1() != 0) {
            n++;
            if (n > 12) {
                throw new IllegalStateException("escape AAC demasiado largo");
            }
        }
        return (1 << n) + b.get(n);
    }

    // ------------------------------------------------------------------ cuantización inversa y herramientas
    private void dequant(Ics ics) {
        float[] spec = ics.spec;
        int[] q = ics.quant;
        Arrays.fill(spec, 0f);
        int[] swb = ics.swb;
        int win0 = 0;
        boolean shortWin = ics.windowSequence == EIGHT_SHORT;
        for (int g = 0; g < ics.numGroups; g++) {
            int glen = ics.groupLen[g];
            for (int s = 0; s < ics.maxSfb; s++) {
                int cb = ics.cb[g][s];
                int start = swb[s];
                int end = swb[s + 1];
                if (cb == ZERO_HCB || cb > NOISE_HCB) {
                    continue;
                }
                for (int w = 0; w < glen; w++) {
                    int base = shortWin ? (win0 + w) * 128 : 0;
                    if (cb == NOISE_HCB) {
                        noise(spec, base + start, end - start, ics.sf[g][s]);
                        continue;
                    }
                    float gain = POW2SF[ics.sf[g][s]];
                    for (int k = start; k < end; k++) {
                        int v = q[base + k];
                        if (v == 0) {
                            continue;
                        }
                        int a = v < 0 ? -v : v;
                        if (a >= POW43.length) {
                            throw new IllegalStateException("valor cuantizado demasiado grande");
                        }
                        float f = POW43[a] * gain;
                        spec[base + k] = v < 0 ? -f : f;
                    }
                }
            }
            win0 += glen;
        }
    }

    private void noise(float[] spec, int off, int n, int nrg) {
        double energy = 0;
        for (int i = 0; i < n; i++) {
            this.noiseSeed = this.noiseSeed * 1664525 + 1013904223;
            float v = this.noiseSeed;
            spec[off + i] = v;
            energy += (double) v * v;
        }
        if (energy <= 0) {
            return;
        }
        double target = Math.pow(2.0, nrg / 4.0);
        float scale = (float) (target / Math.sqrt(energy));
        for (int i = 0; i < n; i++) {
            spec[off + i] *= scale;
        }
    }

    private void midSide(Ics l, Ics r) {
        int win0 = 0;
        boolean shortWin = l.windowSequence == EIGHT_SHORT;
        for (int g = 0; g < l.numGroups; g++) {
            int glen = l.groupLen[g];
            for (int s = 0; s < l.maxSfb; s++) {
                if (!this.msUsed[g][s] || l.cb[g][s] >= NOISE_HCB || r.cb[g][s] >= NOISE_HCB) {
                    continue;
                }
                for (int w = 0; w < glen; w++) {
                    int base = shortWin ? (win0 + w) * 128 : 0;
                    for (int k = l.swb[s]; k < l.swb[s + 1]; k++) {
                        float m = l.spec[base + k];
                        float sd = r.spec[base + k];
                        l.spec[base + k] = m + sd;
                        r.spec[base + k] = m - sd;
                    }
                }
            }
            win0 += glen;
        }
    }

    private void intensity(Ics l, Ics r) {
        int win0 = 0;
        boolean shortWin = l.windowSequence == EIGHT_SHORT;
        for (int g = 0; g < l.numGroups; g++) {
            int glen = l.groupLen[g];
            for (int s = 0; s < r.maxSfb; s++) {
                int cb = r.cb[g][s];
                if (cb != INTENSITY_HCB && cb != INTENSITY_HCB2) {
                    continue;
                }
                float c = cb == INTENSITY_HCB ? 1f : -1f;
                if (this.msPresent != 0 && this.msUsed[g][s]) {
                    c = -c;
                }
                float scale = c * (float) Math.pow(0.5, 0.25 * r.sf[g][s]);
                for (int w = 0; w < glen; w++) {
                    int base = shortWin ? (win0 + w) * 128 : 0;
                    for (int k = l.swb[s]; k < l.swb[s + 1]; k++) {
                        r.spec[base + k] = l.spec[base + k] * scale;
                    }
                }
            }
            win0 += glen;
        }
    }

    private void applyTns(Ics ics) {
        boolean shortWin = ics.windowSequence == EIGHT_SHORT;
        int tnsMax = shortWin ? AacTables.TNS_MAX_BANDS_SHORT[this.sfIndex] : AacTables.TNS_MAX_BANDS_LONG[this.sfIndex];
        int mmm = Math.min(tnsMax, ics.maxSfb);
        float[] lpc = new float[21];
        float[] tmp = new float[21];
        for (int w = 0; w < ics.numWindows; w++) {
            int bottom = ics.numSwb;
            for (int f = 0; f < ics.tnsFilt[w]; f++) {
                int top = bottom;
                bottom = Math.max(0, top - ics.tnsLen[w][f]);
                int order = ics.tnsOrder[w][f];
                if (order == 0) {
                    continue;
                }
                float[] k = ics.tnsCoef[w][f];
                lpc[0] = 1;
                for (int m = 1; m <= order; m++) {
                    float km = k[m - 1];
                    System.arraycopy(lpc, 0, tmp, 0, m);
                    for (int i = 1; i < m; i++) {
                        lpc[i] = tmp[i] + km * tmp[m - i];
                    }
                    lpc[m] = km;
                }
                int start = ics.swb[Math.min(bottom, mmm)];
                int end = ics.swb[Math.min(top, mmm)];
                int size = end - start;
                if (size <= 0) {
                    continue;
                }
                int inc = 1;
                int pos = start;
                if (ics.tnsDir[w][f] != 0) {
                    inc = -1;
                    pos = end - 1;
                }
                pos += w * 128;
                float[] sp = ics.spec;
                for (int m = 0; m < size; m++, pos += inc) {
                    int lim = Math.min(m, order);
                    float acc = 0;
                    for (int i = 1; i <= lim; i++) {
                        acc += sp[pos - i * inc] * lpc[i];
                    }
                    sp[pos] -= acc;
                }
            }
        }
    }

    // ------------------------------------------------------------------ reconstrucción
    private void reconstructPair(Ics a, Ics c, boolean common) {
        dequant(a);
        if (c != null) {
            dequant(c);
            if (common && this.msPresent != 0) {
                midSide(a, c);
            }
            if (c.hasIntensity) {
                intensity(a, c);
            }
        }
        synth(a, this.chanCount++);
        if (c != null) {
            synth(c, this.chanCount++);
        }
    }

    private final double[] fftRe = new double[512];
    private final double[] fftIm = new double[512];
    private final double[] dct = new double[1024];
    private final float[] blk = new float[2048];
    private final float[] shortBuf = new float[256];

    private void synth(Ics ics, int ch) {
        if (ch >= this.states.length) {
            throw new Unsupported("demasiados canales AAC");
        }
        ChanState cs = this.states[ch];
        if (cs == null) {
            cs = new ChanState();
            this.states[ch] = cs;
        }
        if (ics.tnsPresent) {
            applyTns(ics);
        }
        float[] blk = this.blk;
        float[] longPrev = cs.prevShape == 1 ? KBD_LONG : SINE_LONG;
        float[] longCur = ics.windowShape == 1 ? KBD_LONG : SINE_LONG;
        float[] shortPrev = cs.prevShape == 1 ? KBD_SHORT : SINE_SHORT;
        float[] shortCur = ics.windowShape == 1 ? KBD_SHORT : SINE_SHORT;
        switch (ics.windowSequence) {
            case EIGHT_SHORT -> {
                Arrays.fill(blk, 0f);
                for (int w = 0; w < 8; w++) {
                    imdct(ics.spec, w * 128, 128, this.shortBuf);
                    float[] rise = w == 0 ? shortPrev : shortCur;
                    int o = 448 + w * 128;
                    for (int n = 0; n < 128; n++) {
                        blk[o + n] += this.shortBuf[n] * rise[n];
                    }
                    for (int n = 0; n < 128; n++) {
                        blk[o + 128 + n] += this.shortBuf[128 + n] * shortCur[127 - n];
                    }
                }
            }
            default -> {
                imdct(ics.spec, 0, 1024, blk);
                switch (ics.windowSequence) {
                    case ONLY_LONG -> {
                        for (int n = 0; n < 1024; n++) {
                            blk[n] *= longPrev[n];
                            blk[1024 + n] *= longCur[1023 - n];
                        }
                    }
                    case LONG_START -> {
                        for (int n = 0; n < 1024; n++) {
                            blk[n] *= longPrev[n];
                        }
                        for (int n = 1024; n < 1472; n++) {
                            // queda igual (ventana 1)
                            blk[n] *= 1f;
                        }
                        for (int n = 0; n < 128; n++) {
                            blk[1472 + n] *= shortCur[127 - n];
                        }
                        Arrays.fill(blk, 1600, 2048, 0f);
                    }
                    default -> {
                        Arrays.fill(blk, 0, 448, 0f);
                        for (int n = 0; n < 128; n++) {
                            blk[448 + n] *= shortPrev[n];
                        }
                        for (int n = 0; n < 1024; n++) {
                            blk[1024 + n] *= longCur[1023 - n];
                        }
                    }
                }
            }
        }
        float[] out = this.chanOut[ch];
        float[] ov = cs.overlap;
        for (int n = 0; n < 1024; n++) {
            out[n] = ov[n] + blk[n];
            ov[n] = blk[1024 + n];
        }
        cs.prevShape = ics.windowShape;
    }

    // ------------------------------------------------------------------ IMDCT (DCT-IV con FFT de M/2 puntos)
    private final double[][] twRe = new double[2][];
    private final double[][] twIm = new double[2][];
    private final double[][] tpRe = new double[2][];
    private final double[][] tpIm = new double[2][];

    private void ensureTwiddles(int m) {
        int idx = m == 1024 ? 0 : 1;
        if (this.twRe[idx] != null) {
            return;
        }
        int h = m / 2;
        double[] re = new double[h];
        double[] im = new double[h];
        double[] pre = new double[h];
        double[] pim = new double[h];
        for (int n = 0; n < h; n++) {
            double a = -Math.PI * (n + 0.25) / m;
            re[n] = Math.cos(a);
            im[n] = Math.sin(a);
            double c = -Math.PI * n / m;
            pre[n] = Math.cos(c);
            pim[n] = Math.sin(c);
        }
        this.twRe[idx] = re;
        this.twIm[idx] = im;
        this.tpRe[idx] = pre;
        this.tpIm[idx] = pim;
    }

    /** x[n] = (2/N) * sum X[k] cos(2*pi/N * (n + n0) * (k + 1/2)), N = 2m, n0 = (N/2 + 1) / 2. out recibe N muestras. */
    private void imdct(float[] spec, int off, int m, float[] out) {
        ensureTwiddles(m);
        int idx = m == 1024 ? 0 : 1;
        double[] twr = this.twRe[idx];
        double[] twi = this.twIm[idx];
        double[] tpr = this.tpRe[idx];
        double[] tpi = this.tpIm[idx];
        int h = m / 2;
        double[] re = this.fftRe;
        double[] im = this.fftIm;
        for (int n = 0; n < h; n++) {
            double a = spec[off + 2 * n];
            double b = spec[off + m - 1 - 2 * n];
            re[n] = a * twr[n] - b * twi[n];
            im[n] = a * twi[n] + b * twr[n];
        }
        fft(re, im, h);
        double[] y = this.dct;
        for (int n = 0; n < h; n++) {
            double r = re[n] * tpr[n] - im[n] * tpi[n];
            double i = re[n] * tpi[n] + im[n] * tpr[n];
            y[2 * n] = r;
            y[m - 1 - 2 * n] = -i;
        }
        double sc = 2.0 / (2 * m);
        int h2 = m / 2;
        for (int n = 0; n < h2; n++) {
            out[n] = (float) (y[n + h2] * sc);
        }
        for (int n = h2; n < h2 + m; n++) {
            out[n] = (float) (-y[3 * h2 - 1 - n] * sc);
        }
        for (int n = h2 + m; n < 2 * m; n++) {
            out[n] = (float) (-y[n - 3 * h2] * sc);
        }
    }

    private static void fft(double[] re, double[] im, int n) {
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1;
                double ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }

    // ------------------------------------------------------------------ mezcla de salida
    private short[] mix() {
        int n = this.chanCount;
        if (n == 0) {
            throw new IllegalStateException("unidad AAC sin canales");
        }
        short[] pcm = new short[1024 * this.outChannels];
        if (this.outChannels == 1) {
            float[] c = this.chanOut[0];
            for (int i = 0; i < 1024; i++) {
                pcm[i] = toShort(c[i]);
            }
            return pcm;
        }
        if (n == 1) {
            float[] c = this.chanOut[0];
            for (int i = 0; i < 1024; i++) {
                short v = toShort(c[i]);
                pcm[2 * i] = v;
                pcm[2 * i + 1] = v;
            }
            return pcm;
        }
        if (n == 2 && this.chanRole[0] == 10) {
            float[] l = this.chanOut[0];
            float[] r = this.chanOut[1];
            for (int i = 0; i < 1024; i++) {
                pcm[2 * i] = toShort(l[i]);
                pcm[2 * i + 1] = toShort(r[i]);
            }
            return pcm;
        }
        // más de dos canales: mezcla clásica a estéreo
        float[] left = new float[1024];
        float[] right = new float[1024];
        float norm = 1f / 2.4142f;
        for (int ch = 0; ch < n; ch++) {
            int role = this.chanRole[ch];
            float[] c = this.chanOut[ch];
            float lw = 0;
            float rw = 0;
            if (role == 0) {
                lw = 0.7071f;
                rw = 0.7071f;
            } else if (role == 10) {
                lw = 1;
            } else if (role == 11) {
                rw = 1;
            } else if (role == 12 || role == 14) {
                lw = role == 12 ? 0.7071f : 0.5f;
            } else if (role == 13 || role == 15) {
                rw = role == 13 ? 0.7071f : 0.5f;
            }
            if (lw == 0 && rw == 0) {
                continue;
            }
            for (int i = 0; i < 1024; i++) {
                left[i] += c[i] * lw;
                right[i] += c[i] * rw;
            }
        }
        for (int i = 0; i < 1024; i++) {
            pcm[2 * i] = toShort(left[i] * norm);
            pcm[2 * i + 1] = toShort(right[i] * norm);
        }
        return pcm;
    }

    private static short toShort(float v) {
        int x = (int) Math.floor(v + 0.5f);
        return (short) (x < -32768 ? -32768 : Math.min(x, 32767));
    }
}
