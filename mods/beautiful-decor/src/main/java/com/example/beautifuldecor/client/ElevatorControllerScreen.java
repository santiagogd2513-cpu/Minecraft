package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.entity.ElevatorControllerBlockEntity;
import com.example.beautifuldecor.elevator.ElevatorShaft;
import com.example.beautifuldecor.elevator.ElevatorZone;
import com.example.beautifuldecor.entity.ElevatorCabinEntity;
import com.example.beautifuldecor.network.ElevatorControllerPacket;
import com.example.beautifuldecor.network.ElevatorSavePacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Menú del controlador de ascensor, en modo oscuro (V2.2.0, rediseñado en la V3.2.1).
 * <p>
 * Tres columnas: a la IZQUIERDA lo que cambia DÓNDE queda la cabina (el lado respecto del controlador, con una brújula, y desde la
 * V3.3.0 el desplazamiento en X, Y y Z) y el nombre del piso; en el CENTRO la plataforma en vivo (los bloques que de verdad hay
 * ahora en la zona, vista desde arriba o capa por capa, con el color de cada bloque); y a la DERECHA el tamaño (ancho, fondo y
 * alto, con el eje del mundo de cada uno) y la velocidad. Mientras está abierto, la zona queda marcada en el mundo y la
 * cuadrícula se vuelve a leer del mundo cada cuarto de segundo y en cuanto se cambia algo.
 */
public class ElevatorControllerScreen extends Screen {
    private static final String[] ROW_KEYS = {
            "gui.beautifuldecor.elevator.width", "gui.beautifuldecor.elevator.depth", "gui.beautifuldecor.elevator.height"};
    private static final String[] STEP_LABELS = {"-5", "-1", "+1", "+5"};
    private static final String[] SHIFT_AXES = {"X", "Y", "Z"};
    private static final String[] SPEED_LABELS = {"-5", "-0.5", "+0.5", "+5"};
    /** Lado que corresponde a cada botón de la brújula (en el orden de ElevatorMenuLayout.SIDE_*). */
    private static final Direction[] SIDE_BY_SLOT = {Direction.NORTH, Direction.WEST, Direction.EAST, Direction.SOUTH};
    /** Cada cuántos ticks se vuelve a leer el mundo (5 ticks = un cuarto de segundo). */
    private static final int SCAN_EVERY = 5;
    /** Cada cuántos ticks se renueva el contorno de la zona en el mundo (que se apaga solo a los 20 segundos). */
    private static final int OVERLAY_EVERY = 160;
    /** Margen dentro del recuadro de la plataforma, donde se dibuja la marca del controlador. */
    private static final int GRID_INSET = 9;
    private static final int MAX_CELL = 24;
    private static final int GRID_BG = 0xFF0B0D11;
    private static final int CELL_EMPTY = 0xFF171B22;
    private static final int STAY_FILL = 0xFF3A1A1E;
    private static final int STAY_EDGE = 0xFFE05A5A;
    /** Color de los bloques que el mapa deja sin color (vidrio, por ejemplo). */
    private static final int NO_MAP_COLOR = 0x9AD8E8;

    private final BlockPos pos;
    private Direction facing;
    private int cabWidth;
    private int cabDepth;
    private int cabHeight;
    /** Cuánto está corrida la zona en X, Y y Z (V3.3.0). */
    private final int[] shift = new int[3];
    private float speed;
    private final String initialName;
    private EditBox nameBox;
    private ElevatorMenuLayout lay;
    private int left;
    private int top;

    private int layer = ElevatorPlatformView.TOP;
    private ElevatorPlatformView.Grid grid;
    private ElevatorZone gridZone;
    private boolean travelling;
    private boolean dirty = true;
    private int ticks;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    public ElevatorControllerScreen(ElevatorControllerPacket packet) {
        super(Component.translatable("block.beautifuldecor.elevator_controller"));
        this.pos = packet.pos;
        this.facing = packet.facing;
        this.cabWidth = packet.width;
        this.cabDepth = packet.depth;
        this.cabHeight = packet.height;
        this.shift[0] = packet.offX;
        this.shift[1] = packet.offY;
        this.shift[2] = packet.offZ;
        this.speed = packet.speed;
        this.initialName = packet.name;
    }

