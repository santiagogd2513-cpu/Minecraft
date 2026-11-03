package com.amatista.arsenal.client;

import com.amatista.arsenal.ArsenalOrbital;
import com.amatista.arsenal.StrikeType;
import com.amatista.arsenal.net.Network;
import com.amatista.arsenal.net.SelectWeaponPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Menú de armas (tecla H): imagen, nombre y descripción de cada arma; un toque la selecciona. */
public class WeaponSelectScreen extends Screen {
    private static final int ENTRY_H = 60;
    private static final int ENTRY_W = 330;
    private static final int ICON = 46;

    public WeaponSelectScreen() {
        super(Component.translatable("screen.arsenalorbital.weapons"));
    }

    private int left() {
        return (width - Math.min(ENTRY_W, width - 20)) / 2;
    }

    private int entryWidth() {
        return Math.min(ENTRY_W, width - 20);
    }

    private int top() {
        int total = StrikeType.values().length * (ENTRY_H + 6);
        return Math.max(34, (height - total) / 2 + 10);
    }

    private static ResourceLocation icon(StrikeType type) {
        return new ResourceLocation(ArsenalOrbital.MODID, "textures/gui/weapon_" + type.id + ".png");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, top() - 28, 0xE0C8FF);
        g.drawCenteredString(font, Component.translatable("screen.arsenalorbital.weapons.hint"), width / 2, top() - 16, 0x9A8AB0);
        int x = left(), w = entryWidth();
        StrikeType[] types = StrikeType.values();
        for (int i = 0; i < types.length; i++) {
            StrikeType type = types[i];
            int y = top() + i * (ENTRY_H + 6);
            boolean selected = ClientState.selected == type;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ENTRY_H;
            int bg = selected ? 0xD0402468 : hover ? 0xC0302048 : 0xB0180F28;
            g.fill(x, y, x + w, y + ENTRY_H, bg);
            int border = selected ? 0xFFB57BFF : hover ? 0xFF7B5AA8 : 0xFF3A2758;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + ENTRY_H - 1, x + w, y + ENTRY_H, border);
            g.fill(x, y, x + 1, y + ENTRY_H, border);
            g.fill(x + w - 1, y, x + w, y + ENTRY_H, border);
            g.blit(icon(type), x + 9, y + (ENTRY_H - ICON) / 2, 0, 0, ICON, ICON, ICON, ICON);
            int tx = x + ICON + 18;
            g.drawString(font, Component.translatable(type.nameKey()), tx, y + 6, selected ? 0xFFE0B0FF : 0xFFFFFFFF, true);
            if (selected) g.drawString(font, Component.translatable("screen.arsenalorbital.selected"), x + w - 8 - font.width(
                    Component.translatable("screen.arsenalorbital.selected")), y + 6, 0xFF7CFF9C, true);
            List<FormattedCharSequence> lines = font.split(Component.translatable(type.descKey()), w - (tx - x) - 10);
            for (int l = 0; l < Math.min(3, lines.size()); l++) {
                g.drawString(font, lines.get(l), tx, y + 19 + l * 11, 0xFFC8B8E0, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left(), w = entryWidth();
        StrikeType[] types = StrikeType.values();
        for (int i = 0; i < types.length; i++) {
            int y = top() + i * (ENTRY_H + 6);
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ENTRY_H) {
                ClientState.selected = types[i];
                Network.CHANNEL.sendToServer(new SelectWeaponPacket(types[i]));
                Cinematic.play(SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.8f);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientSetup.WEAPONS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
