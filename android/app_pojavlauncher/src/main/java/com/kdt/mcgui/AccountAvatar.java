package com.kdt.mcgui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.value.MinecraftAccount;

/**
 * Amatista: avatares redondos para las cuentas (estilo YouTube).
 * - Cuenta de Microsoft: la cara de su skin; si aún no está, la inicial del nombre.
 * - Cuenta local: silueta predeterminada.
 * - Sin cuenta: un "+".
 */
public final class AccountAvatar {
    private AccountAvatar() {}

    private static final int RING = Color.parseColor("#B57BFF");
    private static final int BG_INITIAL = Color.parseColor("#7B2FD6");
    private static final int BG_DEFAULT = Color.parseColor("#3A2758");
    private static final int FG_DEFAULT = Color.parseColor("#D9C6FF");

    public static Bitmap forAccount(@Nullable MinecraftAccount account, @Nullable String username, int size) {
        if (account == null && username == null) return plus(size);
        if (account == null) account = MinecraftAccount.load(username);
        String name = account != null && account.username != null ? account.username : username;
        if (account != null && account.isLocal()) return silhouette(size);
        Bitmap face = null;
        try {
            face = account != null ? account.getSkinFace() : MinecraftAccount.getSkinFace(name);
        } catch (Throwable ignored) {}
        if (face != null) return face(face, size);
        return initial(name, size);
    }

    public static Bitmap plus(int size) {
        Bitmap b = base(size, BG_DEFAULT);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(size * 0.09f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float m = size / 2f, l = size * 0.22f;
        c.drawLine(m - l, m, m + l, m, p);
        c.drawLine(m, m - l, m, m + l, p);
        ring(c, size);
        return b;
    }

    private static Bitmap silhouette(int size) {
        Bitmap b = base(size, BG_DEFAULT);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(FG_DEFAULT);
        Path clip = new Path();
        clip.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawCircle(size / 2f, size * 0.38f, size * 0.18f, p);                      // cabeza
        c.drawOval(new RectF(size * 0.17f, size * 0.62f, size * 0.83f, size * 1.12f), p); // hombros
        c.restore();
        ring(c, size);
        return b;
    }

    private static Bitmap initial(@Nullable String name, int size) {
        Bitmap b = base(size, BG_INITIAL);
        Canvas c = new Canvas(b);
        String letter = (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size * 0.5f);
        p.setTextAlign(Paint.Align.CENTER);
        Rect r = new Rect();
        p.getTextBounds(letter, 0, letter.length(), r);
        c.drawText(letter, size / 2f, size / 2f + r.height() / 2f, p);
        ring(c, size);
        return b;
    }

    private static Bitmap face(Bitmap face, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path clip = new Path();
        clip.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        Paint p = new Paint(); // sin filtro: píxeles nítidos como en Minecraft
        p.setFilterBitmap(false);
        c.drawBitmap(face, null, new Rect(0, 0, size, size), p);
        c.restore();
        ring(c, size);
        return b;
    }

    private static Bitmap base(int size, int color) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return b;
    }

    private static void ring(Canvas c, int size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(RING);
        float w = Math.max(2f, size * 0.05f);
        p.setStrokeWidth(w);
        c.drawCircle(size / 2f, size / 2f, size / 2f - w / 2f, p);
    }
}