    @Override
    protected void init() {
        super.init();
        String keep = this.nameBox != null ? this.nameBox.getValue() : this.initialName;
        this.lay = new ElevatorMenuLayout(ElevatorMenuLayout.panelWidth(this.width), ElevatorMenuLayout.panelHeight(this.height));
        this.left = (this.width - this.lay.width) / 2;
        this.top = (this.height - this.lay.height) / 2;
        ElevatorMenuLayout.R box = this.lay.nameBox;
        this.nameBox = new EditBox(this.font, this.left + box.x(), this.top + box.y(), box.w(), box.h(), Component.empty());
        this.nameBox.setMaxLength(ElevatorControllerBlockEntity.MAX_NAME);
        this.nameBox.setValue(keep);
        this.addRenderableWidget(this.nameBox);
        changed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
        if (this.dirty || this.ticks % SCAN_EVERY == 0) {
            rescan();
        }
        if (this.ticks % OVERLAY_EVERY == 0) {
            preview();
        }
    }

    // ------------------------------------------------------------------------------------------ datos

    private ElevatorZone zone() {
        return ElevatorZone.of(this.pos, this.facing, this.cabWidth, this.cabDepth, this.cabHeight,
                this.shift[0], this.shift[1], this.shift[2]);
    }

    private void preview() {
        ElevatorOverlay.show(zone());
    }

    /** Se pidió ver algo distinto (otro tamaño, lado o capa): la cuadrícula se lee de nuevo ya. */
    private void changed() {
        this.dirty = true;
        preview();
        rescan();
    }

    /** Lee del mundo lo que hay AHORA dentro de la zona y arma la cuadrícula. */
    private void rescan() {
        this.dirty = false;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ElevatorZone zone = zone();
        this.gridZone = zone;
        this.grid = ElevatorPlatformView.scan(zone.sizeX(), zone.sizeY(), zone.sizeZ(), this.layer,
                (x, y, z) -> sample(level, zone, x, y, z));
        this.layer = this.grid.layer;
        this.travelling = ElevatorShaft.travellingCabin(level, this.pos) != null;
    }

    private int sample(Level level, ElevatorZone zone, int x, int y, int z) {
        this.scratch.set(zone.minX() + x, zone.minY() + y, zone.minZ() + z);
        BlockState state = level.getBlockState(this.scratch);
        if (state.isAir()) {
            return ElevatorPlatformView.EMPTY;
        }
        if (!ElevatorCabinEntity.isMovable(state)) {
            return ElevatorPlatformView.STAYS;
        }
        int rgb = state.getMapColor(level, this.scratch).col & 0xFFFFFF;
        return 0xFF000000 | (rgb == 0 ? NO_MAP_COLOR : rgb);
    }

    private String rowValue(int row) {
        return String.valueOf(row == 0 ? this.cabWidth : row == 1 ? this.cabDepth : this.cabHeight);
    }

    private void adjustSize(int row, int button) {
        boolean large = button == 0 || button == 3;
        int sign = button < 2 ? -1 : 1;
        switch (row) {
            case 0 -> this.cabWidth = Mth.clamp(this.cabWidth + sign * (large ? 5 : 1), 1, ElevatorControllerBlockEntity.MAX_WIDTH);
            case 1 -> this.cabDepth = Mth.clamp(this.cabDepth + sign * (large ? 5 : 1), 1, ElevatorControllerBlockEntity.MAX_DEPTH);
            default -> this.cabHeight = Mth.clamp(this.cabHeight + sign * (large ? 5 : 1), 1, ElevatorControllerBlockEntity.MAX_HEIGHT);
        }
        while (this.cabWidth * this.cabDepth * this.cabHeight > ElevatorControllerBlockEntity.MAX_CELLS && this.cabHeight > 1) {
            this.cabHeight--;
        }
        changed();
    }

    /** Corre la zona en un eje (0 = X, 1 = Y, 2 = Z): -5, -1, +1 o +5 bloques, sin pasar de ±MAX_OFFSET. */
    private void adjustShift(int axis, int button) {
        int step = button == 0 ? -5 : button == 1 ? -1 : button == 2 ? 1 : 5;
        this.shift[axis] = Mth.clamp(this.shift[axis] + step, -ElevatorControllerBlockEntity.MAX_OFFSET, ElevatorControllerBlockEntity.MAX_OFFSET);
        changed();
    }

    private void adjustSpeed(int button) {
        boolean large = button == 0 || button == 3;
        int sign = button < 2 ? -1 : 1;
        this.speed = Mth.clamp(Math.round((this.speed + sign * (large ? 5.0f : 0.5f)) * 2.0f) / 2.0f,
                ElevatorControllerBlockEntity.MIN_SPEED, ElevatorControllerBlockEntity.MAX_SPEED);
    }

