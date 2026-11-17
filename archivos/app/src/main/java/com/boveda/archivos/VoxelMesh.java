package com.boveda.archivos;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte un plano en una malla de caras para dibujarlo en 3D: solo las caras que se ven (las que tocan aire), y las vecinas
 * del mismo color se juntan en rectángulos grandes (así 15 millones de bloques se vuelven unos cientos de miles de caras).
 * Sin Android, para probarlo en el computador.
 */
final class VoxelMesh {
    /** Un trozo de la malla: hasta 16.000 rectángulos (para que los índices quepan en 16 bits). */
    static final class Chunk {
        float[] pos;      // x,y,z por vértice
        int[] col;        // RGB por vértice; el byte de arriba dice qué material es (para el dibujo de la superficie)
        byte[] nrm;       // la normal de la cara (x,y,z) por vértice
        int quads;
    }

    final List<Chunk> chunks = new ArrayList<>();
    int quads;
    final int sx, sy, sz;

    private static final int MAXQ = 16000;
    private float[] cp = new float[MAXQ * 12];
    private int[] cc = new int[MAXQ * 4];
    private byte[] cn = new byte[MAXQ * 12];
    private int cq;

    private VoxelMesh(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
    }

    /** Agrega un rectángulo (4 esquinas), de un color, un material y con su normal. */
    private void addQuad(float[] q, int color, int mat, float nx, float ny, float nz) {
        if (cq == MAXQ) flush();
        int p = cq * 12, c = cq * 4;
        System.arraycopy(q, 0, cp, p, 12);
        int col = (color & 0x00FFFFFF) | (mat << 24);
        cc[c] = cc[c + 1] = cc[c + 2] = cc[c + 3] = col;
        for (int v = 0; v < 4; v++) {
            cn[p + v * 3] = (byte) Math.round(nx * 127);
            cn[p + v * 3 + 1] = (byte) Math.round(ny * 127);
            cn[p + v * 3 + 2] = (byte) Math.round(nz * 127);
        }
        cq++;
        quads++;
    }

    private final float[] qtmp = new float[12];

    private void addQuad(int x0, int y0, int z0, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, int color, int mat, int axis, boolean pos) {
        qtmp[0] = x0; qtmp[1] = y0; qtmp[2] = z0; qtmp[3] = x1; qtmp[4] = y1; qtmp[5] = z1;
        qtmp[6] = x2; qtmp[7] = y2; qtmp[8] = z2; qtmp[9] = x3; qtmp[10] = y3; qtmp[11] = z3;
        float s = pos ? 1 : -1;
        addQuad(qtmp, color, mat, axis == 0 ? s : 0, axis == 1 ? s : 0, axis == 2 ? s : 0);
    }

    private void flush() {
        if (cq == 0) return;
        Chunk k = new Chunk();
        k.pos = java.util.Arrays.copyOf(cp, cq * 12);
        k.col = java.util.Arrays.copyOf(cc, cq * 4);
        k.nrm = java.util.Arrays.copyOf(cn, cq * 12);
        k.quads = cq;
        chunks.add(k);
        cq = 0;
    }

