package com.arsenalatomico.client;

import com.arsenalatomico.BaseLayout;
import com.arsenalatomico.net.FireSiloPacket;
import com.arsenalatomico.net.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * El panel de un silo chiquito o mediano: se escriben las coordenadas X y Z exactas del blanco (la
 * altura la pone el servidor, la del suelo) y se oprime LANZAR.
 */
public class TargetScreen extends Screen {
    private final int silo, safe;
    private EditBox xBox, zBox;
    private Component error;

    public static void open(int silo, int safe) {
        Minecraft.getInstance().setScreen(new TargetScreen(silo, safe));
    }

    public TargetScreen(int silo, int safe) {
        super(Component.translatable("screen.arsenalatomico.target.title"));
        this.silo = silo;
        this.safe = safe;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        xBox = new EditBox(font, cx - 104, cy - 6, 96, 20, Component.literal("X"));
        zBox = new EditBox(font, cx + 8, cy - 6, 96, 20, Component.literal("Z"));
        for (EditBox b : new EditBox[]{xBox, zBox}) {
            b.setMaxLength(9);
            b.setFilter(t -> t.isEmpty() || t.matches("-?\\d*"));
            addRenderableWidget(b);
        }
        setInitialFocus(xBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.arsenalatomico.target.launch"), b -> launch())
                .bounds(cx - 104, cy + 26, 208, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(cx - 104, cy + 52, 208, 20).build());
    }

    private void launch() {
        try {
            int x = Integer.parseInt(xBox.getValue().trim()), z = Integer.parseInt(zBox.getValue().trim());
            Cinematic.play(SoundEvents.STONE_BUTTON_CLICK_ON, 0.6f, 1f);
            Cinematic.play(com.arsenalatomico.registry.ModSounds.ALARM.get(), 1f, 0.8f);
            Network.CHANNEL.sendToServer(new FireSiloPacket(silo, x, z));
            onClose();
        } catch (NumberFormatException e) {
            error = Component.translatable("screen.arsenalatomico.target.bad");
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {   // Enter
            launch();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int cx = width / 2, cy = height / 2;
        boolean small = BaseLayout.small(silo);
        g.fill(cx - 120, cy - 82, cx + 120, cy + 84, 0xF0101214);
        g.fill(cx - 120, cy - 82, cx + 120, cy - 80, small ? 0xFFE0B020 : 0xFFE07020);
        for (int x = cx - 120; x < cx + 120; x += 12) g.fill(x, cy + 80, x + 6, cy + 84, 0xFFE0B020);
        g.drawCenteredString(font, title, cx, cy - 72, 0xFFFF5040);
        g.drawCenteredString(font, Component.translatable(small ? "screen.arsenalatomico.target.small" : "screen.arsenalatomico.target.medium", silo),
                cx, cy - 58, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.arsenalatomico.target.hint", safe), cx, cy - 44, 0xFFB8C0D0);
        g.drawString(font, "X", cx - 104, cy - 18, 0xFFFFD040, false);
        g.drawString(font, "Z", cx + 8, cy - 18, 0xFFFFD040, false);
        if (error != null) g.drawCenteredString(font, error, cx, cy + 14, 0xFFFF5D5D);
        super.render(g, mouseX, mouseY, partial);
    }
}
