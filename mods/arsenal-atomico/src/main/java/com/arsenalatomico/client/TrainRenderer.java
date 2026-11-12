package com.arsenalatomico.client;

import com.arsenalatomico.entity.TrainCarEntity;
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
 * El Tren Lanzamisiles: locomotora diésel, vagón de mando y vagón lanzador, verde oliva, con bogies de seis ruedas.
 * Medidas en bloques; el coche mira hacia +Z, y el 0 de la altura es la cabeza del riel.
 */
public class TrainRenderer extends EntityRenderer<TrainCarEntity> {
    private static final float[] OLIVE = {0.33f, 0.37f, 0.21f}, OLIVE_DARK = {0.24f, 0.27f, 0.15f}, DARK = {0.1f, 0.1f, 0.09f},
            STEEL = {0.42f, 0.43f, 0.42f}, GLASS = {0.16f, 0.24f, 0.30f}, RED = {0.75f, 0.12f, 0.1f}, YELLOW = {0.9f, 0.75f, 0.2f},
            WHITE = {0.92f, 0.9f, 0.82f};
    private float k = 1;

    public TrainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 2.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(TrainCarEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(TrainCarEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;                    // mide más de 20 m: el centro puede quedar fuera de la pantalla con el coche a la vista
    }

    @Override
    public void render(TrainCarEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        var level = Minecraft.getInstance().level;
        float day = level != null ? level.getSkyDarken(partial) : 1;
        float lit = Math.max(LightTexture.block(light) / 15f, LightTexture.sky(light) / 15f * day);
        k = 0.30f + 0.70f * lit;
        TrainCarEntity.Variant var = e.variant();
        float half = (float) var.length / 2f, wb = (float) var.wheelbase / 2f;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(e.getPitchVisual(partial)));
        Matrix4f m = ps.last().pose();
        VertexConsumer v = buf.getBuffer(RenderType.debugQuads());

        // chasis y bogies
        box(v, m, -1.15f, 0.75f, -half, 1.15f, 1.05f, half, DARK);
        for (float z : new float[]{wb, -wb}) bogie(v, m, z, e.getWheelAngle(partial));
        // topes y enganche
        box(v, m, -0.6f, 0.85f, half, 0.6f, 1.0f, half + 0.25f, STEEL);
        box(v, m, -0.6f, 0.85f, -half - 0.25f, 0.6f, 1.0f, -half, STEEL);

        switch (var) {
            case LOCO -> loco(v, m, half);
            case COMMAND -> command(v, m, half);
            default -> launcher(v, m, half, e.cargoSmall(), e.cargoMedium());
        }
        ps.popPose();
    }

    private void bogie(VertexConsumer v, Matrix4f m, float z, float spin) {
        box(v, m, -1.0f, 0.5f, z - 1.9f, 1.0f, 0.75f, z + 1.9f, DARK);
        for (float dz : new float[]{-1.4f, 0f, 1.4f}) {
            for (int side : new int[]{-1, 1}) {
                float x = side * 1.12f;
                box(v, m, x - 0.1f, 0.05f, z + dz - 0.5f, x + 0.1f, 1.0f, z + dz + 0.5f, STEEL);
                float a = (float) Math.sin(spin + dz) * 0.3f;                    // la manivela da la vuelta
                box(v, m, x - 0.13f, 0.5f + a - 0.08f, z + dz - 0.08f, x + 0.13f, 0.5f + a + 0.08f, z + dz + 0.08f, WHITE);
            }
        }
    }

    private void loco(VertexConsumer v, Matrix4f m, float half) {
        // capó largo atrás, cabina adelante
        box(v, m, -1.25f, 1.05f, -half + 0.2f, 1.25f, 3.0f, half - 6.2f, OLIVE);
        box(v, m, -1.3f, 1.05f, half - 6.2f, 1.3f, 4.2f, half - 0.2f, OLIVE);
        box(v, m, -1.3f, 2.45f, -half + 0.2f, 1.3f, 2.6f, half - 0.2f, OLIVE_DARK);
        // ventanas
        box(v, m, -1.32f, 2.7f, half - 5.0f, -1.28f, 3.8f, half - 3.2f, GLASS);
        box(v, m, 1.28f, 2.7f, half - 5.0f, 1.32f, 3.8f, half - 3.2f, GLASS);
        box(v, m, -1.0f, 2.7f, half - 0.22f, 1.0f, 3.8f, half - 0.18f, GLASS);
        // techo, escape y faros
        box(v, m, -1.2f, 4.2f, half - 6.0f, 1.2f, 4.35f, half - 0.1f, OLIVE_DARK);
        box(v, m, -0.3f, 3.0f, -half + 2.0f, 0.3f, 3.6f, -half + 2.6f, DARK);
        box(v, m, -0.9f, 1.3f, half - 0.19f, -0.5f, 1.6f, half - 0.12f, YELLOW);
        box(v, m, 0.5f, 1.3f, half - 0.19f, 0.9f, 1.6f, half - 0.12f, YELLOW);
        // estrella roja en los costados
        box(v, m, -1.33f, 1.6f, half - 9.0f, -1.27f, 2.2f, half - 8.4f, RED);
        box(v, m, 1.27f, 1.6f, half - 9.0f, 1.33f, 2.2f, half - 8.4f, RED);
    }

