package com.forja.orbita;

import java.util.Random;

public class SimTest {
    static int fails;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLA: " + m); } else System.out.println("ok: " + m); }

    public static void main(String[] args) {
        // 1) Tierra y Sol: la energía casi no cambia en 100 años
        {
            Sim s = new Sim();
            s.addBody("Sol", Sim.STAR, 1, 0.00465, 0, 0, 0, 0, 0);
            s.addBody("Tierra", Sim.PLANET, 3.003e-6, 4.26e-5, 1, 0, 0, Sim.circularSpeed(1, 1), 0);
            s.centerMomentum();
            double e0 = s.totalEnergy();
            for (int i = 0; i < 100 * 4000; i++) s.step(1.0 / 4000);
            double rel = Math.abs((s.totalEnergy() - e0) / e0);
            double d = Math.hypot(s.x[1] - s.x[0], s.y[1] - s.y[0]);
            check(rel < 1e-6, "Tierra-Sol 100 años: la energía cambia " + rel + " (menos de 1e-6)");
            check(Math.abs(d - 1) < 0.001, "la Tierra sigue a 1 UA del Sol: " + d);
        }
        // 2) Sistema solar completo: energía y Luna
        {
            Sim s = Sim.solarSystem();
            double e0 = s.totalEnergy();
            double minM = 1, maxM = 0;
            int tierra = s.find("Tierra"), luna = s.find("Luna");
            double[] p0 = s.momentum();
            int steps = 50 * 3000;
            for (int i = 0; i < steps; i++) {
                s.step(1.0 / 3000);
                if (i % 100 == 0) { double d = Math.hypot(s.x[luna] - s.x[tierra], s.y[luna] - s.y[tierra]); minM = Math.min(minM, d); maxM = Math.max(maxM, d); }
            }
            double rel = Math.abs((s.totalEnergy() - e0) / e0);
            check(s.n == 10, "no hubo choques raros en 50 años: " + s.n + " cuerpos");
            check(rel < 1e-5, "sistema solar 50 años: la energía cambia " + rel);
            check(minM > 0.0015 && maxM < 0.0040, "la Luna se queda cerca de la Tierra: " + minM + " a " + maxM + " UA");
            double[] p1 = s.momentum();
            check(Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) < 1e-12, "el momento total se conserva");
        }
        // 3) Partícula en órbita circular
        {
            Sim s = new Sim();
            s.addBody("Sol", Sim.STAR, 1, 0.00465, 0, 0, 0, 0, 0);
            s.addParticle(2.5, 0, 0, Sim.circularSpeed(1, 2.5), 0);
            for (int i = 0; i < 10 * 2000 * 4; i++) s.step(1.0 / 4000);
            double d = Math.hypot(s.px[0], s.py[0]);
            check(Math.abs(d - 2.5) < 0.001, "un asteroide en órbita circular sigue a 2.5 UA: " + d);
        }
        // 4) Un asteroide que choca con un planeta desaparece y deja un destello
        {
            Sim s = Sim.solarSystem();
            int t = s.find("Tierra");
            s.addParticle(s.x[t] + 0.002, s.y[t], s.vx[t] - 3, s.vy[t], 0);   // viene hacia la Tierra
            int antes = s.np;
            for (int i = 0; i < 2000 && s.np == antes; i++) s.step(1.0 / 20000);
            check(s.np == antes - 1 && s.impacts == 1 && s.flashes.size() == 1, "el asteroide choca con la Tierra y se borra (impactos: " + s.impacts + ")");
        }
        // 5) Choque suave entre dos planetas: se funden y se conserva masa y momento
        {
            Sim s = new Sim();
            s.addBody("Sol", Sim.STAR, 1, 0.00465, 0, 0, 0, 0, 0);
            double v = Sim.circularSpeed(1, 1);
            s.addBody("A", Sim.PLANET, 3e-6, 4e-5, 1, 0, 0, v, 0);
            s.addBody("B", Sim.PLANET, 1e-6, 3e-5, 1.0003, 0, 0, v * 0.98, 0);
            double M0 = s.totalMass();
            double[] p0 = s.momentum();
            for (int i = 0; i < 20000 && s.merges == 0; i++) s.step(1.0 / 20000);
            check(s.merges == 1 && s.n == 2, "dos planetas lentos se funden (cuerpos: " + s.n + ")");
            double[] p1 = s.momentum();
            check(Math.abs(s.totalMass() - M0) < 1e-15 && Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) < 1e-12, "se conserva la masa y el momento en la fusión");
        }
        // 6) Choque violento: el pequeño se rompe en escombros
        {
            Sim s = new Sim();
            s.addBody("Sol", Sim.STAR, 1, 0.00465, 0, 0, 0, 0, 0);
            int a = s.addBody("Gran", Sim.PLANET, 3e-6, 4e-5, 1, 0, 0, 0, 0);
            s.addBody("Peq", Sim.ROCK, 3e-8, 1e-5, 1.003, 0, -60, 0, 0);
            for (int i = 0; i < 500 && s.shatters == 0; i++) s.step(1e-6);
            check(s.shatters == 1 && s.np > 50, "un choque a gran velocidad se rompe en escombros: " + s.np + " pedazos");
        }
        // 6a) Dos estrellas: se funden, con gas expulsado, más masa, más radio y otro color
        {
            Sim s = Sim.solarSystem();
            int sol = s.find("Sol");
            double msol = s.m[sol];
            s.addBody("Enana roja", Sim.STAR, 0.3, 0.0016, 0.05, 0, -80, 0, 0xFFFF6B4A);
            double[] p0 = s.momentum();
            int cuerpos = s.n;
            for (int i = 0; i < 4000 && s.n == cuerpos; i++) s.step(1.0 / 50000);
            sol = s.find("Sol");
            check(s.n == cuerpos - 1 && s.merges >= 1, "una enana roja que cae al Sol se funde con él");
            check(Math.abs(s.m[sol] - (msol + 0.3 - 0.03 * 0.3)) < 1e-12, "la estrella nueva tiene la masa de las dos menos el gas expulsado: " + s.m[sol]);
            check(s.r[sol] >= 0.00465 && s.color[sol] != 0xFFFFC857, "la estrella nueva cambia de color y no se encoge");
            check(s.np >= 900, "sale gas de la fusión (" + s.np + " partículas)");
            check(Math.hypot(s.momentum()[0] - p0[0], s.momentum()[1] - p0[1]) < 0.05 * 0.3 * 80 + 1e-9, "el momento casi se conserva (solo se va el del gas)");
            // lo que pasa después: los planetas se alteran pero el sistema sigue ahí
            for (int i = 0; i < 20000; i++) s.step(1.0 / 2000);
            check(s.n >= 8, "10 años después siguen " + s.n + " cuerpos en el sistema");
        }
        check(Sim.starColor(0.3) != Sim.starColor(1.0) && Sim.starColor(3) != Sim.starColor(1.0), "el color de la estrella depende de su masa");
        // 6b) La predicción coincide con lo que de verdad pasa
        {
            Sim s = Sim.solarSystem();
            int t = s.find("Tierra");
            double X = s.x[t] + 0.05, Y = s.y[t] + 0.02, VX = s.vx[t] * 0.9, VY = s.vy[t] * 0.9 + 0.5;
            double[] out = new double[2 * 5000];
            int cnt = s.predict(X, Y, VX, VY, 4000, 1.0 / 4000, 1, out);
            s.addParticle(X, Y, VX, VY, 0);
            for (int i = 0; i < 4000 && s.np == 1; i++) s.step(1.0 / 4000);
            if (s.np == 1) {
                double err = Math.hypot(s.px[0] - out[(cnt - 1) * 2], s.py[0] - out[(cnt - 1) * 2 + 1]);
                check(cnt == 4000 && err < 1e-9, "la línea de predicción coincide con la simulación real (error " + err + ")");
            } else check(cnt < 4000, "la predicción también termina en el choque");
        }
        // 7) Rendimiento con muchas partículas
        {
            Sim s = Sim.solarSystem();
            s.addBelt(5000, 2.2, 3.2, 0.1);
            long t0 = System.nanoTime();
            for (int i = 0; i < 2000; i++) s.step(1.0 / 2000);   // 1 año
            double sec = (System.nanoTime() - t0) / 1e9;
            System.out.println("1 año simulado con 5000 asteroides: " + sec + " s");
            check(sec < 8, "5000 asteroides + 10 cuerpos: 1 año en " + String.format("%.2f", sec) + " s");
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
