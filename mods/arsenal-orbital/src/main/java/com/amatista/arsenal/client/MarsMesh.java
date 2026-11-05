package com.amatista.arsenal.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

import static com.amatista.arsenal.client.PlanetMesh.fbm;
import static com.amatista.arsenal.client.PlanetMesh.smoothstep;

/**
 * Marte (polvo rojizo, zonas oscuras de basalto, miles de cráteres, el cañón Valles Marineris,
 * el volcán Olympus Mons y casquetes polares), Fobos (alargada, con el gran cráter Stickney,
 * surcos y grietas que se encienden con el calor) y Deimos.
 */
final class MarsMesh {
    static final int LAT = 160, LON = 320;
    static final int ROCK_LAT = 72, ROCK_LON = 144;

    private static float[] mn;              // normales de Marte
    private static float[] mc;              // color de Marte (r,g,b)
    private static float[] pPos, pNorm;     // Fobos: posición local y normal
    private static float[] pCol, pCrack;    // Fobos: color y grietas
    private static float[] dPos, dNorm, dCol; // Deimos
    private static boolean built;

    private MarsMesh() {}

    static void build(Vector3f sun) {
        if (built) return;
        buildMars(sun);
        float[][] phobos = buildRock(66f, 52f, 46f, 11, true);
        pPos = phobos[0]; pNorm = phobos[1]; pCol = phobos[2]; pCrack = phobos[3];
        float[][] deimos = buildRock(26f, 22f, 20f, 23, false);
        dPos = deimos[0]; dNorm = deimos[1]; dCol = deimos[2];
        built = true;
    }

    // ------------------------------------------------------------------ Marte

    private static void buildMars(Vector3f sun) {
        int n = (LAT + 1) * (LON + 1);
        mn = new float[n * 3];
        mc = new float[n * 3];
        Random r = new Random(1965);
        int craterCount = 420;
        float[] cr = new float[craterCount * 4];
        for (int i = 0; i < craterCount; i++) {
            double u = r.nextDouble() * 2 - 1, phi = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            cr[i * 4] = (float) (s * Math.cos(phi)); cr[i * 4 + 1] = (float) u; cr[i * 4 + 2] = (float) (s * Math.sin(phi));
            cr[i * 4 + 3] = (float) (0.015 + Math.pow(r.nextDouble(), 3) * 0.11);
        }
        Vector3f olympus = new Vector3f(0.55f, 0.32f, 0.77f).normalize();
        for (int i = 0; i <= LAT; i++) {
            double theta = Math.PI * i / LAT;
            for (int j = 0; j <= LON; j++) {
                double phi = Math.PI * 2 * j / LON;
                int k = i * (LON + 1) + j;
                float x = (float) (Math.sin(theta) * Math.cos(phi)), y = (float) Math.cos(theta), z = (float) (Math.sin(theta) * Math.sin(phi));
                mn[k * 3] = x; mn[k * 3 + 1] = y; mn[k * 3 + 2] = z;
                // polvo rojizo con variaciones de tono
                float dust = fbm(x * 2.4f + 3, y * 2.4f, z * 2.4f - 5, 6);
                float dark = smoothstep(0.52f, 0.66f, fbm(x * 1.7f - 8, y * 1.7f + 4, z * 1.7f + 2, 5));
                float fine = fbm(x * 30f, y * 30f + 1, z * 30f - 2, 3);
                float red = 0.66f + 0.14f * dust, green = 0.32f + 0.10f * dust, blue = 0.18f + 0.06f * dust;
                // zonas oscuras de basalto (como Syrtis Major)
                red = lerp(red, 0.34f, dark * 0.7f); green = lerp(green, 0.20f, dark * 0.7f); blue = lerp(blue, 0.15f, dark * 0.7f);
                // cráteres: fondo oscuro y borde claro
                float relief = 0f;
                for (int c = 0; c < craterCount; c++) {
                    float dx = x - cr[c * 4], dy = y - cr[c * 4 + 1], dz = z - cr[c * 4 + 2];
                    float d2 = dx * dx + dy * dy + dz * dz;
                    float rad = cr[c * 4 + 3];
                    if (d2 > rad * rad * 2.2f) continue;
                    float d = (float) Math.sqrt(d2) / rad;
                    if (d < 0.85f) relief -= 0.16f * (1f - d * 0.6f);
                    else if (d < 1.25f) relief += 0.14f * (1f - Math.abs(d - 1.05f) / 0.2f);
                }
                // Valles Marineris: un cañón enorme cerca del ecuador
                float lon = (float) Math.atan2(z, x);
                float canyonLat = 0.12f * (float) Math.sin(lon * 3) * 0.3f - 0.05f;
                float inCanyonLon = smoothstep(-0.9f, -0.6f, lon) * (1f - smoothstep(0.2f, 0.6f, lon));
                float cw = Math.abs(y - canyonLat);
                float canyon = (1f - smoothstep(0.012f, 0.03f, cw)) * inCanyonLon;
                relief -= canyon * 0.35f;
                relief += (1f - smoothstep(0.03f, 0.05f, cw)) * inCanyonLon * smoothstep(0.012f, 0.03f, cw) * 0.12f;
                // Olympus Mons: volcán gigante con su caldera
                float od = new Vector3f(x, y, z).distance(olympus);
                if (od < 0.16f) {
                    relief += 0.18f * (1f - od / 0.16f);
                    if (od < 0.025f) relief -= 0.25f;
                }
                float shade = 1f + relief + (fine - 0.5f) * 0.18f;
                red *= shade; green *= shade; blue *= shade;
                // casquetes polares de hielo y CO2
                float cap = smoothstep(0.86f, 0.92f, Math.abs(y) + (fbm(x * 6, y * 6, z * 6, 3) - 0.5f) * 0.08f);
                red = lerp(red, 0.86f, cap); green = lerp(green, 0.84f, cap); blue = lerp(blue, 0.82f, cap);
                mc[k * 3] = Math.min(1f, red); mc[k * 3 + 1] = Math.min(1f, green); mc[k * 3 + 2] = Math.min(1f, blue);
            }
        }
    }

