package com.example.beautifuldecor.client;

import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.TrainMenuOpenPacket;
import com.example.beautifuldecor.network.TrainMenuSavePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** V6.0.0: menú del Mando de trenes: nombre del tren, automático sí/no, ruta (las estaciones en orden; la última es el depósito) y borrar la ruta. */
public class TrainRemoteScreen extends Screen {
    private static final int W = 220;
    private final TrainMenuOpenPacket data;
    private EditBox nameBox;
    private boolean confirmClear;

    public TrainRemoteScreen(TrainMenuOpenPacket data) {
        super(Component.translatable("gui.beautifuldecor.train.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int left = (this.width - W) / 2;
        int top = Math.max(8, this.height / 2 - 95);
        String keep = this.nameBox != null ? this.nameBox.getValue() : this.data.name;
        this.nameBox = new EditBox(this.font, left, top + 28, W, 20, Component.empty());
        this.nameBox.setMaxLength(24);
        this.nameBox.setValue(keep);
        this.addRenderableWidget(this.nameBox);
        boolean noRoute = this.data.stops.isEmpty();
        Component autoLabel = Component.translatable(this.data.auto ? "gui.beautifuldecor.train.auto_on" : "gui.beautifuldecor.train.auto_off");
        Button auto = Button.builder(autoLabel, b -> send(this.data.auto ? 2 : 1)).bounds(left, top + 54, W, 20).build();
        auto.active = !noRoute || this.data.auto;
        this.addRenderableWidget(auto);
        Component clearLabel = Component.translatable(this.confirmClear ? "gui.beautifuldecor.train.clear_confirm" : "gui.beautifuldecor.train.clear");
        Button clear = Button.builder(clearLabel, b -> {
            if (this.confirmClear) {
                send(3);
            } else {
                this.confirmClear = true;
                this.rebuildWidgets();
            }
        }).bounds(left, top + 78, W, 20).build();
        clear.active = !noRoute;
        this.addRenderableWidget(clear);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.beautifuldecor.train.ok"), b -> send(0)).bounds(left, top + 162, W / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.beautifuldecor.train.cancel"), b -> this.onClose())
                .bounds(left + W / 2 + 2, top + 162, W / 2 - 2, 20).build());
    }

    private void send(int action) {
        ModMessages.CHANNEL.sendToServer(new TrainMenuSavePacket(this.data.entityId, this.nameBox.getValue(), action));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        int left = (this.width - W) / 2;
        int top = Math.max(8, this.height / 2 - 95);
        g.drawCenteredString(this.font, this.title, this.width / 2, top + 4, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.train.name"), left, top + 17, 0xA0A0A0);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.train.route", this.data.stops.size()), left, top + 104, 0xA0A0A0);
        int y = top + 116;
        int shown = Math.min(4, this.data.stops.size());
        for (int i = 0; i < shown; i++) {
            boolean depot = i == this.data.stops.size() - 1;
            String line = (i + 1) + ". " + this.data.stops.get(i) + (depot ? "  (" + Component.translatable("gui.beautifuldecor.train.depot").getString() + ")" : "");
            g.drawString(this.font, line, left + 4, y, depot ? 0xFFD060 : 0xFFFFFF);
            y += 10;
        }
        if (this.data.stops.size() > shown) {
            g.drawString(this.font, "…", left + 4, y, 0xA0A0A0);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
