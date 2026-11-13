package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.SkinnableVehicle;
import net.minecraft.world.entity.Entity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.VehicleSkinPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** V6.7.0: ficha "Skins" del menú de colores: una lista de las skins del vehículo; al escoger una se aplica enseguida. */
public class VehicleSkinScreen extends Screen {
    private final Entity entity;
    private final SkinnableVehicle vehicle;
    private final Screen parent;

    public VehicleSkinScreen(Entity entity, SkinnableVehicle vehicle, Screen parent) {
        super(Component.translatable("gui.beautifuldecor.skins.title"));
        this.entity = entity;
        this.vehicle = vehicle;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int n = this.vehicle.skinCount();
        int y = this.height / 2 - n * 12;
        for (int i = 0; i < n; i++) {
            final int skin = i;
            Component name = Component.translatable(this.vehicle.skinKey(i));
            Button b = Button.builder(i == this.vehicle.getSkin() ? Component.literal("> ").append(name).append(" <") : name, btn -> {
                ModMessages.CHANNEL.sendToServer(new VehicleSkinPacket(this.entity.getId(), skin));
                this.minecraft.setScreen(this.parent);
            }).bounds(this.width / 2 - 100, y + i * 24, 200, 20).build();
            this.addRenderableWidget(b);
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), btn -> this.onClose())
                .bounds(this.width / 2 - 100, y + n * 24 + 8, 200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - this.vehicle.skinCount() * 12 - 24, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
