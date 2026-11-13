package com.example.beautifuldecor.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.List;

/**
 * V6.11.0: el noticiero de HS News, dibujado en vivo sobre las pantallas de anuncios: presentador, titular grande, clima y hora, y una cinta
 * de titulares abajo. Se dibuja en un cuadrado virtual de 128x128 centrado en la pantalla (como el video); lo demás de la pantalla queda azul oscuro.
 * Todo con rectángulos de color y la letra del juego: no usa ningún archivo de imagen ni de video.
 */
public final class NewsScreenRenderer {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    private static final int FULL_BRIGHT = 15728880;
    private static final long HEADLINE_MS = 6500L;

    private NewsScreenRenderer() {
    }

    /** Pone el fondo y el noticiero. Las coordenadas son las mismas del renderizador de pantallas (en dieciseisavos de bloque). */
    public static void draw(MultiBufferSource buffer, Matrix4f m, float xLeft, float xRight, float yTop, float yBottom, float frontZ, float layer, long elapsedMs) {
        Context c = new Context(buffer, m, xLeft, xRight, yTop, yBottom, frontZ, layer);
        c.rect(-1000, -1000, 2000, 2000, 1, 12, 20, 52, true);                       // todo el fondo, también a los lados
        float t = elapsedMs / 1000.0f;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<Component> news = NewsClient.headlines();
        int idx = (int) ((elapsedMs / HEADLINE_MS) % news.size());
        Component headline = news.get(news.size() - 1 - idx);                       // primero la más reciente

        // ---- cabecera
        c.rect(0, 0, 128, 14, 2, 200, 30, 40, false);
        c.text(font, Component.literal("HS NEWS").withStyle(ChatFormatting.BOLD), 6, 3.2f, 1.25f, 0xFFFFFF, 3);
        boolean blink = ((long) (t * 2)) % 2 == 0;
        if (blink) {
            c.rect(88, 5, 94, 11, 3, 255, 255, 255, false);
        }
        c.text(font, Component.translatable("news.live"), 98, 4.2f, 0.8f, 0xFFFFFF, 3);

        // ---- presentador
        c.rect(8, 58, 44, 100, 2, 24, 30, 60, false);                                // traje
        c.rect(21, 58, 31, 78, 3, 245, 245, 250, false);                             // camisa
        c.rect(25, 60, 27, 76, 4, 200, 30, 40, false);                               // corbata
        c.rect(15, 32, 37, 58, 3, 240, 200, 160, false);                             // cara
        c.rect(14, 28, 38, 36, 4, 70, 46, 34, false);                                // pelo
        c.rect(19, 43, 23, 46, 4, 20, 20, 24, false);
        c.rect(29, 43, 33, 46, 4, 20, 20, 24, false);
        int open = 1 + (int) ((t * 9) % 2) * 2;                                      // boca que habla
        c.rect(22, 51, 30, 51 + open, 4, 130, 40, 44, false);
        c.rect(0, 94, 62, 112, 5, 112, 82, 52, false);                               // escritorio
        c.rect(0, 94, 62, 96, 6, 150, 112, 76, false);
        c.rect(22, 99, 40, 108, 6, 0, 110, 220, false);                              // placa con el logo HS
        c.text(font, Component.literal("HS").withStyle(ChatFormatting.BOLD), 26, 100.5f, 1.0f, 0xFFFFFF, 7);

        // ---- titular grande
        c.rect(48, 18, 124, 66, 2, 240, 242, 248, false);
        c.rect(48, 18, 88, 26, 3, 200, 30, 40, false);
        c.text(font, Component.translatable("news.breaking"), 50, 19.2f, 0.62f, 0xFFFFFF, 4);
        List<FormattedCharSequence> lines = font.split(headline, 118);
        for (int i = 0; i < lines.size() && i < 5; i++) {
            c.text(font, lines.get(i), 51, 29 + i * 7.4f, 0.62f, 0x14183C, 4);
        }

        // ---- clima y hora
        c.rect(48, 70, 124, 92, 2, 30, 60, 130, false);
        weatherIcon(c, t);
        c.text(font, NewsClient.weather(), 68, 73, 0.58f, 0xFFFFFF, 4);
        c.text(font, Component.translatable("news.time", NewsClient.clock()), 68, 83, 0.62f, 0xFFD23C, 4);

        // ---- cinta de titulares
        c.rect(0, 114, 128, 128, 2, 8, 12, 36, false);
        c.rect(0, 114, 18, 128, 3, 200, 30, 40, false);
        c.text(font, Component.literal("HS").withStyle(ChatFormatting.BOLD), 4, 117.5f, 0.9f, 0xFFFFFF, 4);
        StringBuilder all = new StringBuilder();
        for (int i = news.size() - 1; i >= 0; i--) {
            all.append(news.get(i).getString()).append("   •   ");
        }
        String ticker = all.toString();
        int visible = 34;
        int start = (int) ((elapsedMs / 140L) % ticker.length());
        StringBuilder window = new StringBuilder();
        for (int i = 0; i < visible; i++) {
            window.append(ticker.charAt((start + i) % ticker.length()));
        }
        c.text(font, Component.literal(window.toString()), 21, 118.5f, 0.55f, 0xFFFFFF, 4);
    }

