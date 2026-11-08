package com.example.beautifuldecor.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posición de cada pieza del menú del controlador de ascensor (V3.2.1, con el desplazamiento X/Y/Z en la V3.3.0). Todo en
 * píxeles, relativo a la esquina de arriba a la izquierda del panel. No usa nada de Minecraft, así que se puede probar fuera
 * del juego (que no se pisen las piezas ni se salgan del panel).
 * <p>
 * Tres columnas: a la IZQUIERDA lo que cambia DÓNDE queda la cabina (lado respecto del controlador, desplazamiento en X, Y y Z)
 * y el nombre del piso; en el CENTRO la plataforma en vivo; y a la DERECHA el tamaño (ancho, fondo y alto) y la velocidad.
 */
final class ElevatorMenuLayout {
    static final int PREFERRED_W = 460;
    static final int MIN_W = 410;
    static final int MIN_H = 214;
    static final int MAX_H = 250;
    static final int COL_W = 128;
    static final int GAP = 10;
    static final int PAD = 10;
    /** Alto de la franja del título. */
    static final int HEADER = 30;
    /** Donde empieza el contenido. */
    static final int TOP = 32;
    /** Alto de una fila de texto. */
    static final int TEXT_H = 9;
    static final int BUTTON_H = 14;
    /** Ancho de los botones -grande, -chico, +chico, +grande y separación entre ellos. */
    static final int STEP_W = 30;
    static final int STEP_GAP = 2;
    /** Botones del desplazamiento (más angostos, porque van en la misma fila que la letra del eje y el valor). */
    static final int SHIFT_BTN_W = 21;
    /** Orden de los botones de lado: norte, oeste, este, sur (como una brújula). */
    static final int SIDE_N = 0;
    static final int SIDE_W = 1;
    static final int SIDE_E = 2;
    static final int SIDE_S = 3;

    /** Un rectángulo. */
    record R(int x, int y, int w, int h) {
        boolean inside(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }

        boolean overlaps(R o) {
            return this.x < o.x + o.w && o.x < this.x + this.w && this.y < o.y + o.h && o.y < this.y + this.h;
        }

        int right() {
            return this.x + this.w;
        }

        int bottom() {
            return this.y + this.h;
        }
    }

    final int width;
    final int height;
    final int leftX;
    final int centreX;
    final int centreW;
    final int rightX;

    // izquierda
    final R headingSide;
    final R[] side = new R[4];
    final R ctrl;
    final R labelShift;
    /** Las letras X, Y y Z de las tres filas del desplazamiento. */
    final R[] shiftAxis = new R[3];
    /** Botones -5, -1, +1 y +5 de cada fila del desplazamiento. */
    final R[][] shift = new R[3][4];
    /** El valor de cada fila (con un clic vuelve a 0). */
    final R[] shiftValue = new R[3];
    final R labelName;
    final R nameBox;
    // centro
    final R headingPlatform;
    final R grid;
    final R layerPrev;
    final R layerLabel;
    final R layerNext;
    final R counts;
    final R hover;
    // derecha
    final R headingSize;
    final R[] sizeLabel = new R[3];
    final R[][] size = new R[3][4];
    final R labelSpeed;
    final R[] speed = new R[4];
    final R zoneDims;
    final R zoneCells;
    // abajo
    final R save;
    final R close;

