package com.arsenalatomico.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Lo que se ve encima de la cinemática: franjas de cine, textos y la cuenta regresiva. */
public final class Hud {
    private Hud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (!Cinematic.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float s = Cinematic.seconds(partial);
        // destello de los impactos grandes
        float fl = Cinematic.flash(s);
        if (fl > 0.01f) g.fill(0, 0, w, h, ((int) (fl * 235) << 24) | 0xFFF4E0);
        // franjas negras de cine
        int bar = Math.round(h * 0.09f);
        float in = Mth.clamp(s / 1.2f, 0, 1) * Mth.clamp((Cinematic.totalSeconds() - s) / 1.2f, 0, 1);
        int b = Math.round(bar * in);
        g.fill(0, 0, w, b, 0xFF000000);
        g.fill(0, h - b, w, h, 0xFF000000);

        // ficha arriba a la izquierda
        int x = 10, y = b + 8;
        g.drawString(font, Component.translatable("hud.arsenalatomico.title"), x, y, 0xFFFFB347, true);
        g.drawString(font, Component.translatable(Cinematic.type().nameKey()), x, y + 11, 0xFFFFFFFF, true);
        var t = Cinematic.targetBlock();
        g.drawString(font, Component.translatable("hud.arsenalatomico.target", t.getX(), t.getY(), t.getZ()), x, y + 22, 0xFFB8C0D0, false);

        // texto del momento, abajo al centro
        String key = "hud.arsenalatomico." + Cinematic.caption(s);
        int[] c = Cinematic.counts(s);
        Component line = Component.translatable(key, c[0], c[1], c[2]);
        float launch = Cinematic.launchSecond();
        boolean blink = (s >= Cinematic.alarmSecond() && s < launch) && ((int) (s * 2)) % 2 == 0;
        g.drawCenteredString(font, line, w / 2, h - b - 18, blink ? 0xFFFF5D5D : 0xFFFFFFFF);

        // cuenta regresiva grande
        if (s >= launch - 10 && s < launch) {
            int n = 10 - (int) Math.floor(s - (launch - 10));
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f - 20, 0);
            g.pose().scale(5, 5, 1);
            g.drawCenteredString(font, String.valueOf(n), 0, 0, 0xFFFF4040);
            g.pose().popPose();
        }
    }
}
