package com.amatista.arsenal.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Planeta procedural realista: océanos con aguas profundas y costas turquesa, reflejo del sol en
 * el mar, playas, selvas, sabanas, desiertos con cañones rojizos, praderas, bosques boreales,
 * tundra, lagos, cordilleras con relieve sombreado y nieve, casquetes polares; nubes que giran;
 * atmósfera fina en el borde; luces de ciudades en el lado nocturno.
 */
final class PlanetMesh {
    static final int LAT = 120;
    static final int LON = 240;

    private static float[] nx, ny, nz;       // normales (= posiciones en la esfera unidad)
    private static float[] cr, cg, cb;       // color del suelo (con relieve ya aplicado)
    private static float[] water;            // 1 = agua (para el reflejo del sol)
    private static float[] cloud;            // opacidad de nubes
    private static float[] lights;           // luces de ciudad (x,y,z por luz)
    private static boolean built;

    private PlanetMesh() {}

    static void build(Vector3f sun) {
        if (built) return;
        int n = (LAT + 1) * (LON + 1);
        nx = new float[n]; ny = new float[n]; nz = new float[n];
        cr = new float[n]; cg = new float[n]; cb = new float[n];
        water = new float[n];
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
                float[] col = surfaceColor(x, y, z, sun);
                cr[k] = col[0]; cg[k] = col[1]; cb[k] = col[2]; water[k] = col[3];
                // nubes: grandes sistemas, bandas por latitud y remolinos
                float c = fbm(x * 3.1f + 11f, y * 3.1f + 3f, z * 3.1f - 7f, 6);
                float swirl = fbm(x * 7f - 4f, y * 7f + 9f, z * 7f + 1f, 3);
                float band = 0.5f + 0.5f * (float) Math.cos(y * 9.0);
                cloud[k] = smoothstep(0.52f, 0.74f, c * 0.75f + band * 0.12f + swirl * 0.13f) * 0.85f;
            }
        }
        Random rnd = new Random(7);
        float[] tmp = new float[3 * 2200];
        int count = 0;
        for (int tries = 0; tries < 80000 && count < 2200; tries++) {
            double u = rnd.nextDouble() * 2 - 1, phi = rnd.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            float x = (float) (s * Math.cos(phi)), y = (float) u, z = (float) (s * Math.sin(phi));
            float e = elevation(x, y, z);
            if (e < 0.53f || e > 0.66f || Math.abs(y) > 0.72f) continue;
            if (fbm(x * 9 + 2, y * 9, z * 9 - 4, 3) < 0.52f) continue; // las ciudades se agrupan
            tmp[count * 3] = x; tmp[count * 3 + 1] = y; tmp[count * 3 + 2] = z;
            count++;
        }
        lights = new float[count * 3];
        System.arraycopy(tmp, 0, lights, 0, count * 3);
        built = true;
    }

    // --------------------------------------------------------------- dibujo

    /** Superficie (opaca), con día/noche, atardecer en la línea del terminador y reflejo del sol en el mar. */
    static void renderSurface(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun, Vector3f eye) {
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int k0 = i * (LON + 1) + j, k1 = k0 + 1, k2 = k0 + LON + 2, k3 = k0 + LON + 1;
                vertexSurface(bb, m, radius, k0, sun, eye);
                vertexSurface(bb, m, radius, k3, sun, eye);
                vertexSurface(bb, m, radius, k2, sun, eye);
                vertexSurface(bb, m, radius, k1, sun, eye);
            }
        }
    }

    private static void vertexSurface(BufferBuilder bb, Matrix4f m, float radius, int k, Vector3f sun, Vector3f eye) {
        float x = nx[k], y = ny[k], z = nz[k];
        float d = x * sun.x + y * sun.y + z * sun.z;
        float light = 0.025f + 0.975f * smoothstep(-0.06f, 0.5f, d);
        float dusk = Math.max(0f, 1f - Math.abs(d) * 8f) * 0.22f;
        float r = cr[k] * light + dusk * 0.8f;
        float g = cg[k] * light + dusk * 0.3f;
        float b = cb[k] * light + dusk * 0.08f;
        if (water[k] > 0f && d > 0f) {
            // reflejo especular del sol en el océano
            float px = x * radius, py = y * radius, pz = z * radius;
            float vx = eye.x - px, vy = eye.y - py, vz = eye.z - pz;
            float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float hx = sun.x + vx / vl, hy = sun.y + vy / vl, hz = sun.z + vz / vl;
            float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
            float spec = (float) Math.pow(Math.max(0f, (x * hx + y * hy + z * hz) / hl), 90) * 0.75f * water[k];
            r += spec; g += spec * 0.92f; b += spec * 0.8f;
        }
        bb.vertex(m, x * radius, y * radius, z * radius).color(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), 1f).endVertex();
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
        float light = 0.04f + 0.9f * smoothstep(-0.1f, 0.45f, d);
        float dusk = Math.max(0f, 1f - Math.abs(d) * 7f) * 0.35f;
        bb.vertex(m, x * radius, y * radius, z * radius)
                .color(light + dusk * 0.5f, light * 0.97f + dusk * 0.18f, light * 0.95f, cloud[k] * (0.3f + 0.6f * light))
                .endVertex();
    }

    /**
     * Atmósfera: una capa fina azul que solo brilla en el borde del planeta (aditivo).
     * Si la cámara está dentro o muy cerca de la capa, se apaga para no cubrir toda la pantalla.
     */
    static void renderAtmosphere(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun, Vector3f eye) {
        float eyeDist = eye.length();
        float fade = smoothstep(radius * 1.01f, radius * 1.12f, eyeDist);
        if (fade <= 0.01f) return;
        int step = 2;
        for (int i = 0; i < LAT; i += step) {
            for (int j = 0; j < LON; j += step) {
                int i2 = Math.min(LAT, i + step), j2 = Math.min(LON, j + step);
                int k0 = i * (LON + 1) + j, k1 = i * (LON + 1) + j2, k2 = i2 * (LON + 1) + j2, k3 = i2 * (LON + 1) + j;
                vertexAtmo(bb, m, radius, k0, sun, eye, fade);
                vertexAtmo(bb, m, radius, k3, sun, eye, fade);
                vertexAtmo(bb, m, radius, k2, sun, eye, fade);
                vertexAtmo(bb, m, radius, k1, sun, eye, fade);
            }
        }
    }

    private static void vertexAtmo(BufferBuilder bb, Matrix4f m, float radius, int k, Vector3f sun, Vector3f eye, float fade) {
        float px = nx[k] * radius, py = ny[k] * radius, pz = nz[k] * radius;
        float vx = eye.x - px, vy = eye.y - py, vz = eye.z - pz;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float facing = Math.abs((nx[k] * vx + ny[k] * vy + nz[k] * vz) / len);
        float rim = (float) Math.pow(1f - facing, 4.0);
        float d = nx[k] * sun.x + ny[k] * sun.y + nz[k] * sun.z;
        float day = smoothstep(-0.3f, 0.35f, d);
        float dusk = Math.max(0f, 1f - Math.abs(d + 0.05f) * 6f);
        float a = (rim * 0.55f + 0.015f) * (0.08f + 0.92f * day) * fade;
        bb.vertex(m, px, py, pz).color(0.22f + dusk * 0.6f, 0.45f + dusk * 0.12f, 0.95f - dusk * 0.4f, a).endVertex();
    }

    /** Luces de ciudades en el lado de noche (aditivo). */
    static void renderCityLights(BufferBuilder bb, Matrix4f m, float radius, Vector3f sun, float time) {
        for (int i = 0; i < lights.length; i += 3) {
            float x = lights[i], y = lights[i + 1], z = lights[i + 2];
            float d = x * sun.x + y * sun.y + z * sun.z;
            float night = smoothstep(0.05f, -0.2f, d);
            if (night <= 0.01f) continue;
            float flicker = 0.75f + 0.25f * (float) Math.sin(time * 3 + i);
            Vector3f n = new Vector3f(x, y, z);
            Vector3f t1 = new Vector3f(n).cross(Math.abs(y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f t2 = new Vector3f(n).cross(t1).normalize();
            float s = 2.4f;
            float cx = x * (radius + 0.6f), cy = y * (radius + 0.6f), cz = z * (radius + 0.6f);
            RenderUtil.streak(bb, m, cx, cy, cz, t1, s, t2, s, 1.0f, 0.75f, 0.35f, 0.8f * night * flicker);
        }
    }

    // --------------------------------------------------------------- terreno

    private static float elevation(float x, float y, float z) {
        float base = fbm(x * 1.9f + 5f, y * 1.9f, z * 1.9f + 1f, 7);
        // cordilleras: ruido "de crestas"
        float ridge = 1f - Math.abs(fbm(x * 4.2f - 2f, y * 4.2f + 6f, z * 4.2f, 4) * 2f - 1f);
        ridge = ridge * ridge;
        return base + Math.max(0f, base - 0.55f) * ridge * 0.9f;
    }

    /** Color y si es agua (r, g, b, agua). Incluye sombreado de relieve según la dirección del sol. */
    private static float[] surfaceColor(float x, float y, float z, Vector3f sun) {
        float e = elevation(x, y, z);
        float lat = Math.abs(y);
        float moist = fbm(x * 2.7f - 3f, y * 2.7f + 8f, z * 2.7f, 5);
        float detail = fbm(x * 26f + 1f, y * 26f - 3f, z * 26f + 5f, 3);
        float temp = 1f - lat * 1.15f - Math.max(0f, e - 0.55f) * 1.6f + (fbm(x * 5, y * 5 + 2, z * 5, 2) - 0.5f) * 0.15f;
        float r, g, b, wat = 0f;

        if (e < 0.52f) {
            // océano: profundo → plataforma → costa turquesa
            float depth = smoothstep(0.28f, 0.52f, e);
            float coast = smoothstep(0.495f, 0.52f, e);
            r = lerp(0.008f, 0.03f, depth); g = lerp(0.035f, 0.18f, depth); b = lerp(0.14f, 0.40f, depth);
            r = lerp(r, 0.08f, coast); g = lerp(g, 0.42f, coast); b = lerp(b, 0.50f, coast);
            if (temp < 0.08f) { r = 0.82f; g = 0.88f; b = 0.94f; } // hielo marino
            else wat = 1f;
            float v = 0.94f + 0.12f * detail;
            return new float[]{r * v, g * v, b * v, wat};
        }
        // lagos interiores
        float lake = fbm(x * 11f + 7f, y * 11f, z * 11f - 2f, 3);
        if (lake > 0.72f && e < 0.6f && temp > 0.15f) return new float[]{0.04f, 0.2f, 0.35f, 1f};

        if (temp < 0.08f) { r = 0.9f; g = 0.93f; b = 0.97f; }                  // casquete polar / glaciar
        else if (e < 0.53f) { r = 0.78f; g = 0.71f; b = 0.52f; }               // playa
        else if (e > 0.78f || (e > 0.68f && temp < 0.35f)) { r = 0.93f; g = 0.94f; b = 0.97f; } // nieve en cumbres
        else if (e > 0.64f) { r = 0.40f; g = 0.36f; b = 0.32f; }               // roca de montaña
        else if (temp < 0.22f) { r = 0.47f; g = 0.46f; b = 0.38f; }            // tundra
        else if (temp < 0.38f) {                                               // bosque boreal
            r = 0.07f; g = 0.2f; b = 0.1f;
        } else if (moist < 0.42f && temp > 0.55f) {                            // desierto con cañones rojizos
            float canyon = 1f - Math.abs(fbm(x * 14f, y * 14f + 3f, z * 14f - 1f, 3) * 2f - 1f);
            r = lerp(0.78f, 0.58f, canyon * canyon); g = lerp(0.62f, 0.30f, canyon * canyon); b = lerp(0.38f, 0.16f, canyon * canyon);
        } else if (moist < 0.5f && temp > 0.5f) { r = 0.58f; g = 0.52f; b = 0.26f; } // sabana
        else if (moist > 0.56f && temp > 0.6f) { r = 0.05f; g = 0.26f; b = 0.07f; }  // selva
        else { r = 0.22f; g = 0.42f; b = 0.15f; }                                   // pradera / bosque templado

        // variación fina del terreno
        float v = 0.82f + 0.36f * detail;
        r *= v; g *= v; b *= v;
        // relieve: las laderas que miran al sol son más claras
        Vector3f n = new Vector3f(x, y, z);
        Vector3f ts = new Vector3f(sun).sub(new Vector3f(n).mul(n.dot(sun)));
        if (ts.lengthSquared() > 1e-6f) {
            ts.normalize();
            float eps = 0.006f;
            Vector3f p2 = new Vector3f(n).add(new Vector3f(ts).mul(eps)).normalize();
            float slope = (elevation(p2.x, p2.y, p2.z) - e) / eps;
            float relief = Math.max(0.55f, Math.min(1.5f, 1f + slope * 0.22f));
            r *= relief; g *= relief; b *= relief;
        }
        return new float[]{Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), 0f};
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