    ElevatorMenuLayout(int width, int height) {
        this.width = width;
        this.height = height;
        this.leftX = PAD;
        this.rightX = width - PAD - COL_W;
        this.centreX = this.leftX + COL_W + GAP;
        this.centreW = this.rightX - GAP - this.centreX;

        // ---- izquierda: lado (brújula), desplazamiento X/Y/Z y nombre del piso
        this.headingSide = new R(this.leftX, TOP, COL_W, TEXT_H);
        int compassW = 36;
        int compassX = this.leftX + (COL_W - (3 * compassW + 2 * 4)) / 2;
        int row1 = TOP + 11;
        int row2 = row1 + BUTTON_H + 1;
        int row3 = row2 + BUTTON_H + 1;
        this.side[SIDE_N] = new R(compassX + compassW + 4, row1, compassW, BUTTON_H);
        this.side[SIDE_W] = new R(compassX, row2, compassW, BUTTON_H);
        this.ctrl = new R(compassX + compassW + 4, row2, compassW, BUTTON_H);
        this.side[SIDE_E] = new R(compassX + 2 * (compassW + 4), row2, compassW, BUTTON_H);
        this.side[SIDE_S] = new R(compassX + compassW + 4, row3, compassW, BUTTON_H);
        this.labelShift = new R(this.leftX, row3 + BUTTON_H + 4, COL_W, TEXT_H);
        for (int i = 0; i < 3; i++) {
            int y = this.labelShift.bottom() + 2 + i * (BUTTON_H + 1);
            this.shiftAxis[i] = new R(this.leftX, y, 10, BUTTON_H);
            this.shift[i][0] = new R(this.leftX + 12, y, SHIFT_BTN_W, BUTTON_H);
            this.shift[i][1] = new R(this.leftX + 34, y, SHIFT_BTN_W, BUTTON_H);
            this.shiftValue[i] = new R(this.leftX + 56, y, 26, BUTTON_H);
            this.shift[i][2] = new R(this.leftX + 83, y, SHIFT_BTN_W, BUTTON_H);
            this.shift[i][3] = new R(this.leftX + 105, y, SHIFT_BTN_W, BUTTON_H);
        }
        this.labelName = new R(this.leftX, this.shiftAxis[2].bottom() + 5, COL_W, TEXT_H);
        this.nameBox = new R(this.leftX, this.labelName.bottom() + 1, COL_W, BUTTON_H);

        // ---- derecha: tamaño (ancho, fondo, alto) y velocidad
        this.headingSize = new R(this.rightX, TOP, COL_W, TEXT_H);
        for (int i = 0; i < 3; i++) {
            int base = TOP + 11 + i * 27;
            this.sizeLabel[i] = new R(this.rightX, base, COL_W, TEXT_H);
            for (int k = 0; k < 4; k++) {
                this.size[i][k] = new R(this.rightX + k * (STEP_W + STEP_GAP), base + 10, STEP_W, BUTTON_H);
            }
        }
        this.labelSpeed = new R(this.rightX, this.size[2][0].bottom() + 4, COL_W, TEXT_H);
        for (int k = 0; k < 4; k++) {
            this.speed[k] = new R(this.rightX + k * (STEP_W + STEP_GAP), this.labelSpeed.bottom() + 1, STEP_W, BUTTON_H);
        }
        this.zoneDims = new R(this.rightX, this.speed[0].bottom() + 5, COL_W, TEXT_H);
        this.zoneCells = new R(this.rightX, this.zoneDims.bottom() + 1, COL_W, TEXT_H);

        // ---- abajo
        int bottomY = height - 28;
        this.save = new R(width / 2 - 4 - 136, bottomY, 136, 18);
        this.close = new R(width / 2 + 4, bottomY, 136, 18);

        // ---- centro (de abajo hacia arriba: texto del mouse, cuenta, capas y, arriba, la cuadrícula)
        this.headingPlatform = new R(this.centreX, TOP, this.centreW, TEXT_H);
        int limit = bottomY - 6;
        this.hover = new R(this.centreX, limit - TEXT_H, this.centreW, TEXT_H);
        this.counts = new R(this.centreX, this.hover.y - 1 - TEXT_H, this.centreW, TEXT_H);
        int layerY = this.counts.y - 4 - BUTTON_H;
        this.layerPrev = new R(this.centreX, layerY, 16, BUTTON_H);
        this.layerNext = new R(this.centreX + this.centreW - 16, layerY, 16, BUTTON_H);
        this.layerLabel = new R(this.centreX + 18, layerY, this.centreW - 36, BUTTON_H);
        int gridY = this.headingPlatform.bottom() + 3;
        this.grid = new R(this.centreX, gridY, this.centreW, layerY - 4 - gridY);
    }

    /** Alto del panel para una pantalla de {@code screenHeight} píxeles (cabe entero y no pasa de lo cómodo). */
    static int panelHeight(int screenHeight) {
        return Math.max(MIN_H, Math.min(MAX_H, screenHeight - 8));
    }

    /** Ancho del panel para una pantalla de {@code screenWidth} píxeles. */
    static int panelWidth(int screenWidth) {
        return Math.max(MIN_W, Math.min(PREFERRED_W, screenWidth - 8));
    }

    /** Todas las piezas con nombre (para las pruebas y para dibujar). */
    Map<String, R> all() {
        Map<String, R> all = new LinkedHashMap<>();
        all.put("headingSide", this.headingSide);
        all.put("sideN", this.side[SIDE_N]);
        all.put("sideW", this.side[SIDE_W]);
        all.put("ctrl", this.ctrl);
        all.put("sideE", this.side[SIDE_E]);
        all.put("sideS", this.side[SIDE_S]);
        all.put("labelShift", this.labelShift);
        for (int i = 0; i < 3; i++) {
            all.put("shiftAxis" + i, this.shiftAxis[i]);
            for (int k = 0; k < 4; k++) {
                all.put("shift" + i + "_" + k, this.shift[i][k]);
            }
            all.put("shiftValue" + i, this.shiftValue[i]);
        }
        all.put("labelName", this.labelName);
        all.put("nameBox", this.nameBox);
        all.put("headingPlatform", this.headingPlatform);
        all.put("grid", this.grid);
        all.put("layerPrev", this.layerPrev);
        all.put("layerLabel", this.layerLabel);
        all.put("layerNext", this.layerNext);
        all.put("counts", this.counts);
        all.put("hover", this.hover);
        all.put("headingSize", this.headingSize);
        for (int i = 0; i < 3; i++) {
            all.put("sizeLabel" + i, this.sizeLabel[i]);
            for (int k = 0; k < 4; k++) {
                all.put("size" + i + "_" + k, this.size[i][k]);
            }
        }
        all.put("labelSpeed", this.labelSpeed);
        for (int k = 0; k < 4; k++) {
            all.put("speed" + k, this.speed[k]);
        }
        all.put("zoneDims", this.zoneDims);
        all.put("zoneCells", this.zoneCells);
        all.put("save", this.save);
        all.put("close", this.close);
        return all;
    }
}
