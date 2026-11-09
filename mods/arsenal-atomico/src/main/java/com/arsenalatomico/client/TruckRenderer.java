package com.arsenalatomico.client;

import com.arsenalatomico.entity.TruckEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/**
 * El camión militar: cabina verde oliva con la bandera de Rusia en las puertas y otra ondeando en el
 * techo, caja con lona, seis ruedas y, al volante, un conductor con capucha y gafas oscuras al que
 * nunca se le ve la cara… solo, de vez en cuando, dos puntitos blancos donde van los ojos.
 * <p>
 * Medidas en bloques; el camión mira hacia +Z y la izquierda (el lado del conductor) es +X.
 */
public class TruckRenderer extends EntityRenderer<TruckEntity> {
    private static final float[] OLIVE = {0.33f, 0.37f, 0.21f}, OLIVE_DARK = {0.25f, 0.28f, 0.16f}, CANVAS = {0.38f, 0.39f, 0.25f},
            DARK = {0.1f, 0.1f, 0.09f}, TIRE = {0.07f, 0.07f, 0.07f}, STEEL = {0.42f, 0.43f, 0.42f};
    private float k = 1;

    public TruckRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 1.6f;
    }

    @Override
    public ResourceLocation getTextureLocation(TruckEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(TruckEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        var level = Minecraft.getInstance().level;
        float day = level != null ? level.getSkyDarken(partial) : 1;
        float lit = Math.max(LightTexture.block(light) / 15f, LightTexture.sky(light) / 15f * day);
        k = 0.28f + 0.72f * lit;
        float t = e.tickCount + partial;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
        Matrix4f m = ps.last().pose();
        VertexConsumer v = buf.getBuffer(RenderType.debugQuads());

        // chasis y guardafangos
        box(v, m, -1.05f, 0.5f, -3.3f, 1.05f, 0.85f, 3.35f, DARK);
        for (float z : new float[]{2.45f, -1.2f, -2.5f}) {
            box(v, m, -1.3f, 1.05f, z - 0.75f, -1.1f, 1.15f, z + 0.75f, OLIVE_DARK);
            box(v, m, 1.1f, 1.05f, z - 0.75f, 1.3f, 1.15f, z + 0.75f, OLIVE_DARK);
        }
        // ruedas (tres ejes)
        for (float z : new float[]{2.45f, -1.2f, -2.5f}) {
            for (int side = -1; side <= 1; side += 2) wheel(v, m, side * 1.05f, z, side, e.wheel() + partial * e.speed() * 1.8f);
        }
        cab(v, m, e.mission(), t);
        bed(v, m);
        flag(v, m, t, e.speed());
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    // ------------------------------------------------------------------ piezas

    private void wheel(VertexConsumer v, Matrix4f m, float x, float z, int side, float spin) {
        Matrix4f w = new Matrix4f(m).translate(x, 0.55f, z).rotateY((float) Math.PI / 2).rotateZ(spin * side);
        float in = side > 0 ? -0.05f : -0.4f, out = side > 0 ? 0.4f : 0.05f;
        Shapes.tube(v, w, 0.55f, 0.55f, in, out, 12, TIRE[0] * k, TIRE[1] * k, TIRE[2] * k, 1);
        float face = side > 0 ? out + 0.005f : in - 0.005f;
        disc(v, w, 0.55f, face, TIRE);
        disc(v, w, 0.3f, face + side * 0.004f, OLIVE_DARK);
        // la tapa con sus tuercas: se ve girar
        Shapes.quad(v, w, -0.06f, -0.26f, face + side * 0.008f, 0.06f, -0.26f, face + side * 0.008f,
                0.06f, 0.26f, face + side * 0.008f, -0.06f, 0.26f, face + side * 0.008f, STEEL[0] * k, STEEL[1] * k, STEEL[2] * k, 1);
    }

    private void disc(VertexConsumer v, Matrix4f m, float r, float z, float[] c) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            Shapes.quad(v, m, 0, 0, z, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, z,
                    (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, z, 0, 0, z, c[0] * k, c[1] * k, c[2] * k, 1);
        }
    }

    /** La cabina: abierta por dentro (con vidrios) para ver al conductor y para la toma del copiloto. */
    private void cab(VertexConsumer v, Matrix4f m, boolean mission, float t) {
        float z0 = 1.55f, z1 = 2.95f, top = 3.05f;
        // capó y parrilla
        box(v, m, -1.12f, 0.85f, z1, 1.12f, 1.95f, 3.45f, OLIVE);
        box(v, m, -0.85f, 1.0f, 3.45f, 0.85f, 1.85f, 3.5f, DARK);
        for (int i = 0; i < 5; i++) box(v, m, -0.8f, 1.08f + i * 0.15f, 3.5f, 0.8f, 1.13f + i * 0.15f, 3.52f, OLIVE_DARK);
        box(v, m, -1.2f, 0.75f, 3.45f, 1.2f, 0.95f, 3.62f, DARK);
        // faros (no se apagan con la oscuridad)
        for (int s = -1; s <= 1; s += 2) {
            Shapes.box(v, m, s * 0.95f - 0.15f, 1.45f, 3.45f, s * 0.95f + 0.15f, 1.75f, 3.53f, 1f, 0.95f, 0.75f);
        }
        // piso, pared de atrás y techo
        box(v, m, -1.12f, 0.85f, z0, 1.12f, 1.05f, z1, DARK);
        box(v, m, -1.12f, 1.05f, z0, 1.12f, top, z0 + 0.1f, OLIVE);
        box(v, m, -1.15f, top, z0, 1.15f, top + 0.12f, z1 + 0.05f, OLIVE);
        // puertas: abajo de lata, arriba vidrio; con la bandera de Rusia
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 1.12f, xo = s * 1.13f;
            box(v, m, Math.min(x, x - s * 0.06f), 1.05f, z0, Math.max(x, x - s * 0.06f), 2.1f, z1, OLIVE);
            glass(v, m, xo, 2.1f, z0 + 0.1f, xo, top, z1, true);
            float[][] stripes = {{1f, 1f, 1f}, {0.05f, 0.22f, 0.65f}, {0.85f, 0.1f, 0.12f}};
            for (int i = 0; i < 3; i++) {
                float y1 = 1.95f - i * 0.15f, y0 = y1 - 0.15f;
                float fx = s * 1.135f;
                Shapes.quad(v, m, fx, y0, 2.0f, fx, y1, 2.0f, fx, y1, 2.65f, fx, y0, 2.65f,
                        stripes[i][0] * k, stripes[i][1] * k, stripes[i][2] * k, 1);
            }
            // espejo
            box(v, m, s * 1.15f, 2.3f, 2.85f, s * 1.45f, 2.75f, 2.9f, DARK);
        }
        // parabrisas, tablero y volante
        glass(v, m, -1.12f, 1.95f, z1, 1.12f, top, z1, false);
        box(v, m, -1.05f, 1.6f, z1 - 0.35f, 1.05f, 1.95f, z1, DARK);
        Matrix4f sw = new Matrix4f(m).translate(0.62f, 2.05f, z1 - 0.5f).rotateX(-0.9f);
        ring(v, sw, 0.22f, DARK);
        // asientos
        for (int s = -1; s <= 1; s += 2) {
            box(v, m, s * 0.62f - 0.3f, 1.05f, z0 + 0.15f, s * 0.62f + 0.3f, 1.45f, z0 + 0.65f, DARK);
            box(v, m, s * 0.62f - 0.3f, 1.45f, z0 + 0.1f, s * 0.62f + 0.3f, 2.4f, z0 + 0.25f, DARK);
        }
        if (mission) driver(v, m, t);
    }

    /** El conductor: capucha, gafas… y, de vez en cuando, dos puntitos blancos. */
    private void driver(VertexConsumer v, Matrix4f m, float t) {
        float x = 0.62f, z = 1.95f;
        float[] coat = {0.09f, 0.09f, 0.1f}, hood = {0.06f, 0.06f, 0.065f};
        box(v, m, x - 0.25f, 1.45f, z - 0.15f, x + 0.25f, 2.25f, z + 0.15f, coat);
        // brazos al volante
        box(v, m, x + 0.15f, 1.85f, z, x + 0.28f, 2.0f, z + 0.5f, coat);
        box(v, m, x - 0.28f, 1.85f, z, x - 0.15f, 2.0f, z + 0.5f, coat);
        // la cabeza con capucha: la cara queda en sombra total
        box(v, m, x - 0.21f, 2.25f, z - 0.2f, x + 0.21f, 2.72f, z + 0.2f, hood);
        Shapes.quad(v, m, x - 0.16f, 2.3f, z + 0.205f, x + 0.16f, 2.3f, z + 0.205f, x + 0.16f, 2.62f, z + 0.205f, x - 0.16f, 2.62f, z + 0.205f,
                0.01f, 0.01f, 0.01f, 1);
        // las gafas oscuras
        Shapes.quad(v, m, x - 0.15f, 2.47f, z + 0.21f, x + 0.15f, 2.47f, z + 0.21f, x + 0.15f, 2.54f, z + 0.21f, x - 0.15f, 2.54f, z + 0.21f,
                0.02f, 0.02f, 0.03f, 1);
        // los ojos: casi siempre apagados; brillan un momento cada tanto
        float glow = (float) Math.pow(Math.max(0, Math.sin(t * 0.045)), 24);
        if (glow > 0.02f) {
            for (int s = -1; s <= 1; s += 2) {
                float ex = x + s * 0.07f;
                Shapes.quad(v, m, ex - 0.025f, 2.49f, z + 0.215f, ex + 0.025f, 2.49f, z + 0.215f, ex + 0.025f, 2.52f, z + 0.215f, ex - 0.025f, 2.52f, z + 0.215f,
                        1, 1, 1, glow);
            }
        }
    }

    /** La caja de carga con su lona y arcos. */
    private void bed(VertexConsumer v, Matrix4f m) {
        float z0 = -3.35f, z1 = 1.45f;
        box(v, m, -1.15f, 0.85f, z0, 1.15f, 1.05f, z1, OLIVE_DARK);
        box(v, m, -1.15f, 1.05f, z0, -1.05f, 1.5f, z1, OLIVE);
        box(v, m, 1.05f, 1.05f, z0, 1.15f, 1.5f, z1, OLIVE);
        // la lona: los lados, el techo y la tapa de atrás medio abierta
        box(v, m, -1.17f, 1.5f, z0, -1.12f, 2.75f, z1, CANVAS);
        box(v, m, 1.12f, 1.5f, z0, 1.17f, 2.75f, z1, CANVAS);
        box(v, m, -1.17f, 2.75f, z0, 1.17f, 2.95f, z1, CANVAS);
        box(v, m, -1.12f, 1.05f, z1 - 0.06f, 1.12f, 2.75f, z1, CANVAS);
        box(v, m, -1.12f, 2.1f, z0, 1.12f, 2.75f, z0 + 0.05f, CANVAS);
        box(v, m, -1.1f, 1.05f, z0 + 0.06f, 1.1f, 2.1f, z0 + 0.07f, DARK);
        // los arcos que sostienen la lona
        for (float z = z0 + 0.6f; z < z1; z += 1.2f) {
            box(v, m, -1.19f, 1.5f, z - 0.05f, 1.19f, 2.97f, z + 0.05f, OLIVE_DARK);
        }
        // placa de atrás
        box(v, m, -0.3f, 0.6f, z0 - 0.04f, 0.3f, 0.8f, z0, new float[]{0.85f, 0.85f, 0.8f});
    }

    /** La banderita de Rusia en un palito detrás de la cabina, ondeando más rápido cuanto más corre. */
    private void flag(VertexConsumer v, Matrix4f m, float t, float speed) {
        float px = 0.95f, pz = 1.5f, y0 = 3.15f, y1 = 4.05f;
        box(v, m, px - 0.03f, y0 - 0.1f, pz - 0.03f, px + 0.03f, y1 + 0.05f, pz + 0.03f, STEEL);
        float[][] stripes = {{1f, 1f, 1f}, {0.05f, 0.22f, 0.65f}, {0.85f, 0.1f, 0.12f}};
        float len = 0.9f, h = 0.6f, top = y1;
        int seg = 8;
        float wave = 1.5f + Math.abs(speed) * 14f;
        for (int i = 0; i < seg; i++) {
            float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
            float za = pz - u0 * len, zb = pz - u1 * len;
            float xa = px + (float) Math.sin(t * 0.35f * wave - u0 * 6) * 0.12f * u0;
            float xb = px + (float) Math.sin(t * 0.35f * wave - u1 * 6) * 0.12f * u1;
            for (int s = 0; s < 3; s++) {
                float ya = top - s * h / 3, yb = ya - h / 3;
                Shapes.quad(v, m, xa, yb, za, xa, ya, za, xb, ya, zb, xb, yb, zb, stripes[s][0] * k, stripes[s][1] * k, stripes[s][2] * k, 1);
            }
        }
    }

    // ------------------------------------------------------------------ ayudas

    private void box(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float[] c) {
        Shapes.box(v, m, x0, y0, z0, x1, y1, z1, c[0] * k, c[1] * k, c[2] * k);
    }

    /** Vidrio: oscuro y medio transparente (desde adentro se ve la calle). */
    private void glass(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, boolean side) {
        float r = 0.2f * k, g = 0.3f * k, b = 0.34f * k;
        if (side) Shapes.quad(v, m, x0, y0, z0, x0, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, 0.35f);
        else Shapes.quad(v, m, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, r, g, b, 0.35f);
    }

    private void ring(VertexConsumer v, Matrix4f m, float r, float[] c) {
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            Shapes.quad(v, m, x0 * 0.8f, y0 * 0.8f, 0, x1 * 0.8f, y1 * 0.8f, 0, x1, y1, 0, x0, y0, 0, c[0] * k, c[1] * k, c[2] * k, 1);
        }
    }
}
