package com.example.beautifuldecor.client;

import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.StationOpenPacket;
import com.example.beautifuldecor.network.StationSavePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** V6.0.0: ajustes de una estación: nombre, espera, velocidades y el horario del depósito (solo cuenta si es la última parada). */
public class StationScreen extends Screen {
    private static final int W = 220;
    private final StationOpenPacket data;
    private EditBox nameBox;
    private EditBox dwell;
    private EditBox depart;
    private EditBox travel;
    private EditBox open;
    private EditBox close;

    public StationScreen(StationOpenPacket data) {
        super(Component.translatable("gui.beautifuldecor.station.title"));
        this.data = data;
    }

    private EditBox number(int x, int y, int w, String value, int maxLen) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.empty());
        box.setMaxLength(maxLen);
        box.setFilter(s -> s.matches("\\d*"));
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    @Override
    protected void init() {
        int left = (this.width - W) / 2;
        int top = Math.max(8, this.height / 2 - 115);
        String keep = this.nameBox != null ? this.nameBox.getValue() : this.data.name;
        this.nameBox = new EditBox(this.font, left, top + 34, W, 20, Component.empty());
        this.nameBox.setMaxLength(24);
        this.nameBox.setValue(keep);
        this.addRenderableWidget(this.nameBox);
        this.dwell = number(left, top + 70, 100, String.valueOf(this.data.dwell), 3);
        this.depart = number(left, top + 106, 100, String.valueOf(this.data.depart), 3);
        this.travel = number(left + 120, top + 106, 100, String.valueOf(this.data.travel), 3);
        this.open = number(left, top + 154, 100, String.valueOf(this.data.open), 2);
        this.close = number(left + 120, top + 154, 100, String.valueOf(this.data.close), 2);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.beautifuldecor.train.ok"), b -> save()).bounds(left, top + 190, W / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.beautifuldecor.train.cancel"), b -> this.onClose())
                .bounds(left + W / 2 + 2, top + 190, W / 2 - 2, 20).build());
    }

    private static int num(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        ModMessages.CHANNEL.sendToServer(new StationSavePacket(this.data.pos, this.nameBox.getValue(), num(this.dwell, 20), num(this.depart, 40),
                num(this.travel, 80), num(this.open, 0), num(this.close, 24)));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        int left = (this.width - W) / 2;
        int top = Math.max(8, this.height / 2 - 115);
        g.drawCenteredString(this.font, this.title, this.width / 2, top + 4, 0xFFFFFF);
        if (this.data.index >= 0) {
            boolean depot = this.data.index == this.data.total - 1;
            Component where = depot ? Component.translatable("gui.beautifuldecor.station.depot", this.data.index + 1)
                    : Component.translatable("gui.beautifuldecor.station.stop", this.data.index + 1, this.data.total);
            g.drawCenteredString(this.font, where, this.width / 2, top + 16, depot ? 0xFFD060 : 0x80E080);
        }
        int grey = 0xA0A0A0;
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.name"), left, top + 24, grey);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.dwell"), left, top + 60, grey);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.depart"), left, top + 96, grey);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.travel"), left + 120, top + 96, grey);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.schedule"), left, top + 130, grey);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.open"), left, top + 143, grey);
        g.drawString(this.font, Component.translatable("gui.beautifuldecor.station.close"), left + 120, top + 143, grey);
        super.render(g, mouseX, mouseY, partial);
    }
}
