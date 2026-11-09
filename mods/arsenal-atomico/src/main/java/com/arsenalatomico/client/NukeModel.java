package com.arsenalatomico.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * El misil del Protocolo Hongo (18 bloques, apunta hacia +Z): cuerpo blanco con una banda negra, la
 * sección de la ojiva con franjas de peligro, el símbolo nuclear pintado a los dos lados, la ojiva verde
 * oliva con punta oscura, cuatro aletas con puntas de peligro y la tobera.
 */
final class NukeModel {
    private NukeModel() {}

    static final float R = 0.82f;

    static void body(VertexConsumer v, Matrix4f m) {
        // tobera y faldón
        Shapes.tube(v, m, 0.62f, 0.78f, -9.6f, -9.0f, 18, 0.16f, 0.16f, 0.17f, 1);
        Shapes.tube(v, m, 0.9f, R, -9.0f, -8.2f, 18, 0.3f, 0.31f, 0.32f, 1);
        // cuerpo
        Shapes.tube(v, m, R, R, -8.2f, 2.4f, 20, 0.92f, 0.92f, 0.9f, 1);
        Shapes.tube(v, m, R + 0.015f, R + 0.015f, -6.4f, -5.7f, 20, 0.07f, 0.07f, 0.08f, 1);
        Shapes.tube(v, m, R + 0.015f, R + 0.015f, -1.9f, -1.7f, 20, 0.75f, 0.12f, 0.1f, 1);
        // franjas de peligro (amarillo y negro)
        for (int i = 0; i < 6; i++) {
            float z = 2.4f + i * 0.28f;
            boolean y = i % 2 == 0;
            Shapes.tube(v, m, R, R, z, z + 0.28f, 20, y ? 0.98f : 0.07f, y ? 0.8f : 0.07f, y ? 0.08f : 0.07f, 1);
        }
        // la ojiva: verde oliva, más angosta, y la punta oscura
        Shapes.tube(v, m, R, 0.62f, 4.08f, 5.6f, 20, 0.33f, 0.36f, 0.24f, 1);
        Shapes.tube(v, m, 0.62f, 0.34f, 5.6f, 7.6f, 20, 0.33f, 0.36f, 0.24f, 1);
        Shapes.tube(v, m, 0.34f, 0.0f, 7.6f, 9.0f, 16, 0.12f, 0.12f, 0.13f, 1);
        // aletas con punta amarilla
        for (int i = 0; i < 4; i++) {
            Matrix4f fm = new Matrix4f(m).rotateZ((float) Math.toRadians(45 + i * 90));
            Shapes.box(v, fm, -0.06f, R - 0.05f, -9.0f, 0.06f, R + 0.95f, -6.8f, 0.85f, 0.85f, 0.83f);
            Shapes.box(v, fm, -0.065f, R + 0.65f, -9.0f, 0.065f, R + 0.95f, -8.2f, 0.98f, 0.8f, 0.08f);
        }
        // el símbolo nuclear a los dos lados del cuerpo
        for (int side = 0; side < 2; side++) {
            Matrix4f dm = new Matrix4f(m).rotateZ((float) Math.PI * side);
            trefoil(v, dm, R + 0.012f, 0f, 0.6f, 0.62f);
        }
    }

    /**
     * El símbolo de radiación en el plano X = x (mirando hacia +X), centrado en (y, z) con radio «size»:
     * círculo amarillo, tres aspas negras de 60° y el punto del centro.
     */
    static void trefoil(VertexConsumer v, Matrix4f m, float x, float cy, float cz, float size) {
        disc(v, m, x, cy, cz, 0, size, 0, (float) Math.PI * 2, 20, 0.98f, 0.82f, 0.1f);
        for (int i = 0; i < 3; i++) {
            float a0 = (float) Math.toRadians(90 + i * 120 - 30), a1 = (float) Math.toRadians(90 + i * 120 + 30);
            disc(v, m, x + 0.004f, cy, cz, size * 0.22f, size * 0.86f, a0, a1, 6, 0.06f, 0.06f, 0.06f);
        }
        disc(v, m, x + 0.004f, cy, cz, 0, size * 0.14f, 0, (float) Math.PI * 2, 12, 0.06f, 0.06f, 0.06f);
    }

    /** Pedazo de anillo plano (de r0 a r1, entre los ángulos a0 y a1) en el plano X = x. */
    private static void disc(VertexConsumer v, Matrix4f m, float x, float cy, float cz, float r0, float r1, float a0, float a1,
                             int seg, float r, float g, float b) {
        for (int i = 0; i < seg; i++) {
            float t0 = a0 + (a1 - a0) * i / seg, t1 = a0 + (a1 - a0) * (i + 1) / seg;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            Shapes.quad(v, m, x, cy + s0 * r0, cz + c0 * r0, x, cy + s0 * r1, cz + c0 * r1,
                    x, cy + s1 * r1, cz + c1 * r1, x, cy + s1 * r0, cz + c1 * r0, r, g, b, 1);
        }
    }

    /** El fuego del motor (hacia -Z desde la tobera), que crece al despegar. */
    static void flame(VertexConsumer solid, VertexConsumer glow, Matrix4f m, float size, float t) {
        float flick = 0.86f + 0.09f * (float) Math.sin(t * 2.1f) + 0.05f * (float) Math.sin(t * 6.7f);
        float k = size * flick, z0 = -9.6f;
        Shapes.tube(solid, m, 0.55f * size, 0.12f, z0, z0 - 8f * k, 16, 1f, 0.97f, 0.88f, 1f);
        Shapes.tube(solid, m, 0.82f * size, 0.2f, z0 + 0.05f, z0 - 15f * k, 16, 1f, 0.68f, 0.2f, 0.8f);
        Shapes.tube(solid, m, 1.2f * size, 0f, z0 + 0.1f, z0 - 23f * k, 16, 1f, 0.38f, 0.07f, 0.42f);
        Shapes.tube(glow, m, 2.4f * size, 0f, z0 + 0.3f, z0 - 12f * k, 16, 1f, 0.55f, 0.15f, 0.6f);
        Shapes.tube(glow, m, 3.6f * size, 1f * size, z0 + 0.8f, z0 - 2.5f, 16, 1f, 0.6f, 0.2f, 0.35f);
        // diamantes de choque dentro de la llama (como en los motores de verdad)
        for (int i = 1; i <= 4; i++) {
            float z = z0 - i * 2.6f * k;
            Shapes.tube(glow, m, 0.42f * size, 0.05f, z + 0.6f, z - 0.6f, 12, 1f, 0.95f, 0.8f, 0.55f / i);
        }
    }
}
