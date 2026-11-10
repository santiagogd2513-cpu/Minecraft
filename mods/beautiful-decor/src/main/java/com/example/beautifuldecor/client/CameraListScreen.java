package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.SecurityMonitorBlock;
import com.example.beautifuldecor.block.entity.SecurityMonitorBlockEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.MonitorSelectPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * V4.3.0: menú del monitor (o pantalla de pared) de cámaras de seguridad: la lista de TODAS las cámaras (del 1 en adelante, sin límite)
 * con su nombre y, en pequeño, sus coordenadas. Al tocar una, el monitor empieza a mostrar su imagen. Se desplaza con la rueda, con la
 * barra de la derecha o arrastrando la lista con el dedo.
 */
public class CameraListScreen extends Screen {
    private static final int W = 280;
    private static final int ROW = 27;
    private static final int BAR_W = 6;
    private static final int DRAG_SLOP = 5;

    private final BlockPos monitor;
    private List<CameraClientRegistry.Entry> rows = List.of();
    private int left;
    private int top;
    private int height2;
    private int scroll;
    private boolean draggingBar;
    private double barGrab;
    /** Arrastre de la lista con el dedo: dónde empezó y si ya se movió lo bastante para no contar como un toque. */
    private boolean pressing;
    private double pressX;
    private double pressY;
    private int pressScroll;
    private boolean dragged;

    public CameraListScreen(BlockPos monitor) {
        super(Component.translatable("gui.beautifuldecor.camera.list_title"));
        this.monitor = monitor.immutable();
    }

    @Override
    protected void init() {
        super.init();
        this.height2 = Math.max(180, Math.min(270, this.height - 8));
        this.left = (this.width - W) / 2;
        this.top = Math.max(2, (this.height - this.height2) / 2);
        refresh();
    }

