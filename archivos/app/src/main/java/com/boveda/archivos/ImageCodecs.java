package com.boveda.archivos;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Formatos de imagen que Android no abre solo: TGA, TIFF (sin comprimir, PackBits y LZW), PSD de
 * Photoshop (la imagen ya combinada), QOI y PPM/PGM/PBM/PAM. Todo en Java, sin librerías.
 */
final class ImageCodecs {
    private ImageCodecs() {}

    /** Máximo de píxeles que se aceptan (para no quedarse sin memoria). */
    private static final long MAX_PIXELS = 40_000_000L;

    static boolean handles(String ext) {
        switch (ext) {
            case "tga": case "tpic": case "icb": case "vda": case "vst":
            case "tif": case "tiff":
            case "psd": case "psb":
            case "qoi":
            case "ppm": case "pgm": case "pbm": case "pnm": case "pam":
                return true;
            default:
                return false;
        }
    }

    static Bitmap decode(String ext, byte[] d) throws IOException {
        switch (ext) {
            case "tif": case "tiff": return tiff(d);
            case "psd": case "psb": return psd(d);
            case "qoi": return qoi(d);
            case "ppm": case "pgm": case "pbm": case "pnm": case "pam": return pnm(d);
            default: return tga(d);
        }
    }

    private static Bitmap make(int w, int h, int[] px) throws IOException {
        if (w <= 0 || h <= 0) throw new IOException("La imagen no tiene tamaño");
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private static void check(long w, long h) throws IOException {
        if (w <= 0 || h <= 0) throw new IOException("Tamaño inválido");
        if (w * h > MAX_PIXELS) throw new IOException("La imagen es demasiado grande (" + w + "×" + h + ")");
    }

    private static int u8(byte[] d, int i) {
        return d[i] & 0xFF;
    }

    private static int le16(byte[] d, int i) {
        return u8(d, i) | u8(d, i + 1) << 8;
    }

    private static int be16(byte[] d, int i) {
        return u8(d, i) << 8 | u8(d, i + 1);
    }

    private static long be32(byte[] d, int i) {
        return (long) u8(d, i) << 24 | u8(d, i + 1) << 16 | u8(d, i + 2) << 8 | u8(d, i + 3);
    }

    private static int argb(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ TGA

    private static Bitmap tga(byte[] d) throws IOException {
        if (d.length < 18) throw new IOException("TGA dañado");
        int idLen = u8(d, 0), cmapType = u8(d, 1), type = u8(d, 2);
        int cmapFirst = le16(d, 3), cmapLen = le16(d, 5), cmapBits = u8(d, 7);
        int w = le16(d, 12), h = le16(d, 14), bpp = u8(d, 16), desc = u8(d, 17);
        check(w, h);
        boolean rle = type >= 9;
        int base = type & 7;
        if (base < 1 || base > 3) throw new IOException("Tipo de TGA no soportado (" + type + ")");
        int pos = 18 + idLen;
        int[] cmap = null;
        if (cmapType == 1) {
            int eb = (cmapBits + 7) / 8;
            cmap = new int[cmapFirst + cmapLen];
            for (int i = 0; i < cmapLen; i++) cmap[cmapFirst + i] = tgaPixel(d, pos + i * eb, cmapBits, true);
            pos += cmapLen * eb;
        }
        boolean alpha = (desc & 0x0F) > 0;
        int bytes = (bpp + 7) / 8;
        int n = w * h;
        int[] lin = new int[n];
        int i = 0;
        while (i < n) {
            int count = 1;
            boolean repeat = false;
            if (rle) {
                if (pos >= d.length) break;
                int hdr = u8(d, pos++);
                count = (hdr & 0x7F) + 1;
                repeat = (hdr & 0x80) != 0;
            } else {
                count = n;
            }
            for (int k = 0; k < count && i < n; k++) {
                if (pos + bytes > d.length) { i = n; break; }
                int c;
                if (base == 1 && cmap != null) {
                    int idx = bytes == 1 ? u8(d, pos) : le16(d, pos);
                    c = idx < cmap.length ? cmap[idx] : 0xFF000000;
                } else if (base == 3) {
                    int g = u8(d, pos);
                    c = argb(bytes >= 2 && alpha ? u8(d, pos + 1) : 255, g, g, g);
                } else {
                    c = tgaPixel(d, pos, bpp, alpha);
                }
                lin[i++] = c;
                if (!repeat) pos += bytes;
            }
            if (repeat) pos += bytes;
        }
        boolean top = (desc & 0x20) != 0, rightToLeft = (desc & 0x10) != 0;
        int[] px = new int[n];
        for (int y = 0; y < h; y++) {
            int sy = top ? y : h - 1 - y;
            for (int x = 0; x < w; x++) {
                int sx = rightToLeft ? w - 1 - x : x;
                px[y * w + x] = lin[sy * w + sx];
            }
        }
        return make(w, h, px);
    }

    private static int tgaPixel(byte[] d, int p, int bpp, boolean alpha) {
        if (p + (bpp + 7) / 8 > d.length) return 0xFF000000;
        switch (bpp) {
            case 15: case 16: {
                int v = le16(d, p);
                int r = (v >> 10 & 31) * 255 / 31, g = (v >> 5 & 31) * 255 / 31, b = (v & 31) * 255 / 31;
                return argb(bpp == 16 && alpha ? ((v & 0x8000) != 0 ? 255 : 0) : 255, r, g, b);
            }
            case 24: return argb(255, u8(d, p + 2), u8(d, p + 1), u8(d, p));
            case 32: return argb(alpha ? u8(d, p + 3) : 255, u8(d, p + 2), u8(d, p + 1), u8(d, p));
            default: { int g = u8(d, p); return argb(255, g, g, g); }
        }
    }

    // ------------------------------------------------------------------ QOI

    private static Bitmap qoi(byte[] d) throws IOException {
        if (d.length < 14 || d[0] != 'q' || d[1] != 'o' || d[2] != 'i' || d[3] != 'f') throw new IOException("No es un QOI");
        long w = be32(d, 4), h = be32(d, 8);
        check(w, h);
        int n = (int) (w * h);
        int[] px = new int[n];
        int[] index = new int[64];
        int r = 0, g = 0, b = 0, a = 255;
        int p = 14, run = 0;
        for (int i = 0; i < n; i++) {
            if (run > 0) {
                run--;
            } else if (p < d.length - 8) {
                int b1 = u8(d, p++);
                if (b1 == 0xFE) { r = u8(d, p++); g = u8(d, p++); b = u8(d, p++); }
                else if (b1 == 0xFF) { r = u8(d, p++); g = u8(d, p++); b = u8(d, p++); a = u8(d, p++); }
                else if ((b1 & 0xC0) == 0x00) { int c = index[b1]; a = c >>> 24; r = c >> 16 & 255; g = c >> 8 & 255; b = c & 255; }
                else if ((b1 & 0xC0) == 0x40) { r = (r + (b1 >> 4 & 3) - 2) & 255; g = (g + (b1 >> 2 & 3) - 2) & 255; b = (b + (b1 & 3) - 2) & 255; }
                else if ((b1 & 0xC0) == 0x80) {
                    int b2 = u8(d, p++), vg = (b1 & 0x3F) - 32;
                    r = (r + vg - 8 + (b2 >> 4 & 15)) & 255; g = (g + vg) & 255; b = (b + vg - 8 + (b2 & 15)) & 255;
                } else { run = b1 & 0x3F; }
                index[(r * 3 + g * 5 + b * 7 + a * 11) % 64] = argb(a, r, g, b);
            }
            px[i] = argb(a, r, g, b);
        }
        return make((int) w, (int) h, px);
    }

    // ------------------------------------------------------------------ PPM / PGM / PBM / PAM

    private static Bitmap pnm(byte[] d) throws IOException {
        int[] pos = {0};
        String magic = token(d, pos);
        if (magic.length() != 2 || magic.charAt(0) != 'P') throw new IOException("No es un PNM");
        char t = magic.charAt(1);
        int w, h, max = 1, depth;
        if (t == '7') {
            w = 0; h = 0; depth = 0; max = 255;
            String k;
            while (!(k = token(d, pos)).equals("ENDHDR") && !k.isEmpty()) {
                switch (k) {
                    case "WIDTH": w = Integer.parseInt(token(d, pos)); break;
                    case "HEIGHT": h = Integer.parseInt(token(d, pos)); break;
                    case "DEPTH": depth = Integer.parseInt(token(d, pos)); break;
                    case "MAXVAL": max = Integer.parseInt(token(d, pos)); break;
                    default: token(d, pos);
                }
            }
            pos[0]++;
        } else {
            w = Integer.parseInt(token(d, pos));
            h = Integer.parseInt(token(d, pos));
            if (t != '1' && t != '4') max = Integer.parseInt(token(d, pos));
            depth = t == '3' || t == '6' ? 3 : 1;
            if (t >= '4') pos[0]++;   // un solo espacio antes de los datos binarios
        }
        check(w, h);
        int[] px = new int[w * h];
        boolean ascii = t <= '3';
        int p = pos[0];
        int sb = max > 255 ? 2 : 1;
        for (int y = 0; y < h; y++) {
            int bit = 0;
            for (int x = 0; x < w; x++) {
                int c;
                if (t == '1' || t == '4') {
                    int v;
                    if (ascii) v = Integer.parseInt(token(d, pos));
                    else { v = p < d.length ? (u8(d, p) >> (7 - bit) & 1) : 0; if (++bit == 8) { bit = 0; p++; } }
                    c = v == 1 ? 0xFF000000 : 0xFFFFFFFF;
                } else {
                    int[] s = new int[Math.max(1, depth)];
                    for (int k = 0; k < s.length; k++) {
                        int v;
                        if (ascii) v = Integer.parseInt(token(d, pos));
                        else { v = p + sb <= d.length ? (sb == 2 ? be16(d, p) : u8(d, p)) : 0; p += sb; }
                        s[k] = Math.min(255, v * 255 / Math.max(1, max));
                    }
                    if (s.length >= 3) c = argb(s.length >= 4 ? s[3] : 255, s[0], s[1], s[2]);
                    else c = argb(s.length == 2 ? s[1] : 255, s[0], s[0], s[0]);
                }
                px[y * w + x] = c;
            }
            if (bit != 0) p++;
        }
        return make(w, h, px);
    }

    private static String token(byte[] d, int[] pos) {
        int p = pos[0];
        while (p < d.length) {
            char c = (char) d[p];
            if (c == '#') { while (p < d.length && d[p] != '\n') p++; }
            else if (Character.isWhitespace(c)) p++;
            else break;
        }
        int s = p;
        while (p < d.length && !Character.isWhitespace((char) d[p])) p++;
        pos[0] = p;
        return new String(d, s, p - s, StandardCharsets.US_ASCII);
    }

    // ------------------------------------------------------------------ PSD (imagen combinada)

    private static Bitmap psd(byte[] d) throws IOException {
        if (d.length < 26 || d[0] != '8' || d[1] != 'B' || d[2] != 'P' || d[3] != 'S') throw new IOException("No es un PSD");
        int version = be16(d, 4);
        int channels = be16(d, 12);
        long h = be32(d, 14), w = be32(d, 18);
        int depth = be16(d, 22), mode = be16(d, 24);
        check(w, h);
        if (depth != 8 && depth != 16) throw new IOException("PSD de " + depth + " bits no soportado");
        int p = 26;
        p += 4 + (int) be32(d, p);                         // datos de color
        p += 4 + (int) be32(d, p);                         // recursos
        long lm = version == 2 ? (be32(d, p) << 32 | be32(d, p + 4)) : be32(d, p);
        p += (version == 2 ? 8 : 4) + (int) lm;            // capas (se usa la imagen ya combinada)
        int comp = be16(d, p);
        p += 2;
        int W = (int) w, H = (int) h, bpc = depth / 8;
        int use = Math.min(channels, mode == 3 ? 4 : 2);
        byte[][] plane = new byte[use][W * H];
        if (comp == 0) {
            for (int c = 0; c < use; c++) {
                for (int i = 0; i < W * H; i++) {
                    int at = p + (c * W * H + i) * bpc;
                    plane[c][i] = at < d.length ? d[at] : 0;
                }
            }
        } else if (comp == 1) {
            int cw = version == 2 ? 4 : 2;
            int[] counts = new int[channels * H];
            for (int i = 0; i < counts.length; i++) counts[i] = cw == 2 ? be16(d, p + i * 2) : (int) be32(d, p + i * 4);
            int q = p + counts.length * cw;
            for (int c = 0; c < channels; c++) {
                for (int y = 0; y < H; y++) {
                    int len = counts[c * H + y];
                    if (c < use) packBits(d, q, len, plane[c], y * W, W);
                    q += len;
                }
            }
        } else {
            throw new IOException("Compresión de PSD no soportada (" + comp + ")");
        }
        int[] px = new int[W * H];
        for (int i = 0; i < W * H; i++) {
            if (mode == 3 && use >= 3) {
                px[i] = argb(use >= 4 ? plane[3][i] & 255 : 255, plane[0][i] & 255, plane[1][i] & 255, plane[2][i] & 255);
            } else {
                int g = plane[0][i] & 255;
                px[i] = argb(use >= 2 ? plane[1][i] & 255 : 255, g, g, g);
            }
        }
        return make(W, H, px);
    }

    /** Descomprime PackBits (PSD y TIFF) de src[at..at+len) en dst a partir de off, hasta n bytes. */
    private static int packBits(byte[] src, int at, int len, byte[] dst, int off, int n) {
        int p = at, end = Math.min(src.length, at + len), o = 0;
        while (p < end && o < n) {
            int c = src[p++];
            if (c >= 0) {
                for (int k = 0; k <= c && p < end && o < n; k++) dst[off + o++] = src[p++];
            } else if (c != -128) {
                byte v = p < end ? src[p++] : 0;
                for (int k = 0; k <= -c && o < n; k++) dst[off + o++] = v;
            }
        }
        return o;
    }

    // ------------------------------------------------------------------ TIFF

    private static Bitmap tiff(byte[] d) throws IOException {
        if (d.length < 8) throw new IOException("TIFF dañado");
        boolean le = d[0] == 'I';
        Tif t = new Tif(d, le);
        if (t.s(2) != 42) throw new IOException("No es un TIFF (o es BigTIFF)");
        int ifd = (int) t.l(4);
        int n = t.s(ifd);
        long w = 0, h = 0, comp = 1, photo = 2, spp = 1, rps = 0, predictor = 1, planar = 1, extra = 0;
        int bps = 8;
        long[] offsets = null, counts = null;
        int[] cmap = null;
        for (int i = 0; i < n; i++) {
            int e = ifd + 2 + i * 12;
            int tag = t.s(e), type = t.s(e + 2);
            long cnt = t.l(e + 4);
            switch (tag) {
                case 256: w = t.val(e, type); break;
                case 257: h = t.val(e, type); break;
                case 258: bps = (int) t.arr(e, type, cnt)[0]; break;
                case 259: comp = t.val(e, type); break;
                case 262: photo = t.val(e, type); break;
                case 273: offsets = t.arr(e, type, cnt); break;
                case 277: spp = t.val(e, type); break;
                case 278: rps = t.val(e, type); break;
                case 279: counts = t.arr(e, type, cnt); break;
                case 284: planar = t.val(e, type); break;
                case 317: predictor = t.val(e, type); break;
                case 338: extra = t.val(e, type); break;
                case 320: {
                    long[] m = t.arr(e, type, cnt);
                    int k = m.length / 3;
                    cmap = new int[k];
                    for (int j = 0; j < k; j++) cmap[j] = argb(255, (int) (m[j] >> 8), (int) (m[k + j] >> 8), (int) (m[2 * k + j] >> 8));
                    break;
                }
                case 322: throw new IOException("TIFF por mosaicos no soportado");
                default:
            }
        }
        check(w, h);
        if (offsets == null || counts == null) throw new IOException("TIFF sin datos");
        if (planar != 1) throw new IOException("TIFF por planos no soportado");
        if (bps != 8 && bps != 1 && bps != 16) throw new IOException("TIFF de " + bps + " bits no soportado");
        if (comp != 1 && comp != 5 && comp != 32773) throw new IOException("Compresión de TIFF no soportada (" + comp + ")");
        int W = (int) w, H = (int) h;
        if (rps <= 0 || rps > H) rps = H;
        int bpc = bps == 16 ? 2 : 1;
        int rowBytes = bps == 1 ? (W * (int) spp + 7) / 8 : W * (int) spp * bpc;
        byte[] all = new byte[rowBytes * H];
        int filled = 0;
        for (int s = 0; s < offsets.length && filled < all.length; s++) {
            int off = (int) offsets[s], len = (int) Math.min(counts[s], d.length - offsets[s]);
            int want = (int) Math.min((long) rowBytes * rps, all.length - filled);
            if (comp == 1) { System.arraycopy(d, off, all, filled, Math.min(want, len)); }
            else if (comp == 32773) packBits(d, off, len, all, filled, want);
            else lzw(d, off, len, all, filled, want);
            if (predictor == 2 && bps == 8) {
                int rows = want / rowBytes;
                for (int r = 0; r < rows; r++) {
                    int b = filled + r * rowBytes;
                    int sp0 = (int) spp;
                    for (int x = sp0; x < rowBytes; x++) all[b + x] = (byte) (all[b + x] + all[b + x - sp0]);
                }
            }
            filled += want;
        }
        int[] px = new int[W * H];
        int sp = (int) spp;
        for (int y = 0; y < H; y++) {
            int row = y * rowBytes;
            for (int x = 0; x < W; x++) {
                int c;
                if (bps == 1) {
                    int v = all[row + x / 8] >> (7 - x % 8) & 1;
                    if (photo == 0) v = 1 - v;
                    c = v == 1 ? 0xFFFFFFFF : 0xFF000000;
                } else {
                    int b = row + x * sp * bpc;
                    int s0 = all[b] & 255;   // con 16 bits se usa el byte alto (TIFF de Intel: el segundo)
                    if (bpc == 2 && le) s0 = all[b + 1] & 255;
                    if (photo == 3 && cmap != null) c = s0 < cmap.length ? cmap[s0] : 0xFF000000;
                    else if (photo == 2 && sp >= 3) {
                        int g = all[b + bpc] & 255, bl = all[b + 2 * bpc] & 255;
                        if (bpc == 2 && le) { g = all[b + bpc + 1] & 255; bl = all[b + 2 * bpc + 1] & 255; }
                        int a = sp >= 4 && extra != 0 ? all[b + 3 * bpc + (bpc == 2 && le ? 1 : 0)] & 255 : 255;
                        c = argb(a, s0, g, bl);
                    } else {
                        int g = photo == 0 ? 255 - s0 : s0;
                        int a = sp >= 2 && extra != 0 ? all[b + bpc + (bpc == 2 && le ? 1 : 0)] & 255 : 255;
                        c = argb(a, g, g, g);
                    }
                }
                px[y * W + x] = c;
            }
        }
        return make(W, H, px);
    }

    /** LZW de TIFF (códigos de 9 a 12 bits, primero el bit más alto). */
    private static void lzw(byte[] src, int at, int len, byte[] dst, int off, int n) {
        byte[][] table = new byte[4096][];
        for (int i = 0; i < 256; i++) table[i] = new byte[]{(byte) i};
        int next = 258, width = 9, o = 0;
        long bitPos = (long) at * 8, end = (long) Math.min(src.length, at + len) * 8;
        byte[] prev = null;
        while (bitPos + width <= end && o < n) {
            int code = 0;
            for (int k = 0; k < width; k++) {
                long bp = bitPos + k;
                code = code << 1 | (src[(int) (bp >> 3)] >> (7 - (int) (bp & 7)) & 1);
            }
            bitPos += width;
            if (code == 257) break;
            if (code == 256) {
                next = 258;
                width = 9;
                prev = null;
                continue;
            }
            byte[] entry;
            if (code < next && table[code] != null) entry = table[code];
            else if (prev != null) { entry = new byte[prev.length + 1]; System.arraycopy(prev, 0, entry, 0, prev.length); entry[prev.length] = prev[0]; }
            else break;
            for (int k = 0; k < entry.length && o < n; k++) dst[off + o++] = entry[k];
            if (prev != null && next < 4096) {
                byte[] add = new byte[prev.length + 1];
                System.arraycopy(prev, 0, add, 0, prev.length);
                add[prev.length] = entry[0];
                table[next++] = add;
            }
            prev = entry;
            if (next >= (1 << width) - 1 && width < 12) width++;
        }
    }

    private static final class Tif {
        final byte[] d;
        final boolean le;

        Tif(byte[] d, boolean le) {
            this.d = d;
            this.le = le;
        }

        int s(int i) {
            return le ? le16(d, i) : be16(d, i);
        }

        long l(int i) {
            return le ? ((long) u8(d, i) | (long) u8(d, i + 1) << 8 | (long) u8(d, i + 2) << 16 | (long) u8(d, i + 3) << 24) : be32(d, i);
        }

        long val(int e, int type) {
            return type == 3 ? s(e + 8) : l(e + 8);
        }

        long[] arr(int e, int type, long cnt) {
            int size = type == 3 ? 2 : 4;
            int c = (int) Math.min(cnt, 1 << 20);
            int at = c * size <= 4 ? e + 8 : (int) l(e + 8);
            long[] r = new long[Math.max(1, c)];
            for (int i = 0; i < c; i++) r[i] = size == 2 ? s(at + i * 2) : l(at + i * 4);
            return r;
        }
    }

    static byte[] readAll(java.io.InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        return out.toByteArray();
    }
}
