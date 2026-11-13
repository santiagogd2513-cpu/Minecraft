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

    /** ¿Android tiene las animaciones apagadas (Accesibilidad → Quitar animaciones)? */
    static boolean systemOff(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean on(Context c) {
        Prefs pf = new Prefs(c);
        if (!pf.superAnim()) return false;
        if (pf.animForce()) return true;
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

        private final float phase;

        SelectGlow(float radius) {
            this(radius, 0f);
        }

        /** phase (0 a 1) desplaza el degradado: filas seguidas con fases distintas hacen una ola. */
        SelectGlow(float radius, float phase) {
            this.radius = radius;
            this.phase = phase;
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
            float t = ((System.currentTimeMillis() % 2600) / 2600f + phase) % 1f;
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
                        int dark = mix(color, Color.BLACK, 0.45f), light = mix(color, Color.WHITE, 0.8f);
                        LinearGradient g = new LinearGradient(0, 0, 220, 0, new int[]{dark, color, light, color, dark},
                                new float[]{0f, 0.35f, 0.5f, 0.65f, 1f}, Shader.TileMode.REPEAT);
                        m.setTranslate((now % 1100) / 1100f * 220f, 0);
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

    // ------------------------------------------------------------------ ayudas de animación

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Las tarjetas «se levantan» un poquito mientras las tocas. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void lift(View v) {
        v.setOnTouchListener((view, ev) -> {
            if (!on(view.getContext())) return false;
            switch (ev.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN ->
                        view.animate().scaleX(1.03f).scaleY(1.03f).translationZ(dp(view.getContext(), 8)).setDuration(110).start();
                case android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(160).start();
                default -> {
                }
            }
            return false;
        });
    }

    /** Aparece creciendo desde chiquito, con un rebotito. */
    static void popIn(View v, long delay) {
        if (!on(v.getContext())) return;
        v.setAlpha(0f);
        v.setScaleX(0.7f);
        v.setScaleY(0.7f);
        v.setTranslationY(dp(v.getContext(), 14));
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setStartDelay(delay).setDuration(280)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
    }

    /** Un cartel que sube desde abajo. */
    static void slideUp(View v) {
        if (!on(v.getContext())) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 70));
        v.animate().alpha(1f).translationY(0).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f)).start();
    }

    /** El botón de crear: brilla en azul y da un latido. */
    static void pulseFab(View fab) {
        if (!on(fab.getContext())) return;
        fab.setOutlineSpotShadowColor(0xFF3FA0FF);
        fab.setOutlineAmbientShadowColor(0xFF3FA0FF);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f, 0f);
        va.setDuration(420);
        float base = fab.getElevation();
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            fab.setElevation(base + dp(fab.getContext(), 22) * f);
            fab.setScaleX(1f + 0.14f * f);
            fab.setScaleY(1f + 0.14f * f);
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                fab.setElevation(base);
                fab.setScaleX(1f);
                fab.setScaleY(1f);
                fab.setOutlineSpotShadowColor(0xFF000000);
                fab.setOutlineAmbientShadowColor(0xFF000000);
            }
        });
        va.start();
    }

    /** Un destello con un ícono grande en el centro de la pantalla (el candado al abrir la Bóveda). */
    static void flash(android.app.Activity a, String emoji) {
        if (!on(a)) return;
        android.view.ViewGroup decor = (android.view.ViewGroup) a.getWindow().getDecorView();
        android.widget.FrameLayout layer = new android.widget.FrameLayout(a);
        View ring = new View(a);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setStroke(dp(a, 4), 0xFF7BC4FF);
        ring.setBackground(g);
        android.widget.FrameLayout.LayoutParams rl = new android.widget.FrameLayout.LayoutParams(dp(110, a), dp(110, a), android.view.Gravity.CENTER);
        layer.addView(ring, rl);
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(emoji);
        t.setTextSize(64);
        t.setGravity(android.view.Gravity.CENTER);
        layer.addView(t, new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        layer.setClickable(false);
        decor.addView(layer, new android.view.ViewGroup.LayoutParams(-1, -1));
        t.setScaleX(0.3f);
        t.setScaleY(0.3f);
        t.setRotationY(-180f);
        t.animate().scaleX(1.15f).scaleY(1.15f).rotationY(0f).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        ring.setScaleX(0.4f);
        ring.setScaleY(0.4f);
        ring.animate().scaleX(2.6f).scaleY(2.6f).alpha(0f).setStartDelay(200).setDuration(650).start();
        layer.animate().alpha(0f).setStartDelay(780).setDuration(260).withEndAction(() -> decor.removeView(layer)).start();
    }

    private static int dp(int v, Context c) {
        return dp(c, v);
    }

    /** Un ícono que viaja en arco de un punto a otro de la pantalla y se desvanece al llegar. */
    static void fly(android.app.Activity a, String emoji, float x0, float y0, float x1, float y1, long delay) {
        if (!on(a)) return;
        android.view.ViewGroup decor = (android.view.ViewGroup) a.getWindow().getDecorView();
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(emoji);
        t.setTextSize(26);
        t.setElevation(dp(a, 12));
        decor.addView(t, new android.widget.FrameLayout.LayoutParams(-2, -2));
        t.setAlpha(0f);
        float cx = (x0 + x1) / 2f + dp(a, 60), cy = Math.min(y0, y1) - dp(a, 90);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setStartDelay(delay);
        va.setDuration(520);
        va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        va.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue(), u = 1 - f;
            t.setX(u * u * x0 + 2 * u * f * cx + f * f * x1);
            t.setY(u * u * y0 + 2 * u * f * cy + f * f * y1);
            t.setAlpha(f < 0.1f ? f * 10f : f > 0.8f ? (1f - f) * 5f : 1f);
            float sc = 1.2f - 0.5f * f;
            t.setScaleX(sc);
            t.setScaleY(sc);
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                decor.removeView(t);
            }
        });
        va.start();
    }
}
