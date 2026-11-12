package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.WideRailBlock;
import com.example.beautifuldecor.block.entity.WideRailBlockEntity;
import com.example.beautifuldecor.entity.WideArc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * V6.1.0: dibuja un pedazo de curva ancha siguiendo el arco: balasto (la base), durmientes y los dos rieles de acero, con las mismas texturas
 * que el riel recto de su familia. Es plano (como la parte de arriba del riel recto) y por piezas de 25 cm.
 */
public class WideRailRenderer implements BlockEntityRenderer<WideRailBlockEntity> {
    private static final double STEP = 0.25;
    private static final String[] PREFIX = {"freight_rail", "passenger_rail", "metro_rail"};

    public WideRailRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static TextureAtlasSprite sprite(String name) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation(BeautifulDecor.MOD_ID, "block/" + name));
    }

    @Override
    public void render(WideRailBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        WideArc.Piece piece = be.piece();
        BlockState state = be.getBlockState();
        if (piece == null || !(state.getBlock() instanceof WideRailBlock wide)) {
            return;
        }
        String prefix = PREFIX[Math.max(0, Math.min(2, wide.family()))];
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        TextureAtlasSprite ballast = sprite(prefix + "_ballast");
        TextureAtlasSprite tie = sprite(prefix + "_tie");
        TextureAtlasSprite steel = sprite(prefix + "_steel");
        double len = piece.length();
        int n = Math.max(1, (int) Math.ceil(len / STEP));
        double[] a = new double[3];
        double[] b = new double[3];
        double[] t = new double[2];
        for (int i = 0; i < n; i++) {
            double s0 = len * i / n;
            double s1 = len * (i + 1) / n;
            ribbon(vc, m, piece, ballast, s0, s1, -7.0 / 16.0, 7.0 / 16.0, 2.0 / 16.0, light, a, b, t);
            ribbon(vc, m, piece, steel, s0, s1, -4.25 / 16.0 - 0.75 / 16.0, -4.25 / 16.0 + 0.75 / 16.0, 4.0 / 16.0, light, a, b, t);
            ribbon(vc, m, piece, steel, s0, s1, 4.25 / 16.0 - 0.75 / 16.0, 4.25 / 16.0 + 0.75 / 16.0, 4.0 / 16.0, light, a, b, t);
        }
        // durmientes cada 3/16 de bloque
        double pitch = 3.0 / 16.0;
        for (double c = pitch / 2; c < len; c += pitch) {
            ribbon(vc, m, piece, tie, Math.max(0.0, c - 1.0 / 16.0), Math.min(len, c + 1.0 / 16.0), -6.0 / 16.0, 6.0 / 16.0, 3.0 / 16.0, light, a, b, t);
        }
    }

    /** Una tira plana entre las distancias s0 y s1 del arco, de lado w0 a lado w1 (perpendicular al arco), a altura y. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, WideArc.Piece piece, TextureAtlasSprite sp, double s0, double s1, double w0, double w1,
                               double y, int light, double[] a, double[] b, double[] t) {
        piece.point(s0, a);
        piece.tangent(s0, t);
        double nax = -t[1];
        double naz = t[0];
        piece.point(s1, b);
        piece.tangent(s1, t);
        double nbx = -t[1];
        double nbz = t[0];
        float u0 = sp.getU0();
        float u1 = sp.getU1();
        float v0 = sp.getV0();
        float v1 = sp.getV1();
        vertex(vc, m, a[0] + nax * w0, y, a[2] + naz * w0, u0, v0, light);
        vertex(vc, m, a[0] + nax * w1, y, a[2] + naz * w1, u1, v0, light);
        vertex(vc, m, b[0] + nbx * w1, y, b[2] + nbz * w1, u1, v1, light);
        vertex(vc, m, b[0] + nbx * w0, y, b[2] + nbz * w0, u0, v1, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, double z, float u, float v, int light) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(0.0f, 1.0f, 0.0f).endVertex();
    }
}
