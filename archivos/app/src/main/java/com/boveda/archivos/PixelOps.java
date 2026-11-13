package com.boveda.archivos;

import java.util.ArrayDeque;

/** Las operaciones de dibujo del editor de pixel art, sin nada de Android (así se prueban aparte). */
final class PixelOps {
    private PixelOps() {}

    /** Quien recibe cada píxel que hay que pintar. */
    interface Plot {
        void plot(int x, int y);
    }

    /** Línea de Bresenham entre dos puntos, incluyendo los dos extremos. */
    static void line(int x0, int y0, int x1, int y1, Plot p) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            p.plot(x0, y0);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Rectángulo entre dos esquinas, solo el borde o relleno. */
    static void rect(int x0, int y0, int x1, int y1, boolean filled, Plot p) {
        int l = Math.min(x0, x1), r = Math.max(x0, x1), t = Math.min(y0, y1), b = Math.max(y0, y1);
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                if (filled || x == l || x == r || y == t || y == b) p.plot(x, y);
            }
        }
    }

    /** Elipse (o círculo) dentro del rectángulo dado, solo el borde o rellena. */
    static void ellipse(int x0, int y0, int x1, int y1, boolean filled, Plot p) {
        int l = Math.min(x0, x1), r = Math.max(x0, x1), t = Math.min(y0, y1), b = Math.max(y0, y1);
        double cx = (l + r) / 2.0, cy = (t + b) / 2.0, rx = (r - l + 1) / 2.0, ry = (b - t + 1) / 2.0;
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                double nx = (x - cx) / rx, ny = (y - cy) / ry;
                double d = nx * nx + ny * ny;
                if (filled ? d <= 1.0 : d <= 1.0 && isEdge(x, y, cx, cy, rx, ry)) p.plot(x, y);
            }
        }
    }

    /** Un píxel de la elipse es de borde si alguno de sus vecinos de arriba, abajo, izquierda o derecha queda afuera. */
    private static boolean isEdge(int x, int y, double cx, double cy, double rx, double ry) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] k : d) {
            double nx = (x + k[0] - cx) / rx, ny = (y + k[1] - cy) / ry;
            if (nx * nx + ny * ny > 1.0) return true;
        }
        return false;
    }

    /**
     * Relleno de cubeta: cambia por {@code color} todo el grupo de píxeles pegados que tengan el mismo color que el de
     * partida (4 vecinos). Devuelve los índices cambiados.
     */
    static int[] floodFill(int[] px, int w, int h, int x, int y, int color) {
        if (x < 0 || y < 0 || x >= w || y >= h) return new int[0];
        int target = px[y * w + x];
        if (target == color) return new int[0];
        int[] changed = new int[w * h];
        int n = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        boolean[] seen = new boolean[w * h];
        while (!stack.isEmpty()) {
            int[] s = stack.pop();
            int cx = s[0], cy = s[1];
            // se avanza por la fila hacia los dos lados y se miran las filas de arriba y de abajo
            int l = cx, r = cx;
            while (l > 0 && px[cy * w + l - 1] == target && !seen[cy * w + l - 1]) l--;
            while (r < w - 1 && px[cy * w + r + 1] == target && !seen[cy * w + r + 1]) r++;
            for (int i = l; i <= r; i++) {
                int idx = cy * w + i;
                if (px[idx] != target || seen[idx]) continue;
                seen[idx] = true;
                changed[n++] = idx;
                if (cy > 0 && px[idx - w] == target && !seen[idx - w]) stack.push(new int[]{i, cy - 1});
                if (cy < h - 1 && px[idx + w] == target && !seen[idx + w]) stack.push(new int[]{i, cy + 1});
            }
        }
        int[] out = java.util.Arrays.copyOf(changed, n);
        for (int idx : out) px[idx] = color;
        return out;
    }

    /** Mezcla {@code top} (con su transparencia) sobre {@code base}, como al pintar con un color semitransparente. */
    static int over(int top, int base) {
        int ta = top >>> 24;
        if (ta == 255) return top;
        if (ta == 0) return base;
        int ba = base >>> 24;
        float a = ta / 255f, b = ba / 255f * (1 - a);
        float oa = a + b;
        if (oa <= 0f) return 0;
        int r = Math.round((((top >> 16) & 0xFF) * a + ((base >> 16) & 0xFF) * b) / oa);
        int g = Math.round((((top >> 8) & 0xFF) * a + ((base >> 8) & 0xFF) * b) / oa);
        int bl = Math.round(((top & 0xFF) * a + (base & 0xFF) * b) / oa);
        return Math.round(oa * 255) << 24 | r << 16 | g << 8 | bl;
    }
}
