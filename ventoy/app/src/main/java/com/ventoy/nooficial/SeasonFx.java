package com.ventoy.nooficial;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Random;

/**
 * Los efectos de cada temporada, dibujados en código:
 * <ul>
 *   <li>Navidad: nieve que cae por toda la pantalla y, en las tarjetas grandes, un borde de nieve
 *   con una tira de lucecitas de colores que titilan.</li>
 *   <li>Día de las Velitas: chispitas que suben desde abajo con un resplandor cálido y, en las
 *   tarjetas grandes, una guirnalda de faroles de colores que se mecen y brillan.</li>
 *   <li>San Valentín: corazones que suben flotando y, en las tarjetas, una guirnalda de corazones
 *   que laten.</li>
 *   <li>Amor y Amistad: confeti, corazones y estrellitas de colores que caen y, en las tarjetas,
 *   banderines de fiesta que ondean.</li>
 * </ul>
 * Nada de esto recibe toques: todo pasa por debajo hacia la app.
 */
final class SeasonFx {
    private SeasonFx() {}

    private static final Object TAG = new Object();
    private static final Handler H = new Handler(Looper.getMainLooper());

    /** Si la temporada de ahora tiene decoraciones. */
    static int active(Context c) {
        int s = Season.current(c);
        return s >= 0 && Season.THEME_READY[s] ? s : Season.NONE;
    }

    /** Colores de fondo de las tarjetas grandes en la temporada (o los de siempre). */
    static int[] cardColors(Context c, int c1, int c2) {
        return switch (active(c)) {
            case Season.NAVIDAD -> new int[]{0xFF17482F, 0xFF4E1424};
            case Season.VELITAS -> new int[]{0xFF5A340C, 0xFF2C1508};
            case Season.VALENTIN -> new int[]{0xFF5C1232, 0xFF2A0816};
            case Season.AMISTAD -> new int[]{0xFF5A3A10, 0xFF4A1438};
            default -> new int[]{c1, c2};
        };
    }

    // ------------------------------------------------------------------ pantalla completa

    /** false en apps que ponen la nieve en otro lado (Nocturna la pone solo en su inicio). */
    static boolean overlay = true;

    /** Pone (o quita) la nieve o las chispitas encima de toda la ventana. Llamar en onResume. */
    static void attach(Activity a) {
        if (!overlay) return;
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        View old = decor.findViewWithTag(TAG);
        int s = active(a);
        if (old instanceof FallView && ((FallView) old).season == s) return;
        if (old != null) decor.removeView(old);
        if (s == Season.NONE) return;
        FallView f = new FallView(a, s);
        f.setTag(TAG);
        decor.addView(f, new FrameLayout.LayoutParams(-1, -1));
    }

    static final class FallView extends View {
        final int season;
        private final float d;
        private final Random rnd = new Random();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float[] x, y, r, v, ph;
        private int[] col;
        /** Amor y Amistad: 0 confeti, 1 corazón, 2 estrellita. */
        private int[] kind;
        private final Path shape = new Path();
        private long last;
        private final Runnable tick = this::invalidate;

        FallView(Context c, int season) {
            super(c);
            this.season = season;
            d = c.getResources().getDisplayMetrics().density;
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            return false;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int n = season == Season.NAVIDAD ? 46 : season == Season.VALENTIN ? 22 : season == Season.AMISTAD ? 40 : 30;
            x = new float[n];
            y = new float[n];
            r = new float[n];
            v = new float[n];
            ph = new float[n];
            col = new int[n];
            kind = new int[n];
            for (int i = 0; i < n; i++) spawn(i, w, h, true);
            if (season == Season.VELITAS && h > 0) {
                glow.setShader(new LinearGradient(0, h, 0, h - 170 * d, 0x40FF9A2E, 0x00FF9A2E, Shader.TileMode.CLAMP));
            }
        }

