package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.TelevisionBlock;
import com.example.beautifuldecor.block.entity.TelevisionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Dibuja el video de {@link TvPlayback} sobre la pantalla del televisor (V2.11.0): fondo negro, el cuadro ajustado sin deformarlo
 * y, mientras carga o si falla, un texto. Todo con luz máxima, como una pantalla encendida.
 * <p>
 * Las medidas de cada pantalla (en dieciseisavos de bloque) están medidas sobre los modelos, vistos de frente con el televisor
 * mirando al norte. En los televisores grandes el bloque con los datos es la esquina inferior izquierda (vista de frente), así que
 * la pantalla se extiende hacia la izquierda en esas coordenadas (x negativas).
 * <p>
 * V3.0.2: {@link #render} nunca deja escapar un error (un fallo de dibujo se vuelve un mensaje en la pantalla del televisor y una
 * nota en el registro del juego, no un cierre del juego) y siempre deja la pila de matrices como la encontró.
 */
public class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private static final ResourceLocation BLACK = new ResourceLocation("minecraft", "textures/block/black_concrete.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Separación entre capas (en dieciseisavos de bloque) para que no parpadeen entre sí. */
    private static final float LAYER = 0.08f;

    /** El rectángulo de la pantalla: x0 < x1 y y0 < y1 en dieciseisavos de bloque; z es la cara más adelantada que hay que tapar. */
    private record Screen(float x0, float x1, float y0, float y1, float z) {
    }

    public TelevisionRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Screen screenOf(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) {
            return null;
        }
        return switch (key.getPath()) {
            case "television" -> new Screen(0f, 16f, 3.5f, 13.5f, 6.9f);
            case "tv_modern" -> new Screen(0.3f, 15.7f, 4.8f, 13.2f, 7.0f);
            case "tv_crt" -> new Screen(2.6f, 10.8f, 3.2f, 11.8f, 2.3f);
            case "tv_retro" -> new Screen(2.8f, 10.0f, 5.2f, 12.2f, 2.4f);
            case "tv_big" -> new Screen(16f - 48f + 1f, 15f, 5f, 31f, 7.0f);
            case "tv_cinema" -> new Screen(16f - 64f + 1f, 15f, 5f, 31f, 7.0f);
            default -> null;
        };
    }

    @Override
    public void render(TelevisionBlockEntity tv, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        try {
            draw(tv, pose, buffer);
        } catch (Throwable t) {
            TvPlayback.fail(tv, t);
        }
    }

    private static void draw(TelevisionBlockEntity tv, PoseStack pose, MultiBufferSource buffer) {
        TvPlayback.Session session = TvPlayback.sessionOf(tv);
        if (session == null || session.crashed) {
            return;
        }
        BlockState state = tv.getBlockState();
        if (!(state.getBlock() instanceof TelevisionBlock)) {
            return;
        }
        Screen screen = screenOf(state);
        if (screen == null) {
            return;
        }

        HsVideoPlayer video = session.video;
        String message = session.error;
        boolean showFrame = false;
        boolean loading = false;
        float progress = -1f;
        if (video != null) {
            video.update();
            int st = video.state();
            if (st == HsVideoPlayer.ERROR) {
                message = video.message();
            } else if (video.hasFrame()) {
                showFrame = true;
            } else if (st != HsVideoPlayer.IDLE) {
                loading = true;
                progress = st == HsVideoPlayer.LOADING ? video.loadProgress() : -1f;
            }
        }
        if (!showFrame && !loading && (message == null || message.isEmpty())) {
            return;
        }

        // el modelo "mira al norte" sin girar; el bloque lo gira según hacia dónde mira el televisor
        float yaw = (180f + state.getValue(TelevisionBlock.FACING).toYRot()) % 360f;
        pose.pushPose();
        try {
            drawScreen(pose, buffer, screen, video, showFrame, loading, progress, message, yaw);
        } finally {
            pose.popPose();
        }
    }

    private static void drawScreen(PoseStack pose, MultiBufferSource buffer, Screen screen, HsVideoPlayer video, boolean showFrame,
                                   boolean loading, float progress, String message, float yaw) {
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(-0.5, 0.0, -0.5);

        Matrix4f m = pose.last().pose();
        quad(buffer.getBuffer(RenderType.text(BLACK)), m, screen.x1(), screen.x0(), screen.y1(), screen.y0(), screen.z() - LAYER,
                0, 0, 0, 0f, 0f, 1f, 1f);

        if (showFrame) {
            float sw = screen.x1() - screen.x0();
            float sh = screen.y1() - screen.y0();
            float aspect = video.videoWidth() / (float) Math.max(1, video.videoHeight());
            float w = sw;
            float h = sh;
            if (sw / sh > aspect) {
                w = sh * aspect;
            } else {
                h = sw / aspect;
            }
            float cx = (screen.x0() + screen.x1()) / 2f;
            float cy = (screen.y0() + screen.y1()) / 2f;
            quad(buffer.getBuffer(RenderType.text(video.textureId())), m, cx + w / 2f, cx - w / 2f, cy + h / 2f, cy - h / 2f,
                    screen.z() - 2 * LAYER, 255, 255, 255, 0f, 0f, 1f, 1f);
        } else {
            String text;
            int color = 0xFFFFFFFF;
            if (loading) {
                text = Component.translatable(video.state() == HsVideoPlayer.LOADING ? "gui.beautifuldecor.tv.loading"
                        : "gui.beautifuldecor.tv.opening").getString();
                if (progress >= 0f && video.state() == HsVideoPlayer.LOADING) {
                    text += " " + Math.round(progress * 100f) + "%";
                }
            } else {
                text = message;
                color = 0xFFFF7070;
            }
            drawText(pose, buffer, screen, text, color);
        }
    }

    /**
     * Un rectángulo visto de frente. {@code xLeft} es el borde izquierdo para quien mira el televisor de frente (el de x mayor) y
     * {@code xRight} el derecho. Las medidas vienen en dieciseisavos de bloque.
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float xLeft, float xRight, float yTop, float yBottom, float z,
                             int r, int g, int b, float u0, float v0, float u1, float v1) {
        float zz = z / 16f;
        vc.vertex(m, xLeft / 16f, yTop / 16f, zz).color(r, g, b, 255).uv(u0, v0).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xLeft / 16f, yBottom / 16f, zz).color(r, g, b, 255).uv(u0, v1).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xRight / 16f, yBottom / 16f, zz).color(r, g, b, 255).uv(u1, v1).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xRight / 16f, yTop / 16f, zz).color(r, g, b, 255).uv(u1, v0).uv2(FULL_BRIGHT).endVertex();
    }

    // ------------------------------------------------------------------ texto

    private static void drawText(PoseStack pose, MultiBufferSource buffer, Screen screen, String text, int color) {
        Font font = Minecraft.getInstance().font;
        float sw = screen.x1() - screen.x0();
        float sh = screen.y1() - screen.y0();
        float scale = sh / 48f;                                  // tamaño de una unidad de letra, en dieciseisavos de bloque
        int maxUnits = Math.max(20, Math.round(sw * 0.92f / scale));
        int maxLines = Math.max(1, (int) (sh * 0.9f / (9f * scale)));
        List<String> lines = wrap(font, text, maxUnits, maxLines);

        pose.pushPose();
        try {
            pose.translate((screen.x0() + screen.x1()) / 32.0, (screen.y0() + screen.y1()) / 32.0, (screen.z() - 3 * LAYER) / 16.0);
            pose.mulPose(Axis.YP.rotationDegrees(180f));        // las letras se leen desde el frente del televisor
            float s = scale / 16f;
            pose.scale(s, -s, s);
            Matrix4f m = pose.last().pose();
            float y = -lines.size() * 9f / 2f;
            for (String line : lines) {
                font.drawInBatch(line, -font.width(line) / 2f, y, color, false, m, buffer, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
                y += 9f;
            }
        } finally {
            pose.popPose();
        }
    }

    /** Parte el texto en líneas de como mucho {@code maxUnits} de ancho; si sobra, la última termina en "…". */
    private static List<String> wrap(Font font, String text, int maxUnits, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String w = word;
            while (font.width(w) > maxUnits && w.length() > 1) {            // una palabra más larga que la línea: se corta
                int cut = w.length() - 1;
                while (cut > 1 && font.width(w.substring(0, cut)) > maxUnits) {
                    cut--;
                }
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(w.substring(0, cut));
                w = w.substring(cut);
            }
            String joined = line.length() == 0 ? w : line + " " + w;
            if (font.width(joined) <= maxUnits) {
                line.setLength(0);
                line.append(joined);
            } else {
                if (line.length() > 0) {
                    lines.add(line.toString());
                }
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String last = lines.get(maxLines - 1);
            while (last.length() > 1 && font.width(last + "…") > maxUnits) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(maxLines - 1, last + "…");
        }
        return lines;
    }
}
