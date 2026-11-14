package com.forja.orbita;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;

/** La pantalla del espacio: dibuja el sistema, mueve la cámara con los dedos y deja lanzar objetos. */
final class SpaceView extends View implements Choreographer.FrameCallback {
    interface Listener {
        void onInfo(String text);

        void onStatus(String text);

        void onPlaced();

        void onView();
    }

    /** Lo que se está colocando con el menú «Objetos». */
    static final class Kind {
        final String label;
        final int type;          // 0 asteroide, 1 lluvia, 2 cuerpo con masa
        final double mass, radius;
        final int bodyKind, color;

        Kind(String label, int type, double mass, double radius, int bodyKind, int color) {
            this.label = label;
            this.type = type;
            this.mass = mass;
            this.radius = radius;
            this.bodyKind = bodyKind;
            this.color = color;
        }
    }

    Sim sim = Sim.solarSystem();
    Listener listener;
    boolean paused;
    double yearsPerSecond = 10.0 / 365;
    double simDt = 1.0 / 3000;
    Kind placing;
    int follow = -1;
    String followName;

    // texturas de los planetas (se hacen en segundo plano; mientras tanto se ve un círculo de color)
    private final Map<String, Bitmap> sprites = new ConcurrentHashMap<>();
    private final java.util.Set<String> making = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService artPool = Executors.newSingleThreadExecutor();
    private final Paint pSprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG), pRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    // esfera «viva» del cuerpo fijado: se vuelve a pintar según la cámara, la luz y el giro del planeta
    private final Map<String, int[]> maps = new ConcurrentHashMap<>();
    private final java.util.Set<String> mapping = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile Bitmap sphereBmp;
    private volatile String sphereKey = "";
    private volatile boolean sphereBusy;
    private double sYaw = 9e9, sPhi = 9e9, sSun = 9e9, sSpin = 9e9;
    private double spinAcc;
    private static final int MAP_W = 384, MAP_H = 192;


    // cámara
    private double camX, camY, scale = 40;        // UA y píxeles por UA
    private final float density;
    private final Paint pBody = new Paint(Paint.ANTI_ALIAS_FLAG), pLine = new Paint(Paint.ANTI_ALIAS_FLAG), pText = new Paint(Paint.ANTI_ALIAS_FLAG),
            pStar = new Paint(), pPart = new Paint(), pFlash = new Paint(Paint.ANTI_ALIAS_FLAG), pSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaler;
    private float[] stars;
    private float[] pts = new float[0], ptsDebris = new float[0];
    private long lastNs;
    private double simYears;                      // lo que de verdad se avanzó (para mostrar)
    private long lastInfo;
    private boolean slowed;
    private int placedCount;

    // estelas: por cuerpo, un anillo de posiciones relativas al cuerpo que se sigue
    private static final int TRAIL = 220;
    private final List<float[]> trails = new ArrayList<>();   // [x0,y0,x1,y1,...] por cuerpo
    private final List<int[]> trailInfo = new ArrayList<>();  // [cuántos, siguiente]
    private final List<String> trailNames = new ArrayList<>();
    private double sinceTrail;
    private int trailFollow = -2;

    // destellos de choques
    private static final class F {
        final double x, y, size;
        final long start;

        F(double x, double y, double size, long start) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.start = start;
        }
    }

    private final List<F> flashes = new ArrayList<>();

    // lanzamiento
    private boolean aiming;
    private double aimX, aimY, curX, curY;       // mundo
    private float aimPx, aimPy, curPx, curPy;    // pantalla
    private final double[] pred = new double[2 * 1500];
    private int predCount;

    SpaceView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF03050D);
        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeWidth(density * 1.2f);
        pText.setColor(0xCCFFFFFF);
        pText.setTextSize(density * 11);
        pStar.setColor(0xFFFFFFFF);
        pStar.setStrokeWidth(density * 1.3f);
        pPart.setStrokeWidth(Math.max(2f, density * 1.6f));
        pPart.setStrokeCap(Paint.Cap.ROUND);
        pFlash.setStyle(Paint.Style.STROKE);
        pSel.setStyle(Paint.Style.STROKE);
        pSel.setColor(0xFF7AA7FF);
        pSel.setStrokeWidth(density * 1.5f);
        Random r = new Random(5);
        stars = new float[2 * 260];
        for (int i = 0; i < stars.length; i++) stars[i] = r.nextFloat();
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomAt(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        resetView();
    }

    // ------------------------------------------------------------------ cámara

    private void zoomAt(double f, float fx, float fy) {
        double ns = Math.max(1.0, Math.min(2.0e6, scale * f));
        double[] before = unproject(fx, fy);
        scale = ns;
        if (follow < 0) {
            // el punto bajo los dedos se queda donde está
            double[] after = unproject(fx, fy);
            camX += before[0] - after[0];
            camY += before[1] - after[1];
        }
        invalidate();
    }

    void resetView() {
        camX = 0;
        camY = 0;
        yaw = 0;
        phi = 0;
        scale = 38;   // se ve hasta más allá de Marte
        follow = -1;
        followName = null;
    }

    void reset() {
        sim = Sim.solarSystem();
        trails.clear();
        trailInfo.clear();
        trailNames.clear();
        flashes.clear();
        resetView();
        simYears = 0;
        placedCount = 0;
    }

    void follow(String name) {
        followName = name;
        follow = name == null ? -1 : sim.find(name);
        if (follow < 0) followName = null;
        clearTrails();
    }

    private void clearTrails() {
        trails.clear();
        trailInfo.clear();
        trailNames.clear();
    }

    // cámara en 3D: yaw gira alrededor del eje vertical; phi es el ángulo desde arriba (0 = vista de arriba, 90° = de canto, 180° = desde abajo)
    double yaw, phi;
    private double cosY = 1, sinY = 0, cph = 1;

    private void updateProj() {
        cosY = Math.cos(yaw);
        sinY = Math.sin(yaw);
        double c = Math.cos(phi);
        cph = Math.abs(c) < 0.07 ? (c < 0 ? -0.07 : 0.07) : c;
    }

    private float sxp(double x, double y) {
        double dx = x - camX, dy = y - camY;
        return (float) ((dx * cosY - dy * sinY) * scale + getWidth() / 2.0);
    }

    private float syp(double x, double y) {
        double dx = x - camX, dy = y - camY;
        return (float) ((dx * sinY + dy * cosY) * cph * scale + getHeight() / 2.0);
    }

    /** De un punto de la pantalla al punto del plano del sistema que está debajo. */
    private double[] unproject(float sx, float sy) {
        double rx = (sx - getWidth() / 2.0) / scale, ry = (sy - getHeight() / 2.0) / (scale * cph);
        return new double[]{camX + rx * cosY + ry * sinY, camY - rx * sinY + ry * cosY};
    }

    private double wx(float sx, float sy) {
        return unproject(sx, sy)[0];
    }

    private double wy(float sx, float sy) {
        return unproject(sx, sy)[1];
    }

    // ------------------------------------------------------------------ bucle

    void start() {
        lastNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        Choreographer.getInstance().removeFrameCallback(this);
    }

    void destroy() {
        artPool.shutdownNow();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        double realDt = lastNs == 0 ? 1.0 / 60 : Math.min(0.1, (frameTimeNanos - lastNs) / 1e9);
        lastNs = frameTimeNanos;
        if (!paused) step(realDt);
        updateCamera();
        updateProj();
        if (aiming) predictPath();
        invalidate();
        long now = SystemClock.uptimeMillis();
        if (now - lastInfo > 250 && listener != null) {
            lastInfo = now;
            listener.onStatus(statusText());
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void step(double realDt) {
        double target = realDt * yearsPerSecond;
        long budget = 11_000_000L;              // máximo ~11 ms de cálculo por cuadro
        long t0 = System.nanoTime();
        double done = 0;
        int count = 0;
        int sub = Math.max(1, (int) Math.ceil(target / simDt));
        double dt = target / sub;
        for (int i = 0; i < sub; i++) {
            sim.step(dt);
            done += dt;
            count++;
            if ((count & 3) == 0 && System.nanoTime() - t0 > budget) break;
        }
        slowed = done < target * 0.98;
        simYears += done;
        if (follow >= 0 && follow < sim.n) {
            // el planeta gira sobre sí mismo, pero no tan rápido que se vea como un borrón
            double days = done * 365.25, perDay = 1.0 / spinDays(sim.name[follow]);
            double daysPerSec = yearsPerSecond * 365.25;
            double k = Math.min(1.0, 0.35 / Math.max(1e-9, daysPerSec * Math.abs(perDay)));
            spinAcc += days * perDay * k * 2 * Math.PI;
        }
        sinceTrail += done;
        // un punto de estela cada cierto tiempo simulado (más espaciado cuanto más rápido)
        double every = Math.max(yearsPerSecond / 60.0, 1e-5) * 2;
        if (sinceTrail >= every) {
            sinceTrail = 0;
            addTrailPoints();
        }
        for (Sim.Flash f : sim.flashes) flashes.add(new F(f.x, f.y, f.size, SystemClock.uptimeMillis()));
        sim.flashes.clear();
        if (follow >= 0 && (follow >= sim.n || !followName.equals(sim.name[follow]))) {
            // el cuerpo seguido se fundió o cambió de lugar en la lista
            follow = followName == null ? -1 : sim.find(followName);
            if (follow < 0) {
                followName = null;
                clearTrails();
            }
        }
    }

    private void updateCamera() {
        if (follow >= 0 && follow < sim.n) {
            camX = sim.x[follow];
            camY = sim.y[follow];
        }
    }

    private void addTrailPoints() {
        double ox = follow >= 0 && follow < sim.n ? sim.x[follow] : 0, oy = follow >= 0 && follow < sim.n ? sim.y[follow] : 0;
        if (trailFollow != follow) {
            trailFollow = follow;
            clearTrails();
        }
        for (int i = 0; i < sim.n; i++) {
            int k = trailNames.indexOf(sim.name[i]);
            if (k < 0) {
                trailNames.add(sim.name[i]);
                trails.add(new float[TRAIL * 2]);
                trailInfo.add(new int[]{0, 0});
                k = trailNames.size() - 1;
            }
            float[] t = trails.get(k);
            int[] inf = trailInfo.get(k);
            t[inf[1] * 2] = (float) (sim.x[i] - ox);
            t[inf[1] * 2 + 1] = (float) (sim.y[i] - oy);
            inf[1] = (inf[1] + 1) % TRAIL;
            inf[0] = Math.min(TRAIL, inf[0] + 1);
        }
    }

    private String statusText() {
        int years = (int) simYears;
        int days = (int) ((simYears - years) * 365.25);
        String speed;
        double dps = yearsPerSecond * 365.25;
        if (dps < 1) speed = String.format("%.1f h/s", dps * 24);
        else if (dps < 365) speed = String.format("%d d/s", Math.round(dps));
        else speed = String.format("%.0f años/s", dps / 365.25);
        return (paused ? "⏸ " : "") + "Año " + years + ", día " + days + "  ·  " + speed + (slowed ? "  ⚠ ralentizado" : "")
                + "\n" + sim.np + " partículas  ·  " + sim.impacts + " impactos  ·  " + sim.merges + " fusiones  ·  " + sim.disruptions + " desgarros";
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        updateProj();
        c.drawColor(0xFF03050D);
        // estrellas de fondo (fijas en la pantalla, con un movimiento muy leve)
        float px = (float) (camX * 0.5), py = (float) (camY * 0.5);
        float[] sp = new float[stars.length];
        for (int i = 0; i < stars.length; i += 2) {
            sp[i] = ((stars[i] * w - px * 0.02f * (float) Math.min(scale, 200)) % w + w) % w;
            sp[i + 1] = ((stars[i + 1] * h - py * 0.02f * (float) Math.min(scale, 200)) % h + h) % h;
        }
        pStar.setAlpha(150);
        c.drawPoints(sp, pStar);

        // estelas
        double ox = follow >= 0 && follow < sim.n ? sim.x[follow] : 0, oy = follow >= 0 && follow < sim.n ? sim.y[follow] : 0;
        for (int k = 0; k < trails.size(); k++) {
            int idx = sim.find(trailNames.get(k));
            if (idx < 0) continue;
            float[] t = trails.get(k);
            int[] inf = trailInfo.get(k);
            int cnt = inf[0];
            if (cnt < 2) continue;
            int col = sim.color[idx];
            float lx = 0, ly = 0;
            for (int j = 0; j < cnt; j++) {
                int at = ((inf[1] - cnt + j) % TRAIL + TRAIL) % TRAIL;
                float X = sxp(t[at * 2] + ox, t[at * 2 + 1] + oy), Y = syp(t[at * 2] + ox, t[at * 2 + 1] + oy);
                if (j > 0) {
                    pLine.setColor(col);
                    pLine.setAlpha((int) (25 + 150.0 * j / cnt));
                    c.drawLine(lx, ly, X, Y, pLine);
                }
                lx = X;
                ly = Y;
            }
            // cerrar hasta el cuerpo
            pLine.setAlpha(170);
            c.drawLine(lx, ly, sxp(sim.x[idx], sim.y[idx]), syp(sim.x[idx], sim.y[idx]), pLine);
        }

        // partículas (asteroides y escombros)
        int na = 0, nd = 0;
        if (pts.length < sim.np * 2) {
            pts = new float[sim.np * 2 + 512];
            ptsDebris = new float[sim.np * 2 + 512];
        }
        for (int i = 0; i < sim.np; i++) {
            float X = sxp(sim.px[i], sim.py[i]), Y = syp(sim.px[i], sim.py[i]);
            if (X < -4 || Y < -4 || X > w + 4 || Y > h + 4) continue;
            if (sim.pkind[i] == 0) {
                pts[na++] = X;
                pts[na++] = Y;
            } else {
                ptsDebris[nd++] = X;
                ptsDebris[nd++] = Y;
            }
        }
        pPart.setColor(0xFFB9C7E8);
        c.drawPoints(pts, 0, na, pPart);
        pPart.setColor(0xFFFFA45C);
        c.drawPoints(ptsDebris, 0, nd, pPart);

        // cuerpos
        for (int i = 0; i < sim.n; i++) {
            float X = sxp(sim.x[i], sim.y[i]), Y = syp(sim.x[i], sim.y[i]);
            float rpx = (float) (sim.collisionRadius(i) * scale);
            float minPx = density * (float) Math.max(2.0, Math.min(18.0, 4.0 + 4.5 * Math.log10(Math.max(sim.r[i], 1e-7) / 1e-5)));   // más grande el cuerpo, más grande el punto (para verse sin acercar)
            float draw = Math.max(rpx, minPx);
            if (X < -draw - 40 || Y < -draw - 40 || X > w + draw + 40 || Y > h + draw + 40) continue;
            if (sim.kind[i] == Sim.STAR) {
                float g = draw * 3.2f;
                pBody.setShader(new RadialGradient(X, Y, g, new int[]{sim.color[i], (sim.color[i] & 0x00FFFFFF) | 0x66000000, 0x00000000}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(X, Y, g, pBody);
                pBody.setShader(null);
            }
            Bitmap spr = draw >= density * 3.5f ? sprite(i) : null;
            boolean saturn = "Saturno".equals(sim.name[i]);
            boolean live = false;
            if (spr != null && i == follow && draw >= density * 34) {
                Bitmap sb = liveSphere(i, draw);
                if (sb != null) {
                    spr = sb;
                    live = true;
                }
            }
            if (spr == null) {
                pBody.setColor(sim.color[i]);
                c.drawCircle(X, Y, draw, pBody);
            } else {
                if (saturn) drawRings(c, X, Y, draw, false);
                c.save();
                c.translate(X, Y);
                if (sim.kind[i] != Sim.STAR && !live) c.rotate((float) Math.toDegrees(lightAngle(i)));
                rect.set(-draw, -draw, draw, draw);
                c.drawBitmap(spr, null, rect, pSprite);
                c.restore();
                if (saturn) drawRings(c, X, Y, draw, true);
            }
            if (i == follow) {
                c.drawCircle(X, Y, draw + density * 5, pSel);
            }
            // nombre: solo si no estorba (lejos del cuerpo grande más cercano)
            if (sim.kind[i] != Sim.ROCK && (i == follow || labelFits(i))) c.drawText(sim.name[i], X + draw + density * 4, Y - density * 3, pText);
        }

        // destellos
        long now = SystemClock.uptimeMillis();
        for (int i = flashes.size() - 1; i >= 0; i--) {
            F f = flashes.get(i);
            float a = (now - f.start) / 700f;
            if (a >= 1) {
                flashes.remove(i);
                continue;
            }
            float rr = (float) (density * (6 + 38 * f.size) * a);
            pFlash.setColor(0xFFFFD27A);
            pFlash.setAlpha((int) (255 * (1 - a)));
            pFlash.setStrokeWidth(density * (3 - 2 * a));
            c.drawCircle(sxp(f.x, f.y), syp(f.x, f.y), rr, pFlash);
            pFlash.setAlpha((int) (140 * (1 - a)));
            pFlash.setColor(0xFFFFFFFF);
            c.drawCircle(sxp(f.x, f.y), syp(f.x, f.y), rr * 0.55f, pFlash);
        }

        // lanzamiento: flecha y camino previsto
        if (aiming) {
            pLine.setColor(0xFF7AA7FF);
            pLine.setAlpha(230);
            c.drawLine(aimPx, aimPy, curPx, curPy, pLine);
            c.drawCircle(aimPx, aimPy, density * 4, pSel);
            if (predCount > 1) {
                pLine.setAlpha(150);
                float lx = sxp(pred[0], pred[1]), ly = syp(pred[0], pred[1]);
                for (int i = 1; i < predCount; i++) {
                    float X = sxp(pred[i * 2], pred[i * 2 + 1]), Y = syp(pred[i * 2], pred[i * 2 + 1]);
                    if ((i & 1) == 0) c.drawLine(lx, ly, X, Y, pLine);
                    lx = X;
                    ly = Y;
                }
            }
        }
    }

    /** Ángulo (giro del dibujo) para que la luz de la esfera mire hacia la estrella más cercana y grande. */
    private double lightAngle(int i) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int j = 0; j < sim.n; j++) {
            if (j == i || sim.kind[j] != Sim.STAR) continue;
            double d = Math.hypot(sim.x[j] - sim.x[i], sim.y[j] - sim.y[i]) / Math.sqrt(sim.m[j]);
            if (d < bd) {
                bd = d;
                best = j;
            }
        }
        if (best < 0) return 0;
        // la luz está dibujada viniendo desde la izquierda (ángulo π)
        return Math.atan2(syp(sim.x[best], sim.y[best]) - syp(sim.x[i], sim.y[i]), sxp(sim.x[best], sim.y[best]) - sxp(sim.x[i], sim.y[i])) - Math.PI;
    }

    private String artType(int i) {
        String t = PlanetArt.typeFor(sim.name[i]);
        if (t != null) return t;
        return switch (sim.kind[i]) {
            case Sim.STAR -> "estrella";
            case Sim.ROCK -> "roca";
            default -> sim.name[i].startsWith("Gigante") ? "gaseoso" : "rocoso";
        };
    }

    private Bitmap sprite(int i) {
        String type = artType(i);
        int seed = Math.abs(sim.name[i].hashCode() % 1000);
        int col = sim.kind[i] == Sim.STAR ? sim.color[i] : 0;
        String key = type + "#" + seed + "#" + (col & 0xFFFFFF);
        Bitmap b = sprites.get(key);
        if (b != null) return b;
        if (making.add(key)) {
            final String ft = type;
            artPool.execute(() -> {
                int[] px = PlanetArt.make(ft, seed, col);
                sprites.put(key, Bitmap.createBitmap(px, PlanetArt.SIZE, PlanetArt.SIZE, Bitmap.Config.ARGB_8888));
                making.remove(key);
            });
        }
        return null;
    }

    /** Los anillos de Saturno, en el plano del sistema: se ven como círculos desde arriba y como una línea de canto. «front» dibuja la mitad que pasa por delante del planeta. */
    private void drawRings(Canvas c, float X, float Y, float R, boolean front) {
        c.save();
        c.translate(X, Y);
        float ratio = (float) Math.abs(cph);
        float[] radii = {1.35f, 1.6f, 1.85f, 2.1f, 2.3f};
        int[] cols = {0x66B8A27A, 0x99D8C294, 0xCCE8D8B0, 0x88C9B58A, 0x55A99870};
        float w = R * 0.26f;
        boolean fromAbove = Math.cos(phi) >= 0;
        for (int k = 0; k < radii.length; k++) {
            float rr = R * radii[k];
            rect.set(-rr, -rr * ratio, rr, rr * ratio);
            pRing.setStyle(Paint.Style.STROKE);
            pRing.setStrokeWidth(k == 1 ? w * 0.5f : w);
            pRing.setColor(cols[k]);
            // desde arriba, el lado de abajo de la pantalla es el más cercano; desde abajo, el de arriba
            boolean lower = front == fromAbove;
            if (ratio > 0.95f) {
                if (!front) c.drawOval(rect, pRing);      // desde arriba son círculos completos y no pasan por delante del planeta
            } else c.drawArc(rect, lower ? 0 : 180, 180, false, pRing);
        }
        c.restore();
    }

    private static double spinDays(String name) {
        return switch (name) {
            case "Mercurio" -> 58.6;
            case "Venus" -> -243;
            case "Tierra" -> 1.0;
            case "Luna" -> 27.3;
            case "Marte" -> 1.03;
            case "Júpiter" -> 0.41;
            case "Saturno" -> 0.44;
            case "Urano" -> -0.72;
            case "Neptuno" -> 0.67;
            case "Sol" -> 25.4;
            default -> 1.0;
        };
    }

    /** La esfera del cuerpo fijado, vista desde donde está la cámara (con su luz). Devuelve null mientras se prepara. */
    private Bitmap liveSphere(int i, float drawPx) {
        String type = artType(i);
        int seed = Math.abs(sim.name[i].hashCode() % 1000);
        int col = sim.kind[i] == Sim.STAR ? sim.color[i] : 0;
        String mapKey = type + "#" + seed + "#" + (col & 0xFFFFFF);
        int[] map = maps.get(mapKey);
        if (map == null) {
            if (mapping.add(mapKey)) artPool.execute(() -> {
                maps.put(mapKey, PlanetArt.map(type, seed, col, MAP_W, MAP_H));
                mapping.remove(mapKey);
            });
            return null;
        }
        // dirección del Sol (la estrella más cercana y grande), en el plano del sistema
        double sdx = 1, sdy = 0;
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int j = 0; j < sim.n; j++) {
            if (j == i || sim.kind[j] != Sim.STAR) continue;
            double d = Math.hypot(sim.x[j] - sim.x[i], sim.y[j] - sim.y[i]) / Math.sqrt(sim.m[j]);
            if (d < bd) {
                bd = d;
                best = j;
            }
        }
        if (best >= 0) {
            double l = Math.hypot(sim.x[best] - sim.x[i], sim.y[best] - sim.y[i]);
            if (l > 0) {
                sdx = (sim.x[best] - sim.x[i]) / l;
                sdy = (sim.y[best] - sim.y[i]) / l;
            }
        }
        double sunAng = Math.atan2(sdy, sdx);
        boolean star = sim.kind[i] == Sim.STAR;
        boolean changed = !mapKey.equals(sphereKey) || Math.abs(yaw - sYaw) > 0.004 || Math.abs(phi - sPhi) > 0.004
                || Math.abs(sunAng - sSun) > 0.004 || Math.abs(spinAcc - sSpin) > 0.01;
        if (changed && !sphereBusy) {
            sphereBusy = true;
            final double fyaw = yaw, fphi = phi, fspin = spinAcc;
            final double fsx = sdx, fsy = sdy;
            final int n = Math.max(96, Math.min(192, ((int) (drawPx * 2) / 16) * 16));
            artPool.execute(() -> {
                try {
                    double[] R = {Math.cos(fyaw), -Math.sin(fyaw), 0};
                    double[] D = {Math.sin(fyaw) * Math.cos(fphi), Math.cos(fyaw) * Math.cos(fphi), Math.sin(fphi)};
                    double[] U = {-D[0], -D[1], -D[2]};
                    double[] V = {R[1] * U[2] - R[2] * U[1], R[2] * U[0] - R[0] * U[2], R[0] * U[1] - R[1] * U[0]};
                    int[] px = new int[n * n];
                    PlanetArt.render(px, n, map, MAP_W, MAP_H, type, star, R, U, V, new double[]{fsx, fsy, 0}, fspin);
                    sphereBmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
                    sphereKey = mapKey;
                    sYaw = fyaw;
                    sPhi = fphi;
                    sSun = Math.atan2(fsy, fsx);
                    sSpin = fspin;
                } finally {
                    sphereBusy = false;
                    postInvalidate();
                }
            });
        }
        return mapKey.equals(sphereKey) ? sphereBmp : null;
    }

    private boolean labelFits(int i) {
        float X = sxp(sim.x[i], sim.y[i]), Y = syp(sim.x[i], sim.y[i]);
        for (int j = 0; j < sim.n; j++) {
            if (j == i || sim.m[j] <= sim.m[i]) continue;
            float d = (float) Math.hypot(X - sxp(sim.x[j], sim.y[j]), Y - syp(sim.x[j], sim.y[j]));
            if (d < density * 34) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ dedos

    private float downX, downY;
    private boolean moved;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        int act = e.getActionMasked();
        if (e.getPointerCount() > 1) {
            aiming = false;
            moved = true;
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (act) {
            case MotionEvent.ACTION_DOWN -> {
                downX = x;
                downY = y;
                moved = false;
                lastTx = x;
                lastTy = y;
                if (placing != null) {
                    aiming = true;
                    aimPx = curPx = x;
                    aimPy = curPy = y;
                    aimX = curX = wx(x, y);
                    aimY = curY = wy(x, y);
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (Math.hypot(x - downX, y - downY) > density * 8) moved = true;
                if (aiming) {
                    curPx = x;
                    curPy = y;
                    curX = wx(x, y);
                    curY = wy(x, y);
                } else if (!scaler.isInProgress()) {
                    if (follow < 0) {
                        // mover la cámara: el desplazamiento de la pantalla se pasa al plano del sistema
                        double[] a0 = unproject(lastTx, lastTy), a1 = unproject(x, y);
                        camX -= a1[0] - a0[0];
                        camY -= a1[1] - a0[1];
                    } else {
                        // con un cuerpo fijado, un dedo gira la cámara alrededor de él
                        yaw += (x - lastTx) * 0.008;
                        phi = Math.max(0, Math.min(Math.PI, phi - (y - lastTy) * 0.006));
                        if (listener != null) listener.onView();
                    }
                    lastTx = x;
                    lastTy = y;
                }
            }
            case MotionEvent.ACTION_UP -> {
                if (aiming) {
                    launch();
                    aiming = false;
                } else if (!moved) tap(x, y);
            }
            case MotionEvent.ACTION_CANCEL -> aiming = false;
            default -> {
            }
        }
        if (act == MotionEvent.ACTION_MOVE) {
            lastTx = x;
            lastTy = y;
        }
        return true;
    }

    private float lastTx, lastTy;

    private long lastTap;

    private void tap(float x, float y) {
        long nowTap = SystemClock.uptimeMillis();
        boolean dbl = nowTap - lastTap < 350;
        lastTap = nowTap;
        if (dbl) {
            yaw = 0;
            phi = 0;
            return;
        }
        int best = -1;
        float bd = density * 28;
        for (int i = 0; i < sim.n; i++) {
            float d = (float) Math.hypot(x - sxp(sim.x[i], sim.y[i]), y - syp(sim.x[i], sim.y[i]));
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best >= 0) {
            follow(sim.name[best]);
            if (listener != null) listener.onInfo(info(best));
        } else if (follow >= 0) {
            // soltar la cámara: queda donde estaba
            follow(null);
            if (listener != null) listener.onInfo("");
        }
    }

    String info(int i) {
        double d = Math.hypot(sim.x[i], sim.y[i]);
        double v = Math.hypot(sim.vx[i], sim.vy[i]) * 4.74047;    // UA/año → km/s
        double mEarth = sim.m[i] / 3.003e-6;
        String mass = mEarth > 1000 ? String.format("%.3f masas solares", sim.m[i]) : mEarth >= 0.01 ? String.format("%.2f masas terrestres", mEarth) : String.format("%.4f masas terrestres", mEarth);
        return sim.name[i] + "\n" + mass + "\n" + String.format("%.1f km/s  ·  %.3f UA del centro", v, d) + "\n↻ Arrastra para girar la cámara\n2 toques: vista de arriba";
    }

    // ------------------------------------------------------------------ lanzar objetos

    /** La velocidad de referencia: cuánto se necesita ahí para dar una órbita circular alrededor del cuerpo seguido o del Sol. */
    private double refSpeed(double X, double Y) {
        if (follow >= 0 && follow < sim.n) {
            double d = Math.max(Math.hypot(X - sim.x[follow], Y - sim.y[follow]), sim.collisionRadius(follow) * 1.5);
            return Sim.circularSpeed(sim.m[follow], d);
        }
        double d = Math.max(0.3, Math.hypot(X, Y));
        return Sim.circularSpeed(sim.totalMass(), d);
    }

    private double[] launchVelocity() {
        double dx = curX - aimX, dy = curY - aimY;
        double px = Math.hypot(curPx - aimPx, curPy - aimPy);
        double len = Math.hypot(dx, dy);
        double vref = refSpeed(aimX, aimY);
        double speed = vref * (px / (density * 80));
        double vx = len > 0 ? dx / len * speed : 0, vy = len > 0 ? dy / len * speed : 0;
        if (follow >= 0 && follow < sim.n) {
            vx += sim.vx[follow];
            vy += sim.vy[follow];
        }
        return new double[]{vx, vy};
    }

    private void predictPath() {
        double[] v = launchVelocity();
        // el horizonte depende de lo cerca que está de algo masivo: cerca de la Tierra, mucho menos tiempo
        int steps = 3000;
        predCount = sim.predict(aimX, aimY, v[0], v[1], steps, 1.0 / 3000 * 2, 4, pred);
    }

    private void launch() {
        double[] v = launchVelocity();
        Kind k = placing;
        if (k == null) return;
        switch (k.type) {
            case 0 -> sim.addParticle(aimX, aimY, v[0], v[1], 0);
            case 1 -> {
                double spread = 12 * density / scale;
                for (int i = 0; i < 200; i++) {
                    double a = sim.rnd.nextDouble() * 2 * Math.PI, rr = spread * Math.sqrt(sim.rnd.nextDouble());
                    double jit = 0.04;
                    sim.addParticle(aimX + Math.cos(a) * rr, aimY + Math.sin(a) * rr,
                            v[0] * (1 + (sim.rnd.nextDouble() - 0.5) * jit), v[1] * (1 + (sim.rnd.nextDouble() - 0.5) * jit), 0);
                }
            }
            default -> {
                placedCount++;
                String nm = k.label + " " + placedCount;
                sim.addBody(nm, k.bodyKind, k.mass, k.radius, aimX, aimY, v[0], v[1], k.color);
            }
        }
        predCount = 0;
        if (listener != null) listener.onPlaced();
    }

    void addBelt() {
        sim.addBelt(2000, 2.2, 3.3, 0.06);
    }

    void setSpeedIndex(int i) {
        double[] days = {1, 3, 10, 30, 100, 365, 1095};
        yearsPerSecond = days[Math.max(0, Math.min(days.length - 1, i))] / 365.25;
    }

    static final double[] SPEED_DAYS = {1, 3, 10, 30, 100, 365, 1095};
}
