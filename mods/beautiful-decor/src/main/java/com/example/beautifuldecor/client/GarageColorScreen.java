package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.GarageColors;
import com.example.beautifuldecor.network.GarageColorPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Menú de colores de la puerta de garaje (V2.3.0), en modo oscuro: tres ranuras (puerta, franja y marco), cada una con
 * tres barras RGB, un cuadro de texto hexadecimal y una paleta de colores listos. Muestra una vista previa de la puerta y
 * solo cambia la puerta de verdad al pulsar "Aplicar". Se abre con la tecla R apuntando a la puerta.
 * <p>
 * V3.6.0: el botón "Otros" abre la lista de TODOS los colores de la puerta (los tres de siempre y además ventanas, manija y cerradura,
 * sello de goma, bisagras y rodillos, y rejillas del cajón). Se elige uno y se edita con las mismas barras y paleta. Al elegir las
 * bisagras y rodillos (que están por dentro) la vista previa muestra la puerta vista desde dentro.
 */
public class GarageColorScreen extends Screen {
    private static final int W = 320;
    private static final int H = 240;
    /** Las fichas de siempre (puerta, franja, marco) y después "Otros". */
    private static final int MAIN_TABS = 3;
    private static final int LIST_ROW = 17;
    private static final String[] CHANNEL_KEYS = {
            "gui.beautifuldecor.garage.red", "gui.beautifuldecor.garage.green", "gui.beautifuldecor.garage.blue"};
    private static final int[] CHANNEL_COLORS = {0xFFE05555, 0xFF55C46A, 0xFF5588E0};
    private static final int[] PRESETS = {
            0xFFFFFF, 0xC8C8D0, 0x6B707A, 0x2E3139, 0x111111, 0xCE1C24, 0xE8651A, 0xF2C230,
            0x3FA34D, 0x1FA8A0, 0x2F6FDE, 0x1B2F6B, 0x7A3FD1, 0xE86AB5, 0x8B5A2B, 0xD8C8A0};

    private static final int TRACK_X = 56;
    private static final int TRACK_W = 104;

    private final BlockPos origin;
    private final int[] colors;
    private int slot = GarageColors.BODY;
    private boolean listOpen;
    private int dragging = -1;
    private boolean updatingBox;
    private EditBox hexBox;
    private int left;
    private int top;

