package com.arsenalatomico.client;

import com.arsenalatomico.SalvoPlan;
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
        String key;
        if (s < 8) key = "hud.arsenalatomico.locked";
        else if (s < 20) key = "hud.arsenalatomico.recon";
        else if (s < 32) key = "hud.arsenalatomico.transit";
        else if (s < 44) key = "hud.arsenalatomico.battery";
        else if (s < 52) key = "hud.arsenalatomico.alarm";
        else if (s < 72) key = "hud.arsenalatomico.raise";
        else if (s < 86) key = "hud.arsenalatomico.aim";
        else if (s < 100) key = "hud.arsenalatomico.countdown";
        else if (s < 107) key = "hud.arsenalatomico.launch";
        else if (s < 130) key = "hud.arsenalatomico.flight";
        else if (s < 176) key = "hud.arsenalatomico.done";
        else if (s < 190) key = "hud.arsenalatomico.stow";
        else key = "hud.arsenalatomico.complete";
        int[] c = Cinematic.counts(s);
        Component line = Component.translatable(key, c[0], c[1], SalvoPlan.COUNT);
        boolean blink = (s >= 44 && s < 100) && ((int) (s * 2)) % 2 == 0;
        g.drawCenteredString(font, line, w / 2, h - b - 18, blink ? 0xFFFF5D5D : 0xFFFFFFFF);

        // cuenta regresiva grande
        if (s >= 90 && s < 100) {
            int n = 10 - (int) Math.floor(s - 90);
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f - 20, 0);
            g.pose().scale(5, 5, 1);
            g.drawCenteredString(font, String.valueOf(n), 0, 0, 0xFFFF4040);
            g.pose().popPose();
        }
    }
}