    private void refresh() {
        this.rows = CameraClientRegistry.sorted();
        clampScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null || !(player.level().getBlockState(this.monitor).getBlock() instanceof SecurityMonitorBlock)
                || player.distanceToSqr(Vec3.atCenterOf(this.monitor)) > 10.0 * 10.0) {
            onClose();
            return;
        }
        if (this.rows.size() != CameraClientRegistry.count()) {
            refresh();
        }
    }

    // ------------------------------------------------------------------ medidas

    private int listTop() {
        return this.top + 38;
    }

    private int listBottom() {
        return this.top + this.height2 - 34;
    }

    private int listHeight() {
        return listBottom() - listTop();
    }

    private int contentHeight() {
        return this.rows.size() * ROW;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - listHeight());
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
    }

    private boolean scrollable() {
        return maxScroll() > 0;
    }

    private int barX() {
        return this.left + W - BAR_W - 3;
    }

    private int rowsRight() {
        return scrollable() ? barX() - 3 : this.left + W - 6;
    }

    private int barThumbH() {
        return Math.max(18, listHeight() * listHeight() / Math.max(1, contentHeight()));
    }

    private int barThumbY() {
        int free = listHeight() - barThumbH();
        return listTop() + (maxScroll() == 0 ? 0 : (int) ((long) free * this.scroll / maxScroll()));
    }

    private int offY() {
        return this.top + this.height2 - 26;
    }

    private BlockPos currentCamera() {
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.monitor) instanceof SecurityMonitorBlockEntity m) {
            return m.getCamera();
        }
        return null;
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (DarkUi.inside(mx, my, this.left, listTop(), W, listHeight())) {
            this.scroll -= (int) (delta * ROW);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (DarkUi.inside(mx, my, this.left + 10, offY(), 120, 18)) {
            ModMessages.sendToServer(MonitorSelectPacket.off(this.monitor));
            onClose();
            return true;
        }
        if (DarkUi.inside(mx, my, this.left + W - 90, offY(), 80, 18)) {
            onClose();
            return true;
        }
        if (scrollable() && DarkUi.inside(mx, my, barX() - 4, listTop(), BAR_W + 8, listHeight())) {
            this.draggingBar = true;
            int ty = barThumbY();
            this.barGrab = (my >= ty && my < ty + barThumbH()) ? my - ty : barThumbH() / 2.0;
            dragBarTo(my);
            return true;
        }
        if (DarkUi.inside(mx, my, this.left, listTop(), W, listHeight())) {
            this.pressing = true;
            this.dragged = false;
            this.pressX = mx;
            this.pressY = my;
            this.pressScroll = this.scroll;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void dragBarTo(double my) {
        int free = listHeight() - barThumbH();
        if (free <= 0) {
            return;
        }
        double t = (my - this.barGrab - listTop()) / free;
        this.scroll = (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * maxScroll());
        clampScroll();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.draggingBar && button == 0) {
            dragBarTo(my);
            return true;
        }
        if (this.pressing && button == 0) {
            if (Math.abs(my - this.pressY) > DRAG_SLOP || Math.abs(mx - this.pressX) > DRAG_SLOP) {
                this.dragged = true;
            }
            if (this.dragged) {
                this.scroll = this.pressScroll + (int) (this.pressY - my);
                clampScroll();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && this.draggingBar) {
            this.draggingBar = false;
            return true;
        }
        if (button == 0 && this.pressing) {
            this.pressing = false;
            if (!this.dragged && DarkUi.inside(mx, my, this.left, listTop(), rowsRight() - this.left, listHeight())) {
                int index = (int) ((my - listTop() + this.scroll) / ROW);
                if (index >= 0 && index < this.rows.size()) {
                    ModMessages.sendToServer(MonitorSelectPacket.show(this.monitor, BlockPos.of(this.rows.get(index).pos())));
                    onClose();
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // ------------------------------------------------------------------ dibujo

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, width - this.font.width("…")) + "…";
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, this.left, this.top, W, this.height2, 30);
        gui.drawString(this.font, this.title, this.left + 14, this.top + 8, DarkUi.ACCENT);
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.camera.list_count", this.rows.size()), this.left + 14, this.top + 19, DarkUi.MUTED);
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.camera.list_hint"), this.left + 10, this.top + 33, DarkUi.MUTED);

        int lt = listTop();
        int lh = listHeight();
        gui.enableScissor(this.left + 1, lt, this.left + W - 1, lt + lh);
        BlockPos current = currentCamera();
        long currentKey = current == null ? Long.MIN_VALUE : current.asLong();
        if (this.rows.isEmpty()) {
            gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.camera.list_empty"), this.left + 14, lt + 10, DarkUi.WARN);
        }
        int y = lt - this.scroll;
        for (CameraClientRegistry.Entry e : this.rows) {
            if (y + ROW >= lt && y <= lt + lh) {
                boolean hover = !this.dragged && DarkUi.inside(mouseX, mouseY, this.left + 6, y, rowsRight() - this.left - 6, ROW - 2)
                        && DarkUi.inside(mouseX, mouseY, this.left, lt, W, lh);
                boolean selected = e.pos() == currentKey;
                gui.fill(this.left + 6, y, rowsRight(), y + ROW - 2, selected ? DarkUi.BUTTON_ACTIVE : hover ? DarkUi.BUTTON_HOVER : DarkUi.BUTTON);
                gui.fill(this.left + 6, y, this.left + 9, y + ROW - 2, selected ? DarkUi.OK : DarkUi.BORDER);
                String num = "#" + e.id();
                gui.drawString(this.font, num, this.left + 14, y + 4, DarkUi.ACCENT);
                int nameX = this.left + 14 + this.font.width(num) + 6;
                gui.drawString(this.font, fit(e.name(), rowsRight() - nameX - 6), nameX, y + 4, DarkUi.TEXT);
                BlockPos p = BlockPos.of(e.pos());
                PoseStack pose = gui.pose();
                pose.pushPose();
                pose.translate(this.left + 14, y + 16, 0);
                pose.scale(0.75f, 0.75f, 1f);
                gui.drawString(this.font, p.getX() + ", " + p.getY() + ", " + p.getZ(), 0, 0, DarkUi.MUTED);
                pose.popPose();
            }
            y += ROW;
        }
        gui.disableScissor();
        if (scrollable()) {
            gui.fill(barX() + 2, lt, barX() + 4, lt + lh, DarkUi.BORDER);
            boolean grab = this.draggingBar || (DarkUi.inside(mouseX, mouseY, barX() - 4, lt, BAR_W + 8, lh)
                    && mouseY >= barThumbY() && mouseY < barThumbY() + barThumbH());
            gui.fill(barX(), barThumbY(), barX() + BAR_W, barThumbY() + barThumbH(), grab ? DarkUi.ACCENT : DarkUi.MUTED);
        }
        int oy = offY();
        DarkUi.button(gui, this.font, this.left + 10, oy, 120, 18, DarkUi.tr("gui.beautifuldecor.camera.turn_off"),
                DarkUi.inside(mouseX, mouseY, this.left + 10, oy, 120, 18), false);
        DarkUi.button(gui, this.font, this.left + W - 90, oy, 80, 18, DarkUi.tr("gui.beautifuldecor.camera.close"),
                DarkUi.inside(mouseX, mouseY, this.left + W - 90, oy, 80, 18), false);
        super.render(gui, mouseX, mouseY, partialTick);
    }
}
