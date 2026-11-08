package com.arsenalatomico.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Cajas y cilindros de color para dibujar el camión y los misiles (sin texturas). */
final class Shapes {
    private Shapes() {}

    static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b, float a) {
        v.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
        v.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        v.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
        v.vertex(m, x3, y3, z3).color(r, g, b, a).endVertex();
    }

    /** Caja con sombreado simple por cara. */
    static void box(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b) {
        quad(v, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, 1);
        quad(v, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r * .35f, g * .35f, b * .35f, 1);
        quad(v, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r * .7f, g * .7f, b * .7f, 1);
        quad(v, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r * .8f, g * .8f, b * .8f, 1);
        quad(v, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * .55f, g * .55f, b * .55f, 1);
        quad(v, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r * .9f, g * .9f, b * .9f, 1);
    }

    /** Cilindro a lo largo de Z, de z0 a z1, con radios r0 → r1 (r1 = 0 hace un cono). */
    static void tube(VertexConsumer v, Matrix4f m, float r0, float r1, float z0, float z1, int seg, float r, float g, float b, float a) {
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float shade = 0.55f + 0.45f * (float) Math.max(0, Math.sin((a0 + a1) / 2 + 0.6));
            quad(v, m, c0 * r0, s0 * r0, z0, c1 * r0, s1 * r0, z0, c1 * r1, s1 * r1, z1, c0 * r1, s0 * r1, z1,
                    r * shade, g * shade, b * shade, a);
        }
    }
}
