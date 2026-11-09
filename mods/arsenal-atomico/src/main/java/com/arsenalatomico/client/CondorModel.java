package com.arsenalatomico.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * El misil Cóndor (26,6 bloques, apunta hacia +Z): primera etapa oscura con banda blanca, segunda
 * etapa blanca con franjas negras de referencia, cofia blanca y punta oscura. La ojiva sola es un
 * cono con escudo térmico.
 */
final class CondorModel {
    private CondorModel() {}

    static final float R = 0.95f;

    /** Primera etapa (z de -13,3 a -2,5). */
    static void stage1(VertexConsumer v, Matrix4f m) {
        Shapes.tube(v, m, R, R, -13.3f, -2.5f, 18, 0.13f, 0.15f, 0.14f, 1);
        Shapes.tube(v, m, R + 0.02f, R + 0.02f, -6.4f, -5.2f, 18, 0.92f, 0.92f, 0.9f, 1);
        Shapes.tube(v, m, R + 0.03f, R + 0.03f, -12.6f, -12.2f, 18, 0.75f, 0.12f, 0.1f, 1);
        Shapes.tube(v, m, 0.7f, 0.85f, -14.1f, -13.3f, 14, 0.18f, 0.18f, 0.2f, 1);     // toberas
    }

    /** Lo de arriba: anillo entre etapas, segunda etapa, cofia y punta (z de -2,5 a 13,3). */
    static void upper(VertexConsumer v, Matrix4f m) {
        Shapes.tube(v, m, R, R, -2.5f, -1.7f, 18, 0.25f, 0.26f, 0.28f, 1);
        Shapes.tube(v, m, R, R, -1.7f, 6.0f, 18, 0.9f, 0.91f, 0.9f, 1);
        // franjas negras de referencia (para ver el giro, como en los cohetes de verdad)
        for (int i = 0; i < 4; i++) {
            float z = -1.2f + i * 1.9f;
            Shapes.tube(v, m, R + 0.015f, R + 0.015f, z, z + 0.35f, 18, 0.08f, 0.08f, 0.09f, 1);
        }
        Shapes.tube(v, m, R + 0.02f, R + 0.02f, 5.0f, 5.5f, 18, 0.75f, 0.12f, 0.1f, 1);
        Shapes.tube(v, m, R, 0.78f, 6.0f, 9.5f, 18, 0.93f, 0.93f, 0.92f, 1);
        Shapes.tube(v, m, 0.78f, 0.25f, 9.5f, 12.6f, 18, 0.93f, 0.93f, 0.92f, 1);
        Shapes.tube(v, m, 0.25f, 0f, 12.6f, 13.3f, 12, 0.15f, 0.15f, 0.16f, 1);
    }

    /** La ojiva sola, centrada en el origen. */
    static void warhead(VertexConsumer v, Matrix4f m) {
        Shapes.tube(v, m, 0.85f, 0.75f, -1.6f, -1.3f, 16, 0.1f, 0.09f, 0.08f, 1);       // escudo térmico
        Shapes.tube(v, m, 0.75f, 0.12f, -1.3f, 2.0f, 16, 0.22f, 0.2f, 0.19f, 1);
        Shapes.tube(v, m, 0.12f, 0f, 2.0f, 2.3f, 10, 0.1f, 0.1f, 0.1f, 1);
    }

    /** Fuego de la primera etapa (grande) o de la segunda (más chico), saliendo hacia -Z desde z0. */
    static void flame(VertexConsumer solid, VertexConsumer glow, Matrix4f m, float z0, float size, float t) {
        float flick = 0.86f + 0.09f * (float) Math.sin(t * 2.1f) + 0.05f * (float) Math.sin(t * 6.7f);
        float k = size * flick;
        Shapes.tube(solid, m, 0.8f * size, 0.15f, z0, z0 - 9f * k, 16, 1f, 0.97f, 0.86f, 1f);
        Shapes.tube(solid, m, 1.15f * size, 0.25f, z0 + 0.05f, z0 - 17f * k, 16, 1f, 0.66f, 0.18f, 0.8f);
        Shapes.tube(solid, m, 1.6f * size, 0f, z0 + 0.1f, z0 - 26f * k, 16, 1f, 0.36f, 0.06f, 0.42f);
        Shapes.tube(glow, m, 3f * size, 0f, z0 + 0.3f, z0 - 14f * k, 16, 1f, 0.55f, 0.15f, 0.6f);
        Shapes.tube(glow, m, 4.5f * size, 1.2f * size, z0 + 0.8f, z0 - 3f, 16, 1f, 0.6f, 0.2f, 0.35f);
    }

    /** Plasma de la reentrada: funda blanca y naranja en la punta y una cola larga que brilla. */
    static void plasma(VertexConsumer glow, Matrix4f m, float t) {
        float f = 0.9f + 0.1f * (float) Math.sin(t * 3.3f);
        Shapes.tube(glow, m, 1.25f * f, 0.2f, 2.6f, -6f, 16, 1f, 0.92f, 0.75f, 0.85f);
        Shapes.tube(glow, m, 1.9f * f, 0.1f, 3.0f, -14f, 16, 1f, 0.55f, 0.18f, 0.6f);
        Shapes.tube(glow, m, 1.0f, 0f, -1.6f, -48f * f, 14, 1f, 0.5f, 0.15f, 0.55f);
        Shapes.tube(glow, m, 2.6f * f, 0f, 3.2f, -26f * f, 16, 0.95f, 0.35f, 0.75f, 0.28f);   // halo rosado
    }
}