    static void renderMars(BufferBuilder bb, Matrix4f view, Vector3f center, float radius, Vector3f sun) {
        // la cámara vista desde el centro de Marte: solo se dibuja la cara que mira hacia ella
        Vector3f cam = Lod.cameraPos(view).sub(center);
        float dist = cam.length();
        boolean cull = dist > radius * 1.001f;
        float lim = radius * 0.985f;
        int step = Lod.step(radius, dist);
        for (int i = 0; i < LAT; i += step) {
            int i2 = Math.min(LAT, i + step);
            for (int j = 0; j < LON; j += step) {
                int j2 = Math.min(LON, j + step);
                int k0 = i * (LON + 1) + j, k1 = i * (LON + 1) + j2, k2 = i2 * (LON + 1) + j2, k3 = i2 * (LON + 1) + j;
                if (cull && dot(k0, cam) <= lim && dot(k1, cam) <= lim && dot(k2, cam) <= lim && dot(k3, cam) <= lim) continue;
                marsVertex(bb, view, center, radius, k0, sun);
                marsVertex(bb, view, center, radius, k3, sun);
                marsVertex(bb, view, center, radius, k2, sun);
                marsVertex(bb, view, center, radius, k1, sun);
            }
        }
    }

    private static float dot(int k, Vector3f c) {
        return mn[k * 3] * c.x + mn[k * 3 + 1] * c.y + mn[k * 3 + 2] * c.z;
    }

    private static void marsVertex(BufferBuilder bb, Matrix4f view, Vector3f c, float radius, int k, Vector3f sun) {
        float x = mn[k * 3], y = mn[k * 3 + 1], z = mn[k * 3 + 2];
        float d = x * sun.x + y * sun.y + z * sun.z;
        float light = 0.02f + 0.88f * smoothstep(-0.05f, 0.6f, d);
        bb.vertex(view, c.x + x * radius, c.y + y * radius, c.z + z * radius)
                .color(mc[k * 3] * light, mc[k * 3 + 1] * light, mc[k * 3 + 2] * light, 1f).endVertex();
    }

    /** Atmósfera fina de Marte: polvo rosado en el borde (aditivo). */
    static void renderMarsAtmosphere(BufferBuilder bb, Matrix4f view, Vector3f c, float radius, Vector3f sun, Vector3f eye) {
        int step = 2;
        for (int i = 0; i < LAT; i += step) {
            for (int j = 0; j < LON; j += step) {
                int i2 = Math.min(LAT, i + step), j2 = Math.min(LON, j + step);
                int[] ks = {i * (LON + 1) + j, i2 * (LON + 1) + j, i2 * (LON + 1) + j2, i * (LON + 1) + j2};
                for (int k : ks) {
                    float x = mn[k * 3], y = mn[k * 3 + 1], z = mn[k * 3 + 2];
                    float px = c.x + x * radius, py = c.y + y * radius, pz = c.z + z * radius;
                    float vx = eye.x - px, vy = eye.y - py, vz = eye.z - pz;
                    float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float facing = Math.abs((x * vx + y * vy + z * vz) / len);
                    float rim = (float) Math.pow(1f - facing, 4);
                    float day = smoothstep(-0.25f, 0.35f, x * sun.x + y * sun.y + z * sun.z);
                    bb.vertex(view, px, py, pz).color(0.95f, 0.62f, 0.45f, (rim * 0.3f + 0.005f) * (0.1f + 0.9f * day)).endVertex();
                }
            }
        }
    }