    private void stepLayer(int delta) {
        this.layer = ElevatorPlatformView.stepLayer(this.layer, delta, this.cabHeight);
        changed();
    }

    private String sideName(Direction side) {
        return DarkUi.tr("gui.beautifuldecor.elevator.side." + side.getName());
    }

    private boolean in(ElevatorMenuLayout.R r, double mx, double my) {
        return r.inside(mx - this.left, my - this.top);
    }

    // ------------------------------------------------------------------------------------------ mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int row = 0; row < 3; row++) {
                for (int k = 0; k < 4; k++) {
                    if (in(this.lay.size[row][k], mx, my)) {
                        adjustSize(row, k);
                        return true;
                    }
                }
            }
            for (int k = 0; k < 4; k++) {
                if (in(this.lay.speed[k], mx, my)) {
                    adjustSpeed(k);
                    return true;
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                for (int k = 0; k < 4; k++) {
                    if (in(this.lay.shift[axis][k], mx, my)) {
                        adjustShift(axis, k);
                        return true;
                    }
                }
                if (in(this.lay.shiftValue[axis], mx, my)) {
                    this.shift[axis] = 0;
                    changed();
                    return true;
                }
            }
            for (int slot = 0; slot < 4; slot++) {
                if (in(this.lay.side[slot], mx, my)) {
                    this.facing = SIDE_BY_SLOT[slot];
                    changed();
                    return true;
                }
            }
            if (in(this.lay.layerPrev, mx, my)) {
                stepLayer(-1);
                return true;
            }
            if (in(this.lay.layerNext, mx, my)) {
                stepLayer(1);
                return true;
            }
            if (in(this.lay.layerLabel, mx, my)) {
                this.layer = ElevatorPlatformView.TOP;
                changed();
                return true;
            }
            if (in(this.lay.save, mx, my)) {
                ModMessages.sendToServer(new ElevatorSavePacket(this.pos, this.facing, this.cabWidth, this.cabDepth,
                        this.cabHeight, this.shift[0], this.shift[1], this.shift[2], this.speed, this.nameBox.getValue()));
                this.onClose();
                return true;
            }
            if (in(this.lay.close, mx, my)) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** La rueda del mouse sobre la plataforma sube o baja de capa. */
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta != 0.0 && (in(this.lay.grid, mx, my) || in(this.lay.layerLabel, mx, my))) {
            stepLayer(delta > 0.0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------------------------------ dibujo

    /** Texto en un renglón; si no cabe en el ancho del renglón, se recorta (nunca se sale a la columna de al lado). */
    private void text(GuiGraphics gui, ElevatorMenuLayout.R row, String text, int color) {
        gui.drawString(this.font, this.font.plainSubstrByWidth(text, row.w()), this.left + row.x(), this.top + row.y(), color);
    }

    private void textRight(GuiGraphics gui, ElevatorMenuLayout.R row, String text, int color) {
        String clipped = this.font.plainSubstrByWidth(text, row.w());
        gui.drawString(this.font, clipped, this.left + row.right() - this.font.width(clipped), this.top + row.y(), color);
    }

    private void button(GuiGraphics gui, ElevatorMenuLayout.R r, String label, int mouseX, int mouseY, boolean active) {
        DarkUi.button(gui, this.font, this.left + r.x(), this.top + r.y(), r.w(), r.h(), this.font.plainSubstrByWidth(label, r.w() - 4),
                in(r, mouseX, mouseY), active);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, this.left, this.top, this.lay.width, this.lay.height, ElevatorMenuLayout.HEADER);
        gui.drawString(this.font, this.title, this.left + 16, this.top + 11, DarkUi.ACCENT);
        String where = this.pos.getX() + ", " + this.pos.getY() + ", " + this.pos.getZ();
        gui.drawString(this.font, where, this.left + this.lay.width - 16 - this.font.width(where), this.top + 11, DarkUi.MUTED);

        // rayitas que separan las tres columnas
        int lineTop = this.top + ElevatorMenuLayout.TOP;
        int lineBottom = this.top + this.lay.save.y() - 8;
        gui.fill(this.left + this.lay.centreX - 5, lineTop, this.left + this.lay.centreX - 4, lineBottom, DarkUi.BORDER);
        gui.fill(this.left + this.lay.rightX - 5, lineTop, this.left + this.lay.rightX - 4, lineBottom, DarkUi.BORDER);

        drawLeft(gui, mouseX, mouseY);
        drawRight(gui, mouseX, mouseY);
        drawPlatform(gui, mouseX, mouseY);

        button(gui, this.lay.save, DarkUi.tr("gui.beautifuldecor.elevator.save"), mouseX, mouseY, true);
        button(gui, this.lay.close, DarkUi.tr("gui.beautifuldecor.elevator.close"), mouseX, mouseY, false);
        super.render(gui, mouseX, mouseY, partialTick);
        if (this.nameBox.getValue().isEmpty() && !this.nameBox.isFocused()) {
            gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.elevator.name_hint"),
                    this.left + this.lay.nameBox.x() + 4, this.top + this.lay.nameBox.y() + 4, DarkUi.MUTED);
        }
    }

    /** Izquierda: lado de la cabina (brújula), desplazamiento X/Y/Z y nombre del piso. */
    private void drawLeft(GuiGraphics gui, int mouseX, int mouseY) {
        text(gui, this.lay.headingSide, DarkUi.tr("gui.beautifuldecor.elevator.side"), DarkUi.ACCENT);
        for (int slot = 0; slot < 4; slot++) {
            button(gui, this.lay.side[slot], sideName(SIDE_BY_SLOT[slot]), mouseX, mouseY, SIDE_BY_SLOT[slot] == this.facing);
        }
        ElevatorMenuLayout.R c = this.lay.ctrl;
        gui.fill(this.left + c.x(), this.top + c.y(), this.left + c.right(), this.top + c.bottom(), DarkUi.BORDER);
        gui.fill(this.left + c.x() + 1, this.top + c.y() + 1, this.left + c.right() - 1, this.top + c.bottom() - 1, DarkUi.HEADER);
        String ctrl = DarkUi.tr("gui.beautifuldecor.elevator.ctrl");
        gui.drawString(this.font, ctrl, this.left + c.x() + (c.w() - this.font.width(ctrl)) / 2, this.top + c.y() + 4, DarkUi.MUTED);

        text(gui, this.lay.labelShift, DarkUi.tr("gui.beautifuldecor.elevator.shift"), DarkUi.ACCENT);
        for (int axis = 0; axis < 3; axis++) {
            ElevatorMenuLayout.R letter = this.lay.shiftAxis[axis];
            gui.drawString(this.font, SHIFT_AXES[axis], this.left + letter.x(), this.top + letter.y() + 4, DarkUi.TEXT);
            for (int k = 0; k < 4; k++) {
                button(gui, this.lay.shift[axis][k], STEP_LABELS[k], mouseX, mouseY, false);
            }
            int value = this.shift[axis];
            button(gui, this.lay.shiftValue[axis], value > 0 ? "+" + value : String.valueOf(value), mouseX, mouseY, value != 0);
        }
        text(gui, this.lay.labelName, DarkUi.tr("gui.beautifuldecor.elevator.name"), DarkUi.TEXT);
    }

    /** Derecha: tamaño de la cabina (un renglón por eje del mundo) y velocidad. */
    private void drawRight(GuiGraphics gui, int mouseX, int mouseY) {
        text(gui, this.lay.headingSize, DarkUi.tr("gui.beautifuldecor.elevator.size"), DarkUi.ACCENT);
        boolean alongZ = this.facing.getAxis() == Direction.Axis.Z;
        String[] axes = {String.valueOf(ElevatorPlatformView.widthAxis(alongZ)), String.valueOf(ElevatorPlatformView.depthAxis(alongZ)), "Y"};
        for (int row = 0; row < 3; row++) {
            text(gui, this.lay.sizeLabel[row], DarkUi.tr(ROW_KEYS[row]) + " (" + DarkUi.tr("gui.beautifuldecor.elevator.axis", axes[row]) + ")",
                    DarkUi.TEXT);
            textRight(gui, this.lay.sizeLabel[row], rowValue(row), DarkUi.ACCENT);
            for (int k = 0; k < 4; k++) {
                button(gui, this.lay.size[row][k], STEP_LABELS[k], mouseX, mouseY, false);
            }
        }
        text(gui, this.lay.labelSpeed, DarkUi.tr("gui.beautifuldecor.elevator.speed"), DarkUi.TEXT);
        textRight(gui, this.lay.labelSpeed, DarkUi.tr("gui.beautifuldecor.elevator.speed_value",
                String.format(Locale.ROOT, "%.1f", this.speed)), DarkUi.ACCENT);
        for (int k = 0; k < 4; k++) {
            button(gui, this.lay.speed[k], SPEED_LABELS[k], mouseX, mouseY, false);
        }
        text(gui, this.lay.zoneDims, DarkUi.tr("gui.beautifuldecor.elevator.zone", this.cabWidth, this.cabDepth, this.cabHeight), DarkUi.WARN);
        text(gui, this.lay.zoneCells, DarkUi.tr("gui.beautifuldecor.elevator.zone_cells", this.cabWidth * this.cabDepth * this.cabHeight),
                DarkUi.MUTED);
    }

    /** Centro: la plataforma en vivo. */
    private void drawPlatform(GuiGraphics gui, int mouseX, int mouseY) {
        text(gui, this.lay.headingPlatform, DarkUi.tr("gui.beautifuldecor.elevator.platform"), DarkUi.ACCENT);
        ElevatorMenuLayout.R area = this.lay.grid;
        int ax = this.left + area.x();
        int ay = this.top + area.y();
        gui.fill(ax - 1, ay - 1, ax + area.w() + 1, ay + area.h() + 1, DarkUi.BORDER);
        gui.fill(ax, ay, ax + area.w(), ay + area.h(), GRID_BG);
        gui.drawString(this.font, "N", ax + 2, ay + 1, DarkUi.MUTED);

        ElevatorPlatformView.Grid g = this.grid;
        ElevatorZone zone = this.gridZone;
        int sizeY = zone == null ? this.cabHeight : zone.sizeY();
        String layerText;
        if (this.layer == ElevatorPlatformView.TOP) {
            layerText = DarkUi.tr("gui.beautifuldecor.elevator.platform.top");
        } else {
            layerText = DarkUi.tr("gui.beautifuldecor.elevator.platform.layer", this.layer + 1, sizeY, (zone == null ? 0 : zone.minY()) + this.layer);
            if (this.font.width(layerText) + 6 > this.lay.layerLabel.w()) {
                layerText = DarkUi.tr("gui.beautifuldecor.elevator.platform.layer_short", this.layer + 1, sizeY);
            }
        }
        button(gui, this.lay.layerPrev, "<", mouseX, mouseY, false);
        button(gui, this.lay.layerNext, ">", mouseX, mouseY, false);
        button(gui, this.lay.layerLabel, layerText, mouseX, mouseY, this.layer == ElevatorPlatformView.TOP);

        if (g == null || zone == null) {
            return;
        }
        int inner = GRID_INSET;
        int cs = ElevatorPlatformView.cellSize(area.w() - 2 * inner, area.h() - 2 * inner, g.sizeX, g.sizeZ, MAX_CELL);
        int gw = cs * g.sizeX;
        int gh = cs * g.sizeZ;
        int gx = ax + (area.w() - gw) / 2;
        int gy = ay + (area.h() - gh) / 2;

        // fondo de la cuadrícula y rayitas entre celdas (cuando las celdas son lo bastante grandes para verse)
        gui.fill(gx, gy, gx + gw, gy + gh, CELL_EMPTY);
        boolean lines = cs >= 5;
        if (lines) {
            for (int x = 0; x <= g.sizeX; x++) {
                gui.fill(gx + x * cs, gy, gx + x * cs + 1, gy + gh + 1, GRID_BG);
            }
            for (int z = 0; z <= g.sizeZ; z++) {
                gui.fill(gx, gy + z * cs, gx + gw + 1, gy + z * cs + 1, GRID_BG);
            }
        }
        int pad = lines ? 1 : 0;
        for (int z = 0; z < g.sizeZ; z++) {
            for (int x = 0; x < g.sizeX; x++) {
                int i = g.index(x, z);
                int cell = g.cells[i];
                if (cell == ElevatorPlatformView.EMPTY) {
                    continue;
                }
                int x0 = gx + x * cs + pad;
                int y0 = gy + z * cs + pad;
                int x1 = gx + (x + 1) * cs;
                int y1 = gy + (z + 1) * cs;
                if (cell == ElevatorPlatformView.STAYS) {
                    if (cs >= 5) {
                        gui.fill(x0, y0, x1, y1, STAY_EDGE);
                        gui.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, STAY_FILL);
                    } else {
                        gui.fill(x0, y0, x1, y1, STAY_EDGE);
                    }
                    continue;
                }
                int color = g.layer == ElevatorPlatformView.TOP ? ElevatorPlatformView.shade(cell, g.heights[i], g.sizeY) : cell;
                gui.fill(x0, y0, x1, y1, color);
            }
        }

        // el controlador, pegado a la zona
        int rx = this.pos.getX() - zone.minX();
        int rz = this.pos.getZ() - zone.minZ();
        int[] mark = ElevatorPlatformView.markerCenter(gx, gy, cs, g.sizeX, g.sizeZ, rx, rz, 5);
        gui.fill(mark[0] - 3, mark[1] - 3, mark[0] + 3, mark[1] + 3, DarkUi.ACCENT);
        gui.fill(mark[0] - 2, mark[1] - 2, mark[0] + 2, mark[1] + 2, DarkUi.BUTTON_ACTIVE);

        // qué hay bajo el mouse
        int hovered = ElevatorPlatformView.cellAt(mouseX, mouseY, gx, gy, cs, g.sizeX, g.sizeZ);
        String hoverLine = null;
        if (hovered >= 0) {
            int hx = hovered % g.sizeX;
            int hz = hovered / g.sizeX;
            gui.fill(gx + hx * cs - 1, gy + hz * cs - 1, gx + (hx + 1) * cs + 1, gy + hz * cs, DarkUi.TEXT);
            gui.fill(gx + hx * cs - 1, gy + (hz + 1) * cs, gx + (hx + 1) * cs + 1, gy + (hz + 1) * cs + 1, DarkUi.TEXT);
            gui.fill(gx + hx * cs - 1, gy + hz * cs, gx + hx * cs, gy + (hz + 1) * cs, DarkUi.TEXT);
            gui.fill(gx + (hx + 1) * cs, gy + hz * cs, gx + (hx + 1) * cs + 1, gy + (hz + 1) * cs, DarkUi.TEXT);
            hoverLine = hoverText(g, zone, hovered, hx, hz);
        }

        // avisos sobre la cuadrícula
        if (this.travelling) {
            centered(gui, area, DarkUi.tr("gui.beautifuldecor.elevator.platform.travelling"), DarkUi.WARN);
        } else if (g.movable == 0 && g.stays == 0) {
            centered(gui, area, DarkUi.tr("gui.beautifuldecor.elevator.platform.empty"), DarkUi.MUTED);
        }

        // texto de abajo: cuántos bloques se mueven y, con el mouse encima, qué bloque es
        text(gui, this.lay.counts, DarkUi.tr("gui.beautifuldecor.elevator.platform.counts", g.movable), DarkUi.TEXT);
        if (hoverLine != null) {
            text(gui, this.lay.hover, hoverLine, DarkUi.MUTED);
        } else if (overShiftValue(mouseX, mouseY)) {
            text(gui, this.lay.hover, DarkUi.tr("gui.beautifuldecor.elevator.shift_reset"), DarkUi.ACCENT);
        } else if (g.stays > 0) {
            text(gui, this.lay.hover, DarkUi.tr("gui.beautifuldecor.elevator.platform.stays", g.stays), STAY_EDGE);
        } else {
            text(gui, this.lay.hover, DarkUi.tr("gui.beautifuldecor.elevator.platform.hint"), DarkUi.MUTED);
        }
    }

    private boolean overShiftValue(int mouseX, int mouseY) {
        for (int axis = 0; axis < 3; axis++) {
            if (in(this.lay.shiftValue[axis], mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void centered(GuiGraphics gui, ElevatorMenuLayout.R area, String text, int color) {
        int w = this.font.width(text);
        int x = this.left + area.x() + (area.w() - w) / 2;
        int y = this.top + area.y() + (area.h() - 8) / 2;
        gui.fill(x - 3, y - 3, x + w + 3, y + 11, 0xCC000000);
        gui.drawString(this.font, text, x, y, color);
    }

    /** "Nombre del bloque x, y, z" de la celda bajo el mouse (el nombre se recorta si no cabe; las coordenadas no). */
    private String hoverText(ElevatorPlatformView.Grid g, ElevatorZone zone, int index, int hx, int hz) {
        int cell = g.cells[index];
        int hy = g.heights[index];
        if (cell == ElevatorPlatformView.EMPTY || hy < 0) {
            return null;
        }
        int wx = zone.minX() + hx;
        int wy = zone.minY() + hy;
        int wz = zone.minZ() + hz;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        BlockState state = level.getBlockState(this.scratch.set(wx, wy, wz));
        String coords = " " + wx + ", " + wy + ", " + wz;
        String name = this.font.plainSubstrByWidth(state.getBlock().getName().getString(), Math.max(10, this.lay.hover.w() - this.font.width(coords)));
        return name + coords;
    }
}