        private void spawn(int i, int w, int h, boolean anywhere) {
            x[i] = rnd.nextFloat() * w;
            ph[i] = rnd.nextFloat() * 6.28f;
            if (season == Season.NAVIDAD) {
                r[i] = (1.2f + rnd.nextFloat() * 2.4f) * d;
                v[i] = (16 + rnd.nextFloat() * 34) * d * (r[i] / (2.4f * d));
                y[i] = anywhere ? rnd.nextFloat() * h : -r[i] * 2;
                col[i] = Color.argb(90 + rnd.nextInt(120), 255, 255, 255);
            } else if (season == Season.VALENTIN) {
                r[i] = (3.5f + rnd.nextFloat() * 4) * d;
                v[i] = (16 + rnd.nextFloat() * 22) * d;
                y[i] = anywhere ? rnd.nextFloat() * h : h + r[i] * 2;
                int[] pink = {0xFFFF4D8D, 0xFFFF7AA8, 0xFFE63946, 0xFFFFB3CC, 0xFFC9184A};
                col[i] = pink[rnd.nextInt(pink.length)];
            } else if (season == Season.AMISTAD) {
                kind[i] = rnd.nextInt(10) < 6 ? 0 : rnd.nextInt(10) < 6 ? 1 : 2;
                r[i] = (kind[i] == 0 ? 2.5f + rnd.nextFloat() * 2.5f : 4 + rnd.nextFloat() * 4) * d;
                v[i] = (22 + rnd.nextFloat() * 36) * d;
                y[i] = anywhere ? rnd.nextFloat() * h : -r[i] * 3;
                int[] party = {0xFFFF4D6D, 0xFFFFC23D, 0xFF3DDC84, 0xFF3FB8FF, 0xFFB06BFF, 0xFFFF7AC8, 0xFFFF8A3D};
                col[i] = party[rnd.nextInt(party.length)];
            } else {
                r[i] = (1.0f + rnd.nextFloat() * 1.8f) * d;
                v[i] = (14 + rnd.nextFloat() * 26) * d;
                y[i] = anywhere ? h - rnd.nextFloat() * h * 0.6f : h + r[i] * 2;
                int[] warm = {0xFFFFD27A, 0xFFFFA63D, 0xFFFF7A1A, 0xFFFFE9A8};
                col[i] = warm[rnd.nextInt(warm.length)];
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (x == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            float t = now / 1000f;
            int w = getWidth(), h = getHeight();
            if (season == Season.VELITAS) c.drawRect(0, h - 170 * d, w, h, glow);
            for (int i = 0; i < x.length; i++) {
                if (season == Season.NAVIDAD) {
                    y[i] += v[i] * dt;
                    float sx = x[i] + (float) Math.sin(t * 0.9f + ph[i]) * 10 * d;
                    if (y[i] > h + r[i] * 2) spawn(i, w, h, false);
                    p.setColor(col[i]);
                    c.drawCircle(sx, y[i], r[i], p);
                } else if (season == Season.VALENTIN) {
                    // corazones que suben meciéndose y se desvanecen arriba
                    y[i] -= v[i] * dt;
                    if (y[i] < -r[i] * 2) spawn(i, w, h, false);
                    float sx = x[i] + (float) Math.sin(t * 1.1f + ph[i]) * 14 * d;
                    float fade = Math.min(1, Math.max(0, y[i] / (h * 0.35f)));
                    int a = (int) ((110 + (r[i] / d) * 8) * fade);
                    c.save();
                    c.rotate((float) Math.sin(t * 1.3f + ph[i]) * 14, sx, y[i]);
                    p.setColor((col[i] & 0x00FFFFFF) | (Math.min(230, a) << 24));
                    heart(shape, sx, y[i], r[i]);
                    c.drawPath(shape, p);
                    c.restore();
                } else if (season == Season.AMISTAD) {
                    // confeti que cae girando, con corazones y estrellitas
                    y[i] += v[i] * dt;
                    if (y[i] > h + r[i] * 3) spawn(i, w, h, false);
                    float sx = x[i] + (float) Math.sin(t * 1.4f + ph[i]) * 12 * d;
                    p.setColor((col[i] & 0x00FFFFFF) | 0xD0000000);
                    c.save();
                    c.rotate((t * 90 + ph[i] * 57) % 360, sx, y[i]);
                    if (kind[i] == 0) {
                        c.scale((float) Math.cos(t * 4 + ph[i]), 1, sx, y[i]);
                        c.drawRect(sx - r[i], y[i] - r[i] * 0.45f, sx + r[i], y[i] + r[i] * 0.45f, p);
                    } else if (kind[i] == 1) {
                        heart(shape, sx, y[i], r[i]);
                        c.drawPath(shape, p);
                    } else {
                        star(shape, sx, y[i], r[i]);
                        c.drawPath(shape, p);
                    }
                    c.restore();
                } else {
                    y[i] -= v[i] * dt;
                    float sx = x[i] + (float) Math.sin(t * 1.6f + ph[i]) * 6 * d;
                    // se apagan al subir: viven en la mitad de abajo de la pantalla
                    float life = (h - y[i]) / (h * 0.62f);
                    if (life >= 1 || y[i] < 0) {
                        spawn(i, w, h, false);
                        continue;
                    }
                    float flick = 0.65f + 0.35f * (float) Math.sin(t * 9 + ph[i] * 3);
                    int a = (int) (230 * (1 - life) * flick);
                    p.setColor((col[i] & 0x00FFFFFF) | (Math.max(0, a) << 24));
                    c.drawCircle(sx, y[i], r[i], p);
                    p.setColor((col[i] & 0x00FFFFFF) | (Math.max(0, a / 9) << 24));
                    c.drawCircle(sx, y[i], r[i] * 2.2f, p);
                }
            }
            H.removeCallbacks(tick);
            if (isShown()) H.postDelayed(tick, 33);
        }

        @Override
        protected void onVisibilityChanged(View v, int vis) {
            super.onVisibilityChanged(v, vis);
            last = 0;
            if (vis == VISIBLE) invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            H.removeCallbacks(tick);
        }
    }

    /** Un corazón centrado en (cx, cy) de ancho ~2·s. */
    static void heart(Path p, float cx, float cy, float s) {
        p.reset();
        p.moveTo(cx, cy + s * 0.75f);
        p.cubicTo(cx - s * 1.9f, cy - s * 0.4f, cx - s * 0.9f, cy - s * 1.7f, cx, cy - s * 0.65f);
        p.cubicTo(cx + s * 0.9f, cy - s * 1.7f, cx + s * 1.9f, cy - s * 0.4f, cx, cy + s * 0.75f);
        p.close();
    }

    /** Una estrella de cinco puntas centrada en (cx, cy). */
    static void star(Path p, float cx, float cy, float s) {
        p.reset();
        for (int k = 0; k < 10; k++) {
            float rr = k % 2 == 0 ? s : s * 0.45f;
            double a = -Math.PI / 2 + k * Math.PI / 5;
            float px = cx + rr * (float) Math.cos(a), py = cy + rr * (float) Math.sin(a);
            if (k == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.close();
    }

    // ------------------------------------------------------------------ tarjetas grandes

    /** Alto extra que necesitan las tarjetas arriba para que la decoración no tape el texto. */
    static int topSpace(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return active(c) == Season.NONE ? 0 : Math.round(20 * d);
    }

    /**
     * Decora una tarjeta grande: borde de nieve con lucecitas (Navidad) o una guirnalda de faroles
     * (Velitas). Corre la tarjeta {@link #topSpace} hacia abajo por dentro.
     */
    static void decorate(View card, float radiusPx) {
        int s = active(card.getContext());
        if (s == Season.NONE) return;
        card.setPadding(card.getPaddingLeft(), card.getPaddingTop() + topSpace(card.getContext()),
                card.getPaddingRight(), card.getPaddingBottom());
        CardDeco deco = new CardDeco(card, s, radiusPx);
        card.getOverlay().add(deco);
        card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> deco.setBounds(0, 0, r - l, b - t));
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                deco.invalidateSelf();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                H.removeCallbacks(deco.tick);
            }
        });
    }

