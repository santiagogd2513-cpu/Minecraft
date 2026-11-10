package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.SecurityCameraBlock;
import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import com.example.beautifuldecor.camera.CameraInfo;
import com.example.beautifuldecor.network.CameraEditPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * V4.3.0: menú de la cámara de seguridad (se abre al ponerla y con clic derecho): nombre y descripción, para distinguir una cámara de
 * otra en la lista del monitor, y si la cámara rota (barrido lento) o se queda fija.
 */
public class CameraEditScreen extends Screen {
    private static final int W = 260;
    private static final int H = 168;

    private final BlockPos pos;
    private EditBox nameBox;
    private EditBox descBox;
    private boolean rotate;
    private String nameText = "";
    private String descText = "";
    private boolean loaded;
    private int left;
    private int top;

    public CameraEditScreen(BlockPos pos) {
        super(Component.translatable("gui.beautifuldecor.camera.edit_title"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        super.init();
        if (!this.loaded) {
            this.loaded = true;
            if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof SecurityCameraBlockEntity cam) {
                this.nameText = cam.getCameraName();
                this.descText = cam.getDescription();
                this.rotate = cam.isRotating();
            }
        } else if (this.nameBox != null) {
            this.nameText = this.nameBox.getValue();
            this.descText = this.descBox.getValue();
        }
        this.left = (this.width - W) / 2;
        this.top = Math.max(2, (this.height - H) / 2);
        this.nameBox = new EditBox(this.font, this.left + 12, this.top + 50, W - 24, 16, Component.translatable("gui.beautifuldecor.camera.name"));
        this.nameBox.setMaxLength(CameraInfo.MAX_NAME);
        this.nameBox.setValue(this.nameText);
        this.descBox = new EditBox(this.font, this.left + 12, this.top + 84, W - 24, 16, Component.translatable("gui.beautifuldecor.camera.description"));
        this.descBox.setMaxLength(CameraInfo.MAX_DESC);
        this.descBox.setValue(this.descText);
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(this.descBox);
        setInitialFocus(this.nameBox);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null || !(player.level().getBlockState(this.pos).getBlock() instanceof SecurityCameraBlock)
                || player.distanceToSqr(Vec3.atCenterOf(this.pos)) > 10.0 * 10.0) {
            onClose();
            return;
        }
        this.nameBox.tick();
        this.descBox.tick();
    }

    private int rotateY() {
        return this.top + 108;
    }

    private int buttonsY() {
        return this.top + H - 28;
    }

    private void save() {
        ModMessages.sendToServer(new CameraEditPacket(this.pos, this.nameBox.getValue(), this.descBox.getValue(), this.rotate));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (DarkUi.inside(mx, my, this.left + 12, rotateY(), W - 24, 18)) {
                this.rotate = !this.rotate;
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 12, buttonsY(), 110, 18)) {
                save();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + W - 122, buttonsY(), 110, 18)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && (this.nameBox.isFocused() || this.descBox.isFocused())) {   // Intro guarda
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, this.left, this.top, W, H, 30);
        gui.drawString(this.font, this.title, this.left + 14, this.top + 8, DarkUi.ACCENT);
        CameraClientRegistry.Entry e = CameraClientRegistry.get(this.pos.asLong());
        String sub = (e == null ? "" : "#" + e.id() + "  ") + this.pos.getX() + ", " + this.pos.getY() + ", " + this.pos.getZ();
        gui.drawString(this.font, sub, this.left + 14, this.top + 19, DarkUi.MUTED);
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.camera.name"), this.left + 12, this.top + 39, DarkUi.TEXT);
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.camera.description"), this.left + 12, this.top + 73, DarkUi.TEXT);
        int ry = rotateY();
        DarkUi.button(gui, this.font, this.left + 12, ry, W - 24, 18,
                DarkUi.tr(this.rotate ? "gui.beautifuldecor.camera.rotation_on" : "gui.beautifuldecor.camera.rotation_off"),
                DarkUi.inside(mouseX, mouseY, this.left + 12, ry, W - 24, 18), this.rotate);
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.camera.rotation_hint"), this.left + 12, ry + 22, DarkUi.MUTED);
        int by = buttonsY();
        DarkUi.button(gui, this.font, this.left + 12, by, 110, 18, DarkUi.tr("gui.beautifuldecor.camera.save"),
                DarkUi.inside(mouseX, mouseY, this.left + 12, by, 110, 18), true);
        DarkUi.button(gui, this.font, this.left + W - 122, by, 110, 18, DarkUi.tr("gui.beautifuldecor.camera.cancel"),
                DarkUi.inside(mouseX, mouseY, this.left + W - 122, by, 110, 18), false);
        super.render(gui, mouseX, mouseY, partialTick);
    }
}
