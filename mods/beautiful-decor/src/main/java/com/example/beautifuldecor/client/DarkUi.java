package com.example.beautifuldecor.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Piezas del modo oscuro de los menús de ascensor (V2.2.0): paneles, botones y colores. */
final class DarkUi {
    static final int DIM = 0xB4000000;
    static final int PANEL = 0xFF14171C;
    static final int HEADER = 0xFF1B1F26;
    static final int BORDER = 0xFF2C323C;
    static final int BUTTON = 0xFF232830;
    static final int BUTTON_HOVER = 0xFF2F3642;
    static final int BUTTON_ACTIVE = 0xFF0E4A63;
    static final int TEXT = 0xFFE8EAF0;
    static final int MUTED = 0xFF8C95A5;
    static final int ACCENT = 0xFF38BDF8;
    static final int OK = 0xFF34D399;
    static final int WARN = 0xFFFBBF24;

    private DarkUi() {
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Panel con borde fino y una franja de título arriba. */
    static void panel(GuiGraphics gui, int x, int y, int w, int h, int headerHeight) {
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        gui.fill(x, y, x + w, y + h, PANEL);
        if (headerHeight > 0) {
            gui.fill(x, y, x + w, y + headerHeight, HEADER);
            gui.fill(x, y + headerHeight, x + w, y + headerHeight + 1, BORDER);
        }
    }

    static void button(GuiGraphics gui, Font font, int x, int y, int w, int h, String label, boolean hover, boolean active) {
        int fill = active ? BUTTON_ACTIVE : hover ? BUTTON_HOVER : BUTTON;
        gui.fill(x, y, x + w, y + h, active ? ACCENT : BORDER);
        gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        int color = active ? TEXT : hover ? TEXT : 0xFFC9CFDA;
        gui.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, color);
    }
}