    static final class CardDeco extends Drawable {
        private final View host;
        private final int season;
        private final float d, radius;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wire = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path clip = new Path();
        private final RectF rect = new RectF();
        private final long seed;
        final Runnable tick = this::invalidateSelf;
        private int lastW = -1;
        private float[] drips;

        CardDeco(View host, int season, float radius) {
            this.host = host;
            this.season = season;
            this.radius = radius;
            d = host.getResources().getDisplayMetrics().density;
            seed = System.identityHashCode(host);
            wire.setStyle(Paint.Style.STROKE);
            wire.setStrokeWidth(1.3f * d);
        }

        @Override
        public void draw(Canvas c) {
            int w = getBounds().width(), h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            float t = SystemClock.uptimeMillis() / 1000f;
            c.save();
            clip.reset();
            rect.set(0, 0, w, h);
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            c.clipPath(clip);
            if (season == Season.NAVIDAD) drawChristmas(c, w, t);
            else if (season == Season.VALENTIN) drawHearts(c, w, t);
            else if (season == Season.AMISTAD) drawBunting(c, w, t);
            else drawVelitas(c, w, t);
            c.restore();
            H.removeCallbacks(tick);
            if (host.isAttachedToWindow() && host.isShown()) H.postDelayed(tick, 40);
        }

