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
        f = ns / scale;
        // el punto bajo los dedos se queda donde está
        double wx = camX + (fx - getWidth() / 2.0) / scale, wy = camY + (fy - getHeight() / 2.0) / scale;
        scale = ns;
        camX = wx - (fx - getWidth() / 2.0) / scale;
        camY = wy - (fy - getHeight() / 2.0) / scale;
        invalidate();
    }

    void resetView() {
        camX = 0;
        camY = 0;
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

    private double wx(float sx) {
        return camX + (sx - getWidth() / 2.0) / scale;
    }

    private double wy(float sy) {
        return camY + (sy - getHeight() / 2.0) / scale;
    }

    private float sx(double x) {
        return (float) ((x - camX) * scale + getWidth() / 2.0);
    }

    private float sy(double y) {
        return (float) ((y - camY) * scale + getHeight() / 2.0);
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
                + "\n" + sim.np + " asteroides  ·  " + sim.impacts + " impactos  ·  " + sim.merges + " fusiones";
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
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
                float X = sx(t[at * 2] + ox), Y = sy(t[at * 2 + 1] + oy);
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
            c.drawLine(lx, ly, sx(sim.x[idx]), sy(sim.y[idx]), pLine);
        }

        // partículas (asteroides y escombros)
        int na = 0, nd = 0;
        if (pts.length < sim.np * 2) {
            pts = new float[sim.np * 2 + 512];
            ptsDebris = new float[sim.np * 2 + 512];
        }
        for (int i = 0; i < sim.np; i++) {
            float X = sx(sim.px[i]), Y = sy(sim.py[i]);
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
            float X = sx(sim.x[i]), Y = sy(sim.y[i]);
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
            if (spr == null) {
                pBody.setColor(sim.color[i]);
                c.drawCircle(X, Y, draw, pBody);
            } else {
                if (saturn) drawRings(c, X, Y, draw, false);
                c.save();
                c.translate(X, Y);
                if (sim.kind[i] != Sim.STAR) c.rotate((float) Math.toDegrees(lightAngle(i)));
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
            c.drawCircle(sx(f.x), sy(f.y), rr, pFlash);
            pFlash.setAlpha((int) (140 * (1 - a)));
            pFlash.setColor(0xFFFFFFFF);
            c.drawCircle(sx(f.x), sy(f.y), rr * 0.55f, pFlash);
        }

        // lanzamiento: flecha y camino previsto
        if (aiming) {
            pLine.setColor(0xFF7AA7FF);
            pLine.setAlpha(230);
            c.drawLine(aimPx, aimPy, curPx, curPy, pLine);
            c.drawCircle(aimPx, aimPy, density * 4, pSel);
            if (predCount > 1) {
                pLine.setAlpha(150);
                float lx = sx(pred[0]), ly = sy(pred[1]);
                for (int i = 1; i < predCount; i++) {
                    float X = sx(pred[i * 2]), Y = sy(pred[i * 2 + 1]);
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
        return Math.atan2(sim.y[best] - sim.y[i], sim.x[best] - sim.x[i]) - Math.PI;
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

    /** Los anillos de Saturno, inclinados. «front» dibuja solo la mitad de abajo (la que pasa por delante del planeta). */
    private void drawRings(Canvas c, float X, float Y, float R, boolean front) {
        c.save();
        c.translate(X, Y);
        c.rotate(-18f);
        float[] radii = {1.35f, 1.6f, 1.85f, 2.1f, 2.3f};
        int[] cols = {0x66B8A27A, 0x99D8C294, 0xCCE8D8B0, 0x88C9B58A, 0x55A99870};
        float w = R * 0.26f;
        for (int k = 0; k < radii.length; k++) {
            float rr = R * radii[k];
            rect.set(-rr, -rr * 0.34f, rr, rr * 0.34f);
            pRing.setStyle(Paint.Style.STROKE);
            pRing.setStrokeWidth(k == 1 ? w * 0.5f : w);
            pRing.setColor(cols[k]);
            if (front) c.drawArc(rect, 0, 180, false, pRing);
            else c.drawArc(rect, 180, 180, false, pRing);
        }
        c.restore();
    }

    private boolean labelFits(int i) {
        float X = sx(sim.x[i]), Y = sy(sim.y[i]);
        for (int j = 0; j < sim.n; j++) {
            if (j == i || sim.m[j] <= sim.m[i]) continue;
            float d = (float) Math.hypot(X - sx(sim.x[j]), Y - sy(sim.y[j]));
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
                    aimX = curX = wx(x);
                    aimY = curY = wy(y);
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (Math.hypot(x - downX, y - downY) > density * 8) moved = true;
                if (aiming) {
                    curPx = x;
                    curPy = y;
                    curX = wx(x);
                    curY = wy(y);
                } else if (!scaler.isInProgress()) {
                    if (follow < 0) {
                        camX -= (x - lastTx) / scale;
                        camY -= (y - lastTy) / scale;
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

    private void tap(float x, float y) {
        int best = -1;
        float bd = density * 28;
        for (int i = 0; i < sim.n; i++) {
            float d = (float) Math.hypot(x - sx(sim.x[i]), y - sy(sim.y[i]));
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
        return sim.name[i] + "\n" + mass + "\n" + String.format("%.1f km/s  ·  %.3f UA del centro", v, d);
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
