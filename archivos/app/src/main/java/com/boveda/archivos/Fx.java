package com.boveda.archivos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.View;

import java.util.Random;

/**
 * «Modo súper animaciones» (experimental): brillos, degradados y chispas. Todo es opcional y
 * se apaga si el usuario quitó las animaciones de Android.
 */
final class Fx {
    private Fx() {}

    static boolean on(Context c) {
        if (!new Prefs(c).superAnim()) return false;
        try {
            // si el sistema tiene las animaciones apagadas, se respeta
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    // ------------------------------------------------------------------ lo seleccionado respira con un degradado

    static final class SelectGlow extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final RectF r = new RectF();
        private final float radius;
        private LinearGradient shader;
        private int lastW;

        SelectGlow(float radius) {
            this.radius = radius;
        }

        @Override
        public void draw(Canvas c) {
            int w = getBounds().width();
            if (w <= 0) return;
            if (shader == null || lastW != w) {
                lastW = w;
                shader = new LinearGradient(0, 0, w, 0, new int[]{0xFF16264A, 0xFF2A5AB8, 0xFF16264A},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.REPEAT);
                p.setShader(shader);
            }
            float t = (System.currentTimeMillis() % 2600) / 2600f;
            m.setTranslate(w * t, 0);
            shader.setLocalMatrix(m);
            r.set(getBounds());
            c.drawRoundRect(r, radius, radius, p);
            if (isVisible()) invalidateSelf();
        }

        @Override
        public void setAlpha(int a) {
            p.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ barra de progreso con degradado y chispa

    /** Misma barra de siempre; con el modo activo lleva degradado que se mueve y suelta una chispa al terminar. */
    static final class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Matrix m = new Matrix();
        private int max = 1000, progress;
        private float shown;               // lo que se dibuja, se acerca poco a poco al real
        private boolean indeterminate;
        private int color = Ui.ACCENT;
        private long sparkAt = -1;
        private int sparkColor;
        private final float[] angle = new float[16], speed = new float[16];

        Bar(Context c) {
            super(c);
            Random rnd = new Random(7);
            for (int i = 0; i < angle.length; i++) {
                angle[i] = (float) (rnd.nextFloat() * Math.PI * 2);
                speed[i] = 0.5f + rnd.nextFloat();
            }
        }

        void setMax(int m) {
            max = m;
        }

        void setProgress(int v) {
            progress = v;
            invalidate();
        }

        int getProgress() {
            return progress;
        }

        void setIndeterminate(boolean b) {
            indeterminate = b;
            invalidate();
        }

        void setTint(int c) {
            color = c;
            invalidate();
        }

        /** La chispa del final: verde si salió bien, roja si falló. */
        void spark(boolean ok) {
            if (!Fx.on(getContext())) return;
            sparkColor = ok ? Ui.GOOD : Ui.BAD;
            sparkAt = System.currentTimeMillis();
            if (ok) {
                shown = 1f;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            boolean fx = Fx.on(getContext());
            float w = getWidth(), h = getHeight(), th = Ui.dp(getContext(), 8), top = (h - th) / 2f;
            float rad = th / 2f;
            p.setShader(null);
            p.setColor(0xFF2A3346);
            r.set(0, top, w, top + th);
            c.drawRoundRect(r, rad, rad, p);
            float target = indeterminate ? 0.28f : Math.max(0, Math.min(1, progress / (float) max));
            if (fx && !indeterminate) shown += (target - shown) * 0.18f;
            else shown = target;
            if (Math.abs(shown - target) < 0.002f) shown = target;
            long now = System.currentTimeMillis();
            float fillW = w * shown;
            float left = 0;
            if (indeterminate) {
                float t = (now % 1400) / 1400f;
                left = (w + fillW) * t - fillW;
            }
            if (fillW > 0.5f) {
                float x0 = Math.max(0, left), x1 = Math.min(w, left + fillW);
                if (x1 > x0) {
                    if (fx) {
                        int dark = mix(color, Color.BLACK, 0.25f), light = mix(color, Color.WHITE, 0.45f);
                        LinearGradient g = new LinearGradient(0, 0, 220, 0, new int[]{dark, color, light, color, dark},
                                new float[]{0f, 0.3f, 0.5f, 0.7f, 1f}, Shader.TileMode.REPEAT);
                        m.setTranslate((now % 1600) / 1600f * 220f, 0);
                        g.setLocalMatrix(m);
                        p.setShader(g);
                    } else {
                        p.setColor(color);
                    }
                    r.set(x0, top, x1, top + th);
                    c.drawRoundRect(r, rad, rad, p);
                    p.setShader(null);
                }
            }
            boolean again = indeterminate && fx || fx && shown != target || fx && !indeterminate && progress > 0 && progress < max;
            // la chispa sale del final de lo llenado
            if (sparkAt >= 0) {
                float t = (now - sparkAt) / 750f;
                if (t >= 1) {
                    sparkAt = -1;
                } else {
                    float cx = Math.max(rad, w * Math.max(shown, 0.02f)), cy = h / 2f;
                    float reach = h * 0.5f;
                    for (int i = 0; i < angle.length; i++) {
                        float d = reach * t * speed[i];
                        float x = cx + (float) Math.cos(angle[i]) * d, y = cy + (float) Math.sin(angle[i]) * d;
                        p.setColor(sparkColor);
                        p.setAlpha((int) (255 * (1 - t)));
                        c.drawCircle(Math.min(w, x), y, Ui.dp(getContext(), 2.2f) * (1 - t * 0.6f), p);
                    }
                    p.setAlpha(255);
                    // un destello suave sobre el final de la barra
                    p.setColor(sparkColor);
                    p.setAlpha((int) (110 * (1 - t)));
                    c.drawCircle(Math.min(w - rad, cx), cy, th * (1.2f + t), p);
                    p.setAlpha(255);
                    again = true;
                }
            }
            if (again) postInvalidateOnAnimation();
        }
    }
}
