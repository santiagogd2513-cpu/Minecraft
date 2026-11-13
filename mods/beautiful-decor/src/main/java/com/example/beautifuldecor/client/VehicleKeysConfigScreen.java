package com.example.beautifuldecor.client;

import com.example.beautifuldecor.config.ModClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** V6.3.0: pantalla de configuración del mod: activar las teclas de las funciones de los interiores (cuadro con cada función y su tecla). */
public class VehicleKeysConfigScreen extends Screen {
    private final Screen parent;
    private Button toggle;

    public VehicleKeysConfigScreen(Screen parent) {
        super(Component.translatable("config.beautifuldecor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.toggle = Button.builder(this.toggleText(), b -> {
            ModClientConfig.VEHICLE_KEYS.set(!ModClientConfig.vehicleKeys());
            ModClientConfig.SPEC.save();
            b.setMessage(this.toggleText());
        }).bounds(this.width / 2 - 100, 40, 200, 20).build();
        this.addRenderableWidget(this.toggle);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private Component toggleText() {
        return Component.translatable("config.beautifuldecor.vehicle_keys",
                Component.translatable(ModClientConfig.vehicleKeys() ? "options.on" : "options.off"));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
        int y = 70;
        g.drawCenteredString(this.font, Component.translatable("config.beautifuldecor.explain"), this.width / 2, y, 0xFFE9A0);
        y += 18;
        String[][] rows = {
                {"config.beautifuldecor.fn_horn", VehicleKeys.HORN.getTranslatedKeyMessage().getString()},
                {"config.beautifuldecor.fn_light", VehicleKeys.LIGHT.getTranslatedKeyMessage().getString()},
                {"config.beautifuldecor.fn_radio", VehicleKeys.RADIO.getTranslatedKeyMessage().getString()},
                {"config.beautifuldecor.fn_gear", AirlinerKeys.GEAR.getTranslatedKeyMessage().getString()}};
        for (String[] r : rows) {
            g.drawString(this.font, Component.translatable(r[0]), this.width / 2 - 90, y, 0xFFFFFF);
            g.drawString(this.font, "[" + r[1] + "]", this.width / 2 + 50, y, 0xA0FFA0);
            y += 14;
        }
        g.drawCenteredString(this.font, Component.translatable("config.beautifuldecor.change_keys"), this.width / 2, y + 8, 0xB8B8C4);
    }
}
