package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.ad.AdBrands;
import com.example.beautifuldecor.block.AdScreenBlock;
import com.example.beautifuldecor.block.entity.AdScreenBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * V4.1.0: dibuja el anuncio sobre la pantalla de varios bloques: fondo negro con un borde finito y, encima, el cuadro del video
 * (o el póster de la marca cuando la pantalla está lejos o el video todavía carga). Todo con luz máxima, como una pantalla encendida.
 * Las medidas van en dieciseisavos de bloque con el bloque de la esquina inferior izquierda como origen y la pantalla mirando al
 * norte (como en {@link TelevisionRenderer}); el resto se extiende hacia x negativas y hacia arriba.
 */
public class AdScreenRenderer implements BlockEntityRenderer<AdScreenBlockEntity> {
    private static final ResourceLocation BLACK = new ResourceLocation("minecraft", "textures/block/black_concrete.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float LAYER = 0.08f;
    /** Cara delantera del panel (el panel mide de z=13 a z=16 mirando al norte). */
    private static final float FRONT_Z = 13.0f;
    private static final float MARGIN = 0.6f;
    private static final Map<String, ResourceLocation> POSTERS = new HashMap<>();

    public AdScreenRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation poster(int index) {
        return POSTERS.computeIfAbsent(AdBrands.brand(index), b -> new ResourceLocation(BeautifulDecor.MOD_ID, AdBrands.posterPath(b)));
    }

    @Override
    public void render(AdScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        try {
            draw(screen, pose, buffer);
        } catch (Throwable t) {
            // un fallo de dibujo no puede cerrar el juego: la pantalla simplemente queda como el bloque apagado
        }
    }

    private static void draw(AdScreenBlockEntity screen, PoseStack pose, MultiBufferSource buffer) {
        if (!screen.isOn()) {
            return;
        }
        BlockState state = screen.getBlockState();
        if (!(state.getBlock() instanceof AdScreenBlock block)) {
            return;
        }
        int size = block.sizeBlocks();
        AdPlayback.Session session = AdPlayback.sessionOf(screen);
        boolean video = session != null && session.video.hasFrame();
        ResourceLocation tex;
        if (video) {
            tex = session.video.textureId();
        } else {
            int start = session != null ? session.index : AdPlayback.startIndexOf(screen);
            int index = session != null ? session.index : AdBrands.posterIndex(start, System.currentTimeMillis());
            tex = poster(index);
        }

        float yaw = (180f + state.getValue(AdScreenBlock.FACING).toYRot()) % 360f;
        float xLeft = 16f - MARGIN;
        float xRight = 16f - 16f * size + MARGIN;
        float yBottom = MARGIN;
        float yTop = 16f * size - MARGIN;

        pose.pushPose();
        try {
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.translate(-0.5, 0.0, -0.5);
            Matrix4f m = pose.last().pose();
            quad(buffer.getBuffer(RenderType.text(BLACK)), m, 16f, 16f - 16f * size, 16f * size, 0f, FRONT_Z - LAYER,
                    0, 0, 0);
            quad(buffer.getBuffer(RenderType.text(tex)), m, xLeft, xRight, yTop, yBottom, FRONT_Z - 2 * LAYER, 255, 255, 255);
        } finally {
            pose.popPose();
        }
    }

    /** Un rectángulo visto de frente; {@code xLeft} es el borde izquierdo para quien mira la pantalla (el de x mayor). */
    private static void quad(VertexConsumer vc, Matrix4f m, float xLeft, float xRight, float yTop, float yBottom, float z,
                             int r, int g, int b) {
        float zz = z / 16f;
        vc.vertex(m, xLeft / 16f, yTop / 16f, zz).color(r, g, b, 255).uv(0f, 0f).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xLeft / 16f, yBottom / 16f, zz).color(r, g, b, 255).uv(0f, 1f).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xRight / 16f, yBottom / 16f, zz).color(r, g, b, 255).uv(1f, 1f).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xRight / 16f, yTop / 16f, zz).color(r, g, b, 255).uv(1f, 0f).uv2(FULL_BRIGHT).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(AdScreenBlockEntity screen) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return (int) AdPlayback.VIDEO_RANGE;
    }
}
