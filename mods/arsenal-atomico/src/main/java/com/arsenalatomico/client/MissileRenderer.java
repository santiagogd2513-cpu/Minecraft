package com.arsenalatomico.client;

import com.arsenalatomico.entity.MissileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/** Misil táctico: cuerpo gris, punta roja, aletas y el fuego de la tobera, que se ve de día y de noche. */
public class MissileRenderer extends EntityRenderer<MissileEntity> {
    public MissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(MissileEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float y = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        float p = Mth.lerp(partial, e.xRotO, e.getXRot());
        ps.mulPose(Axis.YP.rotationDegrees(-y));
        ps.mulPose(Axis.XP.rotationDegrees(p));
        float s = e.scale();
        ps.scale(s, s, s);
        Matrix4f m = ps.last().pose();
        VertexConsumer v = buf.getBuffer(RenderType.debugQuads());
        // cuerpo, punta y aletas (el misil apunta hacia +Z)
        Shapes.tube(v, m, 0.2f, 0.2f, -1.3f, 0.9f, 10, 0.82f, 0.84f, 0.86f, 1);
        Shapes.tube(v, m, 0.2f, 0.2f, 0.25f, 0.45f, 10, 0.25f, 0.27f, 0.3f, 1);
        Shapes.tube(v, m, 0.2f, 0.0f, 0.9f, 1.45f, 10, 0.85f, 0.12f, 0.1f, 1);
        Shapes.tube(v, m, 0.13f, 0.2f, -1.45f, -1.3f, 10, 0.2f, 0.2f, 0.22f, 1);
        for (int i = 0; i < 4; i++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(45 + i * 90));
            Matrix4f fm = ps.last().pose();
            Shapes.box(v, fm, -0.02f, 0.18f, -1.3f, 0.02f, 0.5f, -0.75f, 0.3f, 0.32f, 0.35f);
            ps.popPose();
        }
        // fuego de la tobera: núcleo casi blanco, llama naranja y un halo que brilla (hacia -Z)
        float t = e.tickCount + partial;
        float flick = 0.85f + 0.1f * Mth.sin(t * 2.3f) + 0.05f * Mth.sin(t * 7.1f);
        float z0 = -1.45f;
        Shapes.tube(v, m, 0.15f, 0.03f, z0, z0 - 1.5f * flick, 10, 1f, 0.97f, 0.82f, 1f);
        Shapes.tube(v, m, 0.22f, 0.06f, z0 + 0.02f, z0 - 3.0f * flick, 10, 1f, 0.68f, 0.18f, 0.8f);
        Shapes.tube(v, m, 0.32f, 0.0f, z0 + 0.04f, z0 - 4.6f * flick, 10, 1f, 0.38f, 0.06f, 0.45f);
        VertexConsumer glow = buf.getBuffer(RenderType.lightning());
        Shapes.tube(glow, m, 0.5f, 0.0f, z0 + 0.1f, z0 - 2.4f * flick, 10, 1f, 0.55f, 0.15f, 0.6f);
        Shapes.tube(glow, m, 0.75f, 0.2f, z0 + 0.2f, z0 - 0.6f, 10, 1f, 0.6f, 0.2f, 0.35f);
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
