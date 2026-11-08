package com.amatista.arsenal.client;

import com.amatista.arsenal.block.PedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/** El artefacto flotando y girando sobre el pedestal, con la luz arcoíris de la vitrina. */
public class PedestalRenderer implements BlockEntityRenderer<PedestalBlockEntity> {
    private final ItemRenderer items;

    public PedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        items = ctx.getItemRenderer();
    }

    @Override
    public void render(PedestalBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        long gt = level == null ? 0 : level.getGameTime();
        float t = gt + partial;
        if (be.isRainbow()) {
            float[] c = PedestalBlockEntity.rainbow(gt, partial);
            float pulse = 0.75f + 0.25f * Mth.sin(t / 9f);
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            // haz de luz que sube desde el pedestal y resplandor que llena la vitrina
            box(vc, m, 0.40f, 1.0f, 0.40f, 0.60f, 2.98f, 0.60f, c, 0.40f * pulse);
            box(vc, m, 0.03f, 1.0f, 0.03f, 0.97f, 2.97f, 0.97f, c, 0.09f * pulse);
        }
        ItemStack st = be.getItem();
        if (!st.isEmpty()) {
            ps.pushPose();
            ps.translate(0.5, 1.55 + Mth.sin(t / 12f) * 0.08, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(t * 2f));
            ps.scale(0.9f, 0.9f, 0.9f);
            items.renderStatic(st, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, level,
                    (int) be.getBlockPos().asLong());
            ps.popPose();
        }
    }

    /** Caja de luz (las caras se dibujan por los dos lados). */
    private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float[] c, float a) {
        float[][] q = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
                {x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1},
                {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1},
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1},
        };
        for (float[] f : q) {
            for (int i = 0; i < 4; i++) vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(c[0], c[1], c[2], a).endVertex();
            for (int i = 3; i >= 0; i--) vc.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(c[0], c[1], c[2], a).endVertex();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PedestalBlockEntity be) {
        return true;
    }
}
