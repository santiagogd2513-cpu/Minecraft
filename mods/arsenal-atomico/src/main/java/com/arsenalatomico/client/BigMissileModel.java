package com.arsenalatomico.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * El misil del Protocolo Sol Negro: 28 bloques (apunta hacia +Z, de z = -14 a 14), más imponente que el
 * del Hongo. Dos etapas negras con bandas doradas, el anillo de unión con sus rejillas, cuatro toberas,
 * cuatro aletas grandes, el emblema del sol negro (disco negro con su corona dorada) a los lados, la
 * sección de la ojiva con franjas de peligro y el símbolo nuclear, y la ojiva blanca con punta roja.
 */
final class BigMissileModel {
    private BigMissileModel() {}

    static final float R = 1.6f, HALF = 14f;
    private static final float[] BLACK = {0.09f, 0.09f, 0.1f}, GOLD = {0.92f, 0.7f, 0.18f}, STEEL = {0.45f, 0.46f, 0.48f};

    static void body(VertexConsumer v, Matrix4f m) {
        // cuatro toberas y el faldón
        for (int i = 0; i < 4; i++) {
            double ang = Math.PI / 4 + i * Math.PI / 2;
            Matrix4f nm = new Matrix4f(m).translate((float) Math.cos(ang) * 0.75f, (float) Math.sin(ang) * 0.75f, 0);
            Shapes.tube(v, nm, 0.42f, 0.62f, -14.4f, -13.4f, 14, 0.18f, 0.17f, 0.17f, 1);
            Shapes.tube(v, nm, 0.62f, 0.66f, -14.45f, -14.35f, 14, 0.55f, 0.35f, 0.15f, 1);
        }
        Shapes.tube(v, m, 1.75f, R, -13.4f, -12.4f, 24, STEEL[0], STEEL[1], STEEL[2], 1);
        // primera etapa
        Shapes.tube(v, m, R, R, -12.4f, -1.6f, 28, BLACK[0], BLACK[1], BLACK[2], 1);
        for (float z : new float[]{-11.6f, -7.2f, -2.6f}) band(v, m, z, 0.35f, GOLD);
        // anillo entre etapas con rejillas
        Shapes.tube(v, m, R + 0.06f, R + 0.06f, -1.6f, -0.4f, 28, 0.2f, 0.21f, 0.22f, 1);
        for (int i = 0; i < 12; i++) {
            Matrix4f gm = new Matrix4f(m).rotateZ((float) (Math.PI * 2 * i / 12));
            Shapes.box(v, gm, -0.12f, R + 0.06f, -1.4f, 0.12f, R + 0.1f, -0.6f, 0.05f, 0.05f, 0.05f);
        }
        // segunda etapa, un poco más angosta
        Shapes.tube(v, m, R - 0.15f, R - 0.15f, -0.4f, 7.2f, 28, BLACK[0] * 1.2f, BLACK[1] * 1.2f, BLACK[2] * 1.2f, 1);
        band(v, m, 0.6f, 0.25f, GOLD);
        band(v, m, 6.4f, 0.25f, GOLD);
        // sección de la ojiva: franjas de peligro
        for (int i = 0; i < 8; i++) {
            float z = 7.2f + i * 0.3f;
            boolean y = i % 2 == 0;
            Shapes.tube(v, m, R - 0.15f, R - 0.15f, z, z + 0.3f, 28, y ? 0.98f : 0.07f, y ? 0.8f : 0.07f, y ? 0.08f : 0.07f, 1);
        }
        // la ojiva: blanca, con punta roja
        Shapes.tube(v, m, R - 0.15f, 1.2f, 9.6f, 11.4f, 28, 0.9f, 0.9f, 0.88f, 1);
        Shapes.tube(v, m, 1.2f, 0.55f, 11.4f, 13.2f, 28, 0.9f, 0.9f, 0.88f, 1);
        Shapes.tube(v, m, 0.55f, 0f, 13.2f, 14.6f, 20, 0.78f, 0.1f, 0.08f, 1);
        // aletas grandes con borde dorado
        for (int i = 0; i < 4; i++) {
            Matrix4f fm = new Matrix4f(m).rotateZ((float) Math.toRadians(45 + i * 90));
            Shapes.box(v, fm, -0.1f, R - 0.05f, -13.2f, 0.1f, R + 1.9f, -9.4f, 0.14f, 0.14f, 0.15f);
            Shapes.box(v, fm, -0.11f, R + 1.6f, -13.2f, 0.11f, R + 1.9f, -10.6f, GOLD[0], GOLD[1], GOLD[2]);
            // aletitas de la segunda etapa
            Shapes.box(v, fm, -0.06f, R - 0.2f, 4.6f, 0.06f, R + 0.45f, 6.2f, 0.14f, 0.14f, 0.15f);
        }
        // el emblema del sol negro y el símbolo nuclear, a los dos lados
        for (int side = 0; side < 2; side++) {
            Matrix4f dm = new Matrix4f(m).rotateZ((float) Math.PI * side);
            sun(v, dm, R + 0.015f, -6.8f, 1.15f);
            NukeModel.trefoil(v, dm, R - 0.13f, 0f, 8.4f, 0.55f);
        }
    }

    private static void band(VertexConsumer v, Matrix4f m, float z, float w, float[] c) {
        Shapes.tube(v, m, R + 0.012f, R + 0.012f, z, z + w, 28, c[0], c[1], c[2], 1);
    }

    /** El sol negro: un disco negro con doce rayos dorados alrededor, pintado en el plano X = x. */
    private static void sun(VertexConsumer v, Matrix4f m, float x, float cz, float size) {
        int rays = 12;
        for (int i = 0; i < rays; i++) {
            double a0 = Math.PI * 2 * (i + 0.15) / rays, a1 = Math.PI * 2 * (i + 0.85) / rays, am = Math.PI * 2 * (i + 0.5) / rays;
            float r0 = size * 0.55f, r1 = size;
            Shapes.quad(v, m, x, (float) Math.sin(a0) * r0, cz + (float) Math.cos(a0) * r0,
                    x, (float) Math.sin(am) * r1, cz + (float) Math.cos(am) * r1,
                    x, (float) Math.sin(am) * r1, cz + (float) Math.cos(am) * r1,
                    x, (float) Math.sin(a1) * r0, cz + (float) Math.cos(a1) * r0, GOLD[0], GOLD[1], GOLD[2], 1);
        }
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float r = size * 0.58f;
            Shapes.quad(v, m, x + 0.002f, 0, cz, x + 0.002f, (float) Math.sin(a0) * r, cz + (float) Math.cos(a0) * r,
                    x + 0.002f, (float) Math.sin(a1) * r, cz + (float) Math.cos(a1) * r, x + 0.002f, 0, cz, GOLD[0], GOLD[1], GOLD[2], 1);
            float ri = size * 0.48f;
            Shapes.quad(v, m, x + 0.004f, 0, cz, x + 0.004f, (float) Math.sin(a0) * ri, cz + (float) Math.cos(a0) * ri,
                    x + 0.004f, (float) Math.sin(a1) * ri, cz + (float) Math.cos(a1) * ri, x + 0.004f, 0, cz, 0.02f, 0.02f, 0.02f, 1);
        }
    }

    /** La llamarada: la del Hongo, más grande y ancha, saliendo de las cuatro toberas. */
    static void flame(VertexConsumer solid, VertexConsumer glow, Matrix4f m, float size, float t) {
        float k = 1.7f;
        Matrix4f fm = new Matrix4f(m).translate(0, 0, -14.4f + 9.6f * k).scale(k);
        NukeModel.flame(solid, glow, fm, size, t);
    }
}
