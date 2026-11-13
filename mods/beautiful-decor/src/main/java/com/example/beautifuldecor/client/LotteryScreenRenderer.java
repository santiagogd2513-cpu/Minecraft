package com.example.beautifuldecor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * V6.15.0: segmento de la lotería en las pantallas de anuncios (cada 5 anuncios, 15 s): el último número y clase que salieron en bolas,
 * los premios y cuánto falta para el próximo sorteo. Se dibuja en el mismo cuadrado virtual de 128 x 128 que el noticiero.
 */
public final class LotteryScreenRenderer {
    private LotteryScreenRenderer() {
    }

    public static void draw(MultiBufferSource buffer, Matrix4f m, float xLeft, float xRight, float yTop, float yBottom, float frontZ, float layer, long elapsedMs) {
        NewsScreenRenderer.Context c = new NewsScreenRenderer.Context(buffer, m, xLeft, xRight, yTop, yBottom, frontZ, layer);
        Font font = Minecraft.getInstance().font;
        float t = elapsedMs / 1000.0f;
        c.rect(-1000, -1000, 2000, 2000, 1, 8, 70, 40, true);
        // ---- cabecera
        c.rect(0, 0, 128, 15, 2, 250, 200, 30, false);
        c.text(font, Component.translatable("lottery.title").withStyle(net.minecraft.ChatFormatting.BOLD), 6, 3.6f, 1.15f, 0x3A2600, 3);
        c.text(font, Component.translatable("lottery.draw", LotteryClient.draw() - 1), 86, 5.5f, 0.6f, 0x3A2600, 3);

        // ---- bolas con el último resultado
        c.text(font, Component.translatable("lottery.last"), 6, 19, 0.66f, 0xFFFFFF, 3);
        String num = LotteryClient.numberText(LotteryClient.lastNumber());
        boolean spinning = LotteryClient.lastNumber() >= 0 && elapsedMs < 2500;      // las bolas "giran" un momento al empezar
        for (int i = 0; i < 3; i++) {
            float x = 6 + i * 25;
            c.rect(x, 28, x + 22, 54, 3, 240, 240, 245, false);
            c.rect(x + 2, 30, x + 20, 52, 4, 225, 40, 50, false);
            String d = spinning && elapsedMs < 700 + i * 600 ? String.valueOf((int) ((elapsedMs / 70 + i * 3) % 10)) : String.valueOf(num.charAt(i));
            c.text(font, Component.literal(d).withStyle(net.minecraft.ChatFormatting.BOLD), x + 7, 36, 1.3f, 0xFFFFFF, 5);
        }
        c.rect(84, 28, 106, 54, 3, 240, 240, 245, false);
        c.rect(86, 30, 104, 52, 4, 30, 90, 220, false);
        c.text(font, Component.literal(LotteryClient.classLetter(LotteryClient.lastClass())).withStyle(net.minecraft.ChatFormatting.BOLD), 91, 36, 1.3f, 0xFFFFFF, 5);

        // ---- premios
        c.rect(4, 60, 124, 104, 2, 4, 40, 24, false);
        c.text(font, Component.translatable("lottery.prize.1"), 8, 64, 0.58f, 0x7CF0FF, 4);
        c.text(font, Component.translatable("lottery.prize.2"), 8, 76, 0.58f, 0xFFD23C, 4);
        c.text(font, Component.translatable("lottery.prize.3"), 8, 88, 0.58f, 0xD0D4DC, 4);

        // ---- cuenta regresiva y dónde comprar
        int min = LotteryClient.minutesLeft();
        c.rect(0, 108, 128, 128, 2, 250, 200, 30, false);
        c.text(font, min < 0 ? Component.translatable("lottery.soon") : Component.translatable("lottery.next", Math.max(1, min)), 6, 111, 0.68f, 0x3A2600, 4);
        c.text(font, Component.translatable("lottery.buy"), 6, 119, 0.55f, 0x3A2600, 4);
        // destello de confeti
        for (int i = 0; i < 10; i++) {
            float fx = (i * 37 + t * 20) % 128;
            float fy = 16 + (i * 53 + t * 30) % 90;
            if (fy < 58 || fx > 110) {
                c.rect(fx, fy, fx + 2, fy + 2, 6, i % 2 == 0 ? 255 : 80, i % 3 == 0 ? 220 : 80, i % 2 == 0 ? 60 : 255, false);
            }
        }
    }
}
