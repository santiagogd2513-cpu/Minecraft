package com.forja.orbita;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * El motor de física de Órbita (sin nada de Android, para probarlo en el computador).
 * Unidades: distancia en UA, masa en masas solares, tiempo en años. Así G = 4π².
 * Dos clases de cosas: «cuerpos» con masa (estrellas, planetas, lunas) que se atraen entre sí, y «partículas» sin masa
 * (asteroides, escombros) que sienten la gravedad de los cuerpos pero no la devuelven. Con eso caben miles.
 */
final class Sim {
    static final double G = 4 * Math.PI * Math.PI;
    static final double SOFT2 = 1e-12;          // suavizado para no dividir entre cero
    static final double ESCAPE = 400;           // lo que se va más allá de esta distancia (UA) se borra
    static final int MAX_PARTICLES = 20000;
    /** Para los choques, los radios reales (diminutos) se agrandan: si no, nada chocaría nunca. */
    static final double COLLISION_SCALE_PLANET = 10, COLLISION_SCALE_STAR = 4;

    static final int STAR = 0, PLANET = 1, MOON = 2, ROCK = 3;

    // ---- cuerpos con masa
    int n;
    double[] x = new double[64], y = new double[64], vx = new double[64], vy = new double[64], m = new double[64], r = new double[64];
    double[] ax = new double[64], ay = new double[64];
    int[] kind = new int[64], color = new int[64];
    String[] name = new String[64];

    // ---- partículas sin masa
    int np;
    double[] px = new double[1024], py = new double[1024], pvx = new double[1024], pvy = new double[1024];
    double[] pax = new double[1024], pay = new double[1024];
    byte[] pkind = new byte[1024];   // 0 asteroide, 1 escombro

    double time;                      // años desde el inicio
    boolean accValid;
    final Random rnd = new Random(42);
    int impacts, merges, shatters;    // cuentas para mostrar

    /** Un destello en pantalla (choque). */
    static final class Flash {
        final double x, y, size;
        final long id;

