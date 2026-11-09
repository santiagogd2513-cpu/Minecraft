package com.arsenalatomico.client;

import com.arsenalatomico.net.FirePacket;
import com.arsenalatomico.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * La consola de lanzamiento del Sol Negro. Tres pasos, en orden:
 * <ol>
 *   <li>levantar la tapa roja que protege el botón,</li>
 *   <li>girar la llave (de SEGURO a ARMADO),</li>
 *   <li>oprimir el botón rojo.</li>
 * </ol>
 */
public class ConsoleScreen extends Screen {
    private final BlockPos target;
    private int step;
    private float cover, key, press;
    private long pressedAt = -1;

    public static void open(BlockPos target) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new ConsoleScreen(target));
    }

    public ConsoleScreen(BlockPos target) {
        super(Component.translatable("screen.arsenalatomico.console.title"));
        this.target = target;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cx() {
        return width / 2;
    }

    private int cy() {
        return height / 2 + 6;
    }

    @Override
    public void tick() {
        cover = Mth.approach(cover, step >= 1 ? 1 : 0, 0.18f);
        key = Mth.approach(key, step >= 2 ? 1 : 0, 0.2f);
        press = Mth.approach(press, step >= 3 ? 1 : 0, 0.35f);
        if (pressedAt >= 0 && minecraft != null && minecraft.level != null && minecraft.level.getGameTime() - pressedAt > 12) {
            Network.CHANNEL.sendToServer(new FirePacket());
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = cx(), cy = cy();
        if (step == 0 && Math.abs(mx - (cx + 60)) < 46 && Math.abs(my - cy) < 46) {
            step = 1;
            Cinematic.play(SoundEvents.IRON_TRAPDOOR_OPEN, 1.3f, 0.9f);
            return true;
        }
        if (step == 1 && Math.abs(mx - (cx - 70)) < 34 && Math.abs(my - cy) < 34) {
            step = 2;
            Cinematic.play(SoundEvents.LEVER_CLICK, 0.8f, 1f);
            Cinematic.play(com.arsenalatomico.registry.ModSounds.BEEP.get(), 1.4f, 0.7f);
            return true;
        }
        if (step == 2 && Math.hypot(mx - (cx + 60), my - cy) < 34) {
            step = 3;
            Cinematic.play(SoundEvents.STONE_BUTTON_CLICK_ON, 0.6f, 1f);
            Cinematic.play(com.arsenalatomico.registry.ModSounds.ALARM.get(), 1f, 0.9f);
            if (minecraft != null && minecraft.level != null) pressedAt = minecraft.level.getGameTime();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int cx = cx(), cy = cy();
        // el tablero
        g.fill(cx - 150, cy - 95, cx + 150, cy + 82, 0xF0101214);
        g.fill(cx - 150, cy - 95, cx + 150, cy - 93, 0xFFB01E1E);
        g.fill(cx - 150, cy + 80, cx + 150, cy + 82, 0xFFB01E1E);
        for (int x = cx - 150; x < cx + 150; x += 12) g.fill(x, cy + 74, x + 6, cy + 80, 0xFFE0B020);
        g.drawCenteredString(font, title, cx, cy - 86, 0xFFFF5040);
        g.drawCenteredString(font, Component.translatable("screen.arsenalatomico.console.weapon"), cx, cy - 74, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.arsenalatomico.console.target", target.getX(), target.getY(), target.getZ()),
                cx, cy - 62, 0xFFB8C0D0);

        // la llave: SEGURO → ARMADO
        int kx = cx - 70;
        circle(g, kx, cy, 26, 0xFF2A2D31);
        circle(g, kx, cy, 22, 0xFF3C4046);
        g.drawCenteredString(font, Component.translatable("screen.arsenalatomico.console.safe"), kx - 30, cy - 34, key < 0.5f ? 0xFF6FE07A : 0xFF5A5F66);
        g.drawCenteredString(font, Component.translatable("screen.arsenalatomico.console.armed"), kx + 30, cy - 34, key >= 0.5f ? 0xFFFF4040 : 0xFF5A5F66);
        double ang = -Math.PI / 2 + key * Math.PI / 2;
        for (int i = -16; i <= 16; i++) {
            int px = kx + (int) Math.round(Math.cos(ang) * i), py = cy + (int) Math.round(Math.sin(ang) * i);
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFD8B84A);
        }
        circle(g, kx, cy, 5, 0xFFB09030);

        // el botón y su tapa
        int bx = cx + 60;
        circle(g, bx, cy, 34, 0xFF2A2D31);
        int r = Math.round(26 - press * 3);
        circle(g, bx, cy, r, step >= 3 ? 0xFFFF2020 : 0xFFC01818);
        circle(g, bx - 7, cy - 8, 7, 0x55FFFFFF);
        // la tapa: se levanta hacia arriba (se achica en alto)
        float open = cover;
        int ch = Math.round(76 * (1 - open)), top = cy - 38;
        if (ch > 1) {
            g.fill(bx - 38, top + 76 - ch - Math.round(open * 6), bx + 38, top + 76 - Math.round(open * 6), 0x99FF3030);
            g.fill(bx - 38, top + 76 - ch - Math.round(open * 6), bx + 38, top + 78 - ch - Math.round(open * 6), 0xFFFF6060);
            for (int y = top + 76 - ch; y < top + 76; y += 8) g.fill(bx - 34, y, bx + 34, y + 1, 0x33FFFFFF);
        }
        g.fill(bx - 40, top - 4 - Math.round(open * 14), bx + 40, top - Math.round(open * 14), 0xFF8A8F96);

        // qué sigue
        String hint = step == 0 ? "screen.arsenalatomico.console.step1" : step == 1 ? "screen.arsenalatomico.console.step2"
                : step == 2 ? "screen.arsenalatomico.console.step3" : "screen.arsenalatomico.console.launching";
        boolean blink = step == 3 || ((System.currentTimeMillis() / 450) % 2 == 0);
        g.drawCenteredString(font, Component.translatable(hint), cx, cy + 50, blink ? 0xFFFFD040 : 0xFFB08A20);
        super.render(g, mouseX, mouseY, partial);
    }

    private static void circle(GuiGraphics g, int x, int y, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x - w, y + dy, x + w, y + dy + 1, color);
        }
    }
}