    private static void weatherIcon(Context c, float t) {
        if (NewsClient.isThundering()) {
            c.rect(52, 74, 64, 82, 3, 90, 96, 110, false);                          // nube oscura
            c.rect(57, 82, 60, 86, 4, 255, 220, 40, false);                          // rayo
            c.rect(55, 85, 58, 89, 4, 255, 220, 40, false);
        } else if (NewsClient.isRaining()) {
            c.rect(52, 74, 64, 82, 3, 190, 198, 212, false);                         // nube
            for (int i = 0; i < 3; i++) {
                float dy = (t * 12 + i * 3) % 6;
                c.rect(54 + i * 4, 83 + dy, 55.2f + i * 4, 85 + dy, 4, 120, 190, 255, false);
            }
        } else {
            c.rect(54, 74, 62, 82, 3, 255, 205, 50, false);                          // sol
            c.rect(57, 71, 59, 73, 3, 255, 205, 50, false);
            c.rect(57, 83, 59, 85, 3, 255, 205, 50, false);
            c.rect(51, 77, 53, 79, 3, 255, 205, 50, false);
            c.rect(63, 77, 65, 79, 3, 255, 205, 50, false);
        }
    }

    /** Dibujo en el cuadrado virtual: cada capa queda un poco más cerca de quien mira, para que nada se mezcle. */
    static final class Context {
        final MultiBufferSource buffer;
        final Matrix4f m;
        final float xSquareLeft;
        final float xRight;
        final float xLeft;
        final float yTop;
        final float yBottom;
        final float u;
        final float frontZ;
        final float layer;

        Context(MultiBufferSource buffer, Matrix4f m, float xLeft, float xRight, float yTop, float yBottom, float frontZ, float layer) {
            this.buffer = buffer;
            this.m = m;
            this.xLeft = xLeft;
            this.xRight = xRight;
            this.yTop = yTop;
            this.yBottom = yBottom;
            float side = yTop - yBottom;
            this.xSquareLeft = (xLeft + xRight) / 2f + side / 2f;
            this.u = side / 128f;
            this.frontZ = frontZ;
            this.layer = layer;
        }

        float z(int level) {
            return this.frontZ - (2 + level) * this.layer;
        }

        /** Rectángulo dado por sus esquinas (vx0, vy0)-(vx1, vy1) en coordenadas virtuales, o toda la pantalla si {@code full}. */
        void rect(float vx0, float vy0, float vx1, float vy1, int level, int r, int g, int b, boolean full) {
            VertexConsumer vc = this.buffer.getBuffer(RenderType.text(WHITE));
            float zz = z(level) / 16f;
            float xl;
            float xr;
            float yt;
            float yb;
            if (full) {
                xl = this.xLeft;
                xr = this.xRight;
                yt = this.yTop;
                yb = this.yBottom;
            } else {
                xl = this.xSquareLeft - vx0 * this.u;
                xr = this.xSquareLeft - vx1 * this.u;
                yt = this.yTop - vy0 * this.u;
                yb = this.yTop - vy1 * this.u;
            }
            vc.vertex(this.m, xl / 16f, yt / 16f, zz).color(r, g, b, 255).uv(0f, 0f).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(this.m, xl / 16f, yb / 16f, zz).color(r, g, b, 255).uv(0f, 1f).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(this.m, xr / 16f, yb / 16f, zz).color(r, g, b, 255).uv(1f, 1f).uv2(FULL_BRIGHT).endVertex();
            vc.vertex(this.m, xr / 16f, yt / 16f, zz).color(r, g, b, 255).uv(1f, 0f).uv2(FULL_BRIGHT).endVertex();
        }

        void text(Font font, Component text, float vx, float vy, float scale, int color, int level) {
            Matrix4f tm = this.textMatrix(vx, vy, scale, level);
            font.drawInBatch(text, 0f, 0f, 0xFF000000 | color, false, tm, this.buffer, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        }

        void text(Font font, FormattedCharSequence text, float vx, float vy, float scale, int color, int level) {
            Matrix4f tm = this.textMatrix(vx, vy, scale, level);
            font.drawInBatch(text, 0f, 0f, 0xFF000000 | color, false, tm, this.buffer, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        }

        private Matrix4f textMatrix(float vx, float vy, float scale, int level) {
            float s = this.u * scale / 16f;
            float x = (this.xSquareLeft - vx * this.u) / 16f;
            float y = (this.yTop - vy * this.u) / 16f;
            return new Matrix4f(this.m).translate(x, y, (z(level) - this.layer * 0.5f) / 16f).scale(-s, -s, 1f);
        }
    }
}