        Flash(double x, double y, double size, long id) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.id = id;
        }
    }

    final List<Flash> flashes = new ArrayList<>();
    private long flashId;

    // ================================================================== cuerpos y partículas

    int addBody(String nm, int k, double mass, double radius, double X, double Y, double VX, double VY, int col) {
        if (n == x.length) growBodies();
        x[n] = X; y[n] = Y; vx[n] = VX; vy[n] = VY; m[n] = mass; r[n] = radius; kind[n] = k; name[n] = nm; color[n] = col;
        accValid = false;
        return n++;
    }

    private void growBodies() {
        int c = x.length * 2;
        x = java.util.Arrays.copyOf(x, c); y = java.util.Arrays.copyOf(y, c); vx = java.util.Arrays.copyOf(vx, c); vy = java.util.Arrays.copyOf(vy, c);
        m = java.util.Arrays.copyOf(m, c); r = java.util.Arrays.copyOf(r, c); ax = java.util.Arrays.copyOf(ax, c); ay = java.util.Arrays.copyOf(ay, c);
        kind = java.util.Arrays.copyOf(kind, c); color = java.util.Arrays.copyOf(color, c); name = java.util.Arrays.copyOf(name, c);
    }

    void addParticle(double X, double Y, double VX, double VY, int k) {
        if (np == MAX_PARTICLES) removeParticle(0);   // el más viejo sale
        if (np == px.length) {
            int c = Math.min(MAX_PARTICLES, px.length * 2);
            px = java.util.Arrays.copyOf(px, c); py = java.util.Arrays.copyOf(py, c); pvx = java.util.Arrays.copyOf(pvx, c); pvy = java.util.Arrays.copyOf(pvy, c);
            pax = java.util.Arrays.copyOf(pax, c); pay = java.util.Arrays.copyOf(pay, c); pkind = java.util.Arrays.copyOf(pkind, c);
        }
        px[np] = X; py[np] = Y; pvx[np] = VX; pvy[np] = VY; pkind[np] = (byte) k;
        np++;
        accValid = false;
    }

    private void removeParticle(int i) {
        int last = np - 1;
        if (i != last) {
            px[i] = px[last]; py[i] = py[last]; pvx[i] = pvx[last]; pvy[i] = pvy[last]; pax[i] = pax[last]; pay[i] = pay[last]; pkind[i] = pkind[last];
        }
        np--;
    }

    private void removeBody(int i) {
        int last = n - 1;
        if (i != last) {
            x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]; m[i] = m[last]; r[i] = r[last];
            ax[i] = ax[last]; ay[i] = ay[last]; kind[i] = kind[last]; color[i] = color[last]; name[i] = name[last];
        }
        n--;
        accValid = false;
    }

    double collisionRadius(int i) {
        return r[i] * (kind[i] == STAR ? COLLISION_SCALE_STAR : COLLISION_SCALE_PLANET);
    }

    // ================================================================== gravedad

    private void computeAcc() {
        for (int i = 0; i < n; i++) {
            double sx = 0, sy = 0;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                double dx = x[j] - x[i], dy = y[j] - y[i];
                double d2 = dx * dx + dy * dy + SOFT2;
                double inv = 1.0 / (d2 * Math.sqrt(d2));
                sx += m[j] * dx * inv;
                sy += m[j] * dy * inv;
            }
            ax[i] = G * sx;
            ay[i] = G * sy;
        }
        for (int i = 0; i < np; i++) {
            double sx = 0, sy = 0, X = px[i], Y = py[i];
            for (int j = 0; j < n; j++) {
                double dx = x[j] - X, dy = y[j] - Y;
                double d2 = dx * dx + dy * dy + SOFT2;
                double inv = m[j] / (d2 * Math.sqrt(d2));
                sx += dx * inv;
                sy += dy * inv;
            }
            pax[i] = G * sx;
            pay[i] = G * sy;
        }
        accValid = true;
    }

    /** Un paso de «salto de rana» (kick-drift-kick): conserva muy bien la energía en las órbitas. */
    void step(double dt) {
        if (!accValid) computeAcc();
        double h = dt * 0.5;
        for (int i = 0; i < n; i++) {
            vx[i] += ax[i] * h; vy[i] += ay[i] * h;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt;
        }
        for (int i = 0; i < np; i++) {
            pvx[i] += pax[i] * h; pvy[i] += pay[i] * h;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
        }
        computeAcc();
        for (int i = 0; i < n; i++) {
            vx[i] += ax[i] * h; vy[i] += ay[i] * h;
        }
        for (int i = 0; i < np; i++) {
            pvx[i] += pax[i] * h; pvy[i] += pay[i] * h;
        }
        time += dt;
        collide();
    }

    /** Avanza «total» años con pasos de a lo más «maxDt». Devuelve los años realmente avanzados (puede ser menos si hay demasiados pasos). */
    double advance(double total, double maxDt, int maxSteps) {
        int steps = (int) Math.ceil(total / maxDt);
        if (steps < 1) steps = 1;
        boolean cut = steps > maxSteps;
        if (cut) steps = maxSteps;
        double dt = cut ? maxDt : total / steps;
        for (int s = 0; s < steps; s++) step(dt);
        return dt * steps;
    }

    // ================================================================== choques

    private void collide() {
        // partículas contra cuerpos (y las que se fueron muy lejos)
        for (int i = np - 1; i >= 0; i--) {
            boolean gone = false;
            double X = px[i], Y = py[i];
            if (X * X + Y * Y > ESCAPE * ESCAPE) gone = true;
            else {
                for (int j = 0; j < n; j++) {
                    double dx = X - x[j], dy = Y - y[j], rc = collisionRadius(j);
                    if (dx * dx + dy * dy < rc * rc) {
                        gone = true;
                        impacts++;
                        double vrel = Math.hypot(pvx[i] - vx[j], pvy[i] - vy[j]);
                        flashes.add(new Flash(X, Y, Math.min(1, vrel / 40.0) * 0.5 + 0.25, flashId++));
                        break;
                    }
                }
            }
            if (gone) removeParticle(i);
        }
        // cuerpos contra cuerpos
        boolean again = true;
        while (again) {
            again = false;
            outer:
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = x[j] - x[i], dy = y[j] - y[i];
                    double rs = collisionRadius(i) + collisionRadius(j);
                    if (dx * dx + dy * dy < rs * rs) {
                        bodyCollision(i, j);
                        again = true;
                        break outer;
                    }
                }
            }
        }
    }

    private void bodyCollision(int a, int b) {
        int big = m[a] >= m[b] ? a : b, small = big == a ? b : a;
        double mb = m[big], ms = m[small];
        double vrel = Math.hypot(vx[big] - vx[small], vy[big] - vy[small]);
        double rsum = collisionRadius(big) + collisionRadius(small);
        double vesc = Math.sqrt(2 * G * (mb + ms) / rsum);
        if (kind[big] == STAR && kind[small] == STAR) {
            starMerger(big, small);
            return;
        }
        flashes.add(new Flash(x[small], y[small], 1.0, flashId++));
        if (vrel > 2.5 * vesc && ms > 1e-3 * mb) {
            shatters++;
            // choque violento: el pequeño se hace pedazos. El grande se queda con parte de su masa; el resto sale como escombros.
            int pieces = (int) Math.min(600, 80 + ms / mb * 4000);
            double keep = 0.4;
            double cx = x[small], cy = y[small], bvx = vx[big], bvy = vy[big], svx = vx[small], svy = vy[small];
            double newM = mb + keep * ms;
            vx[big] = (mb * vx[big] + keep * ms * svx) / newM;
            vy[big] = (mb * vy[big] + keep * ms * svy) / newM;
            m[big] = newM;
            r[big] = Math.cbrt(Math.pow(r[big], 3) + keep * Math.pow(r[small], 3));
            double mid = Math.hypot(svx - bvx, svy - bvy);
            for (int k = 0; k < pieces; k++) {
                double ang = rnd.nextDouble() * 2 * Math.PI;
                double sp = vesc * (0.3 + rnd.nextDouble() * 1.2);
                addParticle(cx, cy, svx + Math.cos(ang) * sp * 0.7 + (svx - bvx) * 0.15, svy + Math.sin(ang) * sp * 0.7 + (svy - bvy) * 0.15, 1);
            }
            if (small < big) big--;
            removeBody(small);
            // (la parte de masa que se pierde en escombros no se cuenta: las partículas no tienen masa)
            if (mid < 0) throw new IllegalStateException();
        } else {
            merges++;
            double newM = mb + ms;
            double nx = (x[big] * mb + x[small] * ms) / newM, ny = (y[big] * mb + y[small] * ms) / newM;
            double nvx = (vx[big] * mb + vx[small] * ms) / newM, nvy = (vy[big] * mb + vy[small] * ms) / newM;
            r[big] = Math.cbrt(Math.pow(r[big], 3) + Math.pow(r[small], 3));
            x[big] = nx; y[big] = ny; vx[big] = nvx; vy[big] = nvy; m[big] = newM;
            removeBody(small);
        }
        accValid = false;
    }

    /** Radio de una estrella de la secuencia principal (UA), aproximado: R ∝ M^0.8. */
    static double starRadius(double mass) {
        return 0.00465 * Math.pow(mass, 0.8);
    }

    /** Color de una estrella según su masa (temperatura): rojas las chicas, amarillas como el Sol, blancas y azules las grandes. */
    static int starColor(double mass) {
        double[][] t = {{0.08, 255, 90, 50}, {0.3, 255, 120, 70}, {0.6, 255, 175, 95}, {1.0, 255, 200, 87}, {1.5, 255, 235, 190}, {2.5, 215, 228, 255}, {6, 160, 190, 255}};
        if (mass <= t[0][0]) return 0xFF000000 | (int) t[0][1] << 16 | (int) t[0][2] << 8 | (int) t[0][3];
        for (int i = 1; i < t.length; i++) {
            if (mass <= t[i][0]) {
                double f = (mass - t[i - 1][0]) / (t[i][0] - t[i - 1][0]);
                int r = (int) (t[i - 1][1] + (t[i][1] - t[i - 1][1]) * f), g = (int) (t[i - 1][2] + (t[i][2] - t[i - 1][2]) * f), b = (int) (t[i - 1][3] + (t[i][3] - t[i - 1][3]) * f);
                return 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return 0xFFA0BEFF;
    }

    /**
     * Dos estrellas que chocan: se funden en una sola. Se pierde un 3 % de la masa de la menor como gas que sale a unos 200 km/s
     * (en las fusiones reales de estrellas el gas expulsado es del orden de unos pocos por ciento de la menor y mucho más lento que
     * la velocidad de escape de la estrella). La estrella nueva tiene más masa, más radio y otro color.
     */
    private void starMerger(int big, int small) {
        merges++;
        double mb = m[big], ms = m[small];
        double M = mb + ms, lost = 0.03 * ms;
        double cx = (x[big] * mb + x[small] * ms) / M, cy = (y[big] * mb + y[small] * ms) / M;
        double cvx = (vx[big] * mb + vx[small] * ms) / M, cvy = (vy[big] * mb + vy[small] * ms) / M;
        flashes.add(new Flash(cx, cy, 3.0, flashId++));
        x[big] = cx; y[big] = cy; vx[big] = cvx; vy[big] = cvy;
        m[big] = M - lost;
        r[big] = Math.max(starRadius(m[big]), r[big]);
        color[big] = starColor(m[big]);
        double vej = 200 / 4.74047;            // 200 km/s en UA por año
        for (int k = 0; k < 900; k++) {
            double a = rnd.nextDouble() * 2 * Math.PI, sp = vej * (0.25 + rnd.nextDouble());
            addParticle(cx + Math.cos(a) * r[big], cy + Math.sin(a) * r[big], cvx + Math.cos(a) * sp, cvy + Math.sin(a) * sp, 1);
        }
        removeBody(small);
        accValid = false;
    }

    // ================================================================== predicción (la línea de puntos al lanzar)

    /**
     * Calcula por dónde iría una partícula lanzada desde (X,Y) con velocidad (VX,VY), moviendo también los cuerpos hacia adelante,
     * sin tocar la simulación real. Guarda un punto cada «every» pasos en out (pares x,y) y devuelve cuántos puntos.
     * Termina antes si choca con un cuerpo; en out[...] queda el último punto.
     */
    int predict(double X, double Y, double VX, double VY, int steps, double dt, int every, double[] out) {
        int nb = n;
        double[] bx = java.util.Arrays.copyOf(x, nb), by = java.util.Arrays.copyOf(y, nb), bvx = java.util.Arrays.copyOf(vx, nb), bvy = java.util.Arrays.copyOf(vy, nb);
        double[] bax = new double[nb], bay = new double[nb];
        int count = 0;
        double h = dt * 0.5, tx = X, ty = Y, tvx = VX, tvy = VY;
        double[] rc = new double[nb];
        for (int i = 0; i < nb; i++) rc[i] = collisionRadius(i);
        // aceleración inicial
        double[] ta = new double[2];
        accAll(nb, bx, by, bax, bay, tx, ty, ta);
        for (int st = 0; st < steps; st++) {
            for (int i = 0; i < nb; i++) { bvx[i] += bax[i] * h; bvy[i] += bay[i] * h; bx[i] += bvx[i] * dt; by[i] += bvy[i] * dt; }
            tvx += ta[0] * h; tvy += ta[1] * h; tx += tvx * dt; ty += tvy * dt;
            accAll(nb, bx, by, bax, bay, tx, ty, ta);
            for (int i = 0; i < nb; i++) { bvx[i] += bax[i] * h; bvy[i] += bay[i] * h; }
            tvx += ta[0] * h; tvy += ta[1] * h;
            boolean hit = false;
            for (int i = 0; i < nb; i++) {
                double dx = tx - bx[i], dy = ty - by[i];
                if (dx * dx + dy * dy < rc[i] * rc[i]) { hit = true; break; }
            }
            if ((st % every == 0 || hit) && (count + 1) * 2 <= out.length) { out[count * 2] = tx; out[count * 2 + 1] = ty; count++; }
            if (hit) break;
        }
        return count;
    }

    private void accAll(int nb, double[] bx, double[] by, double[] bax, double[] bay, double tx, double ty, double[] ta) {
        for (int i = 0; i < nb; i++) {
            double sx = 0, sy = 0;
            for (int j = 0; j < nb; j++) {
                if (j == i) continue;
                double dx = bx[j] - bx[i], dy = by[j] - by[i], d2 = dx * dx + dy * dy + SOFT2, inv = m[j] / (d2 * Math.sqrt(d2));
                sx += dx * inv; sy += dy * inv;
            }
            bax[i] = G * sx; bay[i] = G * sy;
        }
        double sx = 0, sy = 0;
        for (int j = 0; j < nb; j++) {
            double dx = bx[j] - tx, dy = by[j] - ty, d2 = dx * dx + dy * dy + SOFT2, inv = m[j] / (d2 * Math.sqrt(d2));
            sx += dx * inv; sy += dy * inv;
        }
        ta[0] = G * sx; ta[1] = G * sy;
    }

    // ================================================================== energía y utilidades (para comprobar)

    double totalEnergy() {
        double e = 0;
        for (int i = 0; i < n; i++) {
            e += 0.5 * m[i] * (vx[i] * vx[i] + vy[i] * vy[i]);
            for (int j = i + 1; j < n; j++) {
                double d = Math.sqrt((x[j] - x[i]) * (x[j] - x[i]) + (y[j] - y[i]) * (y[j] - y[i]) + SOFT2);
                e -= G * m[i] * m[j] / d;
            }
        }
        return e;
    }

    double[] momentum() {
        double px_ = 0, py_ = 0;
        for (int i = 0; i < n; i++) {
            px_ += m[i] * vx[i];
            py_ += m[i] * vy[i];
        }
        return new double[]{px_, py_};
    }

    double totalMass() {
        double s = 0;
        for (int i = 0; i < n; i++) s += m[i];
        return s;
    }

    /** Pone el centro de masa quieto, para que el sistema no se vaya a la deriva. */
    void centerMomentum() {
        double M = 0, cx = 0, cy = 0, pvx_ = 0, pvy_ = 0;
        for (int i = 0; i < n; i++) {
            M += m[i]; cx += m[i] * x[i]; cy += m[i] * y[i]; pvx_ += m[i] * vx[i]; pvy_ += m[i] * vy[i];
        }
        cx /= M; cy /= M; pvx_ /= M; pvy_ /= M;
        for (int i = 0; i < n; i++) {
            x[i] -= cx; y[i] -= cy; vx[i] -= pvx_; vy[i] -= pvy_;
        }
        accValid = false;
    }

    int find(String nm) {
        for (int i = 0; i < n; i++) if (nm.equals(name[i])) return i;
        return -1;
    }

    /** Velocidad circular de un cuerpo de masa pequeña a distancia d alrededor de una masa M. */
    static double circularSpeed(double M, double d) {
        return Math.sqrt(G * M / d);
    }

    // ================================================================== el sistema solar

    static Sim solarSystem() {
        Sim s = new Sim();
        s.addBody("Sol", STAR, 1.0, 0.00465, 0, 0, 0, 0, 0xFFFFC857);
        double[][] pl = {
                // nombre, a (UA), masa (M☉), radio (UA), color
                {0, 0.387, 1.66e-7, 1.63e-5}, {1, 0.723, 2.45e-6, 4.05e-5}, {2, 1.0, 3.003e-6, 4.26e-5}, {3, 1.524, 3.23e-7, 2.27e-5},
                {4, 5.203, 9.55e-4, 4.78e-4}, {5, 9.537, 2.86e-4, 4.03e-4}, {6, 19.19, 4.37e-5, 1.71e-4}, {7, 30.07, 5.15e-5, 1.65e-4}};
        String[] names = {"Mercurio", "Venus", "Tierra", "Marte", "Júpiter", "Saturno", "Urano", "Neptuno"};
        int[] cols = {0xFFB5B5B5, 0xFFE8C98B, 0xFF4C9DFF, 0xFFE0643C, 0xFFD9A066, 0xFFE8D29A, 0xFF7FDBDA, 0xFF4A6BFF};
        Random r = new Random(7);
        for (int i = 0; i < pl.length; i++) {
            double a = pl[i][1], ang = r.nextDouble() * 2 * Math.PI;
            double v = circularSpeed(1.0 + pl[i][2], a);
            int id = s.addBody(names[i], PLANET, pl[i][2], pl[i][3], a * Math.cos(ang), a * Math.sin(ang), -v * Math.sin(ang), v * Math.cos(ang), cols[i]);
            if (i == 2) {
                // la Luna, alrededor de la Tierra
                double am = 0.002570, mm = 3.69e-8, angm = r.nextDouble() * 2 * Math.PI;
                double vm = circularSpeed(pl[i][2] + mm, am);
                s.addBody("Luna", MOON, mm, 1.16e-5, s.x[id] + am * Math.cos(angm), s.y[id] + am * Math.sin(angm),
                        s.vx[id] - vm * Math.sin(angm), s.vy[id] + vm * Math.cos(angm), 0xFFCFCFCF);
            }
        }
        s.centerMomentum();
        return s;
    }

    /** Un cinturón de asteroides (partículas) en un anillo entre dos distancias, en órbitas casi circulares. */
    void addBelt(int count, double a0, double a1, double wobble) {
        for (int i = 0; i < count; i++) {
            double a = a0 + (a1 - a0) * rnd.nextDouble(), ang = rnd.nextDouble() * 2 * Math.PI;
            double v = circularSpeed(m[0], a) * (1 + (rnd.nextDouble() - 0.5) * wobble);
            addParticle(a * Math.cos(ang), a * Math.sin(ang), -v * Math.sin(ang), v * Math.cos(ang), 0);
        }
    }
}