        /** La curva de la tira de luces o de la guirnalda: cuelga entre puntos cada ~70dp. */
        private float sag(float x, float span, float y0, float depth) {
            float u = (x % span) / span;
            return y0 + depth * 4 * u * (1 - u);
        }

        private void drawChristmas(Canvas c, int w, float t) {
            float cap = 7 * d;
            if (lastW != w) {
                lastW = w;
                Random r = new Random(seed);
                int n = Math.max(3, (int) (w / (34 * d)));
                drips = new float[n * 2];
                for (int i = 0; i < n; i++) {
                    drips[i * 2] = (i + 0.2f + r.nextFloat() * 0.6f) * w / n;
                    drips[i * 2 + 1] = (2 + r.nextFloat() * 6) * d;
                }
            }
            // la tira de luces, debajo de la nieve
            float span = Math.max(60 * d, w / Math.max(1, Math.round(w / (80 * d))));
            float y0 = cap + 2 * d, depth = 7 * d;
            path.reset();
            path.moveTo(0, y0);
            for (float x = 0; x <= w; x += 3 * d) path.lineTo(x, sag(x, span, y0, depth));
            wire.setColor(0xFF1E3B22);
            c.drawPath(path, wire);
            int[] colors = {0xFFFF3B4E, 0xFF35E07A, 0xFFFFD23F, 0xFF4CB4FF, 0xFFFF7AD9};
            int i = 0;
            for (float x = 10 * d; x < w - 4 * d; x += 21 * d, i++) {
                float y = sag(x, span, y0, depth);
                int col = colors[i % colors.length];
                // cada foco titila con su propio ritmo
                float on = 0.35f + 0.65f * Math.max(0, (float) Math.sin(t * 2.4f + i * 1.7f));
                p.setShader(new RadialGradient(x, y + 6 * d, 9 * d, (col & 0x00FFFFFF) | ((int) (120 * on) << 24),
                        col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(x, y + 6 * d, 9 * d, p);
                p.setShader(null);
                p.setColor(0xFF24402A);
                c.drawRect(x - 1.6f * d, y - 0.5f * d, x + 1.6f * d, y + 2.5f * d, p);
                p.setColor(blend(0xFF3A3A3A, col, 0.45f + 0.55f * on));
                rect.set(x - 2.4f * d, y + 2 * d, x + 2.4f * d, y + 9.5f * d);
                c.drawOval(rect, p);
                p.setColor(0x99FFFFFF);
                c.drawCircle(x - 0.8f * d, y + 4.2f * d, 0.8f * d, p);
            }
            // la nieve encima: ondas suaves y goteras redondas
            path.reset();
            path.moveTo(0, 0);
            path.lineTo(0, cap);
            for (float x = 0; x <= w; x += 4 * d) {
                path.lineTo(x, cap + (float) Math.sin(x / (13 * d) + seed % 7) * 1.6f * d);
            }
            path.lineTo(w, 0);
            path.close();
            p.setShader(new LinearGradient(0, 0, 0, cap + 2 * d, 0xFFFFFFFF, 0xFFD9E8FF, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            for (int k = 0; k < drips.length; k += 2) {
                float dx = drips[k], len = drips[k + 1];
                rect.set(dx - 3.2f * d, cap - 3 * d, dx + 3.2f * d, cap + len);
                c.drawRoundRect(rect, 3.2f * d, 3.2f * d, p);
            }
            p.setShader(null);
            // brillitos sobre la nieve
            p.setColor(0xCCFFFFFF);
            for (int k = 0; k < drips.length; k += 2) {
                float a = 0.5f + 0.5f * (float) Math.sin(t * 3 + k);
                p.setAlpha((int) (200 * a));
                c.drawCircle(drips[k] + 6 * d, 2.5f * d, 0.9f * d, p);
            }
            p.setAlpha(255);
        }

        private void drawVelitas(Canvas c, int w, float t) {
            float span = Math.max(60 * d, w / Math.max(1, Math.round(w / (90 * d))));
            float y0 = 2 * d, depth = 6 * d;
            path.reset();
            path.moveTo(0, y0);
            for (float x = 0; x <= w; x += 3 * d) path.lineTo(x, sag(x, span, y0, depth));
            wire.setColor(0xFF8A6A3A);
            c.drawPath(path, wire);
            int[] colors = {0xFFFF4D4D, 0xFFFFB020, 0xFF3DDC84, 0xFFB06BFF, 0xFFFF7AC8, 0xFF3FB8FF};
            int i = 0;
            for (float x = 16 * d; x < w - 8 * d; x += 34 * d, i++) {
                float y = sag(x, span, y0, depth);
                int col = colors[i % colors.length];
                float swing = (float) Math.sin(t * 1.3f + i * 0.9f) * 7f;
                float flick = 0.75f + 0.25f * (float) Math.sin(t * 11 + i * 2.3f) * (float) Math.sin(t * 7.3f + i);
                c.save();
                c.rotate(swing, x, y);
                // resplandor de la vela de adentro
                p.setShader(new RadialGradient(x, y + 9 * d, 14 * d, (0xFFD27A & 0x00FFFFFF) | ((int) (110 * flick) << 24),
                        0x00FFD27A, Shader.TileMode.CLAMP));
                c.drawCircle(x, y + 9 * d, 14 * d, p);
                p.setShader(null);
                p.setColor(0xFF6B4A22);
                c.drawRect(x - 0.5f * d, y, x + 0.5f * d, y + 2 * d, p);
                p.setColor(0xFF3A2614);
                rect.set(x - 3.5f * d, y + 2 * d, x + 3.5f * d, y + 3.6f * d);
                c.drawRoundRect(rect, d, d, p);
                // el farol de papel, iluminado por dentro
                p.setShader(new RadialGradient(x, y + 9 * d, 7 * d, blend(0xFFFFF4C8, col, 0.35f), col, Shader.TileMode.CLAMP));
                p.setAlpha((int) (255 * (0.8f + 0.2f * flick)));
                rect.set(x - 5.5f * d, y + 3.4f * d, x + 5.5f * d, y + 15 * d);
                c.drawRoundRect(rect, 4.5f * d, 4.5f * d, p);
                p.setShader(null);
                p.setAlpha(255);
                p.setColor(0x33000000);
                for (int k = -1; k <= 1; k++) c.drawRect(x + k * 2.6f * d - 0.3f * d, y + 4 * d, x + k * 2.6f * d + 0.3f * d, y + 14.5f * d, p);
                p.setColor(0xFF3A2614);
                rect.set(x - 3 * d, y + 14.6f * d, x + 3 * d, y + 16 * d);
                c.drawRoundRect(rect, d, d, p);
                c.restore();
            }
        }

        private final Path shape = new Path();

        /** San Valentín: una guirnalda de corazones que laten como un corazón de verdad. */
        private void drawHearts(Canvas c, int w, float t) {
            float span = Math.max(60 * d, w / Math.max(1, Math.round(w / (90 * d))));
            float y0 = 2 * d, depth = 6 * d;
            path.reset();
            path.moveTo(0, y0);
            for (float x = 0; x <= w; x += 3 * d) path.lineTo(x, sag(x, span, y0, depth));
            wire.setColor(0xFFB0406A);
            c.drawPath(path, wire);
            int[] colors = {0xFFFF2D55, 0xFFFF7AA8, 0xFFE63946, 0xFFFFB3CC};
            int i = 0;
            for (float x = 14 * d; x < w - 8 * d; x += 30 * d, i++) {
                float y = sag(x, span, y0, depth);
                int col = colors[i % colors.length];
                // latido: dos golpes rápidos y una pausa
                float ph = (t * 1.2f + i * 0.17f) % 1f;
                float beat = ph < 0.12f ? (float) Math.sin(ph / 0.12f * Math.PI) : ph > 0.2f && ph < 0.32f
                        ? 0.7f * (float) Math.sin((ph - 0.2f) / 0.12f * Math.PI) : 0;
                float s = (5.2f + 1.2f * beat) * d;
                p.setColor(0xFF8A3A5A);
                c.drawRect(x - 0.5f * d, y, x + 0.5f * d, y + 4 * d, p);
                p.setShader(new RadialGradient(x, y + 10 * d, 12 * d, (col & 0x00FFFFFF) | ((int) (60 + 70 * beat) << 24),
                        col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(x, y + 10 * d, 12 * d, p);
                p.setShader(null);
                p.setColor(col);
                heart(shape, x, y + 10 * d, s);
                c.drawPath(shape, p);
                p.setColor(0x88FFFFFF);
                c.drawCircle(x - s * 0.55f, y + 10 * d - s * 0.55f, s * 0.22f, p);
            }
        }

        /** Amor y Amistad: banderines de fiesta de colores que ondean con el viento. */
        private void drawBunting(Canvas c, int w, float t) {
            float span = Math.max(60 * d, w / Math.max(1, Math.round(w / (100 * d))));
            float y0 = 2 * d, depth = 7 * d;
            path.reset();
            path.moveTo(0, y0);
            for (float x = 0; x <= w; x += 3 * d) path.lineTo(x, sag(x, span, y0, depth));
            wire.setColor(0xFFE8D7B0);
            c.drawPath(path, wire);
            int[] colors = {0xFFFF4D6D, 0xFFFFC23D, 0xFF3DDC84, 0xFF3FB8FF, 0xFFB06BFF, 0xFFFF7AC8};
            int i = 0;
            float fw = 7 * d;
            for (float x = 9 * d; x < w - fw; x += 19 * d, i++) {
                float ya = sag(x - fw, span, y0, depth), yb = sag(x + fw, span, y0, depth);
                float sway = (float) Math.sin(t * 2.2f + i * 0.8f) * 3 * d;
                float len = (14 + (float) Math.sin(t * 1.7f + i) * 1.2f) * d;
                path.reset();
                path.moveTo(x - fw, ya);
                path.lineTo(x + fw, yb);
                path.lineTo(x + sway, (ya + yb) / 2 + len);
                path.close();
                p.setColor(colors[i % colors.length]);
                c.drawPath(path, p);
                // un puntito o un corazoncito en cada banderín
                p.setColor(0xCCFFFFFF);
                if (i % 3 == 1) {
                    heart(shape, x + sway * 0.3f, (ya + yb) / 2 + len * 0.36f, 2.2f * d);
                    c.drawPath(shape, p);
                } else {
                    c.drawCircle(x + sway * 0.3f, (ya + yb) / 2 + len * 0.36f, 1.4f * d, p);
                }
            }
        }

        private static int blend(int a, int b, float f) {
            f = Math.max(0, Math.min(1, f));
            return Color.argb(255,
                    (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                    (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                    (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