    // ------------------------------------------------------------------ Fobos y Deimos

    /** Roca irregular (lat-lon deformada) con cráteres y, para Fobos, surcos y grietas. */
    private static float[][] buildRock(float ax, float ay, float az, int seed, boolean phobos) {
        int n = (ROCK_LAT + 1) * (ROCK_LON + 1);
        float[] pos = new float[n * 3], nor = new float[n * 3], col = new float[n * 3], crack = new float[n];
        Random r = new Random(seed);
        int cc = phobos ? 90 : 40;
        float[] cr = new float[cc * 4];
        for (int i = 0; i < cc; i++) {
            double u = r.nextDouble() * 2 - 1, phi = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            cr[i * 4] = (float) (s * Math.cos(phi)); cr[i * 4 + 1] = (float) u; cr[i * 4 + 2] = (float) (s * Math.sin(phi));
            cr[i * 4 + 3] = (float) (0.06 + Math.pow(r.nextDouble(), 2.5) * 0.22);
        }
        if (phobos) { // Stickney: el gran cráter de Fobos
            cr[0] = 1f; cr[1] = 0.1f; cr[2] = 0.05f; cr[3] = 0.55f;
        }
        for (int i = 0; i <= ROCK_LAT; i++) {
            double theta = Math.PI * i / ROCK_LAT;
            for (int j = 0; j <= ROCK_LON; j++) {
                double phi = Math.PI * 2 * j / ROCK_LON;
                int k = i * (ROCK_LON + 1) + j;
                float x = (float) (Math.sin(theta) * Math.cos(phi)), y = (float) Math.cos(theta), z = (float) (Math.sin(theta) * Math.sin(phi));
                float bump = (fbm(x * 2.2f + seed, y * 2.2f, z * 2.2f, 5) - 0.5f) * 0.32f;
                float shade = 1f;
                for (int c = 0; c < cc; c++) {
                    float dx = x - cr[c * 4], dy = y - cr[c * 4 + 1], dz = z - cr[c * 4 + 2];
                    float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / cr[c * 4 + 3];
                    if (d < 1f) { bump -= 0.12f * cr[c * 4 + 3] * 3f * (1f - d * d); shade -= 0.18f * (1f - d); }
                    else if (d < 1.3f) { bump += 0.05f * cr[c * 4 + 3] * 3f * (1.3f - d) / 0.3f; shade += 0.1f; }
                }
                if (phobos) { // surcos paralelos
                    float groove = (float) Math.abs(Math.sin((x * 0.3f + y * 0.9f + z * 0.2f) * 38f));
                    if (groove > 0.92f) { bump -= 0.012f; shade -= 0.12f; }
                }
                float scale = 1f + bump;
                pos[k * 3] = x * ax * scale; pos[k * 3 + 1] = y * ay * scale; pos[k * 3 + 2] = z * az * scale;
                float g = (0.30f + 0.12f * fbm(x * 7, y * 7, z * 7, 3)) * Math.max(0.5f, shade);
                col[k * 3] = g * 1.08f; col[k * 3 + 1] = g * 0.96f; col[k * 3 + 2] = g * 0.86f;
                float ridge = 1f - Math.abs(fbm(x * 9f + 3, y * 9f, z * 9f - 2, 4) * 2f - 1f);
                crack[k] = smoothstep(0.82f, 0.95f, ridge);
            }
        }
        // normales aproximadas a partir de las posiciones vecinas
        for (int i = 0; i <= ROCK_LAT; i++) {
            for (int j = 0; j <= ROCK_LON; j++) {
                int k = i * (ROCK_LON + 1) + j;
                int kr = i * (ROCK_LON + 1) + (j + 1) % (ROCK_LON + 1);
                int kd = Math.min(ROCK_LAT, i + 1) * (ROCK_LON + 1) + j;
                int ku = Math.max(0, i - 1) * (ROCK_LON + 1) + j;
                Vector3f a = new Vector3f(pos[kr * 3] - pos[k * 3], pos[kr * 3 + 1] - pos[k * 3 + 1], pos[kr * 3 + 2] - pos[k * 3 + 2]);
                Vector3f b = new Vector3f(pos[kd * 3] - pos[ku * 3], pos[kd * 3 + 1] - pos[ku * 3 + 1], pos[kd * 3 + 2] - pos[ku * 3 + 2]);
                Vector3f nn = a.cross(b);
                Vector3f radial = new Vector3f(pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2]);
                if (nn.lengthSquared() < 1e-8f || nn.dot(radial) < 0) nn.negate();
                if (nn.lengthSquared() < 1e-8f) nn.set(radial);
                nn.normalize();
                nor[k * 3] = nn.x; nor[k * 3 + 1] = nn.y; nor[k * 3 + 2] = nn.z;
            }
        }
        return new float[][]{pos, nor, col, crack};
    }

    static void renderPhobos(BufferBuilder bb, Matrix4f view, Vector3f c, Quaternionf rot, Vector3f sun) {
        renderRock(bb, view, c, rot, sun, pPos, pNorm, pCol, 1f);
    }

    /** Fobos a otra escala (la roca que cae en el mundo). */
    static void renderPhobosScaled(BufferBuilder bb, Matrix4f view, Vector3f c, Quaternionf rot, Vector3f sun, float scale) {
        renderRock(bb, view, c, rot, sun, pPos, pNorm, pCol, scale);
    }

    static void renderDeimos(BufferBuilder bb, Matrix4f view, Vector3f c, Quaternionf rot, Vector3f sun) {
        renderRock(bb, view, c, rot, sun, dPos, dNorm, dCol, 1f);
    }

    private static void renderRock(BufferBuilder bb, Matrix4f view, Vector3f c, Quaternionf rot, Vector3f sun,
                                   float[] pos, float[] nor, float[] col, float scale) {
        Vector3f p = new Vector3f(), nn = new Vector3f();
        int[] idx = new int[4];
        for (int i = 0; i < ROCK_LAT; i++) {
            for (int j = 0; j < ROCK_LON; j++) {
                idx[0] = i * (ROCK_LON + 1) + j; idx[1] = idx[0] + ROCK_LON + 1; idx[2] = idx[1] + 1; idx[3] = idx[0] + 1;
                for (int k : idx) {
                    rot.transform(p.set(pos[k * 3] * scale, pos[k * 3 + 1] * scale, pos[k * 3 + 2] * scale));
                    rot.transform(nn.set(nor[k * 3], nor[k * 3 + 1], nor[k * 3 + 2]));
                    float light = 0.03f + 0.9f * Math.max(0f, nn.dot(sun));
                    bb.vertex(view, c.x + p.x, c.y + p.y, c.z + p.z)
                            .color(col[k * 3] * light, col[k * 3 + 1] * light, col[k * 3 + 2] * light, 1f).endVertex();
                }
            }
        }
    }

    /** Calor de Fobos (aditivo): grietas incandescentes y capa de plasma en el frente. */
    static void renderPhobosHeat(BufferBuilder bb, Matrix4f view, Vector3f c, Quaternionf rot, Vector3f motion, float heat, float time) {
        renderPhobosHeat(bb, view, c, rot, motion, heat, time, 1f);
    }

    static void renderPhobosHeat(BufferBuilder bb, Matrix4f view, Vector3f c, Quaternionf rot, Vector3f motion, float heat, float time, float scale) {
        if (heat <= 0.01f) return;
        Vector3f p = new Vector3f(), nn = new Vector3f();
        int[] idx = new int[4];
        float flicker = 0.85f + 0.15f * (float) Math.sin(time * 9);
        for (int i = 0; i < ROCK_LAT; i++) {
            for (int j = 0; j < ROCK_LON; j++) {
                idx[0] = i * (ROCK_LON + 1) + j; idx[1] = idx[0] + ROCK_LON + 1; idx[2] = idx[1] + 1; idx[3] = idx[0] + 1;
                for (int k : idx) {
                    rot.transform(p.set(pos3(k)));
                    rot.transform(nn.set(pNorm[k * 3], pNorm[k * 3 + 1], pNorm[k * 3 + 2]));
                    float front = Math.max(0f, nn.dot(motion));
                    float a = (pCrack[k] * (0.4f + 0.6f * heat) + front * front * Math.max(0f, heat - 0.4f) * 1.4f) * heat * flicker;
                    float s = 1.02f * scale;
                    bb.vertex(view, c.x + p.x * s, c.y + p.y * s, c.z + p.z * s)
                            .color(1f, 0.45f + 0.35f * front, 0.12f + 0.3f * front * heat, Math.min(1f, a)).endVertex();
                }
            }
        }
    }

    private static Vector3f pos3(int k) {
        return new Vector3f(pPos[k * 3], pPos[k * 3 + 1], pPos[k * 3 + 2]);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
