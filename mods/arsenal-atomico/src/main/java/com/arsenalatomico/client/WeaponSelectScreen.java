package com.arsenalatomico.client;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.WeaponType;
import com.arsenalatomico.net.Network;
import com.arsenalatomico.net.SelectWeaponPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Menú de armas (tecla J): las cinco, con imagen y descripción; las que faltan dicen «en desarrollo». */
public class WeaponSelectScreen extends Screen {
    private static final int ENTRY_H = 44, ENTRY_W = 330, ICON = 36;

    public WeaponSelectScreen() {
        super(Component.translatable("screen.arsenalatomico.weapons"));
    }

    private int left() {
        return (width - entryWidth()) / 2;
    }

    private int entryWidth() {
        return Math.min(ENTRY_W, width - 20);
    }

    private int top() {
        int total = WeaponType.values().length * (ENTRY_H + 4);
        return Math.max(30, (height - total) / 2 + 10);
    }

    private static ResourceLocation icon(WeaponType t) {
        return new ResourceLocation(ArsenalAtomico.MODID, "textures/gui/weapon_" + t.id + ".png");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, top() - 24, 0xFFFFC870);
        g.drawCenteredString(font, Component.translatable("screen.arsenalatomico.weapons.hint"), width / 2, top() - 13, 0xFFB09878);
        int x = left(), w = entryWidth();
        WeaponType[] all = WeaponType.values();
        for (int i = 0; i < all.length; i++) {
            WeaponType t = all[i];
            int y = top() + i * (ENTRY_H + 4);
            boolean sel = ClientState.selected == t;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + ENTRY_H;
            int bg = !t.ready ? 0xB0141414 : sel ? 0xD0503010 : hover ? 0xC0402810 : 0xB0201408;
            g.fill(x, y, x + w, y + ENTRY_H, bg);
            int border = !t.ready ? 0xFF333333 : sel ? 0xFFFFB347 : hover ? 0xFFA87A40 : 0xFF5A3E20;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + ENTRY_H - 1, x + w, y + ENTRY_H, border);
            g.fill(x, y, x + 1, y + ENTRY_H, border);
            g.fill(x + w - 1, y, x + w, y + ENTRY_H, border);
            g.blit(icon(t), x + 9, y + (ENTRY_H - ICON) / 2, 0, 0, ICON, ICON, ICON, ICON);
            if (!t.ready) g.fill(x + 9, y + (ENTRY_H - ICON) / 2, x + 9 + ICON, y + (ENTRY_H + ICON) / 2, 0x99000000);
            int tx = x + ICON + 18;
            g.drawString(font, Component.translatable(t.nameKey()), tx, y + 5, !t.ready ? 0xFF808080 : sel ? 0xFFFFD08A : 0xFFFFFFFF, true);
            Component tag = Component.translatable(t.ready ? (sel ? "screen.arsenalatomico.selected" : "") : "screen.arsenalatomico.soon");
            g.drawString(font, tag, x + w - 8 - font.width(tag), y + 5, t.ready ? 0xFF7CFF9C : 0xFFFFB347, true);
            List<FormattedCharSequence> lines = font.split(Component.translatable(t.descKey()), w - (tx - x) - 10);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawString(font, lines.get(l), tx, y + 17 + l * 10, t.ready ? 0xFFD8C8B0 : 0xFF707070, false);
            }
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), w = entryWidth();
        WeaponType[] all = WeaponType.values();
        for (int i = 0; i < all.length; i++) {
            int y = top() + i * (ENTRY_H + 4);
            if (mx >= x && mx < x + w && my >= y && my < y + ENTRY_H) {
                if (!all[i].ready) {
                    Cinematic.play(SoundEvents.NOTE_BLOCK_BASS.get(), 0.6f, 0.8f);
                    return true;
                }
                ClientState.selected = all[i];
                Network.CHANNEL.sendToServer(new SelectWeaponPacket(all[i]));
                Cinematic.play(SoundEvents.NOTE_BLOCK_PLING.get(), 1.6f, 0.8f);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.WEAPONS.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
