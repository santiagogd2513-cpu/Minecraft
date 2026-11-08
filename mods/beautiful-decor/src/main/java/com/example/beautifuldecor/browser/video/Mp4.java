package com.example.beautifuldecor.browser.video;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lector de archivos MP4/MOV (ISO BMFF) en Java puro: tablas de muestras clásicas (moov/stbl) y MP4 fragmentado (moof/trun).
 * Saca la pista de video H.264 (avcC) y la de audio AAC (esds); las demás se ignoran.
 */
public final class Mp4 implements Closeable {
    public static final class Track {
        public int id;
        public String handler = "";
        public String codec = "";
        public int timescale = 1000;
        public long durationUnits;
        public int width;
        public int height;
        public byte[] avcC;                    // video: avcC completo
        public int nalLengthSize = 4;
        public List<byte[]> sps = new ArrayList<>();
        public List<byte[]> pps = new ArrayList<>();
        public byte[] audioConfig;             // audio: AudioSpecificConfig
        public int objectType;                 // audio: 0x40 = AAC
        public int channels;
        public int sampleRate;
        // muestras (en orden de decodificación)
        int count;
        long[] offset = new long[0];
        int[] size = new int[0];
        long[] dts = new long[0];
        int[] cts = new int[0];                // desplazamiento de presentación
        boolean[] sync = new boolean[0];
        long editShift;                        // media_time de la primera edición (unidades de la pista)
        // trex (para fragmentos)
        int defDuration;
        int defSize;
        int defFlags;

        public int sampleCount() {
            return this.count;
        }

        public int size(int i) {
            return this.size[i];
        }

        public boolean isSync(int i) {
            return this.sync[i];
        }

        /** Tiempo de decodificación de la muestra, en microsegundos. */
        public long dtsUs(int i) {
            return (this.dts[i] - this.editShift) * 1_000_000L / this.timescale;
        }

        /** Tiempo de presentación de la muestra, en microsegundos. */
        public long ptsUs(int i) {
            return (this.dts[i] + this.cts[i] - this.editShift) * 1_000_000L / this.timescale;
        }

        public long durationUs() {
            if (this.count > 0) {
                long end = this.dts[this.count - 1] - this.dts[0];
                long last = this.count > 1 ? this.dts[this.count - 1] - this.dts[this.count - 2] : 0;
                long byTable = (end + last) * 1_000_000L / this.timescale;
                if (byTable > 0) {
                    return byTable;
                }
            }
            return this.durationUnits * 1_000_000L / Math.max(1, this.timescale);
        }

        void grow(int n) {
            if (n <= this.offset.length) {
                return;
            }
            int cap = Math.max(n, this.offset.length * 2 + 64);
            this.offset = Arrays.copyOf(this.offset, cap);
            this.size = Arrays.copyOf(this.size, cap);
            this.dts = Arrays.copyOf(this.dts, cap);
            this.cts = Arrays.copyOf(this.cts, cap);
            this.sync = Arrays.copyOf(this.sync, cap);
        }
    }

    private final RandomAccessFile file;
    private final long fileLength;
    public Track video;
    public Track audio;
    /** Nombre del códec de video si existe pero no se puede decodificar (por ejemplo "hvc1", "vp09"). */
    public String unsupportedVideo;
    public String unsupportedAudio;
    private final List<Track> tracks = new ArrayList<>();
    private boolean fragmented;
    private long movieTimescale = 1000;
    private long movieDuration;
    private long requiredBeforeCut;

    private Mp4(RandomAccessFile f) throws IOException {
        this.file = f;
        this.fileLength = f.length();
    }

