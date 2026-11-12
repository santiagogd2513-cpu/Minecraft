package com.forja.arca;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * El anillo de cuatro cuartos del botón de encendido de la 360: al cargar se van prendiendo uno por uno,
 * girando; al terminar quedan los cuatro firmes; y cuando algo falla se pone rojo con un cuarto apagado
 * (el famoso «anillo rojo»).
 */
final class Ring extends View {
    static final int SPIN = 0, DONE = 1, ERROR = 2;

    private static final int GREEN = 0xFF4ADE6B, RED = 0xFFFF4B4B, OFF = 0xFF1E2B21;
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int mode = SPIN;

    Ring(Context c) {
        super(c);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void setMode(int m) {
        mode = m;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.11f;
        float pad = stroke * 1.2f;
        box.set((getWidth() - size) / 2f + pad, (getHeight() - size) / 2f + pad, (getWidth() + size) / 2f - pad, (getHeight() + size) / 2f - pad);
        arc.setStrokeWidth(stroke);
        glow.setStrokeWidth(stroke * 1.9f);
        glow.setMaskFilter(new android.graphics.BlurMaskFilter(stroke * 0.9f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        long t = SystemClock.uptimeMillis();
        // un ciclo completo dura 2,4 s: cada cuarto se prende, se queda un rato y se apaga con los demás
        float phase = (t % 2400) / 2400f * 5f;       // 0..5
        for (int i = 0; i < 4; i++) {
            float level;     // 0 apagado, 1 prendido
            int color = GREEN;
            if (mode == DONE) {
                level = 1f;
            } else if (mode == ERROR) {
                color = RED;
                level = i == 3 ? 0.08f : 0.85f + 0.15f * (float) Math.sin(t / 260.0);
            } else {
                float on = phase - i;                 // cuánto lleva prendiéndose este cuarto
                level = on < 0 ? 0f : Math.min(1f, on / 0.4f);
                if (phase > 4.4f) level *= Math.max(0f, 1f - (phase - 4.4f) / 0.6f);   // al final se apagan juntos
            }
            float start = -90f + i * 90f + 7f;
            if (level > 0.02f) {
                glow.setColor(withAlpha(color, level * 0.55f));
                c.drawArc(box, start, 76f, false, glow);
            }
            arc.setColor(blend(OFF, color, level));
            c.drawArc(box, start, 76f, false, arc);
        }
        if (mode != DONE) postInvalidateOnAnimation();
    }

    private static int withAlpha(int color, float a) {
        return ((int) (255 * Math.min(1f, a)) << 24) | (color & 0xFFFFFF);
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