    private static int shade(int argb, double f) {
        int r = (int) (((argb >> 16) & 255) * f), g = (int) (((argb >> 8) & 255) * f), b = (int) ((argb & 255) * f);
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    interface Progress {
        void on(int percent);
    }

    /** Arma la malla. Con «maxY» se pueden dejar fuera las capas de arriba (-1 = todas). */
    static VoxelMesh build(BdPlan plan, Progress prog) {
        return build(plan, plan.sy - 1, prog);
    }

    /** Igual, pero solo hasta la capa «maxY» (para cortar el plano por altura y ver el interior). */
    static VoxelMesh build(BdPlan plan, int maxY, Progress prog) {
        int sx = plan.sx, sy = plan.sy, sz = plan.sz;
        final int top = Math.max(0, Math.min(sy - 1, maxY));
        VoxelMesh m = new VoxelMesh(sx, sy, sz);
        int np = plan.palette.length;
        byte[] kind = new byte[np];
        int[] color = new int[np];
        byte[] mat = new byte[np];
        for (int i = 0; i < np; i++) {
            kind[i] = (byte) BlockColors.kind(plan.palette[i]);
            color[i] = BlockColors.color(plan.palette[i]);
            mat[i] = (byte) BlockColors.material(plan.palette[i]);
        }
        short[] b = plan.blocks;
        // 6 direcciones: ±X, ±Y, ±Z
        for (int dir = 0; dir < 6; dir++) {
            int axis = dir >> 1;                  // 0 = X, 1 = Y, 2 = Z
            boolean pos = (dir & 1) == 0;
            int dims = axis == 0 ? sx : axis == 1 ? sy : sz;
            int du = axis == 0 ? sz : sx, dv = axis == 1 ? sz : sy;     // el plano del corte
            // u, v: ejes del corte (X: u=z,v=y · Y: u=x,v=z · Z: u=x,v=y)
            int[] mask = new int[du * dv];
            double f = axis == 1 ? (pos ? 1.0 : 0.5) : axis == 0 ? (pos ? 0.8 : 0.7) : (pos ? 0.65 : 0.75);
            for (int s = 0; s < dims; s++) {
                boolean any = false;
                for (int v = 0; v < dv; v++) {
                    for (int u = 0; u < du; u++) {
                        int x, y, z;
                        if (axis == 0) { x = s; z = u; y = v; }
                        else if (axis == 1) { y = s; x = u; z = v; }
                        else { z = s; x = u; y = v; }
                        int id = b[(y * sz + z) * sx + x];
                        int c = 0;
                        if (y <= top && (kind[id] == BlockColors.SOLID || kind[id] == BlockColors.GLASS)) {
                            int nx = x, ny = y, nz = z;
                            if (axis == 0) nx += pos ? 1 : -1; else if (axis == 1) ny += pos ? 1 : -1; else nz += pos ? 1 : -1;
                            boolean hidden = false;
                            if (nx >= 0 && nx < sx && ny >= 0 && ny <= top && nz >= 0 && nz < sz) {
                                int nid = b[(ny * sz + nz) * sx + nx];
                                // lo tapa un bloque sólido; el cristal solo lo tapa otro cristal igual
                                hidden = kind[nid] == BlockColors.SOLID || (kind[id] == BlockColors.GLASS && nid == id);
                            }
                            if (!hidden) c = (shade(color[id], f) & 0x00FFFFFF) | ((mat[id] + 1) << 24);
                        }
                        mask[v * du + u] = c;
                        if (c != 0) any = true;
                    }
                }
                if (any) m.greedy(mask, du, dv, axis, pos, s);
            }
            if (prog != null) prog.on((dir + 1) * 100 / 6);
        }
        m.addEntities(plan, top);
        m.flush();
        return m;
    }

    /** Las entidades (un lobo, un tren…) se agregan como cajas de colores, giradas según hacia dónde miran. */
    private void addEntities(BdPlan plan, int top) {
        float[] c8 = new float[24];
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};   // esquinas de cada cara (índices 0..7)
        for (BdPlan.Ent e : plan.entityList) {
            if (e.y > top + 1.5) continue;
            EntityModels.Model md = EntityModels.of(e.id);
            for (EntityModels.Box b : md.boxes) {
                EntityModels.corners(e, b, c8);
                for (int[] f : faces) {
                    for (int k = 0; k < 4; k++) System.arraycopy(c8, f[k] * 3, qtmp, k * 3, 3);
                    // normal de la cara y brillo según hacia dónde mira
                    float ax = qtmp[3] - qtmp[0], ay = qtmp[4] - qtmp[1], az = qtmp[5] - qtmp[2], bx = qtmp[9] - qtmp[0], by = qtmp[10] - qtmp[1], bz = qtmp[11] - qtmp[2];
                    float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 1e-6f) continue;
                    nx /= len; ny /= len; nz /= len;
                    // la normal debe apuntar hacia afuera de la caja: se compara con el centro de la caja
                    float cxm = 0, cym = 0, czm = 0;
                    for (int k = 0; k < 8; k++) { cxm += c8[k * 3] / 8; cym += c8[k * 3 + 1] / 8; czm += c8[k * 3 + 2] / 8; }
                    float dx = qtmp[0] - cxm, dy = qtmp[1] - cym, dz = qtmp[2] - czm;
                    if (nx * dx + ny * dy + nz * dz < 0) { nx = -nx; ny = -ny; nz = -nz; }
                    float light = Math.max(0, nx * 0.35f + ny * 0.85f + nz * 0.4f);
                    int col = shade(b.color, 0.55 + 0.5 * light);
                    addQuad(qtmp, col, 255, nx, ny, nz);
                }
            }
        }
    }

    /** Junta las celdas vecinas del mismo color en rectángulos. */
    private void greedy(int[] mask, int du, int dv, int axis, boolean pos, int s) {
        for (int v = 0; v < dv; v++) {
            for (int u = 0; u < du; ) {
                int c = mask[v * du + u];
                if (c == 0) {
                    u++;
                    continue;
                }
                int w = 1;
                while (u + w < du && mask[v * du + u + w] == c) w++;
                int h = 1;
                outer:
                while (v + h < dv) {
                    for (int k = 0; k < w; k++) if (mask[(v + h) * du + u + k] != c) break outer;
                    h++;
                }
                for (int j = 0; j < h; j++) for (int k = 0; k < w; k++) mask[(v + j) * du + u + k] = 0;
                emit(axis, pos, s, u, v, w, h, c);
                u += w;
            }
        }
    }

    private void emit(int axis, boolean pos, int s, int u, int v, int w, int h, int cm) {
        int c = cm & 0x00FFFFFF, mt = (cm >>> 24) - 1;
        int p = s + (pos ? 1 : 0);        // el plano de la cara
        switch (axis) {
            case 0 -> {   // X: u = z, v = y
                if (pos) addQuad(p, v, u, p, v + h, u, p, v + h, u + w, p, v, u + w, c, mt, axis, pos);
                else addQuad(p, v, u, p, v, u + w, p, v + h, u + w, p, v + h, u, c, mt, axis, pos);
            }
            case 1 -> {   // Y: u = x, v = z
                if (pos) addQuad(u, p, v, u, p, v + h, u + w, p, v + h, u + w, p, v, c, mt, axis, pos);
                else addQuad(u, p, v, u + w, p, v, u + w, p, v + h, u, p, v + h, c, mt, axis, pos);
            }
            default -> {  // Z: u = x, v = y
                if (pos) addQuad(u, v, p, u + w, v, p, u + w, v + h, p, u, v + h, p, c, mt, axis, pos);
                else addQuad(u, v, p, u, v + h, p, u + w, v + h, p, u + w, v, p, c, mt, axis, pos);
            }
        }
    }
}
