package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.entity.TelevisionBlockEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.TelevisionPathPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TelevisionScreen extends Screen {
    private final BlockPos pos;
    private EditBox pathBox;
    private String status = "";

    public TelevisionScreen(BlockPos pos) {
        super(Component.translatable("block.beautifuldecor.television"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;

        String existing = "";
        if (this.minecraft != null && this.minecraft.level != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.pos);
            if (be instanceof TelevisionBlockEntity tv) {
                existing = tv.getVideoPath();
                if (existing == null) existing = "";
            }
        }

        this.pathBox = new EditBox(this.font, cx - 150, cy - 10, 300, 20, Component.literal("path"));
        this.pathBox.setMaxLength(TelevisionPathPacket.MAX_PATH);
        this.pathBox.setValue(existing);
        this.pathBox.setBordered(true);
        this.pathBox.setEditable(true);
        this.addRenderableWidget(this.pathBox);
        this.setInitialFocus(this.pathBox);

        this.addRenderableWidget(Button.builder(Component.literal("Reproducir"), btn -> {
            String path = this.pathBox.getValue().trim();
            if (path.isEmpty()) {
                this.status = "Pega un enlace o escribe una ruta.";
                return;
            }
            ModMessages.sendToServer(new TelevisionPathPacket(this.pos, path, true));
            this.status = "Reproduciendo: " + fileName(path);
        }).bounds(cx - 150, cy + 20, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Detener"), btn -> {
            ModMessages.sendToServer(new TelevisionPathPacket(this.pos, this.pathBox.getValue().trim(), false));
            this.status = "Detenido.";
        }).bounds(cx - 45, cy + 20, 90, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), btn -> this.onClose())
                .bounds(cx + 55, cy + 20, 95, 20).build());
    }

    private static String fileName(String path) {
        int a = path.lastIndexOf('/');
        int b = path.lastIndexOf('\\');
        int i = Math.max(a, b);
        return i >= 0 ? path.substring(i + 1) : path;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        gui.drawCenteredString(this.font, "Televisor - Enlace del video (MP4)", this.width / 2, this.height / 2 - 67, 0xFFFFFF);
        gui.drawCenteredString(this.font, "Pega el enlace directo de un .mp4: https://sitio.com/video.mp4", this.width / 2, this.height / 2 - 52, 0xA0A0A0);
        gui.drawCenteredString(this.font, "o la ruta de un archivo de este equipo:", this.width / 2, this.height / 2 - 40, 0xA0A0A0);
        gui.drawCenteredString(this.font, "/storage/emulated/0/Movies/video.mp4  (Android)", this.width / 2, this.height / 2 - 28, 0x808080);
        gui.drawCenteredString(this.font, "Solo MP4 con H.264 y sonido AAC, hasta 1280x720 (igual que en el navegador HS)", this.width / 2, this.height / 2 + 68, 0x808080);
        if (this.status != null && !this.status.isEmpty()) {
            gui.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 50, 0x55FF55);
        }
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.pathBox != null && this.pathBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
