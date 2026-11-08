package com.example.beautifuldecor.client;

import com.example.beautifuldecor.elevator.ElevatorLines;
import com.example.beautifuldecor.network.ElevatorCallPacket;
import com.example.beautifuldecor.network.ElevatorDisplayPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Lista de pisos de la pantalla de ascensor (V2.2.0), en modo oscuro. Los pisos más altos van arriba, como en un tablero
 * de ascensor real; sirve para cualquier cantidad de pisos (la lista se desplaza con la rueda del mouse).
 * <p>
 * V3.4.0: el servidor manda las paradas ya en el orden de la pantalla: los pisos de la columna (de arriba hacia abajo) y después las
 * paradas de lado, con su dirección y distancia a la derecha ("Este 12").
 */
public class ElevatorDisplayScreen extends Screen {
    private static final int W = 250;
    private static final int ROW = 26;
    private static final int HEADER = 40;

    private final ElevatorDisplayPacket data;
    private int left;
    private int top;
    private int panelHeight;
    private int scroll;

    public ElevatorDisplayScreen(ElevatorDisplayPacket data) {
        super(Component.translatable("block.beautifuldecor.elevator_display"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        int content = this.data.floors.size() * ROW;
        this.panelHeight = Mth.clamp(HEADER + content + 10, HEADER + 3 * ROW + 10, this.height - 24);
        this.left = (this.width - W) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        // arranca mostrando el piso de la cabina (o el de esta pantalla)
        int focus = this.data.ownFloor;
        for (int i = 0; i < this.data.floors.size(); i++) {
            if (this.data.floors.get(i).cabinHere()) {
                focus = i;
            }
        }
        if (focus >= 0) {
            this.scroll = focus * ROW - (viewHeight() - ROW) / 2;
        }
        clampScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int viewTop() {
        return this.top + HEADER + 4;
    }

    private int viewHeight() {
        return this.panelHeight - HEADER - 8;
    }

    private void clampScroll() {
        int max = Math.max(0, this.data.floors.size() * ROW - viewHeight());
        this.scroll = Mth.clamp(this.scroll, 0, max);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        this.scroll -= (int) (delta * 18.0);
        clampScroll();
        return true;
    }

    /** Piso mostrado en la fila {@code row} (V3.4.0: la lista ya viene en el orden de la pantalla; la fila 0 es el piso más alto). */
    private int floorAt(int row) {
        return row;
    }

    /** ¿Hay paradas de lado? (entonces el subtítulo dice "paradas" en vez de "pisos") */
    private boolean hasSideStops() {
        for (ElevatorDisplayPacket.FloorEntry floor : this.data.floors) {
            if (floor.axis() != ElevatorLines.AXIS_Y) {
                return true;
            }
        }
        return false;
    }

    /** Lo que va a la derecha de una parada que no es la de la cabina ni esta: la altura (columna) o la dirección y distancia (de lado). */
    private String where(ElevatorDisplayPacket.FloorEntry floor) {
        if (floor.axis() == ElevatorLines.AXIS_Y) {
            return "Y " + floor.pos().getY();
        }
        if (this.data.ownFloor < 0 || this.data.ownFloor >= this.data.floors.size()) {
            return (floor.axis() == ElevatorLines.AXIS_X ? "X " : "Z ") + ElevatorLines.along(floor.axis(), floor.pos().getX(), floor.pos().getY(), floor.pos().getZ());
        }
        var own = this.data.floors.get(this.data.ownFloor).pos();
        int from = ElevatorLines.along(floor.axis(), own.getX(), own.getY(), own.getZ());
        int to = ElevatorLines.along(floor.axis(), floor.pos().getX(), floor.pos().getY(), floor.pos().getZ());
        return DarkUi.tr("gui.beautifuldecor.elevator.dir." + ElevatorLines.directionKey(floor.axis(), from, to), Math.abs(to - from));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && DarkUi.inside(mx, my, this.left + 6, viewTop(), W - 12, viewHeight())) {
            int row = (int) ((my - viewTop() + this.scroll) / ROW);
            if (row >= 0 && row < this.data.floors.size()) {
                ElevatorDisplayPacket.FloorEntry floor = this.data.floors.get(floorAt(row));
                ModMessages.sendToServer(new ElevatorCallPacket(this.data.displayPos, floor.pos()));
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, this.left, this.top, W, this.panelHeight, HEADER - 4);
        gui.drawString(this.font, this.title, this.left + 14, this.top + 8, DarkUi.ACCENT);
        String sub = this.data.moving ? DarkUi.tr("gui.beautifuldecor.elevator.moving")
                : DarkUi.tr(hasSideStops() ? "gui.beautifuldecor.elevator.stops" : "gui.beautifuldecor.elevator.floors", this.data.floors.size());
        gui.drawString(this.font, sub, this.left + 14, this.top + 21, this.data.moving ? DarkUi.WARN : DarkUi.MUTED);

        int viewTop = viewTop();
        int viewHeight = viewHeight();
        gui.enableScissor(this.left + 1, viewTop, this.left + W - 1, viewTop + viewHeight);
        List<ElevatorDisplayPacket.FloorEntry> floors = this.data.floors;
        for (int row = 0; row < floors.size(); row++) {
            int y = viewTop + row * ROW - this.scroll;
            if (y + ROW < viewTop || y > viewTop + viewHeight) {
                continue;
            }
            int index = floorAt(row);
            ElevatorDisplayPacket.FloorEntry floor = floors.get(index);
            boolean hover = DarkUi.inside(mouseX, mouseY, this.left + 6, y, W - 12, ROW - 2)
                    && DarkUi.inside(mouseX, mouseY, this.left + 6, viewTop, W - 12, viewHeight);
            boolean own = index == this.data.ownFloor;
            gui.fill(this.left + 6, y, this.left + W - 6, y + ROW - 2, own ? DarkUi.BUTTON_ACTIVE : hover ? DarkUi.BUTTON_HOVER : DarkUi.BUTTON);
            gui.fill(this.left + 6, y, this.left + 9, y + ROW - 2, floor.cabinHere() ? DarkUi.OK : DarkUi.BORDER);
            gui.drawString(this.font, floor.name(), this.left + 18, y + 8, DarkUi.TEXT);
            String right = floor.cabinHere() ? DarkUi.tr("gui.beautifuldecor.elevator.cabin")
                    : own ? DarkUi.tr("gui.beautifuldecor.elevator.here") : where(floor);
            int rightColor = floor.cabinHere() ? DarkUi.OK : own ? DarkUi.ACCENT : DarkUi.MUTED;
            gui.drawString(this.font, right, this.left + W - 16 - this.font.width(right), y + 8, rightColor);
        }
        gui.disableScissor();
        super.render(gui, mouseX, mouseY, partialTick);
    }
}
