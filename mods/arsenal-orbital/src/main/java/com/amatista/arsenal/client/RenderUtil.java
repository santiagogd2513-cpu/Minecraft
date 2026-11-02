package com.amatista.arsenal.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Ayudas para dibujar quads de color con una matriz. */
final class RenderUtil {
    private RenderUtil() {}

    static void quad(BufferBuilder bb, Matrix4f m,
                     float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     float r, float g, float b, float a) {
        bb.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        bb.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
        bb.vertex(m, x3, y3, z3).color(r, g, b, a).endVertex();
    }

    /** Quad que siempre mira a la cámara (right/up son los ejes de la cámara). */
    static void billboard(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz,
                          Vector3f right, Vector3f up, float size, float r, float g, float b, float a) {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        quad(bb, m,
                cx - rx - ux, cy - ry - uy, cz - rz - uz,
                cx + rx - ux, cy + ry - uy, cz + rz - uz,
                cx + rx + ux, cy + ry + uy, cz + rz + uz,
                cx - rx + ux, cy - ry + uy, cz - rz + uz,
                r, g, b, a);
    }

    /** Quad alargado (para rayos y destellos) en la dirección dada. */
    static void streak(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz,
                       Vector3f dirA, float lenA, Vector3f dirB, float lenB, float r, float g, float b, float a) {
        float ax = dirA.x * lenA, ay = dirA.y * lenA, az = dirA.z * lenA;
        float bx = dirB.x * lenB, by = dirB.y * lenB, bz = dirB.z * lenB;
        quad(bb, m,
                cx - ax - bx, cy - ay - by, cz - az - bz,
                cx + ax - bx, cy + ay - by, cz + az - bz,
                cx + ax + bx, cy + ay + by, cz + az + bz,
                cx - ax + bx, cy - ay + by, cz - az + bz,
                r, g, b, a);
    }

    /** Caja con sombreado simple por cara (arriba claro, abajo oscuro). */
    static void box(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                    float r, float g, float b, float a) {
        // arriba
        quad(bb, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        // abajo
        quad(bb, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r * 0.35f, g * 0.35f, b * 0.35f, a);
        // norte / sur
        quad(bb, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r * 0.7f, g * 0.7f, b * 0.7f, a);
        quad(bb, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r * 0.8f, g * 0.8f, b * 0.8f, a);
        // oeste / este
        quad(bb, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * 0.55f, g * 0.55f, b * 0.55f, a);
        quad(bb, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r * 0.9f, g * 0.9f, b * 0.9f, a);
    }

    /** Cilindro a lo largo de Y (de y0 a y1). */
    static void cylinder(BufferBuilder bb, Matrix4f m, float radius, float y0, float y1, int segments,
                         float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float shade = 0.55f + 0.45f * (float) Math.max(0, Math.cos((a0 + a1) / 2 - 0.8));
            quad(bb, m, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, r * shade, g * shade, b * shade, a);
        }
    }

    /** Anillo plano en el plano XZ (de radio interior a exterior) a la altura y. */
    static void ring(BufferBuilder bb, Matrix4f m, float inner, float outer, float y, int segments,
                     float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(bb, m, c0 * inner, y, s0 * inner, c0 * outer, y, s0 * outer,
                    c1 * outer, y, s1 * outer, c1 * inner, y, s1 * inner, r, g, b, a);
        }
    }
}