    public static Mp4 open(Path path) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path.toFile(), "r");
        Mp4 m = new Mp4(f);
        try {
            m.parseTop();
            m.pick();
        } catch (IOException | RuntimeException e) {
            f.close();
            throw e;
        }
        return m;
    }

    @Override
    public void close() throws IOException {
        this.file.close();
    }

    public long durationUs() {
        long d = 0;
        if (this.video != null) {
            d = Math.max(d, this.video.durationUs());
        }
        if (this.audio != null) {
            d = Math.max(d, this.audio.durationUs());
        }
        if (d == 0 && this.movieDuration > 0) {
            d = this.movieDuration * 1_000_000L / Math.max(1, this.movieTimescale);
        }
        return d;
    }

    /** Tamaño del archivo en bytes (cuando se abrió). */
    public long fileLength() {
        return this.fileLength;
    }

    /**
     * Hasta qué byte del archivo llegan las muestras de la pista de video y la de audio elegidas: lo mínimo que debe medir el archivo para
     * estar completo. Si es mayor que {@link #fileLength()}, el archivo está cortado (una descarga que se interrumpió, o una copia subida
     * a medias al servidor). Tras {@link #cutToFile()} sigue diciendo lo que pedía el archivo entero.
     */
    public long requiredBytes() {
        long max = this.requiredBeforeCut;
        for (Track t : new Track[] {this.video, this.audio}) {
            if (t == null) {
                continue;
            }
            for (int i = 0; i < t.count; i++) {
                max = Math.max(max, t.offset[i] + t.size[i]);
            }
        }
        return max;
    }

    /** Cierto si el archivo mide menos de lo que necesita el video entero. */
    public boolean isCut() {
        return requiredBytes() > this.fileLength;
    }

    /**
     * Si el archivo está cortado, deja en cada pista solo las muestras que sí están completas dentro del archivo y las recorta a un mismo
     * instante (el menor de los dos finales) para que la imagen y el sonido terminen juntos. Así se reproduce el principio que sí llegó
     * en vez de fallar. Devuelve cierto si el archivo estaba cortado. Se puede llamar más de una vez.
     */
    public boolean cutToFile() {
        long required = requiredBytes();
        if (required <= this.fileLength) {
            return false;
        }
        this.requiredBeforeCut = required;
        Track[] ts = {this.video, this.audio};
        int[] inside = new int[2];
        long limit = Long.MAX_VALUE;                              // primer instante (µs) que ya no tiene todos sus datos
        for (int n = 0; n < 2; n++) {
            Track t = ts[n];
            if (t == null) {
                continue;
            }
            int k = 0;
            while (k < t.count && t.size[k] >= 0 && t.offset[k] >= 0 && t.offset[k] + t.size[k] <= this.fileLength) {
                k++;
            }
            inside[n] = k;
            if (k < t.count) {
                limit = Math.min(limit, k == 0 ? 0 : t.dtsUs(k));
            }
        }
        for (int n = 0; n < 2; n++) {
            Track t = ts[n];
            if (t == null) {
                continue;
            }
            int k = inside[n];
            while (k > 0 && t.dtsUs(k - 1) >= limit) {
                k--;
            }
            t.count = k;
        }
        return true;
    }

    /** Lee una muestra completa (hilo seguro: cada llamada hace su propio seek). */
    public synchronized byte[] readSample(Track t, int i) throws IOException {
        int n = t.size[i];
        if (n < 0 || t.offset[i] < 0 || t.offset[i] + n > this.fileLength) {
            throw new EOFException("muestra fuera del archivo (muestra " + i + " de " + t.count + ", hasta el byte " + (t.offset[i] + n)
                    + " y el archivo mide " + this.fileLength + ")");
        }
        byte[] b = new byte[n];
        this.file.seek(t.offset[i]);
        this.file.readFully(b);
        return b;
    }

    // ------------------------------------------------------------------ estructura de cajas
    private byte[] readAt(long pos, int n) throws IOException {
        if (pos < 0 || pos + n > this.fileLength) {
            throw new EOFException("caja truncada");
        }
        byte[] b = new byte[n];
        synchronized (this) {
            this.file.seek(pos);
            this.file.readFully(b);
        }
        return b;
    }

    private void parseTop() throws IOException {
        long pos = 0;
        boolean gotMoov = false;
        while (pos + 8 <= this.fileLength) {
            byte[] h = readAt(pos, 8);
            long size = u32(h, 0);
            String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
            long header = 8;
            if (size == 1) {
                byte[] big = readAt(pos + 8, 8);
                size = (u32(big, 0) << 32) | u32(big, 4);
                header = 16;
            } else if (size == 0) {
                size = this.fileLength - pos;
            }
            if (size < header || pos + size > this.fileLength) {
                if (type.equals("mdat")) {
                    size = this.fileLength - pos;
                } else if (!gotMoov) {
                    throw new IOException("caja dañada o archivo incompleto (" + type + ")");
                } else {
                    break;
                }
            }
            if (type.equals("moov")) {
                if (size > 64L << 20) {
                    throw new IOException("moov demasiado grande");
                }
                ByteBuffer bb = ByteBuffer.wrap(readAt(pos + header, (int) (size - header)));
                parseMoov(bb);
                gotMoov = true;
            } else if (type.equals("moof")) {
                if (!gotMoov) {
                    throw new IOException("fragmento antes del moov");
                }
                ByteBuffer bb = ByteBuffer.wrap(readAt(pos + header, (int) Math.min(size - header, 16 << 20)));
                parseMoof(bb, pos);
            }
            pos += size;
        }
        if (!gotMoov) {
            throw new IOException("no es un MP4 (no hay moov)");
        }
    }

    private static long u32(byte[] b, int o) {
        return ((b[o] & 0xffL) << 24) | ((b[o + 1] & 0xff) << 16) | ((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff);
    }

    /** Cajas hijas: llama a visit(tipo, contenido) para cada una. */
    private interface Visitor {
        void visit(String type, ByteBuffer body) throws IOException;
    }

    private static void children(ByteBuffer bb, Visitor v) throws IOException {
        while (bb.remaining() >= 8) {
            int start = bb.position();
            long size = bb.getInt() & 0xffffffffL;
            byte[] t = new byte[4];
            bb.get(t);
            String type = new String(t, StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1) {
                if (bb.remaining() < 8) {
                    return;
                }
                size = bb.getLong();
                header = 16;
            } else if (size == 0) {
                size = bb.limit() - start;
            }
            if (size < header || start + size > bb.limit()) {
                return;
            }
            ByteBuffer body = bb.duplicate();
            body.position(start + header);
            body.limit((int) (start + size));
            body = body.slice();
            v.visit(type, body);
            bb.position((int) (start + size));
        }
    }

    private void parseMoov(ByteBuffer moov) throws IOException {
        children(moov, (type, body) -> {
            switch (type) {
                case "mvhd" -> {
                    int ver = body.get() & 0xff;
                    body.position(body.position() + 3);
                    if (ver == 1) {
                        body.position(body.position() + 16);
                        this.movieTimescale = body.getInt() & 0xffffffffL;
                        this.movieDuration = body.getLong();
                    } else {
                        body.position(body.position() + 8);
                        this.movieTimescale = body.getInt() & 0xffffffffL;
                        this.movieDuration = body.getInt() & 0xffffffffL;
                    }
                }
                case "trak" -> {
                    Track t = new Track();
                    parseTrak(body, t);
                    if (t.timescale > 0) {
                        this.tracks.add(t);
                    }
                }
                case "mvex" -> {
                    this.fragmented = true;
                    children(body, (t2, b2) -> {
                        if (t2.equals("trex")) {
                            b2.position(b2.position() + 4);
                            int id = b2.getInt();
                            b2.getInt();
                            int dd = b2.getInt();
                            int ds = b2.getInt();
                            int df = b2.getInt();
                            for (Track t : this.tracks) {
                                if (t.id == id) {
                                    t.defDuration = dd;
                                    t.defSize = ds;
                                    t.defFlags = df;
                                }
                            }
                        }
                    });
                }
                default -> {
                }
            }
        });
    }

    private void parseTrak(ByteBuffer trak, Track t) throws IOException {
        long[] editMedia = {0};
        children(trak, (type, body) -> {
            switch (type) {
                case "tkhd" -> {
                    int ver = body.get() & 0xff;
                    body.position(body.position() + 3);
                    if (ver == 1) {
                        body.position(body.position() + 16);
                        t.id = body.getInt();
                    } else {
                        body.position(body.position() + 8);
                        t.id = body.getInt();
                    }
                }
                case "edts" -> children(body, (t2, b2) -> {
                    if (t2.equals("elst")) {
                        int ver = b2.get() & 0xff;
                        b2.position(b2.position() + 3);
                        int n = b2.getInt();
                        // se toma la primera edición que no sea "vacía" (media_time -1)
                        for (int i = 0; i < n && b2.remaining() >= (ver == 1 ? 20 : 12); i++) {
                            long mediaTime;
                            if (ver == 1) {
                                b2.getLong();
                                mediaTime = b2.getLong();
                            } else {
                                b2.getInt();
                                mediaTime = b2.getInt();
                            }
                            b2.getInt();
                            if (mediaTime >= 0) {
                                editMedia[0] = mediaTime;
                                break;
                            }
                        }
                    }
                });
                case "mdia" -> parseMdia(body, t);
                default -> {
                }
            }
        });
        t.editShift = editMedia[0];
    }

    private void parseMdia(ByteBuffer mdia, Track t) throws IOException {
        children(mdia, (type, body) -> {
            switch (type) {
                case "mdhd" -> {
                    int ver = body.get() & 0xff;
                    body.position(body.position() + 3);
                    if (ver == 1) {
                        body.position(body.position() + 16);
                        t.timescale = body.getInt();
                        t.durationUnits = body.getLong();
                    } else {
                        body.position(body.position() + 8);
                        t.timescale = body.getInt();
                        t.durationUnits = body.getInt() & 0xffffffffL;
                    }
                }
                case "hdlr" -> {
                    body.position(body.position() + 8);
                    byte[] h = new byte[4];
                    body.get(h);
                    t.handler = new String(h, StandardCharsets.ISO_8859_1);
                }
                case "minf" -> children(body, (t2, b2) -> {
                    if (t2.equals("stbl")) {
                        parseStbl(b2, t);
                    }
                });
                default -> {
                }
            }
        });
    }

    private void parseStbl(ByteBuffer stbl, Track t) throws IOException {
        int[] stszCount = {0};
        long[] chunkOffsets = {};
        int[][] stsc = {};
        int[] sizes = {};
        int[] constSize = {0};
        long[] durationsFlat = null;
        int[] sttsCounts = {};
        int[] sttsDeltas = {};
        int[] cttsCounts = {};
        int[] cttsOffs = {};
        int[] syncList = null;
        boolean[] haveStss = {false};
        Object[] holder = new Object[8];
        children(stbl, (type, body) -> {
            switch (type) {
                case "stsd" -> parseStsd(body, t);
                case "stts" -> {
                    body.position(body.position() + 4);
                    int n = body.getInt();
                    int[] c = new int[n];
                    int[] d = new int[n];
                    for (int i = 0; i < n; i++) {
                        c[i] = body.getInt();
                        d[i] = body.getInt();
                    }
                    holder[0] = c;
                    holder[1] = d;
                }
                case "ctts" -> {
                    int ver = body.get() & 0xff;
                    body.position(body.position() + 3);
                    int n = body.getInt();
                    int[] c = new int[n];
                    int[] o = new int[n];
                    for (int i = 0; i < n; i++) {
                        c[i] = body.getInt();
                        o[i] = body.getInt();       // v0 es sin signo, v1 con signo: igual cabe en int
                    }
                    holder[2] = c;
                    holder[3] = o;
                }
                case "stsc" -> {
                    body.position(body.position() + 4);
                    int n = body.getInt();
                    int[][] s = new int[n][3];
                    for (int i = 0; i < n; i++) {
                        s[i][0] = body.getInt();
                        s[i][1] = body.getInt();
                        s[i][2] = body.getInt();
                    }
                    holder[4] = s;
                }
                case "stsz" -> {
                    body.position(body.position() + 4);
                    int cs = body.getInt();
                    int n = body.getInt();
                    int[] sz = new int[n];
                    for (int i = 0; i < n; i++) {
                        sz[i] = cs != 0 ? cs : body.getInt();
                    }
                    holder[5] = sz;
                }
                case "stco" -> {
                    body.position(body.position() + 4);
                    int n = body.getInt();
                    long[] o = new long[n];
                    for (int i = 0; i < n; i++) {
                        o[i] = body.getInt() & 0xffffffffL;
                    }
                    holder[6] = o;
                }
                case "co64" -> {
                    body.position(body.position() + 4);
                    int n = body.getInt();
                    long[] o = new long[n];
                    for (int i = 0; i < n; i++) {
                        o[i] = body.getLong();
                    }
                    holder[6] = o;
                }
                case "stss" -> {
                    body.position(body.position() + 4);
                    int n = body.getInt();
                    int[] s = new int[n];
                    for (int i = 0; i < n; i++) {
                        s[i] = body.getInt();
                    }
                    holder[7] = s;
                }
                default -> {
                }
            }
        });
        int[] sc = (int[]) holder[0];
        int[] sd = (int[]) holder[1];
        int[] sz = (int[]) holder[5];
        long[] co = (long[]) holder[6];
        int[][] sts = (int[][]) holder[4];
        if (sz == null || co == null || sts == null || sc == null) {
            return;     // pista vacía (MP4 fragmentado): las muestras llegan en los moof
        }
        int n = sz.length;
        t.grow(n);
        t.count = n;
        // desplazamientos
        int sample = 0;
        for (int c = 0; c < co.length && sample < n; c++) {
            int chunkNo = c + 1;
            int per = 0;
            for (int[] e : sts) {
                if (e[0] <= chunkNo) {
                    per = e[1];
                }
            }
            long off = co[c];
            for (int k = 0; k < per && sample < n; k++, sample++) {
                t.offset[sample] = off;
                t.size[sample] = sz[sample];
                off += sz[sample];
            }
        }
        if (sample < n) {
            t.count = sample;
            n = sample;
        }
        // tiempos
        long time = 0;
        int idx = 0;
        for (int e = 0; e < sc.length && idx < n; e++) {
            for (int k = 0; k < sc[e] && idx < n; k++, idx++) {
                t.dts[idx] = time;
                time += sd[e] & 0xffffffffL;
            }
        }
        for (; idx < n; idx++) {
            t.dts[idx] = time;
        }
        int[] cc = (int[]) holder[2];
        int[] co2 = (int[]) holder[3];
        if (cc != null) {
            idx = 0;
            for (int e = 0; e < cc.length && idx < n; e++) {
                for (int k = 0; k < cc[e] && idx < n; k++, idx++) {
                    t.cts[idx] = co2[e];
                }
            }
        }
        int[] ss = (int[]) holder[7];
        if (ss == null) {
            Arrays.fill(t.sync, 0, n, true);
        } else {
            for (int s : ss) {
                if (s >= 1 && s <= n) {
                    t.sync[s - 1] = true;
                }
            }
        }
    }

    private void parseStsd(ByteBuffer stsd, Track t) throws IOException {
        stsd.position(stsd.position() + 4);
        int entries = stsd.getInt();
        if (entries < 1) {
            return;
        }
        // solo la primera entrada
        int start = stsd.position();
        long size = stsd.getInt() & 0xffffffffL;
        byte[] ty = new byte[4];
        stsd.get(ty);
        t.codec = new String(ty, StandardCharsets.ISO_8859_1);
        int end = (int) Math.min(stsd.limit(), start + size);
        if (t.handler.equals("vide")) {
            stsd.position(stsd.position() + 6 + 2 + 16);
            t.width = stsd.getShort() & 0xffff;
            t.height = stsd.getShort() & 0xffff;
            stsd.position(start + 8 + 78);
        } else if (t.handler.equals("soun")) {
            stsd.position(stsd.position() + 6 + 2);
            int ver = stsd.getShort() & 0xffff;
            stsd.position(stsd.position() + 6);
            t.channels = stsd.getShort() & 0xffff;
            stsd.position(stsd.position() + 2 + 2 + 2);
            t.sampleRate = (stsd.getInt() >>> 16);
            if (ver == 1) {
                stsd.position(stsd.position() + 16);
            } else if (ver == 2) {
                stsd.position(stsd.position() + 36);
            }
        } else {
            return;
        }
        if (stsd.position() >= end) {
            return;
        }
        ByteBuffer kids = stsd.duplicate();
        kids.limit(end);
        kids = kids.slice();
        children(kids, (type, body) -> {
            if (type.equals("avcC")) {
                byte[] raw = new byte[body.remaining()];
                body.get(raw);
                t.avcC = raw;
                parseAvcC(raw, t);
            } else if (type.equals("esds")) {
                body.position(body.position() + 4);
                parseEsds(body, t);
            } else if (type.equals("wave")) {
                children(body, (t2, b2) -> {
                    if (t2.equals("esds")) {
                        b2.position(b2.position() + 4);
                        parseEsds(b2, t);
                    }
                });
            }
        });
    }

    private static void parseAvcC(byte[] raw, Track t) {
        if (raw.length < 7) {
            return;
        }
        t.nalLengthSize = (raw[4] & 3) + 1;
        int p = 5;
        int ns = raw[p++] & 0x1f;
        for (int i = 0; i < ns && p + 2 <= raw.length; i++) {
            int len = ((raw[p] & 0xff) << 8) | (raw[p + 1] & 0xff);
            p += 2;
            if (p + len > raw.length) {
                return;
            }
            t.sps.add(Arrays.copyOfRange(raw, p, p + len));
            p += len;
        }
        if (p >= raw.length) {
            return;
        }
        int np = raw[p++] & 0xff;
        for (int i = 0; i < np && p + 2 <= raw.length; i++) {
            int len = ((raw[p] & 0xff) << 8) | (raw[p + 1] & 0xff);
            p += 2;
            if (p + len > raw.length) {
                return;
            }
            t.pps.add(Arrays.copyOfRange(raw, p, p + len));
            p += len;
        }
    }

    private static int descLen(ByteBuffer b) {
        int len = 0;
        for (int i = 0; i < 4 && b.hasRemaining(); i++) {
            int x = b.get() & 0xff;
            len = (len << 7) | (x & 0x7f);
            if ((x & 0x80) == 0) {
                break;
            }
        }
        return len;
    }

    private static void parseEsds(ByteBuffer b, Track t) {
        try {
            while (b.remaining() >= 2) {
                int tag = b.get() & 0xff;
                int len = descLen(b);
                if (tag == 3) {                           // ES_Descriptor
                    b.position(b.position() + 2);
                    int flags = b.get() & 0xff;
                    if ((flags & 0x80) != 0) {
                        b.position(b.position() + 2);
                    }
                    if ((flags & 0x40) != 0) {
                        int n = b.get() & 0xff;
                        b.position(b.position() + n);
                    }
                    if ((flags & 0x20) != 0) {
                        b.position(b.position() + 2);
                    }
                } else if (tag == 4) {                    // DecoderConfigDescriptor
                    t.objectType = b.get() & 0xff;
                    b.position(b.position() + 12);
                } else if (tag == 5) {                    // DecoderSpecificInfo
                    byte[] cfg = new byte[Math.min(len, b.remaining())];
                    b.get(cfg);
                    t.audioConfig = cfg;
                    return;
                } else {
                    b.position(Math.min(b.limit(), b.position() + len));
                }
            }
        } catch (RuntimeException ignored) {
            // descriptor raro: se deja sin configuración
        }
    }

    // ------------------------------------------------------------------ fragmentos
    private void parseMoof(ByteBuffer moof, long moofPos) throws IOException {
        children(moof, (type, body) -> {
            if (type.equals("traf")) {
                parseTraf(body, moofPos);
            }
        });
    }

    private void parseTraf(ByteBuffer traf, long moofPos) throws IOException {
        Track[] tr = {null};
        long[] baseOffset = {moofPos};
        long[] baseTime = {-1};
        int[] defDur = {0};
        int[] defSize = {0};
        int[] defFlags = {0};
        long[] cursor = {-1};
        children(traf, (type, body) -> {
            switch (type) {
                case "tfhd" -> {
                    int vf = body.getInt();
                    int flags = vf & 0xffffff;
                    int id = body.getInt();
                    for (Track t : this.tracks) {
                        if (t.id == id) {
                            tr[0] = t;
                        }
                    }
                    if (tr[0] == null) {
                        return;
                    }
                    defDur[0] = tr[0].defDuration;
                    defSize[0] = tr[0].defSize;
                    defFlags[0] = tr[0].defFlags;
                    if ((flags & 1) != 0) {
                        baseOffset[0] = body.getLong();
                    }
                    if ((flags & 2) != 0) {
                        body.getInt();
                    }
                    if ((flags & 8) != 0) {
                        defDur[0] = body.getInt();
                    }
                    if ((flags & 0x10) != 0) {
                        defSize[0] = body.getInt();
                    }
                    if ((flags & 0x20) != 0) {
                        defFlags[0] = body.getInt();
                    }
                }
                case "tfdt" -> {
                    int ver = body.get() & 0xff;
                    body.position(body.position() + 3);
                    baseTime[0] = ver == 1 ? body.getLong() : (body.getInt() & 0xffffffffL);
                }
                case "trun" -> {
                    Track t = tr[0];
                    if (t == null) {
                        return;
                    }
                    int vf = body.getInt();
                    int ver = vf >>> 24;
                    int flags = vf & 0xffffff;
                    int n = body.getInt();
                    long dataOff = baseOffset[0];
                    if ((flags & 1) != 0) {
                        dataOff += body.getInt();
                    } else if (cursor[0] >= 0) {
                        dataOff = cursor[0];
                    }
                    int firstFlags = defFlags[0];
                    boolean hasFirst = (flags & 4) != 0;
                    if (hasFirst) {
                        firstFlags = body.getInt();
                    }
                    long time;
                    if (baseTime[0] >= 0) {
                        time = baseTime[0];
                        baseTime[0] = -1;
                    } else if (t.count > 0) {
                        time = t.dts[t.count - 1] + (t.count > 1 ? t.dts[t.count - 1] - t.dts[t.count - 2] : defDur[0]);
                    } else {
                        time = 0;
                    }
                    t.grow(t.count + n);
                    for (int i = 0; i < n; i++) {
                        int dur = (flags & 0x100) != 0 ? body.getInt() : defDur[0];
                        int sz = (flags & 0x200) != 0 ? body.getInt() : defSize[0];
                        int fl = (flags & 0x400) != 0 ? body.getInt() : (i == 0 && hasFirst ? firstFlags : defFlags[0]);
                        int co = (flags & 0x800) != 0 ? body.getInt() : 0;
                        int k = t.count++;
                        t.offset[k] = dataOff;
                        t.size[k] = sz;
                        t.dts[k] = time;
                        t.cts[k] = co;
                        t.sync[k] = (fl & 0x10000) == 0;       // sample_is_non_sync_sample
                        dataOff += sz;
                        time += dur & 0xffffffffL;
                    }
                    cursor[0] = dataOff;
                }
                default -> {
                }
            }
        });
    }

    private void pick() {
        for (Track t : this.tracks) {
            if (t.handler.equals("vide") && this.video == null && this.unsupportedVideo == null) {
                if ((t.codec.equals("avc1") || t.codec.equals("avc3")) && t.avcC != null && !t.sps.isEmpty()) {
                    this.video = t;
                } else {
                    this.unsupportedVideo = t.codec;
                }
            } else if (t.handler.equals("soun") && this.audio == null && this.unsupportedAudio == null) {
                if (t.codec.equals("mp4a") && t.objectType == 0x40 && t.audioConfig != null) {
                    this.audio = t;
                } else {
                    this.unsupportedAudio = t.codec + (t.objectType != 0 ? "/" + Integer.toHexString(t.objectType) : "");
                }
            }
        }
        if (this.video != null && this.video.count == 0) {
            this.video = null;
        }
        if (this.audio != null && this.audio.count == 0) {
            this.audio = null;
        }
    }
}
