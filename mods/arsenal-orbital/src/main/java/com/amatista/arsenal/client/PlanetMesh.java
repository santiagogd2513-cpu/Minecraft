package com.amatista.arsenal.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Planeta procedural: océanos, continentes, desiertos, montañas, nieve y polos; nubes que giran;
 * atmósfera brillante en el borde; luces de ciudades en el lado nocturno.
 */
final class PlanetMesh {
    static final int LAT = 64;
    static final int LON = 128;

    private static float[] nx, ny, nz;       // normales (= posiciones en la esfera unidad)
    private static float[] cr, cg, cb;       // color del suelo
    private static float[] cloud;            // opacidad de nubes
    private static float[] lights;           // lista de luces de ciudad (x,y,z por luz)
    private static boolean built;

    private PlanetMesh() {}

    static void build() {
        if (built) return;
        int n = (LAT + 1) * (LON + 1);
        nx = new float[n]; ny = new float[n]; nz = new float[n];
        cr = new float[n]; cg = new float[n]; cb = new float[n];
        cloud = new float[n];
        for (int i = 0; i <= LAT; i++) {
            double theta = Math.PI * i / LAT;          // 0 = polo norte (arriba)
            for (int j = 0; j <= LON; j++) {
                double phi = Math.PI * 2 * j / LON;
                int k = i * (LON + 1) + j;
                float x = (float) (Math.sin(theta) * Math.cos(phi));
                float y = (float) Math.cos(theta);
                float z = (float) (Math.sin(theta) * Math.sin(phi));
                nx[k] = x; ny[k] = y; nz[k] = z;
                float[] col = surfaceColor(x, y, z);
                cr[k] = col[0]; cg[k] = col[1]; cb[k] = col[2];
                float c = fbm(x * 3.1f + 11f, y * 3.1f + 3f, z * 3.1f - 7f, 5);
                float band = 0.5f + 0.5f * (float) Math.cos(y * 9.0);
                cloud[k] = smoothstep(0.50f, 0.72f, c * 0.85f + band * 0.15f) * 0.9f;
            }
        }
        // luces de ciudades sobre tierra firme
        Random rnd = new Random(7);
        float[] tmp = new float[3 * 1400];
        int count = 0;
        for (int tries = 0; tries < 40000 && count < 1400; tries++) {
            double u = rnd.nextDouble() * 2 - 1, phi = rnd.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            float x = (float) (s * Math.cos(phi)), y = (float) u, z = (float) (s * Math.sin(phi));
            float e = elevation(x, y, z);
            if (e < 0.53f || e > 0.7f || Math.abs(y) > 0.75f) continue;
            // las ciudades se agrupan
            if (fbm(x * 9 + 2, y * 9, z * 9 - 4, 3) < 0.5f) continue;
            tmp[count * 3] = x; tmp[count * 3 + 1] = y; tmp[count * 3 + 2] = z;
            count++;
        }
        lights = new float[count * 3];
        System.arraycopy(tmp, 0, lights, 0, count * 3);
        built = true;
    }

    // --------------------------------------------------------------- dibujo

