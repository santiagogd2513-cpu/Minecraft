package com.example.beautifuldecor.browser.video;

import com.example.beautifuldecor.browser.video.H264Params.Pps;
import com.example.beautifuldecor.browser.video.H264Params.Sps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decodificador H.264 en Java puro: perfiles Baseline, Main y High (4:2:0, 8 bits, cuadros progresivos), con CAVLC y CABAC,
 * cuadros I/P/B, predicción ponderada, transformada 8x8, matrices de escala y filtro de desbloqueo.
 * No admite entrelazado (campos / MBAFF), FMO, 4:2:2/4:4:4 ni más de 8 bits: lanza {@link Unsupported}.
 * Un "sample" de MP4 (formato AVCC) = un cuadro.
 */
public final class H264Decoder {
    /** Característica del flujo que este decodificador no soporta. */
    public static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Unsupported(String what) {
            super(what);
        }
    }

    // ------------------------------------------------------------------ tipos de macrobloque (para vecinos y filtro)
    static final byte K_UNDEF = 0;
    static final byte K_I4 = 1;          // I_NxN (4x4 u 8x8)
    static final byte K_I16 = 2;
    static final byte K_PCM = 3;
    static final byte K_P = 4;
    static final byte K_PSKIP = 5;
    static final byte K_BDIRECT = 6;     // B_Direct_16x16 con residuo
    static final byte K_BSKIP = 7;
    static final byte K_B = 8;

    static boolean isIntraKind(int k) {
        return k >= K_I4 && k <= K_PCM;
    }

    // ------------------------------------------------------------------ parámetros
    final Sps[] spsTable = new Sps[32];
    final Pps[] ppsTable = new Pps[256];
    Sps sps;
    Pps pps;
    int nalLengthSize = 4;

    // ------------------------------------------------------------------ cabecera de rebanada
    static final int SLICE_P = 0;
    static final int SLICE_B = 1;
    static final int SLICE_I = 2;

    static final class Mmco {
        int op;
        int a;
        int b;
    }

    static final class Slice {
        int nalType;
        int nalRefIdc;
        boolean idr;
        int firstMb;
        int type;
        int ppsId;
        int frameNum;
        int idrPicId;
        int pocLsb;
        int deltaPocBottom;
        int[] deltaPoc = new int[2];
        int redundantPicCnt;
        boolean directSpatial;
        int[] numRef = new int[2];
        int[][] modIdc = new int[2][];
        int[][] modVal = new int[2][];
        int lumaLog2Denom;
        int chromaLog2Denom;
        final int[][][] wt = new int[2][32][3];       // pesos [lista][ref][comp]
        final int[][][] off = new int[2][32][3];      // desplazamientos
        boolean longTermRef;
        boolean noOutputPriorPics;
        boolean adaptiveMarking;
        List<Mmco> mmco = new ArrayList<>();
        int cabacInitIdc;
        int qp;
        int dbIdc;
        int dbAlphaOffset;
        int dbBetaOffset;
        int sliceNum;
    }

    // ------------------------------------------------------------------ estado de POC / DPB
    private int prevPocMsb;
    private int prevPocLsb;
    private int prevFrameNumOffset;
    private int prevFrameNum;
    private int prevRefFrameNum;
    private int maxLongTermFrameIdx = -1;
    private final List<H264Pic> dpb = new ArrayList<>();
    private final List<H264Pic> pool = new ArrayList<>();
    private H264Pic lastOutput;

    // ------------------------------------------------------------------ cuadro en curso
    H264Pic cur;
    private Slice firstSlice;
    private Slice prevSliceHdr;
    private int decodedMbs;
    private boolean pictureOpen;
    private int curPoc;
    private int curFrameNumOffset;
    private int curPocMsb;
    private boolean curHasMmco5;
    int sliceCounter;
    private boolean damaged;

    int mbW;
    int mbH;
    int mbCount;
    int w4;
    int h4;
    int w8;
    int h8;
    int[] mbSlice;
    byte[] mbKind;
    byte[] mbQp;
    int[] mbCbp;
    byte[] mbChromaMode;
    byte[] mbT8;
    byte[] mbDbIdc;
    byte[] mbDbA;
    byte[] mbDbB;
    byte[] mbQpCbOff;
    byte[] ipm;
    byte[] nnzY;
    byte[] nnzCb;
    byte[] nnzCr;
    byte[] cbfDc;
    short[][] mvd = new short[2][];
    byte[] direct8;
    byte[] mbSub;                 // máscara de bloques 4x4 con coeficientes distintos de cero para el filtro (16 bits)
    int[] nzMask;

    final H264Cabac cabac = new H264Cabac();
    final H264Inter.Scratch scratch = new H264Inter.Scratch();

    public H264Decoder() {
    }

    // ------------------------------------------------------------------ interfaz pública
    public void setParameterSets(List<byte[]> spsList, List<byte[]> ppsList, int lengthSize) {
        this.nalLengthSize = lengthSize;
        for (byte[] n : spsList) {
            handleNal(n, 0, n.length);
        }
        for (byte[] n : ppsList) {
            handleNal(n, 0, n.length);
        }
    }

    /** Dimensiones visibles del primer SPS conocido (para preparar la pantalla). */
    public int[] visibleSize() {
        for (Sps s : this.spsTable) {
            if (s != null) {
                return new int[]{s.mbW * 16 - s.cropLeft - s.cropRight, s.mbH * 16 - s.cropTop - s.cropBottom};
            }
        }
        return null;
    }

    /** Devuelve a la reserva un cuadro que ya no se usa. */
    public synchronized void release(H264Pic p) {
        if (p == null) {
            return;
        }
        p.held = false;
        recycleIfFree(p);
    }

    private synchronized void recycleIfFree(H264Pic p) {
        if (!p.held && !p.inDpb && !this.pool.contains(p) && p != this.cur && p != this.lastOutput) {
            if (this.pool.size() < 6) {
                this.pool.add(p);
            }
        }
    }

    /** Decodifica un sample AVCC y devuelve el cuadro resultante (o null si no trae imagen). */
    public H264Pic decode(byte[] sample) {
        H264Pic result = null;
        int p = 0;
        int ls = this.nalLengthSize;
        while (p + ls <= sample.length) {
            int len = 0;
            for (int i = 0; i < ls; i++) {
                len = (len << 8) | (sample[p + i] & 0xff);
            }
            p += ls;
            if (len <= 0 || p + len > sample.length) {
                break;
            }
            try {
                H264Pic done = handleNal(sample, p, len);
                if (done != null) {
                    result = done;
                }
            } catch (Unsupported u) {
                throw u;
            } catch (RuntimeException e) {
                this.damaged = true;       // NAL dañado (cabecera ilegible, etc.): se ignora y se sigue
            }
            p += len;
        }
        H264Pic fin = finishPicture();
        if (fin != null) {
            result = fin;
        }
        return result;
    }

    /** Termina el cuadro pendiente (al final del flujo). */
    public H264Pic flush() {
        return finishPicture();
    }

    // ------------------------------------------------------------------ NAL
    /** Quita los bytes de "emulation prevention" (00 00 03 -> 00 00). */
    static byte[] unescape(byte[] d, int off, int len) {
        byte[] out = new byte[len];
        int n = 0;
        int zeros = 0;
        for (int i = 0; i < len; i++) {
            byte b = d[off + i];
            if (zeros >= 2 && b == 3) {
                zeros = 0;
                continue;
            }
            zeros = b == 0 ? zeros + 1 : 0;
            out[n++] = b;
        }
        return n == len ? out : Arrays.copyOf(out, n);
    }

    private H264Pic handleNal(byte[] data, int off, int len) {
        if (len < 1) {
            return null;
        }
        int hdr = data[off] & 0xff;
        int type = hdr & 0x1f;
        int refIdc = (hdr >> 5) & 3;
        switch (type) {
            case 7 -> {
                byte[] r = unescape(data, off + 1, len - 1);
                Sps s = H264Params.parseSps(r);
                if (s.id < 32) {
                    this.spsTable[s.id] = s;
                }
                return null;
            }
            case 8 -> {
                byte[] r = unescape(data, off + 1, len - 1);
                Pps pp = H264Params.parsePps(r, this.spsTable);
                this.ppsTable[pp.id] = pp;
                return null;
            }
            case 1, 5 -> {
                byte[] r = unescape(data, off + 1, len - 1);
                return handleSlice(r, type, refIdc);
            }
            default -> {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ rebanadas
    private H264Pic handleSlice(byte[] rbsp, int nalType, int nalRefIdc) {
        BitReader br = new BitReader(rbsp, 0, rbsp.length);
        Slice sh = parseSliceHeader(br, nalType, nalRefIdc);
        H264Pic finished = null;
        boolean newPic = !this.pictureOpen || isNewPicture(this.prevSliceHdr, sh);
        if (newPic) {
            finished = finishPicture();
            startPicture(sh);
        }
        this.prevSliceHdr = sh;
        sh.sliceNum = this.sliceCounter++;
        try {
            buildRefLists(sh);
            decodeSliceData(br, sh, rbsp);
        } catch (Unsupported u) {
            throw u;
        } catch (RuntimeException e) {
            this.damaged = true;          // rebanada dañada: se conceala lo que falte al terminar el cuadro
        }
        return finished;
    }

    private static boolean isNewPicture(Slice a, Slice b) {
        if (a == null) {
            return true;
        }
        return a.frameNum != b.frameNum || a.ppsId != b.ppsId || (a.nalRefIdc == 0) != (b.nalRefIdc == 0) || a.idr != b.idr
                || (a.idr && a.idrPicId != b.idrPicId) || a.pocLsb != b.pocLsb || a.deltaPocBottom != b.deltaPocBottom
                || a.deltaPoc[0] != b.deltaPoc[0] || a.deltaPoc[1] != b.deltaPoc[1];
    }

    private Slice parseSliceHeader(BitReader br, int nalType, int nalRefIdc) {
        Slice s = new Slice();
        s.nalType = nalType;
        s.nalRefIdc = nalRefIdc;
        s.idr = nalType == 5;
        s.firstMb = br.ue();
        int st = br.ue();
        if (st > 9) {
            throw new IllegalStateException("tipo de rebanada inválido");
        }
        st %= 5;
        if (st == 3 || st == 4) {
            throw new Unsupported("rebanadas SP/SI");
        }
        s.type = st == 0 ? SLICE_P : st == 1 ? SLICE_B : SLICE_I;
        s.ppsId = br.ue();
        Pps pp = s.ppsId < 256 ? this.ppsTable[s.ppsId] : null;
        if (pp == null) {
            throw new IllegalStateException("falta el PPS " + s.ppsId);
        }
        Sps sp = this.spsTable[pp.spsId];
        if (sp == null) {
            throw new IllegalStateException("falta el SPS " + pp.spsId);
        }
        this.pps = pp;
        if (this.sps != sp) {
            checkSupported(sp);
            this.sps = sp;
        }
        s.frameNum = br.u(sp.log2MaxFrameNum);
        if (!sp.frameMbsOnly) {
            if (br.flag()) {
                throw new Unsupported("video entrelazado (campos)");
            }
        }
        if (s.idr) {
            s.idrPicId = br.ue();
        }
        if (sp.pocType == 0) {
            s.pocLsb = br.u(sp.log2MaxPocLsb);
            if (pp.bottomFieldPicOrderPresent) {
                s.deltaPocBottom = br.se();
            }
        } else if (sp.pocType == 1 && !sp.deltaPicOrderAlwaysZero) {
            s.deltaPoc[0] = br.se();
            if (pp.bottomFieldPicOrderPresent) {
                s.deltaPoc[1] = br.se();
            }
        }
        if (pp.redundantPicCnt) {
            s.redundantPicCnt = br.ue();
        }
        if (s.type == SLICE_B) {
            s.directSpatial = br.flag();
        }
        s.numRef[0] = pp.numRefIdx0;
        s.numRef[1] = pp.numRefIdx1;
        if (s.type != SLICE_I) {
            if (br.flag()) {
                s.numRef[0] = br.ue() + 1;
                if (s.type == SLICE_B) {
                    s.numRef[1] = br.ue() + 1;
                }
            }
            if (s.numRef[0] > 32 || s.numRef[1] > 32) {
                throw new IllegalStateException("demasiadas referencias");
            }
        }
        if (s.type != SLICE_B) {
            s.numRef[1] = 0;
        }
        if (s.type == SLICE_I) {
            s.numRef[0] = 0;
        }
        // modificación de listas de referencia
        for (int l = 0; l < (s.type == SLICE_B ? 2 : s.type == SLICE_P ? 1 : 0); l++) {
            if (br.flag()) {
                List<int[]> cmds = new ArrayList<>();
                while (true) {
                    int idc = br.ue();
                    if (idc == 3) {
                        break;
                    }
                    if (idc > 5 || cmds.size() > 66) {
                        throw new IllegalStateException("modificación de lista inválida");
                    }
                    cmds.add(new int[]{idc, br.ue()});
                }
                s.modIdc[l] = new int[cmds.size()];
                s.modVal[l] = new int[cmds.size()];
                for (int i = 0; i < cmds.size(); i++) {
                    s.modIdc[l][i] = cmds.get(i)[0];
                    s.modVal[l][i] = cmds.get(i)[1];
                }
            }
        }
        if ((pp.weightedPred && s.type == SLICE_P) || (pp.weightedBipredIdc == 1 && s.type == SLICE_B)) {
            s.lumaLog2Denom = br.ue();
            s.chromaLog2Denom = br.ue();
            for (int l = 0; l < (s.type == SLICE_B ? 2 : 1); l++) {
                for (int i = 0; i < s.numRef[l]; i++) {
                    s.wt[l][i][0] = 1 << s.lumaLog2Denom;
                    s.wt[l][i][1] = s.wt[l][i][2] = 1 << s.chromaLog2Denom;
                    if (br.flag()) {
                        s.wt[l][i][0] = br.se();
                        s.off[l][i][0] = br.se();
                    }
                    if (br.flag()) {
                        for (int j = 1; j < 3; j++) {
                            s.wt[l][i][j] = br.se();
                            s.off[l][i][j] = br.se();
                        }
                    }
                }
            }
        }
        if (nalRefIdc != 0) {
            if (s.idr) {
                s.noOutputPriorPics = br.flag();
                s.longTermRef = br.flag();
            } else {
                s.adaptiveMarking = br.flag();
                if (s.adaptiveMarking) {
                    while (true) {
                        int op = br.ue();
                        if (op == 0) {
                            break;
                        }
                        if (op > 6 || s.mmco.size() > 66) {
                            throw new IllegalStateException("MMCO inválido");
                        }
                        Mmco m = new Mmco();
                        m.op = op;
                        if (op == 1 || op == 3) {
                            m.a = br.ue();
                        }
                        if (op == 2) {
                            m.a = br.ue();
                        }
                        if (op == 3 || op == 6) {
                            m.b = br.ue();
                        }
                        if (op == 4) {
                            m.a = br.ue();
                        }
                        s.mmco.add(m);
                    }
                }
            }
        }
        if (pp.cabac && s.type != SLICE_I) {
            s.cabacInitIdc = br.ue();
            if (s.cabacInitIdc > 2) {
                throw new IllegalStateException("cabac_init_idc inválido");
            }
        }
        s.qp = pp.picInitQp + br.se();
        if (pp.deblockingControl) {
            s.dbIdc = br.ue();
            if (s.dbIdc != 1) {
                s.dbAlphaOffset = br.se() * 2;
                s.dbBetaOffset = br.se() * 2;
            }
        }
        return s;
    }

    private static void checkSupported(Sps s) {
        if (s.chromaFormat != 1) {
            throw new Unsupported("solo se admite video 4:2:0 (este es " + (s.chromaFormat == 0 ? "monocromo" : s.chromaFormat == 2 ? "4:2:2" : "4:4:4") + ")");
        }
        if (s.bitDepthLuma != 8 || s.bitDepthChroma != 8) {
            throw new Unsupported("solo se admite video de 8 bits");
        }
        if (s.transformBypass) {
            throw new Unsupported("video sin pérdida (transform bypass)");
        }
        if (s.mbaff) {
            throw new Unsupported("video entrelazado (MBAFF)");
        }
        long px = (long) s.mbW * s.mbH * 256;
        if (px > 4096L * 2304L) {
            throw new Unsupported("video demasiado grande");
        }
    }

    // ------------------------------------------------------------------ inicio y fin de cuadro
    H264Pic[][] refList = new H264Pic[2][34];
    boolean[][] refLong = new boolean[2][34];
    int[][] refPoc = new int[2][34];
    int[][][] implicitW = new int[32][32][2];
    private H264Pic grayPic;
    private int curTop;
    private int curBottom;

    private void allocate(Sps sp) {
        this.mbW = sp.mbW;
        this.mbH = sp.mbH;
        this.mbCount = this.mbW * this.mbH;
        this.w4 = this.mbW * 4;
        this.h4 = this.mbH * 4;
        this.w8 = this.mbW * 2;
        this.h8 = this.mbH * 2;
        this.mbSlice = new int[this.mbCount];
        this.mbKind = new byte[this.mbCount];
        this.mbQp = new byte[this.mbCount];
        this.mbCbp = new int[this.mbCount];
        this.mbChromaMode = new byte[this.mbCount];
        this.mbT8 = new byte[this.mbCount];
        this.mbDbIdc = new byte[this.mbCount];
        this.mbDbA = new byte[this.mbCount];
        this.mbDbB = new byte[this.mbCount];
        this.mbQpCbOff = new byte[this.mbCount];
        this.mbQpCrOff = new byte[this.mbCount];
        this.ipm = new byte[this.w4 * this.h4];
        this.nnzY = new byte[this.w4 * this.h4];
        this.nnzCb = new byte[this.mbW * 2 * this.mbH * 2];
        this.nnzCr = new byte[this.mbW * 2 * this.mbH * 2];
        this.cbfDc = new byte[this.mbCount];
        this.mvd[0] = new short[this.w4 * this.h4 * 2];
        this.mvd[1] = new short[this.w4 * this.h4 * 2];
        this.direct8 = new byte[this.w8 * this.h8];
        this.nzMask = new int[this.mbCount];
        this.pool.clear();
        this.dpb.clear();
        this.grayPic = null;
        this.lastOutput = null;
    }

    private synchronized H264Pic obtainPic() {
        for (int i = 0; i < this.pool.size(); i++) {
            H264Pic p = this.pool.get(i);
            if (p.mbW == this.mbW && p.mbH == this.mbH) {
                this.pool.remove(i);
                p.refShort = false;
                p.refLong = false;
                p.inDpb = false;
                p.held = false;
                return p;
            }
        }
        return new H264Pic(this.mbW, this.mbH);
    }

    private void startPicture(Slice sh) {
        Sps sp = this.sps;
        if (this.mbSlice == null || this.mbW != sp.mbW || this.mbH != sp.mbH) {
            allocate(sp);
        }
        if (!sh.idr && sh.frameNum != this.prevRefFrameNum && sh.frameNum != (this.prevRefFrameNum + 1) % sp.maxFrameNum()) {
            fillGap(sh);
        }
        computePoc(sh);
        this.cur = obtainPic();
        H264Pic c = this.cur;
        c.cropLeft = sp.cropLeft;
        c.cropRight = sp.cropRight;
        c.cropTop = sp.cropTop;
        c.cropBottom = sp.cropBottom;
        c.poc = this.curPoc;
        c.frameNum = sh.frameNum;
        c.longIdx = 0;
        for (int l = 0; l < 2; l++) {
            Arrays.fill(c.ref[l], (byte) -1);
            Arrays.fill(c.refUid[l], -1);
            Arrays.fill(c.mv[l], (short) 0);
        }
        Arrays.fill(this.mbSlice, -1);
        Arrays.fill(this.mbKind, K_UNDEF);
        Arrays.fill(this.nnzY, (byte) 0);
        Arrays.fill(this.nnzCb, (byte) 0);
        Arrays.fill(this.nnzCr, (byte) 0);
        Arrays.fill(this.cbfDc, (byte) 0);
        Arrays.fill(this.mbCbp, 0);
        Arrays.fill(this.nzMask, 0);
        Arrays.fill(this.mbT8, (byte) 0);
        Arrays.fill(this.direct8, (byte) 0);
        Arrays.fill(this.ipm, (byte) 2);
        Arrays.fill(this.mvd[0], (short) 0);
        Arrays.fill(this.mvd[1], (short) 0);
        this.decodedMbs = 0;
        this.pictureOpen = true;
        this.firstSlice = sh;
        this.damaged = false;
        this.sliceCounter = 0;
        this.curHasMmco5 = false;
        buildScaling();
    }

    private void computePoc(Slice sh) {
        Sps sp = this.sps;
        int maxFn = sp.maxFrameNum();
        if (sp.pocType == 0) {
            if (sh.idr) {
                this.prevPocMsb = 0;
                this.prevPocLsb = 0;
            }
            int maxLsb = 1 << sp.log2MaxPocLsb;
            int msb;
            if (sh.pocLsb < this.prevPocLsb && this.prevPocLsb - sh.pocLsb >= maxLsb / 2) {
                msb = this.prevPocMsb + maxLsb;
            } else if (sh.pocLsb > this.prevPocLsb && sh.pocLsb - this.prevPocLsb > maxLsb / 2) {
                msb = this.prevPocMsb - maxLsb;
            } else {
                msb = this.prevPocMsb;
            }
            this.curPocMsb = msb;
            this.curTop = msb + sh.pocLsb;
            this.curBottom = this.curTop + sh.deltaPocBottom;
        } else {
            int offset;
            if (sh.idr) {
                offset = 0;
            } else if (this.prevFrameNum > sh.frameNum) {
                offset = this.prevFrameNumOffset + maxFn;
            } else {
                offset = this.prevFrameNumOffset;
            }
            this.curFrameNumOffset = offset;
            if (sp.pocType == 1) {
                int n = sp.offsetForRefFrame.length;
                int abs = n != 0 ? offset + sh.frameNum : 0;
                if (sh.nalRefIdc == 0 && abs > 0) {
                    abs--;
                }
                int expected = 0;
                if (abs > 0) {
                    int cycle = (abs - 1) / n;
                    int inCycle = (abs - 1) % n;
                    int perCycle = 0;
                    for (int v : sp.offsetForRefFrame) {
                        perCycle += v;
                    }
                    expected = cycle * perCycle;
                    for (int i = 0; i <= inCycle; i++) {
                        expected += sp.offsetForRefFrame[i];
                    }
                }
                if (sh.nalRefIdc == 0) {
                    expected += sp.offsetForNonRefPic;
                }
                this.curTop = expected + sh.deltaPoc[0];
                this.curBottom = this.curTop + sp.offsetForTopToBottom + sh.deltaPoc[1];
            } else {
                int t = sh.idr ? 0 : 2 * (offset + sh.frameNum) - (sh.nalRefIdc == 0 ? 1 : 0);
                this.curTop = t;
                this.curBottom = t;
            }
        }
        this.curPoc = Math.min(this.curTop, this.curBottom);
    }

    private void fillGap(Slice sh) {
        Sps sp = this.sps;
        int maxFn = sp.maxFrameNum();
        int missing = (sh.frameNum - this.prevRefFrameNum - 1 + maxFn) % maxFn;
        int count = Math.min(missing, Math.max(1, sp.numRefFrames));
        int start = (sh.frameNum - count + maxFn) % maxFn;
        for (int i = 0; i < count; i++) {
            int fn = (start + i) % maxFn;
            H264Pic g = obtainPic();
            H264Pic src = null;
            for (H264Pic d : this.dpb) {
                if (d.refShort && (src == null || d.frameNum > src.frameNum)) {
                    src = d;
                }
            }
            if (src != null) {
                System.arraycopy(src.y, 0, g.y, 0, g.y.length);
                System.arraycopy(src.u, 0, g.u, 0, g.u.length);
                System.arraycopy(src.v, 0, g.v, 0, g.v.length);
            } else {
                Arrays.fill(g.y, (byte) 128);
                Arrays.fill(g.u, (byte) 128);
                Arrays.fill(g.v, (byte) 128);
            }
            for (int l = 0; l < 2; l++) {
                Arrays.fill(g.ref[l], (byte) -1);
                Arrays.fill(g.refUid[l], -1);
                Arrays.fill(g.mv[l], (short) 0);
            }
            g.frameNum = fn;
            g.poc = src != null ? src.poc : 0;
            updateFrameNumWrap(fn);
            slidingWindow();
            g.refShort = true;
            g.inDpb = true;
            this.dpb.add(g);
            this.prevRefFrameNum = fn;
        }
        this.prevFrameNum = (sh.frameNum - 1 + maxFn) % maxFn;
    }

    private H264Pic finishPicture() {
        if (!this.pictureOpen) {
            return null;
        }
        this.pictureOpen = false;
        H264Pic pic = this.cur;
        if (this.decodedMbs < this.mbCount) {
            conceal(pic);
        }
        H264Deblock.run(this);
        Slice sh = this.firstSlice;
        Sps sp = this.sps;
        int maxFn = sp.maxFrameNum();
        if (sh.nalRefIdc != 0) {
            markReference(sh, pic);
            if (this.curHasMmco5) {
                this.prevPocMsb = 0;
                this.prevPocLsb = this.curTop - Math.min(this.curTop, this.curBottom);
                this.prevFrameNumOffset = 0;
                this.prevFrameNum = 0;
                this.prevRefFrameNum = 0;
                pic.frameNum = 0;
                pic.poc = 0;
            } else {
                this.prevPocMsb = this.curPocMsb;
                this.prevPocLsb = sh.pocLsb;
                this.prevFrameNumOffset = sp.pocType == 0 ? 0 : this.curFrameNumOffset;
                this.prevFrameNum = sh.frameNum;
                this.prevRefFrameNum = sh.frameNum;
            }
        } else {
            this.prevFrameNumOffset = sp.pocType == 0 ? 0 : this.curFrameNumOffset;
            this.prevFrameNum = sh.frameNum;
        }
        if (sp.pocType == 0 && sh.nalRefIdc == 0) {
            this.prevFrameNumOffset = 0;
        }
        if (maxFn <= 0) {
            throw new IllegalStateException();
        }
        pic.held = true;
        this.lastOutput = pic;
        this.cur = null;
        return pic;
    }

    /** Cubre con el cuadro anterior los macrobloques que no llegaron o se dañaron. */
    private void conceal(H264Pic pic) {
        H264Pic src = this.lastOutput;
        for (int my = 0; my < this.mbH; my++) {
            for (int mx = 0; mx < this.mbW; mx++) {
                if (this.mbSlice[my * this.mbW + mx] >= 0) {
                    continue;
                }
                for (int j = 0; j < 16; j++) {
                    int o = (my * 16 + j) * pic.width + mx * 16;
                    if (src != null) {
                        System.arraycopy(src.y, o, pic.y, o, 16);
                    } else {
                        Arrays.fill(pic.y, o, o + 16, (byte) 128);
                    }
                }
                for (int j = 0; j < 8; j++) {
                    int o = (my * 8 + j) * (pic.width / 2) + mx * 8;
                    if (src != null) {
                        System.arraycopy(src.u, o, pic.u, o, 8);
                        System.arraycopy(src.v, o, pic.v, o, 8);
                    } else {
                        Arrays.fill(pic.u, o, o + 8, (byte) 128);
                        Arrays.fill(pic.v, o, o + 8, (byte) 128);
                    }
                }
                int idx = my * this.mbW + mx;
                this.mbKind[idx] = K_PCM;           // para el filtro: tratado como intra sin filtrar (dbIdc = 1)
                this.mbDbIdc[idx] = 1;
                this.mbQp[idx] = 0;
            }
        }
    }

    // ------------------------------------------------------------------ marcado de referencias
    private int numShort() {
        int n = 0;
        for (H264Pic p : this.dpb) {
            if (p.refShort) {
                n++;
            }
        }
        return n;
    }

    private int numLong() {
        int n = 0;
        for (H264Pic p : this.dpb) {
            if (p.refLong) {
                n++;
            }
        }
        return n;
    }

    private void updateFrameNumWrap(int curFrameNum) {
        int maxFn = this.sps.maxFrameNum();
        for (H264Pic p : this.dpb) {
            if (p.refShort) {
                p.frameNumWrap = p.frameNum > curFrameNum ? p.frameNum - maxFn : p.frameNum;
            }
        }
    }

    private void unmark(H264Pic p) {
        p.refShort = false;
        p.refLong = false;
        if (p.inDpb) {
            p.inDpb = false;
            this.dpb.remove(p);
            recycleIfFree(p);
        }
    }

    private void slidingWindow() {
        int limit = Math.max(this.sps.numRefFrames, 1);
        while (numShort() + numLong() >= limit && numShort() > 0) {
            H264Pic oldest = null;
            for (H264Pic p : this.dpb) {
                if (p.refShort && (oldest == null || p.frameNumWrap < oldest.frameNumWrap)) {
                    oldest = p;
                }
            }
            unmark(oldest);
        }
    }

    private void markReference(Slice sh, H264Pic pic) {
        if (sh.idr) {
            for (H264Pic p : new ArrayList<>(this.dpb)) {
                unmark(p);
            }
            if (sh.longTermRef) {
                pic.refLong = true;
                pic.longIdx = 0;
                this.maxLongTermFrameIdx = 0;
            } else {
                pic.refShort = true;
                this.maxLongTermFrameIdx = -1;
            }
        } else {
            updateFrameNumWrap(sh.frameNum);
            if (sh.adaptiveMarking) {
                for (Mmco m : sh.mmco) {
                    switch (m.op) {
                        case 1 -> {
                            int picNum = sh.frameNum - (m.a + 1);
                            for (H264Pic p : new ArrayList<>(this.dpb)) {
                                if (p.refShort && p.frameNumWrap == picNum) {
                                    unmark(p);
                                }
                            }
                        }
                        case 2 -> {
                            for (H264Pic p : new ArrayList<>(this.dpb)) {
                                if (p.refLong && p.longIdx == m.a) {
                                    unmark(p);
                                }
                            }
                        }
                        case 3 -> {
                            int picNum = sh.frameNum - (m.a + 1);
                            for (H264Pic p : new ArrayList<>(this.dpb)) {
                                if (p.refLong && p.longIdx == m.b) {
                                    unmark(p);
                                }
                            }
                            for (H264Pic p : this.dpb) {
                                if (p.refShort && p.frameNumWrap == picNum) {
                                    p.refShort = false;
                                    p.refLong = true;
                                    p.longIdx = m.b;
                                }
                            }
                        }
                        case 4 -> {
                            this.maxLongTermFrameIdx = m.a - 1;
                            for (H264Pic p : new ArrayList<>(this.dpb)) {
                                if (p.refLong && p.longIdx > this.maxLongTermFrameIdx) {
                                    unmark(p);
                                }
                            }
                        }
                        case 5 -> {
                            for (H264Pic p : new ArrayList<>(this.dpb)) {
                                unmark(p);
                            }
                            this.maxLongTermFrameIdx = -1;
                            this.curHasMmco5 = true;
                        }
                        default -> {
                            for (H264Pic p : new ArrayList<>(this.dpb)) {
                                if (p.refLong && p.longIdx == m.b) {
                                    unmark(p);
                                }
                            }
                            pic.refLong = true;
                            pic.longIdx = m.b;
                        }
                    }
                }
            } else {
                slidingWindow();
            }
            if (!pic.refLong) {
                pic.refShort = true;
            }
        }
        pic.inDpb = true;
        this.dpb.add(pic);
        int limit = Math.max(this.sps.numRefFrames, 1);
        while (numShort() + numLong() > limit && numShort() > 0) {
            H264Pic oldest = null;
            for (H264Pic p : this.dpb) {
                if (p.refShort && p != pic && (oldest == null || p.frameNumWrap < oldest.frameNumWrap)) {
                    oldest = p;
                }
            }
            if (oldest == null) {
                break;
            }
            unmark(oldest);
        }
    }

    // ------------------------------------------------------------------ listas de referencia
    private H264Pic gray() {
        if (this.grayPic == null) {
            H264Pic g = new H264Pic(this.mbW, this.mbH);
            Arrays.fill(g.y, (byte) 128);
            Arrays.fill(g.u, (byte) 128);
            Arrays.fill(g.v, (byte) 128);
            Arrays.fill(g.ref[0], (byte) -1);
            Arrays.fill(g.ref[1], (byte) -1);
            this.grayPic = g;
        }
        return this.grayPic;
    }

    private void buildRefLists(Slice sh) {
        for (int l = 0; l < 2; l++) {
            Arrays.fill(this.refList[l], null);
            Arrays.fill(this.refLong[l], false);
        }
        if (sh.type == SLICE_I) {
            return;
        }
        Sps sp = this.sps;
        int maxFn = sp.maxFrameNum();
        updateFrameNumWrap(sh.frameNum);
        List<H264Pic> shorts = new ArrayList<>();
        List<H264Pic> longs = new ArrayList<>();
        for (H264Pic p : this.dpb) {
            if (p.refShort) {
                shorts.add(p);
            } else if (p.refLong) {
                longs.add(p);
            }
        }
        longs.sort((a, b) -> Integer.compare(a.longIdx, b.longIdx));
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<H264Pic>[] init = new List[2];
        if (sh.type == SLICE_P) {
            shorts.sort((a, b) -> Integer.compare(b.frameNumWrap, a.frameNumWrap));
            init[0] = new ArrayList<>(shorts);
            init[0].addAll(longs);
        } else {
            List<H264Pic> before = new ArrayList<>();
            List<H264Pic> after = new ArrayList<>();
            for (H264Pic p : shorts) {
                if (p.poc < this.curPoc) {
                    before.add(p);
                } else {
                    after.add(p);
                }
            }
            before.sort((a, b) -> Integer.compare(b.poc, a.poc));
            after.sort((a, b) -> Integer.compare(a.poc, b.poc));
            init[0] = new ArrayList<>(before);
            init[0].addAll(after);
            init[0].addAll(longs);
            init[1] = new ArrayList<>(after);
            init[1].addAll(before);
            init[1].addAll(longs);
            if (init[1].size() > 1 && init[1].equals(init[0])) {
                H264Pic t = init[1].get(0);
                init[1].set(0, init[1].get(1));
                init[1].set(1, t);
            }
        }
        int lists = sh.type == SLICE_B ? 2 : 1;
        for (int l = 0; l < lists; l++) {
            int n = sh.numRef[l];
            H264Pic[] list = new H264Pic[n + 1];
            for (int i = 0; i < n && i < init[l].size(); i++) {
                list[i] = init[l].get(i);
            }
            if (sh.modIdc[l] != null) {
                int pred = sh.frameNum;
                int idx = 0;
                for (int c = 0; c < sh.modIdc[l].length; c++) {
                    int idc = sh.modIdc[l][c];
                    int val = sh.modVal[l][c];
                    H264Pic target = null;
                    if (idc == 0 || idc == 1) {
                        int diff = val + 1;
                        int noWrap;
                        if (idc == 0) {
                            noWrap = pred - diff;
                            if (noWrap < 0) {
                                noWrap += maxFn;
                            }
                        } else {
                            noWrap = pred + diff;
                            if (noWrap >= maxFn) {
                                noWrap -= maxFn;
                            }
                        }
                        pred = noWrap;
                        int picNum = noWrap > sh.frameNum ? noWrap - maxFn : noWrap;
                        for (H264Pic p : shorts) {
                            if (p.frameNumWrap == picNum) {
                                target = p;
                            }
                        }
                    } else if (idc == 2) {
                        for (H264Pic p : longs) {
                            if (p.longIdx == val) {
                                target = p;
                            }
                        }
                    }
                    if (idx >= n) {
                        break;
                    }
                    if (target == null) {
                        continue;
                    }
                    for (int k = n; k > idx; k--) {
                        list[k] = list[k - 1];
                    }
                    list[idx++] = target;
                    int w = idx;
                    for (int k = idx; k <= n; k++) {
                        if (list[k] != target) {
                            list[w++] = list[k];
                        }
                    }
                }
            }
            H264Pic fallback = null;
            for (int i = 0; i < n; i++) {
                if (list[i] != null) {
                    fallback = list[i];
                    break;
                }
            }
            if (fallback == null) {
                fallback = this.dpb.isEmpty() ? gray() : this.dpb.get(this.dpb.size() - 1);
            }
            for (int i = 0; i < n; i++) {
                H264Pic p = list[i] != null ? list[i] : fallback;
                this.refList[l][i] = p;
                this.refLong[l][i] = p.refLong;
                this.refPoc[l][i] = p.poc;
            }
        }
        // pesos implícitos
        if (sh.type == SLICE_B && this.pps.weightedBipredIdc == 2) {
            for (int i = 0; i < sh.numRef[0]; i++) {
                for (int j = 0; j < sh.numRef[1]; j++) {
                    int w0 = 32;
                    int w1 = 32;
                    H264Pic r0 = this.refList[0][i];
                    H264Pic r1 = this.refList[1][j];
                    if (!this.refLong[0][i] && !this.refLong[1][j]) {
                        int tb = clip3(-128, 127, this.curPoc - r0.poc);
                        int td = clip3(-128, 127, r1.poc - r0.poc);
                        if (td != 0) {
                            int tx = (16384 + Math.abs(td / 2)) / td;
                            int dsf = clip3(-1024, 1023, (tb * tx + 32) >> 6);
                            int w = dsf >> 2;
                            if (w >= -64 && w <= 128) {
                                w1 = w;
                                w0 = 64 - w;
                            }
                        }
                    }
                    this.implicitW[i][j][0] = w0;
                    this.implicitW[i][j][1] = w1;
                }
            }
        }
    }

    static int clip3(int lo, int hi, int v) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ------------------------------------------------------------------ matrices de escala
    /** levelScale4[lista 0..5][qp%6][posición raster 4x4]; levelScale8[lista 0..1][qp%6][posición raster 8x8]. */
    final int[][][] levelScale4 = new int[6][6][16];
    final int[][][] levelScale8 = new int[2][6][64];

    private void buildScaling() {
        Sps sp = this.sps;
        Pps pp = this.pps;
        int[][] l4 = new int[6][];
        int[][] l8 = new int[2][];
        for (int i = 0; i < 6; i++) {
            if (pp.scalingPresent && pp.list4 != null) {
                l4[i] = pp.list4[i];
            } else if (sp.scalingPresent && sp.list4 != null) {
                l4[i] = sp.list4[i];
            } else {
                l4[i] = null;
            }
        }
        for (int i = 0; i < 2; i++) {
            if (pp.scalingPresent && pp.list8 != null) {
                l8[i] = pp.list8[i];
            } else if (sp.scalingPresent && sp.list8 != null) {
                l8[i] = sp.list8[i];
            }
        }
        for (int li = 0; li < 6; li++) {
            int[] w = new int[16];
            Arrays.fill(w, 16);
            if (l4[li] != null) {
                for (int k = 0; k < 16; k++) {
                    w[H264Data.ZIGZAG4[k]] = l4[li][k];
                }
            }
            for (int m = 0; m < 6; m++) {
                for (int pos = 0; pos < 16; pos++) {
                    int x = pos & 3;
                    int y = pos >> 2;
                    int cls = (x & 1) == 0 && (y & 1) == 0 ? 0 : (x & 1) == 1 && (y & 1) == 1 ? 1 : 2;
                    this.levelScale4[li][m][pos] = w[pos] * H264Data.NORM4[m][cls];
                }
            }
        }
        for (int li = 0; li < 2; li++) {
            int[] w = new int[64];
            Arrays.fill(w, 16);
            if (l8[li] != null) {
                for (int k = 0; k < 64; k++) {
                    w[H264Data.ZIGZAG8[k]] = l8[li][k];
                }
            }
            for (int m = 0; m < 6; m++) {
                for (int pos = 0; pos < 64; pos++) {
                    int x = pos & 7;
                    int y = pos >> 3;
                    int cls;
                    if ((y & 3) == 0 && (x & 3) == 0) {
                        cls = 0;
                    } else if ((y & 1) == 1 && (x & 1) == 1) {
                        cls = 1;
                    } else if ((y & 3) == 2 && (x & 3) == 2) {
                        cls = 2;
                    } else if (((y & 3) == 0 && (x & 1) == 1) || ((y & 1) == 1 && (x & 3) == 0)) {
                        cls = 3;
                    } else if (((y & 3) == 0 && (x & 3) == 2) || ((y & 3) == 2 && (x & 3) == 0)) {
                        cls = 4;
                    } else {
                        cls = 5;
                    }
                    this.levelScale8[li][m][pos] = w[pos] * H264Data.NORM8[m][cls];
                }
            }
        }
    }

    // ------------------------------------------------------------------ rebanada en curso
    private Slice sl;
    private BitReader br;
    private boolean cabacOn;
    private int qp;
    private boolean lastQpDeltaNz;
    int mbX;
    int mbY;
    int mbIdx;
    private boolean availA;
    private boolean availB;
    private boolean availC;
    private boolean availD;
    private int sliceNum;
    private byte[] pY;
    private byte[] pU;
    private byte[] pV;
    private int strideY;
    private int strideC;
    private byte[] data;                 // RBSP de la rebanada (para CABAC / PCM)

    // posiciones de los bloques 4x4 de luma en orden de decodificación
    static final int[] BLK_X = {0, 1, 0, 1, 2, 3, 2, 3, 0, 1, 0, 1, 2, 3, 2, 3};
    static final int[] BLK_Y = {0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3};
    static final int[][] BLK_AT = new int[4][4];
    static final boolean[] TR4 = new boolean[16];     // el bloque de arriba a la derecha ya está decodificado (dentro del MB, by > 0)

    static {
        for (int i = 0; i < 16; i++) {
            BLK_AT[BLK_Y[i]][BLK_X[i]] = i;
        }
        for (int i = 0; i < 16; i++) {
            int x = BLK_X[i];
            int y = BLK_Y[i];
            if (y > 0 && x < 3) {
                TR4[i] = BLK_AT[y - 1][x + 1] < i;
            }
        }
    }

    private void decodeSliceData(BitReader reader, Slice sh, byte[] rbsp) {
        this.sl = sh;
        this.br = reader;
        this.data = rbsp;
        this.cabacOn = this.pps.cabac;
        this.qp = sh.qp;
        this.sliceNum = sh.sliceNum;
        this.lastQpDeltaNz = false;
        H264Pic c = this.cur;
        this.pY = c.y;
        this.pU = c.u;
        this.pV = c.v;
        this.strideY = c.width;
        this.strideC = c.width / 2;
        int addr = sh.firstMb;
        if (addr >= this.mbCount) {
            throw new IllegalStateException("first_mb fuera de rango");
        }
        boolean intraSlice = sh.type == SLICE_I;
        if (this.cabacOn) {
            reader.alignToByte();
            this.cabac.initContexts(sh.qp, intraSlice ? 0 : 1 + sh.cabacInitIdc);
            this.cabac.start(rbsp, reader.bitPos(), rbsp.length * 8);
            while (true) {
                if (addr >= this.mbCount) {
                    throw new IllegalStateException("la rebanada se pasa del cuadro");
                }
                beginMb(addr);
                boolean skipped = !intraSlice && cabacSkipFlag();
                if (skipped) {
                    decodeSkipMb();
                } else {
                    decodeMb();
                }
                this.decodedMbs++;
                if (this.cabac.terminate() != 0) {
                    break;
                }
                addr++;
            }
        } else {
            boolean more = true;
            while (more) {
                if (!intraSlice) {
                    int run = reader.ue();
                    for (int i = 0; i < run; i++) {
                        if (addr >= this.mbCount) {
                            throw new IllegalStateException("la rebanada se pasa del cuadro");
                        }
                        beginMb(addr);
                        decodeSkipMb();
                        this.decodedMbs++;
                        addr++;
                    }
                    if (run > 0) {
                        more = reader.moreRbspData();
                    }
                }
                if (more) {
                    if (addr >= this.mbCount) {
                        throw new IllegalStateException("la rebanada se pasa del cuadro");
                    }
                    beginMb(addr);
                    decodeMb();
                    this.decodedMbs++;
                    addr++;
                    more = reader.moreRbspData();
                }
            }
        }
    }

    private void beginMb(int addr) {
        this.mbIdx = addr;
        this.mbX = addr % this.mbW;
        this.mbY = addr / this.mbW;
        this.mbSlice[addr] = this.sliceNum;
        this.availA = this.mbX > 0 && this.mbSlice[addr - 1] == this.sliceNum;
        this.availB = this.mbY > 0 && this.mbSlice[addr - this.mbW] == this.sliceNum;
        this.availC = this.mbY > 0 && this.mbX < this.mbW - 1 && this.mbSlice[addr - this.mbW + 1] == this.sliceNum;
        this.availD = this.mbX > 0 && this.mbY > 0 && this.mbSlice[addr - this.mbW - 1] == this.sliceNum;
        this.mbDbIdc[addr] = (byte) this.sl.dbIdc;
        this.mbDbA[addr] = (byte) this.sl.dbAlphaOffset;
        this.mbDbB[addr] = (byte) this.sl.dbBetaOffset;
        this.mbQpCbOff[addr] = (byte) this.pps.chromaQpOffset;
        this.mbQpCrOff[addr] = (byte) this.pps.chromaQpOffset2;
        this.mbChromaMode[addr] = 0;
        this.mbT8[addr] = 0;
        this.mbCbp[addr] = 0;
    }

    byte[] mbQpCrOff;

    // ------------------------------------------------------------------ movimiento: vecinos y predicción
    static final int NA = -2;
    private int done0;
    private int done1;
    private int mvpX;
    private int mvpY;

    private int refAt(int list, int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= this.w4 || gy >= this.h4) {
            return NA;
        }
        int m = (gy >> 2) * this.mbW + (gx >> 2);
        if (m == this.mbIdx) {
            int bit = (gy & 3) * 4 + (gx & 3);
            if ((((list == 0 ? this.done0 : this.done1) >> bit) & 1) == 0) {
                return NA;
            }
        } else if (this.mbSlice[m] != this.sliceNum) {
            return NA;
        }
        return this.cur.ref[list][(gy >> 1) * this.w8 + (gx >> 1)];
    }

    private int mvAt(int list, int gx, int gy, int comp) {
        return this.cur.mv[list][(gy * this.w4 + gx) * 2 + comp];
    }

    private static int median(int a, int b, int c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    /** shape: 0 mediana, 1 = 16x8 arriba, 2 = 16x8 abajo, 3 = 8x16 izquierda, 4 = 8x16 derecha. */
    private void mvPred(int list, int ref, int gx, int gy, int wBlk, int shape) {
        int refA = refAt(list, gx - 1, gy);
        int refB = refAt(list, gx, gy - 1);
        int refC = refAt(list, gx + wBlk, gy - 1);
        int ax = 0;
        int ay = 0;
        int bx = 0;
        int by = 0;
        int cx = 0;
        int cy = 0;
        if (refA != NA) {
            ax = mvAt(list, gx - 1, gy, 0);
            ay = mvAt(list, gx - 1, gy, 1);
        }
        if (refB != NA) {
            bx = mvAt(list, gx, gy - 1, 0);
            by = mvAt(list, gx, gy - 1, 1);
        }
        if (refC != NA) {
            cx = mvAt(list, gx + wBlk, gy - 1, 0);
            cy = mvAt(list, gx + wBlk, gy - 1, 1);
        } else {
            refC = refAt(list, gx - 1, gy - 1);
            if (refC != NA) {
                cx = mvAt(list, gx - 1, gy - 1, 0);
                cy = mvAt(list, gx - 1, gy - 1, 1);
            }
        }
        if (shape == 1 && refB == ref) {
            this.mvpX = bx;
            this.mvpY = by;
            return;
        }
        if ((shape == 2 || shape == 3) && refA == ref) {
            this.mvpX = ax;
            this.mvpY = ay;
            return;
        }
        if (shape == 4 && refC == ref) {
            this.mvpX = cx;
            this.mvpY = cy;
            return;
        }
        if (refB == NA && refC == NA && refA != NA) {
            refB = refA;
            refC = refA;
            bx = ax;
            by = ay;
            cx = ax;
            cy = ay;
        }
        int matches = (refA == ref ? 1 : 0) + (refB == ref ? 1 : 0) + (refC == ref ? 1 : 0);
        if (matches == 1) {
            if (refA == ref) {
                this.mvpX = ax;
                this.mvpY = ay;
            } else if (refB == ref) {
                this.mvpX = bx;
                this.mvpY = by;
            } else {
                this.mvpX = cx;
                this.mvpY = cy;
            }
            return;
        }
        this.mvpX = median(ax, bx, cx);
        this.mvpY = median(ay, by, cy);
    }

    private void putMv(int list, int bx, int by, int w4b, int h4b, int mx, int my) {
        short[] mv = this.cur.mv[list];
        int base = this.mbY * 4 + by;
        for (int j = 0; j < h4b; j++) {
            int o = ((base + j) * this.w4 + this.mbX * 4 + bx) * 2;
            for (int i = 0; i < w4b; i++) {
                mv[o + 2 * i] = (short) mx;
                mv[o + 2 * i + 1] = (short) my;
            }
        }
        int mask = 0;
        for (int j = 0; j < h4b; j++) {
            for (int i = 0; i < w4b; i++) {
                mask |= 1 << ((by + j) * 4 + bx + i);
            }
        }
        if (list == 0) {
            this.done0 |= mask;
        } else {
            this.done1 |= mask;
        }
    }

    /** Pone el índice de referencia (y la imagen a la que apunta) para bloques de 8x8: (x8, y8) dentro del MB, w8b x h8b bloques. */
    private void putRef(int list, int x8, int y8, int w8b, int h8b, int ref) {
        int uid = ref >= 0 ? this.refList[list][ref].uid : -1;
        for (int j = 0; j < h8b; j++) {
            int o = (this.mbY * 2 + y8 + j) * this.w8 + this.mbX * 2 + x8;
            for (int i = 0; i < w8b; i++) {
                this.cur.ref[list][o + i] = (byte) ref;
                this.cur.refUid[list][o + i] = uid;
            }
        }
    }

    /** Vector de P_Skip en la lista 0, referencia 0. */
    private void pSkipMotion() {
        int gx = this.mbX * 4;
        int gy = this.mbY * 4;
        this.done0 = 0;
        int refA = refAt(0, gx - 1, gy);
        int refB = refAt(0, gx, gy - 1);
        int mx = 0;
        int my = 0;
        if (refA != NA && refB != NA) {
            boolean zeroA = refA == 0 && mvAt(0, gx - 1, gy, 0) == 0 && mvAt(0, gx - 1, gy, 1) == 0;
            boolean zeroB = refB == 0 && mvAt(0, gx, gy - 1, 0) == 0 && mvAt(0, gx, gy - 1, 1) == 0;
            if (!zeroA && !zeroB) {
                mvPred(0, 0, gx, gy, 4, 0);
                mx = this.mvpX;
                my = this.mvpY;
            }
        }
        putRef(0, 0, 0, 2, 2, 0);
        putMv(0, 0, 0, 4, 4, mx, my);
    }

    // ------------------------------------------------------------------ modo directo (B)
    private static int minPositive(int a, int b) {
        return a >= 0 && b >= 0 ? Math.min(a, b) : Math.max(a, b);
    }

    private final int[] dirRef = new int[2];
    private final int[] dirMvx = new int[2];
    private final int[] dirMvy = new int[2];
    private boolean dirBoth0;

    /** Prepara la predicción espacial: índices de referencia y vectores de la partición 16x16 vecina. */
    private void spatialSetup() {
        int gx = this.mbX * 4;
        int gy = this.mbY * 4;
        this.done0 = 0;
        this.done1 = 0;
        for (int l = 0; l < 2; l++) {
            int rA = refAt(l, gx - 1, gy);
            int rB = refAt(l, gx, gy - 1);
            int rC = refAt(l, gx + 4, gy - 1);
            if (rC == NA) {
                rC = refAt(l, gx - 1, gy - 1);
            }
            rA = rA == NA ? -1 : rA;
            rB = rB == NA ? -1 : rB;
            rC = rC == NA ? -1 : rC;
            this.dirRef[l] = minPositive(rA, minPositive(rB, rC));
        }
        this.dirBoth0 = false;
        if (this.dirRef[0] < 0 && this.dirRef[1] < 0) {
            this.dirRef[0] = 0;
            this.dirRef[1] = 0;
            this.dirBoth0 = true;
            this.dirMvx[0] = this.dirMvy[0] = this.dirMvx[1] = this.dirMvy[1] = 0;
        } else {
            for (int l = 0; l < 2; l++) {
                if (this.dirRef[l] >= 0) {
                    mvPred(l, this.dirRef[l], gx, gy, 4, 0);
                    this.dirMvx[l] = this.mvpX;
                    this.dirMvy[l] = this.mvpY;
                } else {
                    this.dirMvx[l] = 0;
                    this.dirMvy[l] = 0;
                }
            }
        }
    }

    /** Aplica la predicción directa a un bloque (bx, by, w4b, h4b en unidades de 4x4 del MB); x8/y8 solo para el índice de referencia. */
    private void directBlock(int bx, int by, int w4b, int h4b) {
        H264Pic col = this.refList[1][0];
        boolean inf = this.sps.direct8x8Inference;
        int cx4 = this.mbX * 4 + bx;
        int cy4 = this.mbY * 4 + by;
        if (inf) {
            cx4 = this.mbX * 4 + (bx < 2 ? 0 : 3);
            cy4 = this.mbY * 4 + (by < 2 ? 0 : 3);
        }
        int c8 = (cy4 >> 1) * this.w8 + (cx4 >> 1);
        int colRef = -1;
        int colMvx = 0;
        int colMvy = 0;
        int colUid = -1;
        if (col.ref[0][c8] >= 0) {
            colRef = col.ref[0][c8];
            colMvx = col.mv[0][(cy4 * this.w4 + cx4) * 2];
            colMvy = col.mv[0][(cy4 * this.w4 + cx4) * 2 + 1];
            colUid = col.refUid[0][c8];
        } else if (col.ref[1][c8] >= 0) {
            colRef = col.ref[1][c8];
            colMvx = col.mv[1][(cy4 * this.w4 + cx4) * 2];
            colMvy = col.mv[1][(cy4 * this.w4 + cx4) * 2 + 1];
            colUid = col.refUid[1][c8];
        }
        int x8 = bx >> 1;
        int y8 = by >> 1;
        int w8b = Math.max(1, w4b >> 1);
        int h8b = Math.max(1, h4b >> 1);
        if (this.sl.directSpatial) {
            boolean colZero = !this.refLong[1][0] && colRef == 0 && colMvx >= -1 && colMvx <= 1 && colMvy >= -1 && colMvy <= 1;
            for (int l = 0; l < 2; l++) {
                int r = this.dirRef[l];
                int mx = this.dirMvx[l];
                int my = this.dirMvy[l];
                if (r < 0) {
                    putRef(l, x8, y8, w8b, h8b, -1);
                    putMv(l, bx, by, w4b, h4b, 0, 0);
                    continue;
                }
                if (r == 0 && colZero) {
                    mx = 0;
                    my = 0;
                }
                if (this.dirBoth0) {
                    mx = 0;
                    my = 0;
                }
                putRef(l, x8, y8, w8b, h8b, r);
                putMv(l, bx, by, w4b, h4b, mx, my);
            }
        } else {
            int ref0 = 0;
            if (colRef >= 0) {
                ref0 = 0;
                for (int i = 0; i < this.sl.numRef[0]; i++) {
                    if (this.refList[0][i].uid == colUid) {
                        ref0 = i;
                        break;
                    }
                }
            } else {
                colMvx = 0;
                colMvy = 0;
            }
            int mv0x;
            int mv0y;
            int mv1x;
            int mv1y;
            int poc0 = this.refPoc[0][ref0];
            int poc1 = this.refPoc[1][0];
            int td = clip3(-128, 127, poc1 - poc0);
            if (this.refLong[0][ref0] || td == 0) {
                mv0x = colMvx;
                mv0y = colMvy;
                mv1x = 0;
                mv1y = 0;
            } else {
                int tb = clip3(-128, 127, this.curPoc - poc0);
                int tx = (16384 + Math.abs(td / 2)) / td;
                int dsf = clip3(-1024, 1023, (tb * tx + 32) >> 6);
                mv0x = (dsf * colMvx + 128) >> 8;
                mv0y = (dsf * colMvy + 128) >> 8;
                mv1x = mv0x - colMvx;
                mv1y = mv0y - colMvy;
            }
            putRef(0, x8, y8, w8b, h8b, ref0);
            putRef(1, x8, y8, w8b, h8b, 0);
            putMv(0, bx, by, w4b, h4b, mv0x, mv0y);
            putMv(1, bx, by, w4b, h4b, mv1x, mv1y);
        }
    }

    /** Predicción directa para todo el macrobloque (B_Skip / B_Direct_16x16). */
    private void directMb() {
        this.done0 = 0;
        this.done1 = 0;
        if (this.sl.directSpatial) {
            spatialSetup();
        }
        if (this.sps.direct8x8Inference) {
            for (int i = 0; i < 4; i++) {
                directBlock((i & 1) * 2, (i >> 1) * 2, 2, 2);
            }
        } else {
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    directBlock(i, j, 1, 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ vecinos (disponibilidad por bloques)
    /** El bloque 4x4 de luma en coordenadas globales (gx, gy) existe y es del macrobloque actual o de la misma rebanada. */
    private boolean nbAvail(int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= this.w4 || gy >= this.h4) {
            return false;
        }
        int m = (gy >> 2) * this.mbW + (gx >> 2);
        return m == this.mbIdx || this.mbSlice[m] == this.sliceNum;
    }

    private boolean nbAvailC(int cx, int cy) {
        if (cx < 0 || cy < 0) {
            return false;
        }
        int m = (cy >> 1) * this.mbW + (cx >> 1);
        return m == this.mbIdx || this.mbSlice[m] == this.sliceNum;
    }

    private static boolean isSkipKind(int k) {
        return k == K_PSKIP || k == K_BSKIP;
    }

    // ------------------------------------------------------------------ elementos de sintaxis CABAC
    private boolean cabacSkipFlag() {
        int base = this.sl.type == SLICE_P ? 11 : 24;
        int inc = 0;
        if (this.availA && !isSkipKind(this.mbKind[this.mbIdx - 1])) {
            inc++;
        }
        if (this.availB && !isSkipKind(this.mbKind[this.mbIdx - this.mbW])) {
            inc++;
        }
        return this.cabac.decision(base + inc) != 0;
    }

    /** Tipo de macrobloque intra (0 = I_NxN, 1..24 = I16x16, 25 = PCM). base: 3 (I), 17 (P), 32 (B). */
    private int cabacMbTypeIntra(int base, boolean intraSlice) {
        H264Cabac c = this.cabac;
        int s = base;
        if (intraSlice) {
            int inc = 0;
            if (this.availA && this.mbKind[this.mbIdx - 1] != K_I4) {
                inc++;
            }
            if (this.availB && this.mbKind[this.mbIdx - this.mbW] != K_I4) {
                inc++;
            }
            if (c.decision(s + inc) == 0) {
                return 0;
            }
            s += 2;
        } else if (c.decision(s) == 0) {
            return 0;
        }
        if (c.terminate() != 0) {
            return 25;
        }
        int ex = intraSlice ? 1 : 0;
        int t = 1;
        t += 12 * c.decision(s + 1);
        if (c.decision(s + 2) != 0) {
            t += 4 + 4 * c.decision(s + 2 + ex);
        }
        t += 2 * c.decision(s + 3 + ex);
        t += c.decision(s + 3 + 2 * ex);
        return t;
    }

    private int cabacMbTypeP() {
        H264Cabac c = this.cabac;
        if (c.decision(14) == 0) {
            if (c.decision(15) == 0) {
                return 3 * c.decision(16);
            }
            return 2 - c.decision(17);
        }
        return 5 + cabacMbTypeIntra(17, false);
    }

    private int cabacMbTypeB() {
        H264Cabac c = this.cabac;
        int inc = 0;
        if (this.availA) {
            int k = this.mbKind[this.mbIdx - 1];
            if (k != K_BSKIP && k != K_BDIRECT) {
                inc++;
            }
        }
        if (this.availB) {
            int k = this.mbKind[this.mbIdx - this.mbW];
            if (k != K_BSKIP && k != K_BDIRECT) {
                inc++;
            }
        }
        if (c.decision(27 + inc) == 0) {
            return 0;
        }
        if (c.decision(27 + 3) == 0) {
            return 1 + c.decision(27 + 5);
        }
        int bits = c.decision(27 + 4) << 3;
        bits |= c.decision(27 + 5) << 2;
        bits |= c.decision(27 + 5) << 1;
        bits |= c.decision(27 + 5);
        if (bits < 8) {
            return bits + 3;
        }
        if (bits == 13) {
            return 23 + cabacMbTypeIntra(32, false);
        }
        if (bits == 14) {
            return 11;
        }
        if (bits == 15) {
            return 22;
        }
        bits = (bits << 1) | c.decision(27 + 5);
        return bits - 4;
    }

    private int cabacSubTypeP() {
        H264Cabac c = this.cabac;
        if (c.decision(21) != 0) {
            return 0;
        }
        if (c.decision(22) == 0) {
            return 1;
        }
        if (c.decision(23) != 0) {
            return 2;
        }
        return 3;
    }

    private int cabacSubTypeB() {
        H264Cabac c = this.cabac;
        if (c.decision(36) == 0) {
            return 0;
        }
        if (c.decision(37) == 0) {
            return 1 + c.decision(39);
        }
        int type = 3;
        if (c.decision(38) != 0) {
            if (c.decision(39) != 0) {
                return 11 + c.decision(39);
            }
            type += 4;
        }
        type += 2 * c.decision(39);
        type += c.decision(39);
        return type;
    }

    private boolean cabacT8Flag() {
        int inc = 0;
        if (this.availA && this.mbT8[this.mbIdx - 1] != 0) {
            inc++;
        }
        if (this.availB && this.mbT8[this.mbIdx - this.mbW] != 0) {
            inc++;
        }
        return this.cabac.decision(399 + inc) != 0;
    }

    private int cabacChromaMode() {
        H264Cabac c = this.cabac;
        int inc = 0;
        if (this.availA && this.mbChromaMode[this.mbIdx - 1] != 0) {
            inc++;
        }
        if (this.availB && this.mbChromaMode[this.mbIdx - this.mbW] != 0) {
            inc++;
        }
        if (c.decision(64 + inc) == 0) {
            return 0;
        }
        if (c.decision(64 + 3) == 0) {
            return 1;
        }
        if (c.decision(64 + 3) == 0) {
            return 2;
        }
        return 3;
    }

    private int cabacCbp() {
        H264Cabac c = this.cabac;
        int cbpA = this.availA ? this.mbCbp[this.mbIdx - 1] : 0x0f;
        int cbpB = this.availB ? this.mbCbp[this.mbIdx - this.mbW] : 0x0f;
        int cbp = 0;
        for (int b8 = 0; b8 < 4; b8++) {
            int bitA = (b8 & 1) == 0 ? (cbpA >> (b8 + 1)) & 1 : (cbp >> (b8 - 1)) & 1;
            int bitB = (b8 & 2) == 0 ? (cbpB >> (b8 + 2)) & 1 : (cbp >> (b8 - 2)) & 1;
            int inc = (bitA == 0 ? 1 : 0) + 2 * (bitB == 0 ? 1 : 0);
            cbp |= c.decision(73 + inc) << b8;
        }
        int chA = this.availA ? this.mbCbp[this.mbIdx - 1] >> 4 : 0;
        int chB = this.availB ? this.mbCbp[this.mbIdx - this.mbW] >> 4 : 0;
        int inc = (chA != 0 ? 1 : 0) + (chB != 0 ? 2 : 0);
        if (c.decision(77 + inc) != 0) {
            inc = (chA == 2 ? 1 : 0) + (chB == 2 ? 2 : 0);
            cbp |= (1 + c.decision(77 + 4 + inc)) << 4;
        }
        return cbp;
    }

    private int cabacQpDelta() {
        H264Cabac c = this.cabac;
        int ctx = this.lastQpDeltaNz ? 1 : 0;
        int val = 0;
        while (c.decision(60 + ctx) != 0) {
            ctx = 2 + (ctx >> 1);
            val++;
            if (val > 104) {
                throw new IllegalStateException("mb_qp_delta");
            }
        }
        return (val & 1) != 0 ? (val + 1) >> 1 : -(val >> 1);
    }

    private int refCond(int list, int gx, int gy) {
        if (!nbAvail(gx, gy)) {
            return 0;
        }
        int g8 = (gy >> 1) * this.w8 + (gx >> 1);
        if (this.direct8[g8] != 0 || this.cur.ref[list][g8] <= 0) {
            return 0;
        }
        return 1;
    }

    private int cabacRefIdx(int list, int gx, int gy) {
        H264Cabac c = this.cabac;
        int ctx = refCond(list, gx - 1, gy) + 2 * refCond(list, gx, gy - 1);
        int ref = 0;
        while (c.decision(54 + ctx) != 0) {
            ref++;
            ctx = (ctx >> 2) + 4;
            if (ref >= 32) {
                throw new IllegalStateException("ref_idx");
            }
        }
        return ref;
    }

    private int absMvd(int list, int gx, int gy, int comp) {
        if (!nbAvail(gx, gy)) {
            return 0;
        }
        return this.mvd[list][(gy * this.w4 + gx) * 2 + comp];
    }

    private int cabacMvd(int base, int sum) {
        H264Cabac c = this.cabac;
        int inc = sum < 3 ? 0 : sum > 32 ? 2 : 1;
        if (c.decision(base + inc) == 0) {
            return 0;
        }
        int mvd = 1;
        int ctx = base + 3;
        while (mvd < 9 && c.decision(ctx) != 0) {
            if (mvd < 4) {
                ctx++;
            }
            mvd++;
        }
        if (mvd >= 9) {
            int k = 3;
            while (c.bypass() != 0) {
                mvd += 1 << k;
                k++;
                if (k > 24) {
                    throw new IllegalStateException("mvd");
                }
            }
            while (k-- > 0) {
                mvd += c.bypass() << k;
            }
        }
        return c.bypass() != 0 ? -mvd : mvd;
    }

    /** Lee mvd (x, y) de la lista y la bloque (gx, gy) y lo guarda en la tabla de |mvd| para los contextos. Devuelve en mvdX / mvdY. */
    private int mvdX;
    private int mvdY;

    private void readMvd(int list, int gx, int gy, int w4b, int h4b) {
        if (this.cabacOn) {
            int sx = absMvd(list, gx - 1, gy, 0) + absMvd(list, gx, gy - 1, 0);
            this.mvdX = cabacMvd(40, sx);
            int sy = absMvd(list, gx - 1, gy, 1) + absMvd(list, gx, gy - 1, 1);
            this.mvdY = cabacMvd(47, sy);
            short ax = (short) Math.min(Math.abs(this.mvdX), 32000);
            short ay = (short) Math.min(Math.abs(this.mvdY), 32000);
            short[] md = this.mvd[list];
            for (int j = 0; j < h4b; j++) {
                int o = ((gy + j) * this.w4 + gx) * 2;
                for (int i = 0; i < w4b; i++) {
                    md[o + 2 * i] = ax;
                    md[o + 2 * i + 1] = ay;
                }
            }
        } else {
            this.mvdX = this.br.se();
            this.mvdY = this.br.se();
        }
    }

    // ------------------------------------------------------------------ residuo: lectura de bloques
    private static final int[] CBF_OFF = {0, 4, 8, 12, 16};
    private static final int[] SIG_OFF = {0, 15, 29, 44, 47};
    private static final int[] ABS_OFF = {0, 10, 20, 30, 39};

    private final int[] rIdx = new int[64];
    private final int[] rLev = new int[64];
    private int rCount;
    private final int[] levTmp = new int[16];

    private int cavlcBlock(int nC, int maxCoef) {
        BitReader b = this.br;
        int sym;
        if (nC < 0) {
            sym = H264Data.CHROMA_DC_TOKEN.read(b);
        } else if (nC < 2) {
            sym = H264Data.COEFF_TOKEN[0].read(b);
        } else if (nC < 4) {
            sym = H264Data.COEFF_TOKEN[1].read(b);
        } else if (nC < 8) {
            sym = H264Data.COEFF_TOKEN[2].read(b);
        } else {
            sym = H264Data.COEFF_TOKEN[3].read(b);
        }
        if (sym < 0) {
            throw new IllegalStateException("coeff_token");
        }
        int tc = sym >> 2;
        int t1 = sym & 3;
        this.rCount = 0;
        if (tc == 0) {
            return 0;
        }
        if (tc > maxCoef || t1 > tc) {
            throw new IllegalStateException("total_coeff");
        }
        int[] lev = this.levTmp;
        for (int i = 0; i < t1; i++) {
            lev[i] = b.u1() != 0 ? -1 : 1;
        }
        int sl = tc > 10 && t1 < 3 ? 1 : 0;
        for (int i = t1; i < tc; i++) {
            int prefix = 0;
            while (b.u1() == 0) {
                if (++prefix > 28) {
                    throw new IllegalStateException("level_prefix");
                }
            }
            int levelCode = Math.min(15, prefix) << sl;
            if (sl > 0 || prefix >= 14) {
                int size;
                if (prefix == 14 && sl == 0) {
                    size = 4;
                } else if (prefix >= 15) {
                    size = prefix - 3;
                } else {
                    size = sl;
                }
                if (size > 0) {
                    levelCode += b.u(size);
                }
            }
            if (prefix >= 15 && sl == 0) {
                levelCode += 15;
            }
            if (prefix >= 16) {
                levelCode += (1 << (prefix - 3)) - 4096;
            }
            if (i == t1 && t1 < 3) {
                levelCode += 2;
            }
            int level = (levelCode & 1) == 0 ? (levelCode + 2) >> 1 : (-levelCode - 1) >> 1;
            if (sl == 0) {
                sl = 1;
            }
            if (Math.abs(level) > (3 << (sl - 1)) && sl < 6) {
                sl++;
            }
            lev[i] = level;
        }
        int tz = 0;
        if (tc < maxCoef) {
            H264Data.Vlc v = nC < 0 ? H264Data.CHROMA_DC_TOTAL_ZEROS[tc - 1] : H264Data.TOTAL_ZEROS[tc - 1];
            tz = v.read(b);
            if (tz < 0) {
                throw new IllegalStateException("total_zeros");
            }
            if (tc + tz > maxCoef) {
                throw new IllegalStateException("total_zeros demasiado grande");
            }
        }
        int zerosLeft = tz;
        int pos = tc + tz - 1;
        for (int i = 0; i < tc; i++) {
            this.rIdx[i] = pos;
            this.rLev[i] = lev[i];
            if (i < tc - 1) {
                int run = 0;
                if (zerosLeft > 0) {
                    run = H264Data.RUN[Math.min(zerosLeft, 7) - 1].read(b);
                    if (run < 0 || run > zerosLeft) {
                        throw new IllegalStateException("run_before");
                    }
                }
                zerosLeft -= run;
                pos -= 1 + run;
            }
        }
        this.rCount = tc;
        return tc;
    }

    /** Lee un bloque de coeficientes CABAC (después de coded_block_flag = 1). Rellena rIdx / rLev. */
    private int cabacBlock(int cat, int maxCoef) {
        H264Cabac c = this.cabac;
        int sigBase;
        int lastBase;
        int absBase;
        if (cat == 5) {
            sigBase = 402;
            lastBase = 417;
            absBase = 426;
        } else {
            sigBase = 105 + SIG_OFF[cat];
            lastBase = 166 + SIG_OFF[cat];
            absBase = 227 + ABS_OFF[cat];
        }
        int n = 0;
        int last = maxCoef - 1;
        boolean found = false;
        for (int i = 0; i < last; i++) {
            int si;
            int li;
            if (cat == 5) {
                si = H264Data.SIG8[i];
                li = H264Data.LAST8[i];
            } else if (cat == 3) {
                si = Math.min(i, 2);
                li = si;
            } else {
                si = i;
                li = i;
            }
            if (c.decision(sigBase + si) != 0) {
                this.rIdx[n++] = i;
                if (c.decision(lastBase + li) != 0) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            this.rIdx[n++] = last;
        }
        int eq1 = 0;
        int gt1 = 0;
        for (int k = n - 1; k >= 0; k--) {
            int abs;
            if (c.decision(absBase + (gt1 != 0 ? 0 : Math.min(4, 1 + eq1))) == 0) {
                abs = 1;
                eq1++;
            } else {
                int cx = absBase + 5 + Math.min(4 - (cat == 3 ? 1 : 0), gt1);
                abs = 2;
                while (abs < 15 && c.decision(cx) != 0) {
                    abs++;
                }
                if (abs >= 15) {
                    int kk = 0;
                    while (c.bypass() != 0) {
                        abs += 1 << kk;
                        kk++;
                        if (kk > 24) {
                            throw new IllegalStateException("coeff_abs_level");
                        }
                    }
                    while (kk-- > 0) {
                        abs += c.bypass() << kk;
                    }
                }
                gt1++;
            }
            this.rLev[k] = c.bypass() != 0 ? -abs : abs;
        }
        this.rCount = n;
        return n;
    }

    private int lumaNC(int gx, int gy) {
        boolean aa = nbAvail(gx - 1, gy);
        boolean ab = nbAvail(gx, gy - 1);
        int nA = aa ? this.nnzY[gy * this.w4 + gx - 1] : 0;
        int nB = ab ? this.nnzY[(gy - 1) * this.w4 + gx] : 0;
        if (aa && ab) {
            return (nA + nB + 1) >> 1;
        }
        return aa ? nA : nB;
    }

    private int chromaNC(byte[] nnz, int cx, int cy) {
        int wc = this.mbW * 2;
        boolean aa = nbAvailC(cx - 1, cy);
        boolean ab = nbAvailC(cx, cy - 1);
        int nA = aa ? nnz[cy * wc + cx - 1] : 0;
        int nB = ab ? nnz[(cy - 1) * wc + cx] : 0;
        if (aa && ab) {
            return (nA + nB + 1) >> 1;
        }
        return aa ? nA : nB;
    }

    private int cbfLumaCond(int gx, int gy) {
        int a = nbAvail(gx - 1, gy) ? (this.nnzY[gy * this.w4 + gx - 1] != 0 ? 1 : 0) : (this.curIntra ? 1 : 0);
        int b = nbAvail(gx, gy - 1) ? (this.nnzY[(gy - 1) * this.w4 + gx] != 0 ? 1 : 0) : (this.curIntra ? 1 : 0);
        return a + 2 * b;
    }

    private int cbfChromaCond(byte[] nnz, int cx, int cy) {
        int wc = this.mbW * 2;
        int a = nbAvailC(cx - 1, cy) ? (nnz[cy * wc + cx - 1] != 0 ? 1 : 0) : (this.curIntra ? 1 : 0);
        int b = nbAvailC(cx, cy - 1) ? (nnz[(cy - 1) * wc + cx] != 0 ? 1 : 0) : (this.curIntra ? 1 : 0);
        return a + 2 * b;
    }

    private int cbfDcCond(int bit) {
        int a = this.availA ? (this.cbfDc[this.mbIdx - 1] >> bit) & 1 : (this.curIntra ? 1 : 0);
        int b = this.availB ? (this.cbfDc[this.mbIdx - this.mbW] >> bit) & 1 : (this.curIntra ? 1 : 0);
        return a + 2 * b;
    }

    // ------------------------------------------------------------------ residuo del macrobloque
    private final int[] coefY = new int[256];
    private final int[][] coefC = new int[2][64];
    private final int[] dcRawY = new int[16];
    private final int[] dcOutY = new int[16];
    private final int[][] dcRawC = new int[2][4];
    private final int[] dcOutC = new int[4];
    private boolean curIntra;
    private int curNzMask;
    private final int[] qpc = new int[2];

    private static int dq4(int lev, int ls, int qp6) {
        if (qp6 >= 4) {
            return (lev * ls) << (qp6 - 4);
        }
        return (lev * ls + (1 << (3 - qp6))) >> (4 - qp6);
    }

    private static int dq8(int lev, int ls, int qp6) {
        if (qp6 >= 6) {
            return (lev * ls) << (qp6 - 6);
        }
        return (lev * ls + (1 << (5 - qp6))) >> (6 - qp6);
    }

    private void computeChromaQp() {
        this.qpc[0] = H264Data.chromaQp(clip3(0, 51, this.qp + this.pps.chromaQpOffset));
        this.qpc[1] = H264Data.chromaQp(clip3(0, 51, this.qp + this.pps.chromaQpOffset2));
    }

    private void parseResidual(boolean i16, int cbp, boolean t8) {
        final boolean intra = this.curIntra;
        final int qp6 = this.qp / 6;
        final int[] ls4 = this.levelScale4[intra ? 0 : 3][this.qp % 6];
        final int[] ls8 = this.levelScale8[intra ? 0 : 1][this.qp % 6];
        final int mx = this.mbX * 4;
        final int my = this.mbY * 4;
        final boolean cab = this.cabacOn;
        int mask = 0;
        if (i16) {
            int n;
            if (cab) {
                if (this.cabac.decision(85 + CBF_OFF[0] + cbfDcCond(0)) != 0) {
                    n = cabacBlock(0, 16);
                    this.cbfDc[this.mbIdx] |= 1;
                } else {
                    n = 0;
                }
            } else {
                n = cavlcBlock(lumaNC(mx, my), 16);
            }
            for (int k = 0; k < n; k++) {
                this.dcRawY[H264Data.ZIGZAG4[this.rIdx[k]]] = this.rLev[k];
            }
        }
        for (int i8 = 0; i8 < 4; i8++) {
            if ((cbp & (1 << i8)) == 0) {
                continue;
            }
            int gx8 = mx + (i8 & 1) * 2;
            int gy8 = my + (i8 >> 1) * 2;
            if (t8) {
                if (cab) {
                    int n = cabacBlock(5, 64);
                    for (int k = 0; k < n; k++) {
                        int pos = H264Data.ZIGZAG8[this.rIdx[k]];
                        this.coefY[i8 * 64 + pos] = dq8(this.rLev[k], ls8[pos], qp6);
                    }
                    byte nb = (byte) Math.min(n, 64);
                    for (int j = 0; j < 2; j++) {
                        for (int i = 0; i < 2; i++) {
                            this.nnzY[(gy8 + j) * this.w4 + gx8 + i] = nb;
                        }
                    }
                    if (n > 0) {
                        mask |= 0x33 << ((i8 >> 1) * 8 + (i8 & 1) * 2);
                    }
                } else {
                    int total = 0;
                    for (int i4 = 0; i4 < 4; i4++) {
                        int bx = gx8 + (i4 & 1);
                        int by = gy8 + (i4 >> 1);
                        int n = cavlcBlock(lumaNC(bx, by), 16);
                        this.nnzY[by * this.w4 + bx] = (byte) n;
                        total += n;
                        for (int k = 0; k < n; k++) {
                            int pos = H264Data.ZIGZAG8[4 * this.rIdx[k] + i4];
                            this.coefY[i8 * 64 + pos] = dq8(this.rLev[k], ls8[pos], qp6);
                        }
                    }
                    if (total > 0) {
                        mask |= 0x33 << ((i8 >> 1) * 8 + (i8 & 1) * 2);
                    }
                }
            } else {
                for (int i4 = 0; i4 < 4; i4++) {
                    int blk = i8 * 4 + i4;
                    int bx = mx + BLK_X[blk];
                    int by = my + BLK_Y[blk];
                    int n;
                    if (cab) {
                        int cat = i16 ? 1 : 2;
                        if (this.cabac.decision(85 + CBF_OFF[cat] + cbfLumaCond(bx, by)) != 0) {
                            n = cabacBlock(cat, i16 ? 15 : 16);
                        } else {
                            n = 0;
                        }
                    } else {
                        n = cavlcBlock(lumaNC(bx, by), i16 ? 15 : 16);
                    }
                    this.nnzY[by * this.w4 + bx] = (byte) n;
                    if (n > 0) {
                        mask |= 1 << (BLK_Y[blk] * 4 + BLK_X[blk]);
                        int start = i16 ? 1 : 0;
                        for (int k = 0; k < n; k++) {
                            int pos = H264Data.ZIGZAG4[this.rIdx[k] + start];
                            this.coefY[blk * 16 + pos] = dq4(this.rLev[k], ls4[pos], qp6);
                        }
                    }
                }
            }
        }
        this.curNzMask = mask;
        if ((cbp >> 4) != 0) {
            computeChromaQp();
            for (int p = 0; p < 2; p++) {
                int n;
                if (cab) {
                    if (this.cabac.decision(85 + CBF_OFF[3] + cbfDcCond(1 + p)) != 0) {
                        n = cabacBlock(3, 4);
                        this.cbfDc[this.mbIdx] |= (byte) (2 << p);
                    } else {
                        n = 0;
                    }
                } else {
                    n = cavlcBlock(-1, 4);
                }
                for (int k = 0; k < n; k++) {
                    this.dcRawC[p][this.rIdx[k]] = this.rLev[k];
                }
            }
            if ((cbp >> 4) == 2) {
                int wc = this.mbW * 2;
                for (int p = 0; p < 2; p++) {
                    byte[] nnz = p == 0 ? this.nnzCb : this.nnzCr;
                    int[] lsc = this.levelScale4[(intra ? 1 : 4) + p][this.qpc[p] % 6];
                    int q6 = this.qpc[p] / 6;
                    for (int blk = 0; blk < 4; blk++) {
                        int cx = this.mbX * 2 + (blk & 1);
                        int cy = this.mbY * 2 + (blk >> 1);
                        int n;
                        if (cab) {
                            if (this.cabac.decision(85 + CBF_OFF[4] + cbfChromaCond(nnz, cx, cy)) != 0) {
                                n = cabacBlock(4, 15);
                            } else {
                                n = 0;
                            }
                        } else {
                            n = cavlcBlock(chromaNC(nnz, cx, cy), 15);
                        }
                        nnz[cy * wc + cx] = (byte) n;
                        for (int k = 0; k < n; k++) {
                            int pos = H264Data.ZIGZAG4[this.rIdx[k] + 1];
                            this.coefC[p][blk * 16 + pos] = dq4(this.rLev[k], lsc[pos], q6);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ reconstrucción: sumar el residuo
    private void addBlock4(int blk, boolean hasDc) {
        int bx = BLK_X[blk];
        int by = BLK_Y[blk];
        int off = (this.mbY * 16 + by * 4) * this.strideY + this.mbX * 16 + bx * 4;
        if ((this.curNzMask & (1 << (by * 4 + bx))) != 0) {
            H264Transform.idct4x4Add(this.coefY, blk * 16, this.pY, off, this.strideY);
        } else if (hasDc && this.coefY[blk * 16] != 0) {
            H264Transform.dcAdd4x4(this.coefY[blk * 16], this.pY, off, this.strideY);
            this.coefY[blk * 16] = 0;
        }
    }

    private void addBlock8(int i8) {
        if ((this.curNzMask & (0x33 << ((i8 >> 1) * 8 + (i8 & 1) * 2))) != 0) {
            int off = (this.mbY * 16 + (i8 >> 1) * 8) * this.strideY + this.mbX * 16 + (i8 & 1) * 8;
            H264Transform.idct8x8Add(this.coefY, i8 * 64, this.pY, off, this.strideY);
        }
    }

    private void addChroma(int cbp) {
        if ((cbp >> 4) == 0) {
            return;
        }
        int wc = this.mbW * 2;
        for (int p = 0; p < 2; p++) {
            byte[] plane = p == 0 ? this.pU : this.pV;
            byte[] nnz = p == 0 ? this.nnzCb : this.nnzCr;
            int[] ls = this.levelScale4[(this.curIntra ? 1 : 4) + p][this.qpc[p] % 6];
            H264Transform.chromaDc(this.dcRawC[p], ls[0], this.qpc[p], this.dcOutC);
            Arrays.fill(this.dcRawC[p], 0);
            for (int blk = 0; blk < 4; blk++) {
                int dc = this.dcOutC[blk];
                int cx = this.mbX * 2 + (blk & 1);
                int cy = this.mbY * 2 + (blk >> 1);
                int off = (this.mbY * 8 + (blk >> 1) * 4) * this.strideC + this.mbX * 8 + (blk & 1) * 4;
                if (nnz[cy * wc + cx] != 0) {
                    this.coefC[p][blk * 16] = dc;
                    H264Transform.idct4x4Add(this.coefC[p], blk * 16, plane, off, this.strideC);
                } else if (dc != 0) {
                    H264Transform.dcAdd4x4(dc, plane, off, this.strideC);
                }
            }
        }
    }

    private void readQpDelta() {
        int d = this.cabacOn ? cabacQpDelta() : this.br.se();
        this.lastQpDeltaNz = d != 0;
        if (d < -26 || d > 25) {
            throw new IllegalStateException("mb_qp_delta fuera de rango");
        }
        this.qp = (this.qp + d + 52) % 52;
    }

    // ------------------------------------------------------------------ intra
    private boolean iA;
    private boolean iB;
    private boolean iC;
    private boolean iD;
    private final int[] predModes = new int[16];
    private final int[] tt = new int[17];
    private final int[] ll = new int[9];

    private void setIntraAvail() {
        boolean con = this.pps.constrainedIntra;
        int idx = this.mbIdx;
        this.iA = this.availA && (!con || isIntraKind(this.mbKind[idx - 1]));
        this.iB = this.availB && (!con || isIntraKind(this.mbKind[idx - this.mbW]));
        this.iC = this.availC && (!con || isIntraKind(this.mbKind[idx - this.mbW + 1]));
        this.iD = this.availD && (!con || isIntraKind(this.mbKind[idx - this.mbW - 1]));
    }

    private int predIntraMode(int gx, int gy) {
        int a;
        int b;
        if ((gx & 3) != 0) {
            a = this.ipm[gy * this.w4 + gx - 1];
        } else if (!this.iA) {
            return 2;
        } else {
            a = this.ipm[gy * this.w4 + gx - 1];
        }
        if ((gy & 3) != 0) {
            b = this.ipm[(gy - 1) * this.w4 + gx];
        } else if (!this.iB) {
            return 2;
        } else {
            b = this.ipm[(gy - 1) * this.w4 + gx];
        }
        return Math.min(a, b);
    }

    private int readIntraMode(int gx, int gy) {
        int pred = predIntraMode(gx, gy);
        boolean prev;
        int rem = 0;
        if (this.cabacOn) {
            H264Cabac c = this.cabac;
            prev = c.decision(68) != 0;
            if (!prev) {
                rem = c.decision(69);
                rem |= c.decision(69) << 1;
                rem |= c.decision(69) << 2;
            }
        } else {
            prev = this.br.u1() != 0;
            if (!prev) {
                rem = this.br.u(3);
            }
        }
        return prev ? pred : (rem < pred ? rem : rem + 1);
    }

    private void intra4x4(int blk, int mode) {
        int bx = BLK_X[blk];
        int by = BLK_Y[blk];
        int px = this.mbX * 16 + bx * 4;
        int py = this.mbY * 16 + by * 4;
        int st = this.strideY;
        int off = py * st + px;
        boolean hasLeft = bx > 0 || this.iA;
        boolean hasTop = by > 0 || this.iB;
        boolean hasCorner = bx > 0 ? (by > 0 || this.iB) : (by > 0 ? this.iA : this.iD);
        boolean hasTr = by == 0 ? (bx < 3 ? this.iB : this.iC) : TR4[blk];
        int[] t = this.tt;
        int[] l = this.ll;
        byte[] p = this.pY;
        if (hasTop) {
            for (int i = 0; i < 4; i++) {
                t[1 + i] = p[off - st + i] & 0xff;
            }
            if (hasTr) {
                for (int i = 4; i < 8; i++) {
                    t[1 + i] = p[off - st + i] & 0xff;
                }
            } else {
                for (int i = 4; i < 8; i++) {
                    t[1 + i] = t[4];
                }
            }
        }
        if (hasCorner) {
            t[0] = p[off - st - 1] & 0xff;
        }
        if (hasLeft) {
            for (int i = 0; i < 4; i++) {
                l[1 + i] = p[off + i * st - 1] & 0xff;
            }
        }
        l[0] = t[0];
        H264Intra.predictNxN(mode, 4, t, l, hasTop, hasLeft, p, off, st);
    }

    private void intra8x8(int i8, int mode) {
        int bx = i8 & 1;
        int by = i8 >> 1;
        int px = this.mbX * 16 + bx * 8;
        int py = this.mbY * 16 + by * 8;
        int st = this.strideY;
        int off = py * st + px;
        boolean hasLeft = bx > 0 || this.iA;
        boolean hasTop = by > 0 || this.iB;
        boolean hasCorner = bx > 0 ? (by > 0 || this.iB) : (by > 0 ? this.iA : this.iD);
        boolean hasTr = by == 0 ? (bx == 0 ? this.iB : this.iC) : bx == 0;
        int[] t = this.tt;
        int[] l = this.ll;
        byte[] p = this.pY;
        if (hasTop) {
            for (int i = 0; i < 8; i++) {
                t[1 + i] = p[off - st + i] & 0xff;
            }
            if (hasTr) {
                for (int i = 8; i < 16; i++) {
                    t[1 + i] = p[off - st + i] & 0xff;
                }
            }
        }
        if (hasCorner) {
            t[0] = p[off - st - 1] & 0xff;
        }
        if (hasLeft) {
            for (int i = 0; i < 8; i++) {
                l[1 + i] = p[off + i * st - 1] & 0xff;
            }
        }
        l[0] = t[0];
        H264Intra.filter8x8(t, l, hasTop, hasLeft, hasCorner, hasTr);
        H264Intra.predictNxN(mode, 8, t, l, hasTop, hasLeft, p, off, st);
    }

    private void decodeIntraMb(int it) {
        final int idx = this.mbIdx;
        if (it > 25) {
            throw new IllegalStateException("mb_type intra");
        }
        if (it == 25) {
            decodePcm();
            return;
        }
        this.curIntra = true;
        setIntraAvail();
        final boolean i16 = it >= 1;
        boolean t8 = false;
        this.mbKind[idx] = i16 ? K_I16 : K_I4;
        final int gx0 = this.mbX * 4;
        final int gy0 = this.mbY * 4;
        if (!i16) {
            if (this.pps.transform8x8) {
                t8 = this.cabacOn ? cabacT8Flag() : this.br.u1() != 0;
            }
            this.mbT8[idx] = (byte) (t8 ? 1 : 0);
            if (t8) {
                for (int i8 = 0; i8 < 4; i8++) {
                    int gx = gx0 + (i8 & 1) * 2;
                    int gy = gy0 + (i8 >> 1) * 2;
                    int m = readIntraMode(gx, gy);
                    this.predModes[i8] = m;
                    byte bm = (byte) m;
                    this.ipm[gy * this.w4 + gx] = bm;
                    this.ipm[gy * this.w4 + gx + 1] = bm;
                    this.ipm[(gy + 1) * this.w4 + gx] = bm;
                    this.ipm[(gy + 1) * this.w4 + gx + 1] = bm;
                }
            } else {
                for (int blk = 0; blk < 16; blk++) {
                    int gx = gx0 + BLK_X[blk];
                    int gy = gy0 + BLK_Y[blk];
                    int m = readIntraMode(gx, gy);
                    this.predModes[blk] = m;
                    this.ipm[gy * this.w4 + gx] = (byte) m;
                }
            }
        }
        int cm = this.cabacOn ? cabacChromaMode() : this.br.ue();
        if (cm > 3) {
            throw new IllegalStateException("intra_chroma_pred_mode");
        }
        this.mbChromaMode[idx] = (byte) cm;
        int cbp;
        if (i16) {
            int v = it - 1;
            cbp = ((v >> 2) % 3) << 4 | (v >= 12 ? 15 : 0);
        } else if (this.cabacOn) {
            cbp = cabacCbp();
        } else {
            int c = this.br.ue();
            if (c > 47) {
                throw new IllegalStateException("coded_block_pattern");
            }
            cbp = H264Data.CBP_INTRA[c];
        }
        this.mbCbp[idx] = cbp;
        if (cbp > 0 || i16) {
            readQpDelta();
        } else {
            this.lastQpDeltaNz = false;
        }
        this.mbQp[idx] = (byte) this.qp;
        this.curNzMask = 0;
        if (cbp != 0 || i16) {
            parseResidual(i16, cbp, t8);
        }
        this.nzMask[idx] = this.curNzMask;
        // --- predicción + residuo
        if (i16) {
            H264Intra.predict16x16(it - 1 & 3, this.iB, this.iA, this.pY, (this.mbY * 16) * this.strideY + this.mbX * 16, this.strideY);
            H264Transform.lumaDc(this.dcRawY, this.levelScale4[0][this.qp % 6][0], this.qp, this.dcOutY);
            Arrays.fill(this.dcRawY, 0);
            for (int blk = 0; blk < 16; blk++) {
                this.coefY[blk * 16] = this.dcOutY[BLK_Y[blk] * 4 + BLK_X[blk]];
            }
            for (int blk = 0; blk < 16; blk++) {
                addBlock4(blk, true);
            }
        } else if (t8) {
            for (int i8 = 0; i8 < 4; i8++) {
                intra8x8(i8, this.predModes[i8]);
                addBlock8(i8);
            }
        } else {
            for (int blk = 0; blk < 16; blk++) {
                intra4x4(blk, this.predModes[blk]);
                addBlock4(blk, false);
            }
        }
        int offC = (this.mbY * 8) * this.strideC + this.mbX * 8;
        H264Intra.predictChroma(cm, this.iB, this.iA, this.pU, offC, this.strideC);
        H264Intra.predictChroma(cm, this.iB, this.iA, this.pV, offC, this.strideC);
        addChroma(cbp);
    }

    private void decodePcm() {
        final int idx = this.mbIdx;
        this.curIntra = true;
        this.mbKind[idx] = K_PCM;
        int bit;
        if (this.cabacOn) {
            bit = this.cabac.pcmStartBit();
        } else {
            this.br.alignToByte();
            bit = this.br.bitPos();
        }
        int p = bit >> 3;
        if (p + 384 > this.data.length) {
            throw new IllegalStateException("I_PCM truncado");
        }
        byte[] d = this.data;
        for (int j = 0; j < 16; j++) {
            System.arraycopy(d, p, this.pY, (this.mbY * 16 + j) * this.strideY + this.mbX * 16, 16);
            p += 16;
        }
        for (int j = 0; j < 8; j++) {
            System.arraycopy(d, p, this.pU, (this.mbY * 8 + j) * this.strideC + this.mbX * 8, 8);
            p += 8;
        }
        for (int j = 0; j < 8; j++) {
            System.arraycopy(d, p, this.pV, (this.mbY * 8 + j) * this.strideC + this.mbX * 8, 8);
            p += 8;
        }
        if (this.cabacOn) {
            this.cabac.start(d, p * 8, d.length * 8);
        } else {
            this.br.setBitPos(p * 8);
        }
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                this.nnzY[(this.mbY * 4 + j) * this.w4 + this.mbX * 4 + i] = 16;
            }
        }
        int wc = this.mbW * 2;
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                this.nnzCb[(this.mbY * 2 + j) * wc + this.mbX * 2 + i] = 16;
                this.nnzCr[(this.mbY * 2 + j) * wc + this.mbX * 2 + i] = 16;
            }
        }
        this.cbfDc[idx] = 7;
        this.mbCbp[idx] = 0x2f;
        this.mbQp[idx] = 0;
        this.mbChromaMode[idx] = 0;
        this.nzMask[idx] = 0xffff;
        this.lastQpDeltaNz = false;
    }

    // ------------------------------------------------------------------ inter: compensación de movimiento
    private final byte[][] mcY = new byte[2][256];
    private final byte[][] mcU = new byte[2][64];
    private final byte[][] mcV = new byte[2][64];
    private final int[] rects = new int[16 * 4];
    private int rectCount;

    private void addRect(int bx, int by, int w, int h) {
        int o = this.rectCount * 4;
        this.rects[o] = bx;
        this.rects[o + 1] = by;
        this.rects[o + 2] = w;
        this.rects[o + 3] = h;
        this.rectCount++;
    }

    private void mcAll() {
        for (int i = 0; i < this.rectCount; i++) {
            mcRect(this.rects[i * 4], this.rects[i * 4 + 1], this.rects[i * 4 + 2], this.rects[i * 4 + 3]);
        }
    }

    private void mcRect(int bx, int by, int bw, int bh) {
        final int gx = this.mbX * 4 + bx;
        final int gy = this.mbY * 4 + by;
        final int g8 = (gy >> 1) * this.w8 + (gx >> 1);
        final int r0 = this.cur.ref[0][g8];
        final int r1 = this.cur.ref[1][g8];
        if (r0 < 0 && r1 < 0) {
            return;
        }
        final int w = bw * 4;
        final int h = bh * 4;
        final int px = this.mbX * 16 + bx * 4;
        final int py = this.mbY * 16 + by * 4;
        final int pw = this.cur.width;
        final int ph = this.cur.height;
        for (int l = 0; l < 2; l++) {
            int r = l == 0 ? r0 : r1;
            if (r < 0) {
                continue;
            }
            H264Pic rp = this.refList[l][r];
            int mvx = this.cur.mv[l][(gy * this.w4 + gx) * 2];
            int mvy = this.cur.mv[l][(gy * this.w4 + gx) * 2 + 1];
            H264Inter.luma(rp.y, pw, ph, px + (mvx >> 2), py + (mvy >> 2), mvx & 3, mvy & 3, w, h, this.mcY[l], 0, 16, this.scratch);
            int cx = (px >> 1) + (mvx >> 3);
            int cy = (py >> 1) + (mvy >> 3);
            H264Inter.chroma(rp.u, pw >> 1, ph >> 1, cx, cy, mvx & 7, mvy & 7, w >> 1, h >> 1, this.mcU[l], 0, 8, this.scratch);
            H264Inter.chroma(rp.v, pw >> 1, ph >> 1, cx, cy, mvx & 7, mvy & 7, w >> 1, h >> 1, this.mcV[l], 0, 8, this.scratch);
        }
        int wmode = this.sl.type == SLICE_P ? (this.pps.weightedPred ? 1 : 0) : this.pps.weightedBipredIdc;
        blend(this.pY, py * this.strideY + px, this.strideY, this.mcY[0], this.mcY[1], 16, w, h, 0, r0, r1, wmode);
        int offC = (py >> 1) * this.strideC + (px >> 1);
        blend(this.pU, offC, this.strideC, this.mcU[0], this.mcU[1], 8, w >> 1, h >> 1, 1, r0, r1, wmode);
        blend(this.pV, offC, this.strideC, this.mcV[0], this.mcV[1], 8, w >> 1, h >> 1, 2, r0, r1, wmode);
    }

    private static int clip255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private void blend(byte[] dst, int dOff, int dStride, byte[] a, byte[] b, int s, int w, int h, int comp, int r0, int r1, int wmode) {
        final Slice sh = this.sl;
        if (r0 >= 0 && r1 >= 0) {
            if (wmode == 0) {
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        dst[dOff + j * dStride + i] = (byte) (((a[j * s + i] & 0xff) + (b[j * s + i] & 0xff) + 1) >> 1);
                    }
                }
            } else if (wmode == 1) {
                int ld = comp == 0 ? sh.lumaLog2Denom : sh.chromaLog2Denom;
                int w0 = sh.wt[0][r0][comp];
                int w1 = sh.wt[1][r1][comp];
                int o = (sh.off[0][r0][comp] + sh.off[1][r1][comp] + 1) >> 1;
                int rnd = 1 << ld;
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        int v = (((a[j * s + i] & 0xff) * w0 + (b[j * s + i] & 0xff) * w1 + rnd) >> (ld + 1)) + o;
                        dst[dOff + j * dStride + i] = (byte) clip255(v);
                    }
                }
            } else {
                int w0 = this.implicitW[r0][r1][0];
                int w1 = this.implicitW[r0][r1][1];
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        int v = ((a[j * s + i] & 0xff) * w0 + (b[j * s + i] & 0xff) * w1 + 32) >> 6;
                        dst[dOff + j * dStride + i] = (byte) clip255(v);
                    }
                }
            }
        } else {
            int l = r0 >= 0 ? 0 : 1;
            byte[] src = l == 0 ? a : b;
            int r = l == 0 ? r0 : r1;
            if (wmode == 1) {
                int ld = comp == 0 ? sh.lumaLog2Denom : sh.chromaLog2Denom;
                int wg = sh.wt[l][r][comp];
                int o = sh.off[l][r][comp];
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        int p = src[j * s + i] & 0xff;
                        int v = ld >= 1 ? ((p * wg + (1 << (ld - 1))) >> ld) + o : p * wg + o;
                        dst[dOff + j * dStride + i] = (byte) clip255(v);
                    }
                }
            } else {
                for (int j = 0; j < h; j++) {
                    System.arraycopy(src, j * s, dst, dOff + j * dStride, w);
                }
            }
        }
    }

    // ------------------------------------------------------------------ inter: macrobloques
    private static final int[] PD0 = {1, 2, 1, 2, 1, 2, 3, 3, 3};
    private static final int[] PD1 = {1, 2, 2, 1, 3, 3, 1, 2, 3};
    private static final int[] SUB_SHAPE_B = {0, 0, 0, 0, 1, 2, 1, 2, 1, 2, 3, 3, 3};
    private static final int[] SUB_DIR_B = {0, 1, 2, 3, 1, 1, 2, 2, 3, 3, 1, 2, 3};

    private final int[] partDir = new int[2];
    private final int[][] partRef = new int[2][2];
    private final int[] subType = new int[4];
    private final int[][] subRef = new int[2][4];

    private void markDirect8(int quadMask) {
        for (int q = 0; q < 4; q++) {
            if ((quadMask & (1 << q)) != 0) {
                this.direct8[(this.mbY * 2 + (q >> 1)) * this.w8 + this.mbX * 2 + (q & 1)] = 1;
            }
        }
    }

    private boolean quadsEqual() {
        int base8 = (this.mbY * 2) * this.w8 + this.mbX * 2;
        int base4 = (this.mbY * 4) * this.w4 + this.mbX * 4;
        for (int l = 0; l < 2; l++) {
            byte r = this.cur.ref[l][base8];
            short[] mv = this.cur.mv[l];
            int mx = mv[base4 * 2];
            int my = mv[base4 * 2 + 1];
            for (int q = 1; q < 4; q++) {
                int g8 = base8 + (q >> 1) * this.w8 + (q & 1);
                int g4 = base4 + (q >> 1) * 2 * this.w4 + (q & 1) * 2;
                if (this.cur.ref[l][g8] != r) {
                    return false;
                }
                if (r >= 0 && (mv[g4 * 2] != mx || mv[g4 * 2 + 1] != my)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void addDirectRects() {
        if (this.sps.direct8x8Inference) {
            if (quadsEqual()) {
                addRect(0, 0, 4, 4);
            } else {
                for (int q = 0; q < 4; q++) {
                    addRect((q & 1) * 2, (q >> 1) * 2, 2, 2);
                }
            }
        } else {
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    addRect(i, j, 1, 1);
                }
            }
        }
    }

    private void subMv(int list, int r, int bx, int by, int w4b, int h4b) {
        int gx = this.mbX * 4 + bx;
        int gy = this.mbY * 4 + by;
        mvPred(list, r, gx, gy, w4b, 0);
        readMvd(list, gx, gy, w4b, h4b);
        putMv(list, bx, by, w4b, h4b, this.mvpX + this.mvdX, this.mvpY + this.mvdY);
    }

    private int readRef(int list, int gx, int gy) {
        int n = this.sl.numRef[list];
        int r = 0;
        if (n > 1) {
            r = this.cabacOn ? cabacRefIdx(list, gx, gy) : this.br.te(n - 1);
        }
        if (r < 0 || r >= n) {
            throw new IllegalStateException("ref_idx fuera de rango");
        }
        return r;
    }

    private void decodeInterMb(int t) {
        final Slice s = this.sl;
        final int idx = this.mbIdx;
        final boolean isB = s.type == SLICE_B;
        final int nl = isB ? 2 : 1;
        final int gx0 = this.mbX * 4;
        final int gy0 = this.mbY * 4;
        this.curIntra = false;
        this.done0 = 0;
        this.done1 = 0;
        this.rectCount = 0;
        boolean allow8x8 = true;
        this.mbKind[idx] = isB ? K_B : K_P;
        if (isB && t == 0) {
            this.mbKind[idx] = K_BDIRECT;
            directMb();
            markDirect8(15);
            addDirectRects();
            allow8x8 = this.sps.direct8x8Inference;
        } else if (isB ? t <= 21 : t <= 2) {
            int shape;
            int d0;
            int d1;
            if (!isB) {
                shape = t;
                d0 = 1;
                d1 = 1;
            } else if (t <= 3) {
                shape = 0;
                d0 = t;
                d1 = 0;
            } else {
                int p = (t - 4) >> 1;
                shape = ((t - 4) & 1) == 0 ? 1 : 2;
                d0 = PD0[p];
                d1 = PD1[p];
            }
            final int np = shape == 0 ? 1 : 2;
            this.partDir[0] = d0;
            this.partDir[1] = d1;
            for (int list = 0; list < nl; list++) {
                for (int p = 0; p < np; p++) {
                    int bx = shape == 2 ? p * 2 : 0;
                    int by = shape == 1 ? p * 2 : 0;
                    int w4b = shape == 2 ? 2 : 4;
                    int h4b = shape == 1 ? 2 : 4;
                    int r = -1;
                    if (((this.partDir[p] >> list) & 1) != 0) {
                        r = readRef(list, gx0 + bx, gy0 + by);
                    }
                    this.partRef[list][p] = r;
                    putRef(list, bx >> 1, by >> 1, w4b >> 1, h4b >> 1, r);
                }
            }
            for (int list = 0; list < nl; list++) {
                for (int p = 0; p < np; p++) {
                    int bx = shape == 2 ? p * 2 : 0;
                    int by = shape == 1 ? p * 2 : 0;
                    int w4b = shape == 2 ? 2 : 4;
                    int h4b = shape == 1 ? 2 : 4;
                    int r = this.partRef[list][p];
                    if (r >= 0) {
                        int ps = shape == 0 ? 0 : shape == 1 ? 1 + p : 3 + p;
                        int gx = gx0 + bx;
                        int gy = gy0 + by;
                        mvPred(list, r, gx, gy, w4b, ps);
                        readMvd(list, gx, gy, w4b, h4b);
                        putMv(list, bx, by, w4b, h4b, this.mvpX + this.mvdX, this.mvpY + this.mvdY);
                    } else {
                        putMv(list, bx, by, w4b, h4b, 0, 0);
                    }
                }
            }
            for (int p = 0; p < np; p++) {
                addRect(shape == 2 ? p * 2 : 0, shape == 1 ? p * 2 : 0, shape == 2 ? 2 : 4, shape == 1 ? 2 : 4);
            }
        } else if (isB ? t == 22 : (t == 3 || t == 4)) {
            for (int q = 0; q < 4; q++) {
                int st = this.cabacOn ? (isB ? cabacSubTypeB() : cabacSubTypeP()) : this.br.ue();
                if (st < 0 || st >= (isB ? 13 : 4)) {
                    throw new IllegalStateException("sub_mb_type");
                }
                this.subType[q] = st;
            }
            int directMask = 0;
            if (isB) {
                for (int q = 0; q < 4; q++) {
                    if (this.subType[q] == 0) {
                        directMask |= 1 << q;
                    }
                }
            }
            final boolean inf = this.sps.direct8x8Inference;
            if (directMask != 0) {
                if (s.directSpatial) {
                    spatialSetup();
                } else {
                    this.done0 = 0;
                    this.done1 = 0;
                }
                for (int q = 0; q < 4; q++) {
                    if ((directMask & (1 << q)) == 0) {
                        continue;
                    }
                    int qx = (q & 1) * 2;
                    int qy = (q >> 1) * 2;
                    if (inf) {
                        directBlock(qx, qy, 2, 2);
                    } else {
                        directBlock(qx, qy, 1, 1);
                        directBlock(qx + 1, qy, 1, 1);
                        directBlock(qx, qy + 1, 1, 1);
                        directBlock(qx + 1, qy + 1, 1, 1);
                    }
                    int qm = 0x33 << (qy * 4 + qx);
                    this.done0 &= ~qm;
                    this.done1 &= ~qm;
                }
                markDirect8(directMask);
                if (!inf) {
                    allow8x8 = false;
                }
            }
            for (int list = 0; list < nl; list++) {
                for (int q = 0; q < 4; q++) {
                    int qx = (q & 1) * 2;
                    int qy = (q >> 1) * 2;
                    if ((directMask & (1 << q)) != 0) {
                        continue;
                    }
                    int dir = isB ? SUB_DIR_B[this.subType[q]] : 1;
                    int r = -1;
                    if (((dir >> list) & 1) != 0) {
                        r = t == 4 ? 0 : readRef(list, gx0 + qx, gy0 + qy);
                    }
                    this.subRef[list][q] = r;
                    putRef(list, q & 1, q >> 1, 1, 1, r);
                }
            }
            for (int list = 0; list < nl; list++) {
                for (int q = 0; q < 4; q++) {
                    int qx = (q & 1) * 2;
                    int qy = (q >> 1) * 2;
                    if ((directMask & (1 << q)) != 0) {
                        int qm = 0x33 << (qy * 4 + qx);
                        if (list == 0) {
                            this.done0 |= qm;
                        } else {
                            this.done1 |= qm;
                        }
                        continue;
                    }
                    int r = this.subRef[list][q];
                    if (r < 0) {
                        putMv(list, qx, qy, 2, 2, 0, 0);
                        continue;
                    }
                    int shape = isB ? SUB_SHAPE_B[this.subType[q]] : this.subType[q];
                    switch (shape) {
                        case 0 -> subMv(list, r, qx, qy, 2, 2);
                        case 1 -> {
                            subMv(list, r, qx, qy, 2, 1);
                            subMv(list, r, qx, qy + 1, 2, 1);
                        }
                        case 2 -> {
                            subMv(list, r, qx, qy, 1, 2);
                            subMv(list, r, qx + 1, qy, 1, 2);
                        }
                        default -> {
                            subMv(list, r, qx, qy, 1, 1);
                            subMv(list, r, qx + 1, qy, 1, 1);
                            subMv(list, r, qx, qy + 1, 1, 1);
                            subMv(list, r, qx + 1, qy + 1, 1, 1);
                        }
                    }
                }
            }
            for (int q = 0; q < 4; q++) {
                int qx = (q & 1) * 2;
                int qy = (q >> 1) * 2;
                if ((directMask & (1 << q)) != 0) {
                    if (inf) {
                        addRect(qx, qy, 2, 2);
                    } else {
                        addRect(qx, qy, 1, 1);
                        addRect(qx + 1, qy, 1, 1);
                        addRect(qx, qy + 1, 1, 1);
                        addRect(qx + 1, qy + 1, 1, 1);
                    }
                    continue;
                }
                int shape = isB ? SUB_SHAPE_B[this.subType[q]] : this.subType[q];
                switch (shape) {
                    case 0 -> addRect(qx, qy, 2, 2);
                    case 1 -> {
                        addRect(qx, qy, 2, 1);
                        addRect(qx, qy + 1, 2, 1);
                        allow8x8 = false;
                    }
                    case 2 -> {
                        addRect(qx, qy, 1, 2);
                        addRect(qx + 1, qy, 1, 2);
                        allow8x8 = false;
                    }
                    default -> {
                        addRect(qx, qy, 1, 1);
                        addRect(qx + 1, qy, 1, 1);
                        addRect(qx, qy + 1, 1, 1);
                        addRect(qx + 1, qy + 1, 1, 1);
                        allow8x8 = false;
                    }
                }
            }
        } else {
            throw new IllegalStateException("mb_type inter");
        }
        int cbp;
        if (this.cabacOn) {
            cbp = cabacCbp();
        } else {
            int c = this.br.ue();
            if (c > 47) {
                throw new IllegalStateException("coded_block_pattern");
            }
            cbp = H264Data.CBP_INTER[c];
        }
        boolean t8 = false;
        if ((cbp & 15) != 0 && this.pps.transform8x8 && allow8x8) {
            t8 = this.cabacOn ? cabacT8Flag() : this.br.u1() != 0;
        }
        this.mbT8[idx] = (byte) (t8 ? 1 : 0);
        this.mbCbp[idx] = cbp;
        if (cbp > 0) {
            readQpDelta();
        } else {
            this.lastQpDeltaNz = false;
        }
        this.mbQp[idx] = (byte) this.qp;
        mcAll();
        this.curNzMask = 0;
        if (cbp != 0) {
            parseResidual(false, cbp, t8);
            this.nzMask[idx] = this.curNzMask;
            if ((cbp & 15) != 0) {
                if (t8) {
                    for (int i8 = 0; i8 < 4; i8++) {
                        addBlock8(i8);
                    }
                } else {
                    for (int blk = 0; blk < 16; blk++) {
                        addBlock4(blk, false);
                    }
                }
            }
            addChroma(cbp);
        }
    }

    private void decodeSkipMb() {
        final int idx = this.mbIdx;
        this.curIntra = false;
        this.done0 = 0;
        this.done1 = 0;
        this.rectCount = 0;
        if (this.sl.type == SLICE_P) {
            this.mbKind[idx] = K_PSKIP;
            pSkipMotion();
            addRect(0, 0, 4, 4);
        } else {
            this.mbKind[idx] = K_BSKIP;
            directMb();
            markDirect8(15);
            addDirectRects();
        }
        this.mbCbp[idx] = 0;
        this.mbT8[idx] = 0;
        this.mbQp[idx] = (byte) this.qp;
        this.lastQpDeltaNz = false;
        mcAll();
    }

    private void decodeMb() {
        final Slice s = this.sl;
        if (s.type == SLICE_I) {
            decodeIntraMb(this.cabacOn ? cabacMbTypeIntra(3, true) : checkIntraType(this.br.ue()));
        } else if (s.type == SLICE_P) {
            int t = this.cabacOn ? cabacMbTypeP() : this.br.ue();
            if (t >= 5) {
                decodeIntraMb(checkIntraType(t - 5));
            } else {
                decodeInterMb(t);
            }
        } else {
            int t = this.cabacOn ? cabacMbTypeB() : this.br.ue();
            if (t >= 23) {
                decodeIntraMb(checkIntraType(t - 23));
            } else {
                decodeInterMb(t);
            }
        }
    }

    private static int checkIntraType(int it) {
        if (it < 0 || it > 25) {
            throw new IllegalStateException("mb_type");
        }
        return it;
    }
}