    private void command(VertexConsumer v, Matrix4f m, float half) {
        box(v, m, -1.3f, 1.05f, -half + 0.2f, 1.3f, 3.9f, half - 0.2f, OLIVE);
        box(v, m, -1.3f, 2.8f, -half + 0.2f, 1.3f, 2.95f, half - 0.2f, OLIVE_DARK);
        for (float z = -half + 2.0f; z < half - 2.0f; z += 3.2f) {
            box(v, m, -1.33f, 2.9f, z, -1.28f, 3.6f, z + 1.5f, GLASS);
            box(v, m, 1.28f, 2.9f, z, 1.33f, 3.6f, z + 1.5f, GLASS);
        }
        box(v, m, -1.4f, 1.2f, 0.0f, -1.25f, 2.7f, 1.1f, OLIVE_DARK);            // puerta
        // techo con antena y plato de radar
        box(v, m, -1.2f, 3.9f, -half + 0.4f, 1.2f, 4.0f, half - 0.4f, OLIVE_DARK);
        box(v, m, -0.05f, 4.0f, -half + 2.0f, 0.05f, 5.8f, -half + 2.1f, STEEL);
        box(v, m, -0.8f, 4.0f, 1.0f, 0.8f, 4.25f, 2.6f, STEEL);
        box(v, m, -0.9f, 4.25f, 0.9f, 0.9f, 4.9f, 1.0f, STEEL);
        box(v, m, -0.05f, 4.25f, 1.7f, 0.05f, 4.8f, 1.8f, STEEL);
        box(v, m, -0.3f, 4.1f, half - 3.0f, 0.3f, 4.5f, half - 2.4f, RED);       // baliza
    }

    private void launcher(VertexConsumer v, Matrix4f m, float half, int small, int medium) {
        // plataforma plana con bordes bajos, y encima la cuna de los misiles
        box(v, m, -1.35f, 1.05f, -half + 0.2f, 1.35f, 1.5f, half - 0.2f, OLIVE);
        box(v, m, -1.35f, 1.5f, -half + 0.2f, -1.25f, 1.9f, half - 0.2f, OLIVE_DARK);
        box(v, m, 1.25f, 1.5f, -half + 0.2f, 1.35f, 1.9f, half - 0.2f, OLIVE_DARK);
        for (float z = -half + 2.5f; z < half - 2.0f; z += 4.0f) {
            box(v, m, -1.1f, 1.5f, z, 1.1f, 2.3f, z + 0.3f, STEEL);
        }
        float[] xs = {-0.8f, 0f, 0.8f};
        for (int i = 0; i < small; i++) missile(v, m, xs[i], 2.6f, -half + 2.6f, 5.0f, 0.24f, WHITE);
        float[] xm = {-0.5f, 0.5f};
        for (int i = 0; i < medium; i++) missile(v, m, xm[i], 3.2f, -half + 3.2f, 8.0f, 0.34f, OLIVE_DARK);
        if (small + medium == 0) box(v, m, -0.9f, 1.5f, half - 6.0f, 0.9f, 1.62f, half - 1.0f, STEEL);   // sin carga: la cuna vacía
    }

    private void missile(VertexConsumer v, Matrix4f m, float x, float y, float z, float len, float r, float[] c) {
        Matrix4f t = new Matrix4f(m).translate(x, y, 0);
        Shapes.tube(v, t, r, r, z, z + len * 0.72f, 10, c[0] * k, c[1] * k, c[2] * k, 1);
        Shapes.tube(v, t, r * 1.02f, r * 1.02f, z + len * 0.5f, z + len * 0.56f, 10, 0.85f * k, 0.12f * k, 0.1f * k, 1);
        Shapes.tube(v, t, r, 0, z + len * 0.72f, z + len, 10, 0.85f * k, 0.12f * k, 0.1f * k, 1);
        Shapes.tube(v, t, r * 0.7f, r * 0.85f, z - 0.18f, z, 8, 0.15f * k, 0.15f * k, 0.16f * k, 1);
        // cuna de madera
        box(v, m, x - r - 0.05f, y - r - 0.3f, z + 0.5f, x + r + 0.05f, y - r * 0.4f, z + 0.7f, new float[]{0.42f, 0.3f, 0.18f});
        box(v, m, x - r - 0.05f, y - r - 0.3f, z + len - 0.9f, x + r + 0.05f, y - r * 0.4f, z + len - 0.7f, new float[]{0.42f, 0.3f, 0.18f});
    }

    private void box(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float[] c) {
        Shapes.box(v, m, x0, y0, z0, x1, y1, z1, c[0] * k, c[1] * k, c[2] * k);
    }
}