    /** Superficie (opaca). */
    static void renderSurface(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun) {
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int k0 = i * (LON + 1) + j, k1 = k0 + 1, k2 = k0 + LON + 2, k3 = k0 + LON + 1;
                vertexSurface(bb, m, radius, k0, sun);
                vertexSurface(bb, m, radius, k3, sun);
                vertexSurface(bb, m, radius, k2, sun);
                vertexSurface(bb, m, radius, k1, sun);
            }
        }
    }

    private static void vertexSurface(BufferBuilder bb, Matrix4f m, float radius, int k, Vector3f sun) {
        float d = nx[k] * sun.x + ny[k] * sun.y + nz[k] * sun.z;
        float light = 0.035f + 0.965f * smoothstep(-0.08f, 0.45f, d);
        // atardecer en la línea entre el día y la noche
        float dusk = Math.max(0f, 1f - Math.abs(d) * 7f) * 0.35f;
        float r = cr[k] * light + dusk * 0.9f;
        float g = cg[k] * light + dusk * 0.35f;
        float b = cb[k] * light + dusk * 0.1f;
        bb.vertex(m, nx[k] * radius, ny[k] * radius, nz[k] * radius).color(r, g, b, 1f).endVertex();
    }

    /** Nubes (transparencia normal), giran lentamente. */
    static void renderClouds(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun, float rotation) {
        float cos = (float) Math.cos(rotation), sin = (float) Math.sin(rotation);
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int k0 = i * (LON + 1) + j, k1 = k0 + 1, k2 = k0 + LON + 2, k3 = k0 + LON + 1;
                if (cloud[k0] + cloud[k1] + cloud[k2] + cloud[k3] < 0.02f) continue;
                vertexCloud(bb, m, radius, k0, sun, cos, sin);
                vertexCloud(bb, m, radius, k3, sun, cos, sin);
                vertexCloud(bb, m, radius, k2, sun, cos, sin);
                vertexCloud(bb, m, radius, k1, sun, cos, sin);
            }
        }
    }

    private static void vertexCloud(BufferBuilder bb, Matrix4f m, float radius, int k, Vector3f sun, float cos, float sin) {
        float x = nx[k] * cos - nz[k] * sin;
        float z = nx[k] * sin + nz[k] * cos;
        float y = ny[k];
        float d = x * sun.x + y * sun.y + z * sun.z;
        float light = 0.06f + 0.94f * smoothstep(-0.1f, 0.4f, d);
        float dusk = Math.max(0f, 1f - Math.abs(d) * 6f) * 0.4f;
        bb.vertex(m, x * radius, y * radius, z * radius)
                .color(light + dusk * 0.5f, light * 0.97f + dusk * 0.2f, light * 0.95f, cloud[k] * (0.35f + 0.65f * light))
                .endVertex();
    }

    /** Atmósfera: brilla en el borde del planeta (aditivo). */
    static void renderAtmosphere(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun, Vector3f eye) {
        int step = 2;
        for (int i = 0; i < LAT; i += step) {
            for (int j = 0; j < LON; j += step) {
                int i2 = Math.min(LAT, i + step), j2 = Math.min(LON, j + step);
                int k0 = i * (LON + 1) + j, k1 = i * (LON + 1) + j2, k2 = i2 * (LON + 1) + j2, k3 = i2 * (LON + 1) + j;
                vertexAtmo(bb, m, radius, k0, sun, eye);
                vertexAtmo(bb, m, radius, k3, sun, eye);
                vertexAtmo(bb, m, radius, k2, sun, eye);
                vertexAtmo(bb, m, radius, k1, sun, eye);
            }
        }
    }

    private static void vertexAtmo(BufferBuilder bb, Matrix4f m, float radius, int k, Vector3f sun, Vector3f eye) {
        float px = nx[k] * radius, py = ny[k] * radius, pz = nz[k] * radius;
        float vx = eye.x - px, vy = eye.y - py, vz = eye.z - pz;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float facing = Math.abs((nx[k] * vx + ny[k] * vy + nz[k] * vz) / len);
        float rim = (float) Math.pow(1f - facing, 2.2);
        float d = nx[k] * sun.x + ny[k] * sun.y + nz[k] * sun.z;
        float day = smoothstep(-0.35f, 0.3f, d);
        float dusk = Math.max(0f, 1f - Math.abs(d + 0.05f) * 5f);
        float a = Math.min(1f, rim * 1.25f + 0.06f) * (0.12f + 0.88f * day);
        bb.vertex(m, px, py, pz).color(0.30f + dusk * 0.7f, 0.55f + dusk * 0.15f, 1.0f - dusk * 0.4f, a).endVertex();
    }

    /** Luces de ciudades en el lado de noche (aditivo). */
    static void renderCityLights(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun, float time) {
        for (int i = 0; i < lights.length; i += 3) {
            float x = lights[i], y = lights[i + 1], z = lights[i + 2];
            float d = x * sun.x + y * sun.y + z * sun.z;
            float night = smoothstep(0.05f, -0.2f, d);
            if (night <= 0.01f) continue;
            float flicker = 0.75f + 0.25f * (float) Math.sin(time * 3 + i);
            // pequeño quad tangente a la superficie
            Vector3f n = new Vector3f(x, y, z);
            Vector3f t1 = new Vector3f(n).cross(Math.abs(y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f t2 = new Vector3f(n).cross(t1).normalize();
            float s = 3.2f;
            float cx = x * (radius + 0.6f), cy = y * (radius + 0.6f), cz = z * (radius + 0.6f);
            RenderUtil.streak(bb, m, cx, cy, cz, t1, s, t2, s, 1.0f, 0.75f, 0.35f, 0.8f * night * flicker);
        }
    }

    // --------------------------------------------------------------- terreno

    private static float elevation(float x, float y, float z) {
        return fbm(x * 1.9f + 5f, y * 1.9f, z * 1.9f + 1f, 6);
    }

    private static float[] surfaceColor(float x, float y, float z) {
        float e = elevation(x, y, z);
        float lat = Math.abs(y);
        float moist = fbm(x * 2.7f - 3f, y * 2.7f + 8f, z * 2.7f, 4);
        if (lat > 0.86f || (lat > 0.74f && e > 0.45f)) return new float[]{0.88f, 0.92f, 0.97f}; // hielo polar
        if (e < 0.52f) {
            float depth = smoothstep(0.30f, 0.52f, e);
            return new float[]{lerp(0.01f, 0.06f, depth), lerp(0.05f, 0.30f, depth), lerp(0.20f, 0.52f, depth)}; // océano
        }
        if (e < 0.535f) return new float[]{0.76f, 0.70f, 0.50f}; // playa
        if (e > 0.72f) return new float[]{0.92f, 0.93f, 0.96f};  // nieve
        if (e > 0.64f) return new float[]{0.42f, 0.37f, 0.32f};  // montaña
        if (lat < 0.38f && moist < 0.47f) return new float[]{0.74f, 0.60f, 0.34f}; // desierto
        if (moist > 0.55f) return new float[]{0.10f, 0.33f, 0.10f}; // selva
        return new float[]{0.24f, 0.45f, 0.16f}; // pradera
    }

    // --------------------------------------------------------------- ruido

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float noise(float x, float y, float z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float xf = x - xi, yf = y - yi, zf = z - zi;
        float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
        float c000 = hash(xi, yi, zi), c100 = hash(xi + 1, yi, zi), c010 = hash(xi, yi + 1, zi), c110 = hash(xi + 1, yi + 1, zi);
        float c001 = hash(xi, yi, zi + 1), c101 = hash(xi + 1, yi, zi + 1), c011 = hash(xi, yi + 1, zi + 1), c111 = hash(xi + 1, yi + 1, zi + 1);
        float x00 = lerp(c000, c100, u), x10 = lerp(c010, c110, u), x01 = lerp(c001, c101, u), x11 = lerp(c011, c111, u);
        return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w);
    }

    static float fbm(float x, float y, float z, int octaves) {
        float sum = 0, amp = 0.5f, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x, y, z) * amp;
            norm += amp;
            x *= 2.03f; y *= 2.03f; z *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
