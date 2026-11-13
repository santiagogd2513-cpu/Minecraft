package com.boveda.archivos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Herramientas de imágenes: cambiar el tamaño (estirar, ajustar o recortar), pasar a otro formato y quitar los
 * datos ocultos (EXIF: ubicación, cámara…). Todo se hace en el celular, sin internet.
 */
final class ImageTools {
    private ImageTools() {}

    enum Fit {STRETCH, FIT, COVER}

    enum Out {
        PNG(".png"), JPG(".jpg"), WEBP(".webp"), BMP(".bmp"), ICO(".ico");

        final String ext;

        Out(String e) {
            ext = e;
        }

        /** ¿Puede guardar transparencia? */
        boolean alpha() {
            return this == PNG || this == WEBP || this == ICO;
        }
    }

    /** Máximo de píxeles que se aceptan al abrir (para no quedarse sin memoria). */
    static final long MAX_PIXELS = 40_000_000L;

    // ------------------------------------------------------------------ abrir

    /** Una imagen abierta: sus píxeles ya con la rotación correcta, y el tamaño original. */
    static Bitmap load(byte[] data, String ext) throws IOException {
        ext = ext.toLowerCase(Locale.ROOT);
        Bitmap b;
        if (ImageCodecs.handles(ext)) {
            b = ImageCodecs.decode(ext, data);
        } else {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException(L.t("No pude abrir esta imagen (¿está dañada o es de un formato que Android no lee?)"));
            int sample = 1;
            while ((long) (o.outWidth / sample) * (o.outHeight / sample) > MAX_PIXELS) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b == null) throw new IOException(L.t("No pude abrir esta imagen (¿está dañada o es de un formato que Android no lee?)"));
            b = rotateByExif(b, data);
        }
        return b;
    }

    private static Bitmap rotateByExif(Bitmap b, byte[] data) {
        try {
            ExifInterface ex = new ExifInterface(new ByteArrayInputStream(data));
            int o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix m = new Matrix();
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90);
                case ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180);
                case ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270);
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1, 1);
                case ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1, -1);
                case ExifInterface.ORIENTATION_TRANSPOSE -> {
                    m.postRotate(90);
                    m.postScale(-1, 1);
                }
                case ExifInterface.ORIENTATION_TRANSVERSE -> {
                    m.postRotate(270);
                    m.postScale(-1, 1);
                }
                default -> {
                    return b;
                }
            }
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            return r;
        } catch (Exception e) {
            return b;
        }
    }

    static byte[] readAll(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int r;
        while ((r = in.read(buf)) >= 0) {
            total += r;
            if (total > limit) throw new IOException(L.t("El archivo es demasiado grande para esta herramienta"));
            o.write(buf, 0, r);
        }
        return o.toByteArray();
    }

    // ------------------------------------------------------------------ cambiar el tamaño

    /**
     * Cambia el tamaño. STRETCH estira hasta llenar (puede deformar), FIT mete la imagen entera sin deformarla y rellena
     * los bordes, COVER llena todo sin deformar y recorta lo que sobra por el centro. smooth=false conserva los
     * píxeles nítidos (ideal para pixel art y texturas).
     */
    static Bitmap resize(Bitmap src, int w, int h, Fit fit, boolean smooth, int background) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("tamaño");
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (Color.alpha(background) > 0) c.drawColor(background);
        Paint p = new Paint();
        p.setFilterBitmap(smooth);
        p.setAntiAlias(smooth);
        p.setDither(false);
        int sw = src.getWidth(), sh = src.getHeight();
        switch (fit) {
            case STRETCH -> c.drawBitmap(src, new Rect(0, 0, sw, sh), new RectF(0, 0, w, h), p);
            case FIT -> {
                float s = Math.min(w / (float) sw, h / (float) sh);
                float dw = sw * s, dh = sh * s;
                float l = (w - dw) / 2f, t = (h - dh) / 2f;
                c.drawBitmap(src, new Rect(0, 0, sw, sh), new RectF(l, t, l + dw, t + dh), p);
            }
            case COVER -> {
                float s = Math.max(w / (float) sw, h / (float) sh);
                float cw = w / s, ch = h / s;
                float l = (sw - cw) / 2f, t = (sh - ch) / 2f;
                c.drawBitmap(src, new Rect(Math.round(l), Math.round(t), Math.round(l + cw), Math.round(t + ch)), new RectF(0, 0, w, h), p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ guardar

    static void encode(Bitmap b, Out fmt, int quality, OutputStream o) throws IOException {
        switch (fmt) {
            case PNG -> {
                if (!b.compress(Bitmap.CompressFormat.PNG, 100, o)) throw new IOException(L.t("No pude guardar la imagen"));
            }
            case JPG -> {
                // JPG no tiene transparencia: lo transparente se pone sobre blanco
                Bitmap flat = flatten(b, Color.WHITE);
                boolean ok = flat.compress(Bitmap.CompressFormat.JPEG, quality, o);
                if (flat != b) flat.recycle();
                if (!ok) throw new IOException(L.t("No pude guardar la imagen"));
            }
            case WEBP -> {
                Bitmap.CompressFormat f = android.os.Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSLESS : Bitmap.CompressFormat.WEBP;
                if (!b.compress(f, 100, o)) throw new IOException(L.t("No pude guardar la imagen"));
            }
            case BMP -> {
                Bitmap flat = flatten(b, Color.WHITE);
                int[] px = new int[flat.getWidth() * flat.getHeight()];
                flat.getPixels(px, 0, flat.getWidth(), 0, 0, flat.getWidth(), flat.getHeight());
                writeBmp(px, flat.getWidth(), flat.getHeight(), o);
                if (flat != b) flat.recycle();
            }
            case ICO -> {
                if (b.getWidth() > 256 || b.getHeight() > 256) throw new IOException(L.t("Un ícono .ico no puede pasar de 256×256"));
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 100, png);
                writeIco(png.toByteArray(), b.getWidth(), b.getHeight(), o);
            }
        }
    }

    private static Bitmap flatten(Bitmap b, int bg) {
        if (!b.hasAlpha()) return b;
        Bitmap f = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(f);
        c.drawColor(bg);
        c.drawBitmap(b, 0, 0, null);
        return f;
    }

    /** BMP de 24 bits (BGR, filas de abajo hacia arriba), sin librerías. {@code argb} es el formato de Android. */
    static void writeBmp(int[] argb, int w, int h, OutputStream o) throws IOException {
        int rowBytes = (w * 3 + 3) & ~3;
        int size = 54 + rowBytes * h;
        byte[] head = new byte[54];
        head[0] = 'B';
        head[1] = 'M';
        le32(head, 2, size);
        le32(head, 10, 54);
        le32(head, 14, 40);
        le32(head, 18, w);
        le32(head, 22, h);
        head[26] = 1;
        head[28] = 24;
        le32(head, 34, rowBytes * h);
        le32(head, 38, 2835);
        le32(head, 42, 2835);
        o.write(head);
        byte[] row = new byte[rowBytes];
        for (int y = h - 1; y >= 0; y--) {
            java.util.Arrays.fill(row, (byte) 0);
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                row[x * 3] = (byte) (p & 0xFF);
                row[x * 3 + 1] = (byte) ((p >> 8) & 0xFF);
                row[x * 3 + 2] = (byte) ((p >> 16) & 0xFF);
            }
            o.write(row);
        }
    }

    /** Ícono .ico con una sola imagen PNG adentro (lo entienden Windows y todos los programas actuales). */
    static void writeIco(byte[] png, int w, int h, OutputStream o) throws IOException {
        byte[] head = new byte[22];
        head[2] = 1;       // tipo: ícono
        head[4] = 1;       // una imagen
        head[6] = (byte) (w >= 256 ? 0 : w);
        head[7] = (byte) (h >= 256 ? 0 : h);
        head[10] = 1;      // planos
        head[12] = 32;     // bits por píxel
        le32(head, 14, png.length);
        le32(head, 18, 22);
        o.write(head);
        o.write(png);
    }

    private static void le32(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }
}
