package com.example.beautifuldecor.browser.video;

/** Un cuadro decodificado (planos YUV 4:2:0 con tamaño múltiplo de 16) más su información de movimiento. */
public final class H264Pic {
    public final int mbW;
    public final int mbH;
    public final int width;       // ancho completo (múltiplo de 16)
    public final int height;
    public final byte[] y;
    public final byte[] u;
    public final byte[] v;
    /** Recorte (del SPS) para obtener la imagen visible. */
    public int cropLeft;
    public int cropRight;
    public int cropTop;
    public int cropBottom;
    /** Tiempo de presentación en microsegundos, puesto por quien llama. */
    public long ptsUs;

    int poc;
    int frameNum;
    int frameNumWrap;
    int longIdx;
    boolean refShort;
    boolean refLong;
    boolean held;               // todavía en uso por quien lo pidió
    boolean inDpb;
    final int uid;
    private static int nextUid = 1;

    final short[][] mv = new short[2][];
    final byte[][] ref = new byte[2][];
    final int[][] refUid = new int[2][];

    H264Pic(int mbW, int mbH) {
        this.mbW = mbW;
        this.mbH = mbH;
        this.width = mbW * 16;
        this.height = mbH * 16;
        this.y = new byte[this.width * this.height];
        this.u = new byte[this.width * this.height / 4];
        this.v = new byte[this.width * this.height / 4];
        for (int l = 0; l < 2; l++) {
            this.mv[l] = new short[mbW * 4 * mbH * 4 * 2];
            this.ref[l] = new byte[mbW * 2 * mbH * 2];
            this.refUid[l] = new int[mbW * 2 * mbH * 2];
        }
        synchronized (H264Pic.class) {
            this.uid = nextUid++;
        }
    }

    public int visibleWidth() {
        return this.width - this.cropLeft - this.cropRight;
    }

    public int visibleHeight() {
        return this.height - this.cropTop - this.cropBottom;
    }

    /** Convierte la parte visible a píxeles ABGR (formato de NativeImage) con la matriz BT.601 o BT.709 según el alto. */
    public void toAbgr(int[] out) {
        int w = visibleWidth();
        int h = visibleHeight();
        boolean hd = h >= 720 || w >= 1280;
        int cr = hd ? 459 : 409;       // 1.402 / 1.5748 (x256 con escala 298)
        int cgU = hd ? 55 : 100;
        int cgV = hd ? 136 : 208;
        int cb = hd ? 541 : 516;
        int cw = this.width / 2;
        for (int j = 0; j < h; j++) {
            int yRow = (j + this.cropTop) * this.width + this.cropLeft;
            int cRow = ((j + this.cropTop) >> 1) * cw + (this.cropLeft >> 1);
            int o = j * w;
            for (int i = 0; i < w; i++) {
                int yy = 298 * ((this.y[yRow + i] & 0xff) - 16) + 128;
                int ci = cRow + (i >> 1);
                int uu = (this.u[ci] & 0xff) - 128;
                int vv = (this.v[ci] & 0xff) - 128;
                int r = (yy + cr * vv) >> 8;
                int g = (yy - cgU * uu - cgV * vv) >> 8;
                int b = (yy + cb * uu) >> 8;
                r = r < 0 ? 0 : Math.min(r, 255);
                g = g < 0 ? 0 : Math.min(g, 255);
                b = b < 0 ? 0 : Math.min(b, 255);
                out[o + i] = 0xff000000 | (b << 16) | (g << 8) | r;
            }
        }
    }
}