    public GarageColorScreen(BlockPos origin, int[] colors) {
        super(Component.translatable("gui.beautifuldecor.garage.title"));
        this.origin = origin;
        this.colors = colors.clone();
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.hexBox = new EditBox(this.font, this.left + 44, this.top + 130, 64, 16, Component.empty());
        this.hexBox.setMaxLength(6);
        this.hexBox.setFilter(s -> s.matches("[0-9a-fA-F]{0,6}"));
        this.hexBox.setResponder(this::onHexChanged);
        this.hexBox.visible = !this.listOpen;
        this.addRenderableWidget(this.hexBox);
        syncHex();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ lista de "Otros" (V3.6.0)

    private static String slotName(int slot) {
        return DarkUi.tr("gui.beautifuldecor.garage." + GarageColors.slotKey(slot));
    }

    private int tabWidth() {
        return (W - 32 - 6 * MAIN_TABS) / (MAIN_TABS + 1);
    }

    private int tabX(int i) {
        return this.left + 16 + i * (tabWidth() + 6);
    }

    private void setListOpen(boolean open) {
        this.listOpen = open;
        this.dragging = -1;
        if (this.hexBox != null) {
            this.hexBox.visible = !open;
            if (open) {
                this.hexBox.setFocused(false);
            }
        }
    }

    private int listX() {
        return this.left + 16;
    }

    private int listY(int i) {
        return this.top + 64 + 4 + i * LIST_ROW;
    }

    private int listWidth() {
        return 174;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.listOpen && keyCode == 256) {
            setListOpen(false);         // Esc con la lista abierta solo la cierra
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ valores

    private int channel(int c) {
        return (this.colors[this.slot] >> (16 - 8 * c)) & 255;
    }

    private void setChannel(int c, int value) {
        int shift = 16 - 8 * c;
        this.colors[this.slot] = (this.colors[this.slot] & ~(255 << shift)) | (Mth.clamp(value, 0, 255) << shift);
        syncHex();
    }

    private void syncHex() {
        if (this.hexBox == null) {
            return;
        }
        this.updatingBox = true;
        this.hexBox.setValue(String.format("%06X", this.colors[this.slot]));
        this.updatingBox = false;
    }

    private void onHexChanged(String text) {
        if (!this.updatingBox && text.length() == 6) {
            this.colors[this.slot] = Integer.parseInt(text, 16);
        }
    }

    private int sliderY(int c) {
        return this.top + 68 + c * 20;
    }

    private void dragTo(double mouseX) {
        if (this.dragging >= 0) {
            setChannel(this.dragging, (int) Math.round((mouseX - (this.left + TRACK_X)) / TRACK_W * 255.0));
        }
    }

    // ------------------------------------------------------------------ ratón

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i <= MAIN_TABS; i++) {
                if (DarkUi.inside(mx, my, tabX(i), this.top + 36, tabWidth(), 20)) {
                    this.hexBox.setFocused(false);
                    if (i == MAIN_TABS) {
                        setListOpen(!this.listOpen);
                    } else {
                        this.slot = i;
                        setListOpen(false);
                        syncHex();
                    }
                    return true;
                }
            }
            if (this.listOpen) {
                for (int i = 0; i < GarageColors.SLOTS; i++) {
                    if (DarkUi.inside(mx, my, listX(), listY(i), listWidth(), LIST_ROW - 1)) {
                        this.slot = i;
                        setListOpen(false);
                        syncHex();
                        return true;
                    }
                }
                if (!DarkUi.inside(mx, my, this.left + 16, this.top + 208, W - 32, 20)
                        && !DarkUi.inside(mx, my, this.left + 200, this.top + 176, 100, 18)) {
                    return true;        // con la lista abierta, las barras y la paleta no se tocan
                }
            }
            for (int c = 0; c < 3; c++) {
                if (DarkUi.inside(mx, my, this.left + TRACK_X - 3, sliderY(c), TRACK_W + 6, 14)) {
                    this.dragging = c;
                    dragTo(mx);
                    return true;
                }
            }
            for (int i = 0; i < PRESETS.length; i++) {
                if (DarkUi.inside(mx, my, presetX(i), presetY(i), 16, 16)) {
                    this.colors[this.slot] = PRESETS[i];
                    syncHex();
                    return true;
                }
            }
            if (DarkUi.inside(mx, my, this.left + 200, this.top + 176, 100, 18)) {
                this.colors[this.slot] = GarageColors.defaults()[this.slot];
                syncHex();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 16, this.top + 208, 96, 20)) {
                ModMessages.sendToServer(new GarageColorPacket(this.origin, this.colors));
                this.onClose();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 120, this.top + 208, 96, 20)) {
                System.arraycopy(GarageColors.defaults(), 0, this.colors, 0, GarageColors.SLOTS);
                syncHex();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 224, this.top + 208, 80, 20)) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging >= 0 && button == 0) {
            dragTo(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = -1;
        return super.mouseReleased(mx, my, button);
    }

    private int presetX(int i) {
        return this.left + 16 + (i % 8) * 20;
    }

    private int presetY(int i) {
        return this.top + 156 + (i / 8) * 22;
    }

    // ------------------------------------------------------------------ dibujo

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** Oscurece (f < 1) un color 0xRRGGBB. */
    private static int dim(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f);
        int g = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return opaque((r << 16) | (g << 8) | b);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, this.left, this.top, W, H, 30);
        gui.drawString(this.font, this.title, this.left + 16, this.top + 11, DarkUi.ACCENT);

        for (int i = 0; i <= MAIN_TABS; i++) {
            int x = tabX(i);
            int y = this.top + 36;
            boolean hover = DarkUi.inside(mouseX, mouseY, x, y, tabWidth(), 20);
            if (i == MAIN_TABS) {
                DarkUi.button(gui, this.font, x, y, tabWidth(), 20, DarkUi.tr("gui.beautifuldecor.garage.others"), hover,
                        this.listOpen || this.slot >= GarageColors.OTHERS_FIRST);
                continue;
            }
            DarkUi.button(gui, this.font, x, y, tabWidth(), 20, slotName(i), hover, !this.listOpen && i == this.slot);
            gui.fill(x + 3, y + 4, x + 15, y + 16, 0xFF000000);
            gui.fill(x + 4, y + 5, x + 14, y + 15, opaque(this.colors[i]));
        }

        if (this.listOpen) {
            renderList(gui, mouseX, mouseY);
        } else {
            gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.garage.editing", slotName(this.slot)),
                    this.left + 16, this.top + 58, DarkUi.MUTED);
            renderEditor(gui, mouseX, mouseY);
        }

        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.garage.preview"), this.left + 200, this.top + 60, DarkUi.MUTED);
        drawPreview(gui, this.left + 200, this.top + 72, this.slot == GarageColors.HARDWARE);
        DarkUi.button(gui, this.font, this.left + 200, this.top + 176, 100, 18, DarkUi.tr("gui.beautifuldecor.garage.original"),
                DarkUi.inside(mouseX, mouseY, this.left + 200, this.top + 176, 100, 18), false);

        DarkUi.button(gui, this.font, this.left + 16, this.top + 208, 96, 20, DarkUi.tr("gui.beautifuldecor.garage.apply"),
                DarkUi.inside(mouseX, mouseY, this.left + 16, this.top + 208, 96, 20), true);
        DarkUi.button(gui, this.font, this.left + 120, this.top + 208, 96, 20, DarkUi.tr("gui.beautifuldecor.garage.reset"),
                DarkUi.inside(mouseX, mouseY, this.left + 120, this.top + 208, 96, 20), false);
        DarkUi.button(gui, this.font, this.left + 224, this.top + 208, 80, 20, DarkUi.tr("gui.beautifuldecor.garage.cancel"),
                DarkUi.inside(mouseX, mouseY, this.left + 224, this.top + 208, 80, 20), false);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    /** Barras RGB, cuadro hexadecimal y paleta de la ranura que se está editando. */
    private void renderEditor(GuiGraphics gui, int mouseX, int mouseY) {
        for (int c = 0; c < 3; c++) {
            int y = sliderY(c);
            int trackX = this.left + TRACK_X;
            gui.drawString(this.font, DarkUi.tr(CHANNEL_KEYS[c]), this.left + 16, y + 3, DarkUi.TEXT);
            gui.fill(trackX - 1, y + 4, trackX + TRACK_W + 1, y + 10, DarkUi.BORDER);
            gui.fill(trackX, y + 5, trackX + TRACK_W, y + 9, DarkUi.BUTTON);
            int filled = (int) Math.round(channel(c) / 255.0 * TRACK_W);
            gui.fill(trackX, y + 5, trackX + filled, y + 9, CHANNEL_COLORS[c]);
            gui.fill(trackX + filled - 2, y + 1, trackX + filled + 2, y + 13, DarkUi.TEXT);
            gui.drawString(this.font, String.valueOf(channel(c)), trackX + TRACK_W + 8, y + 3, DarkUi.MUTED);
        }

        gui.drawString(this.font, "HEX #", this.left + 16, this.top + 134, DarkUi.MUTED);
        gui.fill(this.left + 114, this.top + 129, this.left + 132, this.top + 147, DarkUi.BORDER);
        gui.fill(this.left + 115, this.top + 130, this.left + 131, this.top + 146, opaque(this.colors[this.slot]));

        for (int i = 0; i < PRESETS.length; i++) {
            int x = presetX(i);
            int y = presetY(i);
            boolean selected = PRESETS[i] == this.colors[this.slot];
            gui.fill(x - 1, y - 1, x + 17, y + 17, selected ? DarkUi.ACCENT
                    : DarkUi.inside(mouseX, mouseY, x, y, 16, 16) ? DarkUi.TEXT : DarkUi.BORDER);
            gui.fill(x, y, x + 16, y + 16, opaque(PRESETS[i]));
        }
    }

    /** V3.6.0: la lista de TODOS los colores de la puerta, cada uno con su muestra y su nombre. */
    private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
        int count = GarageColors.SLOTS;
        gui.fill(this.left + 12, this.top + 64, this.left + 16 + listWidth() + 4, this.top + 64 + count * LIST_ROW + 8, DarkUi.BORDER);
        gui.fill(this.left + 13, this.top + 65, this.left + 16 + listWidth() + 3, this.top + 64 + count * LIST_ROW + 7, DarkUi.HEADER);
        for (int i = 0; i < count; i++) {
            int x = listX();
            int y = listY(i);
            int w = listWidth();
            boolean hover = DarkUi.inside(mouseX, mouseY, x, y, w, LIST_ROW - 1);
            boolean selected = i == this.slot;
            gui.fill(x, y, x + w, y + LIST_ROW - 1, selected ? DarkUi.ACCENT : DarkUi.BORDER);
            gui.fill(x + 1, y + 1, x + w - 1, y + LIST_ROW - 2,
                    selected ? DarkUi.BUTTON_ACTIVE : hover ? DarkUi.BUTTON_HOVER : DarkUi.BUTTON);
            gui.fill(x + 3, y + 3, x + 14, y + LIST_ROW - 4, 0xFF000000);
            gui.fill(x + 4, y + 4, x + 13, y + LIST_ROW - 5, opaque(this.colors[i]));
            gui.drawString(this.font, slotName(i), x + 20, y + 4, hover || selected ? DarkUi.TEXT : 0xFFC9CFDA);
        }
    }

    /**
     * Puerta en miniatura (100 x 96) con los colores elegidos, tal como se verán en el juego. {@code inside} la muestra vista desde
     * dentro (con las bisagras y los rodillos).
     */
    private void drawPreview(GuiGraphics gui, int x, int y, boolean inside) {
        int body = GarageColors.visible(GarageColors.BODY, this.colors[GarageColors.BODY]);
        int accent = GarageColors.visible(GarageColors.ACCENT, this.colors[GarageColors.ACCENT]);
        int frame = GarageColors.visible(GarageColors.FRAME, this.colors[GarageColors.FRAME]);
        int window = this.colors[GarageColors.WINDOW] & 0xFFFFFF;
        int handle = this.colors[GarageColors.HANDLE] & 0xFFFFFF;
        int seal = this.colors[GarageColors.SEAL] & 0xFFFFFF;
        int hardware = this.colors[GarageColors.HARDWARE] & 0xFFFFFF;
        int vents = this.colors[GarageColors.VENTS] & 0xFFFFFF;

        gui.fill(x - 1, y - 1, x + 101, y + 97, DarkUi.BORDER);
        gui.fill(x, y, x + 100, y + 96, 0xFF0B0D10);
        gui.fill(x, y, x + 8, y + 96, opaque(frame));                      // rieles
        gui.fill(x + 92, y, x + 100, y + 96, opaque(frame));
        gui.fill(x + 6, y, x + 8, y + 96, opaque(accent));
        gui.fill(x + 92, y, x + 94, y + 96, opaque(accent));
        int leaf = inside ? GarageColors.inside(this.colors[GarageColors.BODY]) : body;
        gui.fill(x + 8, y + 14, x + 92, y + 96, opaque(leaf));            // hoja
        for (int k = 1; k < 4; k++) {                                        // juntas entre secciones
            gui.fill(x + 8, y + 14 + 20 * k, x + 92, y + 15 + 20 * k, dim(leaf, 0.55));
        }
        int glass = inside ? dim(GarageColors.windowInside(window), 0.8) : dim(window, 0.38);
        for (int i = 0; i < 5; i++) {                                        // ventanas
            gui.fill(x + 12 + i * 16, y + 19, x + 24 + i * 16, y + 27, glass);
        }
        if (inside) {
            for (int row = 0; row < 3; row++) {                              // bisagras y rodillos
                int hy = y + 30 + row * 22;
                for (int col = 0; col < 3; col++) {
                    gui.fill(x + 12 + col * 34, hy, x + 20 + col * 34, hy + 6, dim(hardware, 0.35));
                }
                gui.fill(x + 9, hy + 1, x + 13, hy + 5, dim(hardware, 0.55));
                gui.fill(x + 87, hy + 1, x + 91, hy + 5, dim(hardware, 0.55));
            }
        } else {
            gui.fill(x + 8, y + 76, x + 92, y + 78, opaque(accent));      // franja
            gui.fill(x + 40, y + 82, x + 60, y + 84, opaque(handle));      // manija
            gui.fill(x + 47, y + 87, x + 51, y + 91, dim(handle, 0.3));    // cerradura
        }
        gui.fill(x + 8, y + 92, x + 92, y + 96, dim(seal, 0.55));          // sello de goma
        gui.fill(x, y, x + 100, y + 14, opaque(body));                     // cajón del motor
        gui.fill(x, y + 10, x + 100, y + 12, opaque(accent));
        gui.fill(x, y + 12, x + 100, y + 14, dim(vents, 0.9));
        for (int k = 0; k < 6; k++) {                                        // rejillas
            gui.fill(x + 10 + k * 3, y + 2, x + 11 + k * 3, y + 6, opaque(vents));
        }
    }
}
