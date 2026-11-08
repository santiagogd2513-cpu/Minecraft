package com.example.beautifuldecor.client;

import com.example.beautifuldecor.elevator.ElevatorZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pruebas de la V3.2.1 (y del desplazamiento X/Y/Z de la V3.3.0) sin Minecraft: la cuadrícula de la plataforma (ElevatorPlatformView), la distribución del menú
 * (ElevatorMenuLayout) y, con piezas de mentira de BlockPos y Direction, que la marca del controlador caiga donde debe
 * con la fórmula REAL de ElevatorZone.
 * <p>
 * Con el argumento "dump" imprime los rectángulos del menú (para que el programa de Python revise que los textos caben).
 */
public class ElevatorMenuTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static final int RED = 0xFFC82828;
    private static final int BLUE = 0xFF2850C8;
    private static final int GREEN = 0xFF28C850;

    /** Zona de mentira que se puede cambiar a mano (para probar el "tiempo real"). */
    private static final class FakeZone implements ElevatorPlatformView.Source {
        final int sx;
        final int sy;
        final int sz;
        final int[] data;
        int reads;

        FakeZone(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.data = new int[sx * sy * sz];
        }

        void put(int x, int y, int z, int cell) {
            this.data[(y * this.sz + z) * this.sx + x] = cell;
        }

        @Override
        public int cell(int x, int y, int z) {
            check(x >= 0 && x < this.sx && y >= 0 && y < this.sy && z >= 0 && z < this.sz,
                    "se leyó una celda fuera de la zona: " + x + "," + y + "," + z);
            this.reads++;
            return this.data[(y * this.sz + z) * this.sx + x];
        }
    }

    // ------------------------------------------------------------------------------------------ la cuadrícula

    private static void testGrid() {
        FakeZone zone = new FakeZone(3, 4, 3);
        zone.put(0, 0, 0, RED);        // abajo
        zone.put(0, 2, 0, BLUE);       // más arriba en la misma columna
        zone.put(1, 0, 1, ElevatorPlatformView.STAYS);          // un bloque que se queda, solo
        zone.put(2, 0, 2, GREEN);
        zone.put(2, 3, 2, ElevatorPlatformView.STAYS);          // un bloque que se queda ENCIMA de uno que se mueve
        zone.put(1, 1, 0, ElevatorPlatformView.STAYS);          // se queda, y abajo hay uno que se mueve
        zone.put(1, 0, 0, GREEN);

        ElevatorPlatformView.Grid top = ElevatorPlatformView.scan(3, 4, 3, ElevatorPlatformView.TOP, zone);
        check(zone.reads == 3 * 4 * 3, "se debe leer cada celda de la zona una sola vez, se leyeron " + zone.reads);
        check(top.movable == 4 && top.stays == 3, "cuentas de toda la zona: se mueven 4 (R, B, G, G) y se quedan 3; salió "
                + top.movable + " y " + top.stays);
        check(top.cells[top.index(0, 0)] == BLUE && top.heights[top.index(0, 0)] == 2, "desde arriba se ve el bloque más alto que se mueve");
        check(top.cells[top.index(1, 1)] == ElevatorPlatformView.STAYS && top.heights[top.index(1, 1)] == 0,
                "una columna con solo bloques que se quedan los muestra como 'se queda'");
        check(top.cells[top.index(2, 2)] == GREEN && top.heights[top.index(2, 2)] == 0,
                "un bloque que se queda encima de uno que se mueve NO tapa al que se mueve");
        check(top.cells[top.index(1, 0)] == GREEN && top.heights[top.index(1, 0)] == 0, "idem con un bloque que se queda entre medio");
        check(top.cells[top.index(2, 0)] == ElevatorPlatformView.EMPTY && top.heights[top.index(2, 0)] == -1, "columna vacía");

        ElevatorPlatformView.Grid layer0 = ElevatorPlatformView.scan(3, 4, 3, 0, zone);
        check(layer0.cells[layer0.index(0, 0)] == RED, "la capa 0 muestra lo de la capa 0");
        check(layer0.cells[layer0.index(1, 1)] == ElevatorPlatformView.STAYS, "la capa 0 muestra lo que se queda");
        check(layer0.movable == top.movable && layer0.stays == top.stays, "las cuentas son de toda la zona, no de la capa");
        ElevatorPlatformView.Grid layer1 = ElevatorPlatformView.scan(3, 4, 3, 1, zone);
        check(layer1.cells[layer1.index(0, 0)] == ElevatorPlatformView.EMPTY, "en la capa 1 no hay nada en (0,0)");
        check(layer1.cells[layer1.index(1, 0)] == ElevatorPlatformView.STAYS, "en la capa 1 sí está el bloque que se queda de (1,0)");
        ElevatorPlatformView.Grid layer2 = ElevatorPlatformView.scan(3, 4, 3, 2, zone);
        check(layer2.cells[layer2.index(0, 0)] == BLUE && layer2.heights[layer2.index(0, 0)] == 2, "capa 2");
        ElevatorPlatformView.Grid layer3 = ElevatorPlatformView.scan(3, 4, 3, 3, zone);
        check(layer3.cells[layer3.index(2, 2)] == ElevatorPlatformView.STAYS, "capa 3");

        // tiempo real: si alguien pone o quita un bloque, la siguiente lectura lo refleja
        zone.put(2, 1, 1, RED);
        ElevatorPlatformView.Grid again = ElevatorPlatformView.scan(3, 4, 3, ElevatorPlatformView.TOP, zone);
        check(again.cells[again.index(2, 1)] == RED && again.movable == 5, "un bloque nuevo aparece en la lectura siguiente");
        zone.put(2, 1, 1, ElevatorPlatformView.EMPTY);
        zone.put(0, 2, 0, ElevatorPlatformView.EMPTY);
        ElevatorPlatformView.Grid removed = ElevatorPlatformView.scan(3, 4, 3, ElevatorPlatformView.TOP, zone);
        check(removed.cells[removed.index(0, 0)] == RED && removed.heights[removed.index(0, 0)] == 0 && removed.movable == 3,
                "quitar un bloque deja ver el de abajo");

        // zona vacía
        ElevatorPlatformView.Grid empty = ElevatorPlatformView.scan(2, 2, 2, ElevatorPlatformView.TOP, new FakeZone(2, 2, 2));
        check(empty.movable == 0 && empty.stays == 0, "zona vacía: cuentas en cero");
        for (int c : empty.cells) {
            check(c == ElevatorPlatformView.EMPTY, "zona vacía: celdas vacías");
        }

        // la capa pedida se acomoda al alto de la zona (por ejemplo, si el alto se achicó)
        check(ElevatorPlatformView.scan(3, 4, 3, 99, zone).layer == 3, "una capa de más se queda en la de arriba");
        check(ElevatorPlatformView.scan(3, 4, 3, -5, zone).layer == ElevatorPlatformView.TOP, "una capa negativa es la vista de arriba");

        // zona grande: 50 x 24 x 50 se lee entera
        FakeZone big = new FakeZone(50, 24, 50);
        big.put(49, 23, 49, RED);
        big.put(0, 0, 0, BLUE);
        ElevatorPlatformView.Grid bigGrid = ElevatorPlatformView.scan(50, 24, 50, ElevatorPlatformView.TOP, big);
        check(big.reads == 60000 && bigGrid.movable == 2, "zona máxima: 60 000 celdas, 2 bloques");
        check(bigGrid.cells[bigGrid.index(49, 49)] == RED && bigGrid.heights[bigGrid.index(49, 49)] == 23, "esquina de la zona máxima");
    }

    private static void testLayers() {
        int top = ElevatorPlatformView.TOP;
        check(ElevatorPlatformView.stepLayer(top, 1, 4) == 0, "de la vista de arriba, 'subir' va a la capa 0 (el piso)");
        check(ElevatorPlatformView.stepLayer(0, 1, 4) == 1 && ElevatorPlatformView.stepLayer(2, 1, 4) == 3, "subir de capa");
        check(ElevatorPlatformView.stepLayer(3, 1, 4) == 3, "arriba del todo no da la vuelta");
        check(ElevatorPlatformView.stepLayer(1, -1, 4) == 0, "bajar de capa");
        check(ElevatorPlatformView.stepLayer(0, -1, 4) == top, "bajo la capa 0 queda la vista de arriba");
        check(ElevatorPlatformView.stepLayer(top, -1, 4) == top, "la vista de arriba no baja más");
        check(ElevatorPlatformView.stepLayer(0, 1, 1) == 0 && ElevatorPlatformView.stepLayer(top, 1, 1) == 0, "alto 1: solo hay la capa 0");
        check(ElevatorPlatformView.clampLayer(7, 4) == 3 && ElevatorPlatformView.clampLayer(2, 4) == 2 && ElevatorPlatformView.clampLayer(-1, 4) == top,
                "clampLayer");
    }

    private static void testShade() {
        check(ElevatorPlatformView.shade(RED, 3, 4) == RED, "la capa de arriba no cambia el color");
        check(ElevatorPlatformView.shade(RED, 2, 4) != RED, "una capa más abajo se oscurece un poco");
        int prev = 0x1000000;
        for (int layer = 3; layer >= 0; layer--) {
            int c = ElevatorPlatformView.shade(0xFFC8C8C8, layer, 4);
            int r = (c >> 16) & 0xFF;
            check((c >>> 24) == 0xFF, "el color sigue opaco");
            check(r < prev, "más abajo, más oscuro");
            prev = r;
        }
        check(ElevatorPlatformView.shade(RED, 0, 1) == RED, "con alto 1 no se oscurece nada");
        int dark = ElevatorPlatformView.shade(0xFFFFFFFF, 0, 24);
        check(((dark >> 16) & 0xFF) >= 150, "ni la capa más baja de la zona más alta se vuelve negra");
    }

    private static void testGeometry() {
        check(ElevatorPlatformView.cellSize(100, 100, 3, 3, 16) == 16, "zona chica: celdas grandes con tope");
        check(ElevatorPlatformView.cellSize(100, 100, 50, 50, 16) == 2, "50 x 50 en 100 px: celdas de 2");
        check(ElevatorPlatformView.cellSize(100, 40, 10, 10, 16) == 4, "manda el lado más apretado");
        check(ElevatorPlatformView.cellSize(10, 10, 50, 50, 16) == 1, "nunca menos de 1 px");
        for (int n = 1; n <= 50; n++) {
            int cs = ElevatorPlatformView.cellSize(134, 67, n, n, 16);
            check(cs * n <= Math.max(134, n) || cs == 1, "la zona cabe en el recuadro (n=" + n + ")");
        }
        check(ElevatorPlatformView.cellAt(105, 55, 100, 50, 10, 4, 3) == 0, "primera celda");
        check(ElevatorPlatformView.cellAt(139.9, 79.9, 100, 50, 10, 4, 3) == 2 * 4 + 3, "última celda");
        check(ElevatorPlatformView.cellAt(140, 55, 100, 50, 10, 4, 3) == -1, "justo fuera, a la derecha");
        check(ElevatorPlatformView.cellAt(105, 80, 100, 50, 10, 4, 3) == -1, "justo fuera, abajo");
        check(ElevatorPlatformView.cellAt(99, 55, 100, 50, 10, 4, 3) == -1 && ElevatorPlatformView.cellAt(105, 49, 100, 50, 10, 4, 3) == -1,
                "fuera, a la izquierda o arriba");
        check(ElevatorPlatformView.widthAxis(true) == 'X' && ElevatorPlatformView.depthAxis(true) == 'Z', "mirando al norte o al sur: ancho por X, fondo por Z");
        check(ElevatorPlatformView.widthAxis(false) == 'Z' && ElevatorPlatformView.depthAxis(false) == 'X', "mirando al este o al oeste: ancho por Z, fondo por X");
    }

    // ------------------------------------------------------------------------------------------ la marca del controlador (con ElevatorZone de verdad)

    private static void testMarker() {
        BlockPos ctrl = new BlockPos(1000, 64, -500);
        int gx = 200;
        int gy = 80;
        int combos = 0;
        for (Direction facing : Direction.values()) {
            for (int width = 1; width <= 9; width++) {
                for (int depth = 1; depth <= 9; depth++) {
                    ElevatorZone zone = ElevatorZone.of(ctrl, facing, width, depth, 3);
                    check(zone.sizeX() * zone.sizeZ() == width * depth, "la zona tiene ancho x fondo celdas de planta");
                    int rx = ctrl.getX() - zone.minX();
                    int rz = ctrl.getZ() - zone.minZ();
                    boolean insideX = rx >= 0 && rx < zone.sizeX();
                    boolean insideZ = rz >= 0 && rz < zone.sizeZ();
                    check(insideX != insideZ, "el controlador está afuera de la zona, pegado a un borde (" + facing + " " + width + "x" + depth + ")");
                    int cs = 6;
                    int[] m = ElevatorPlatformView.markerCenter(gx, gy, cs, zone.sizeX(), zone.sizeZ(), rx, rz, 5);
                    int gw = zone.sizeX() * cs;
                    int gh = zone.sizeZ() * cs;
                    switch (facing) {
                        case NORTH -> check(m[1] == gy + gh + 5 && m[0] > gx && m[0] < gx + gw, "mirando al norte, el controlador queda ABAJO de la cuadrícula");
                        case SOUTH -> check(m[1] == gy - 5 && m[0] > gx && m[0] < gx + gw, "mirando al sur, el controlador queda ARRIBA de la cuadrícula");
                        case EAST -> check(m[0] == gx - 5 && m[1] > gy && m[1] < gy + gh, "mirando al este, el controlador queda a la IZQUIERDA");
                        default -> check(m[0] == gx + gw + 5 && m[1] > gy && m[1] < gy + gh, "mirando al oeste, el controlador queda a la DERECHA");
                    }
                    combos++;
                }
            }
        }
        check(combos == 4 * 81, "se probaron todas las combinaciones");
    }

    /** V3.3.0: el desplazamiento corre la zona entera en los ejes del mundo y con 0, 0, 0 no cambia nada. */
    private static void testShift() {
        BlockPos ctrl = new BlockPos(-37, 70, 912);
        int count = 0;
        for (Direction facing : Direction.values()) {
            for (int width = 1; width <= 7; width++) {
                for (int depth = 1; depth <= 7; depth++) {
                    ElevatorZone base = ElevatorZone.of(ctrl, facing, width, depth, 5);
                    check(base.equals(ElevatorZone.of(ctrl, facing, width, depth, 5, 0, 0, 0)), "sin desplazamiento es la zona de siempre");
                    check(base.minY() == ctrl.getY() - 1 && base.sizeY() == 5, "la capa de abajo está justo debajo del controlador");
                    for (int ox = -64; ox <= 64; ox += 32) {
                        for (int oy = -64; oy <= 64; oy += 64) {
                            for (int oz = -64; oz <= 64; oz += 21) {
                                ElevatorZone z = ElevatorZone.of(ctrl, facing, width, depth, 5, ox, oy, oz);
                                check(z.minX() == base.minX() + ox && z.minY() == base.minY() + oy && z.minZ() == base.minZ() + oz,
                                        "el desplazamiento corre la zona en x, y y z");
                                check(z.sizeX() == base.sizeX() && z.sizeY() == base.sizeY() && z.sizeZ() == base.sizeZ(),
                                        "el desplazamiento no cambia el tamaño");
                                check(z.cells() == width * depth * 5, "las celdas siguen siendo ancho x fondo x alto");
                                // la marca del controlador: adentro de la cuadrícula si el controlador cae en la planta de la zona, si no, en el margen
                                int rx = ctrl.getX() - z.minX();
                                int rz = ctrl.getZ() - z.minZ();
                                int cs = 4;
                                int[] m = ElevatorPlatformView.markerCenter(100, 60, cs, z.sizeX(), z.sizeZ(), rx, rz, 5);
                                int gw = z.sizeX() * cs;
                                int gh = z.sizeZ() * cs;
                                boolean inX = rx >= 0 && rx < z.sizeX();
                                boolean inZ = rz >= 0 && rz < z.sizeZ();
                                if (inX && inZ) {
                                    check(m[0] > 100 && m[0] < 100 + gw && m[1] > 60 && m[1] < 60 + gh, "controlador dentro de la planta: la marca va sobre su celda");
                                } else {
                                    boolean outside = m[0] < 100 || m[0] > 100 + gw || m[1] < 60 || m[1] > 60 + gh;
                                    check(outside, "controlador fuera de la planta: la marca va en el margen, no sobre un bloque");
                                    check(Math.abs(m[0] - 100) <= 5 + gw && Math.abs(m[1] - 60) <= 5 + gh && m[0] >= 95 && m[0] <= 105 + gw && m[1] >= 55 && m[1] <= 65 + gh,
                                            "la marca queda a lo más a 5 píxeles del borde");
                                }
                                count++;
                            }
                        }
                    }
                }
            }
        }
        check(count > 4 * 49 * 5 * 3 * 5, "se probaron todas las combinaciones de desplazamiento: " + count);
        // un ejemplo a mano: controlador en (10, 64, 10) mirando al norte, 3 x 2 x 4, corrido +5 en X, +1 en Y y -2 en Z
        ElevatorZone z = ElevatorZone.of(new BlockPos(10, 64, 10), Direction.NORTH, 3, 2, 4, 5, 1, -2);
        check(z.minX() == 9 + 5 && z.maxX() == 11 + 5 && z.minZ() == 8 - 2 && z.maxZ() == 9 - 2 && z.minY() == 63 + 1 && z.maxY() == 63 + 1 + 3,
                "ejemplo a mano: " + z);
    }

    // ------------------------------------------------------------------------------------------ la distribución del menú

    private static void testLayout(int w, int h) {
        ElevatorMenuLayout lay = new ElevatorMenuLayout(w, h);
        Map<String, ElevatorMenuLayout.R> all = lay.all();
        List<String> names = new ArrayList<>(all.keySet());
        for (String name : names) {
            ElevatorMenuLayout.R r = all.get(name);
            check(r.w() > 0 && r.h() > 0, name + " tiene tamaño (" + w + "x" + h + ")");
            check(r.x() >= 0 && r.y() >= 0 && r.right() <= w && r.bottom() <= h, name + " queda dentro del panel " + w + "x" + h + ": " + r);
        }
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                check(!all.get(names.get(i)).overlaps(all.get(names.get(j))),
                        names.get(i) + " y " + names.get(j) + " se pisan en el panel " + w + "x" + h + ": " + all.get(names.get(i)) + " " + all.get(names.get(j)));
            }
        }
        // el título del panel (franja de arriba) queda libre
        for (String name : names) {
            check(all.get(name).y() >= ElevatorMenuLayout.HEADER + 1, name + " invade la franja del título");
        }

        // LO PEDIDO: el tamaño (ancho, fondo, alto) a la DERECHA; lo que cambia DÓNDE queda la cabina (lado y X, Y, Z) a la IZQUIERDA; la plataforma en medio
        for (int i = 0; i < 3; i++) {
            check(lay.sizeLabel[i].x() >= lay.rightX, "la etiqueta de tamaño " + i + " va en la columna derecha");
            for (int k = 0; k < 4; k++) {
                check(lay.size[i][k].x() >= lay.rightX && lay.size[i][k].x() > lay.grid.right(), "el botón de tamaño " + i + "," + k + " va a la derecha de todo");
            }
        }
        check(lay.headingSize.x() >= lay.rightX, "el título 'Tamaño' va a la derecha");
        for (int slot = 0; slot < 4; slot++) {
            check(lay.side[slot].right() <= lay.centreX, "el botón de lado " + slot + " va a la izquierda");
        }
        check(lay.labelShift.right() <= lay.centreX && lay.headingSide.right() <= lay.centreX, "los títulos de lado y desplazamiento van a la izquierda");
        for (int axis = 0; axis < 3; axis++) {
            check(lay.shiftAxis[axis].right() <= lay.centreX && lay.shiftValue[axis].right() <= lay.centreX, "la letra y el valor del eje " + axis + " van a la izquierda");
            for (int k = 0; k < 4; k++) {
                check(lay.shift[axis][k].right() <= lay.centreX && lay.shift[axis][k].x() < lay.grid.x(), "el botón de desplazamiento " + axis + "," + k + " va a la izquierda de la plataforma");
            }
        }
        check(lay.nameBox.right() <= lay.centreX && lay.labelName.right() <= lay.centreX, "el nombre del piso va a la izquierda");
        check(lay.labelSpeed.x() >= lay.rightX, "la velocidad va a la derecha, debajo del tamaño");
        for (int k = 0; k < 4; k++) {
            check(lay.speed[k].x() >= lay.rightX && lay.speed[k].y() > lay.size[2][0].bottom(), "el botón de velocidad " + k + " va a la derecha, debajo del tamaño");
        }
        check(lay.grid.x() >= lay.leftX + ElevatorMenuLayout.COL_W && lay.grid.right() <= lay.rightX, "la plataforma queda entre las dos columnas");
        check(lay.grid.w() == lay.centreW && lay.grid.w() >= 112, "la plataforma tiene ancho suficiente: " + lay.grid.w());
        check(lay.grid.h() >= 80, "la plataforma tiene alto suficiente: " + lay.grid.h());

        // el desplazamiento: tres filas X, Y, Z de arriba hacia abajo, y en cada una -5, -1, [valor], +1, +5 de izquierda a derecha
        for (int axis = 0; axis < 3; axis++) {
            ElevatorMenuLayout.R[] row = lay.shift[axis];
            check(lay.shiftAxis[axis].right() <= row[0].x() && row[0].right() <= row[1].x() && row[1].right() <= lay.shiftValue[axis].x()
                    && lay.shiftValue[axis].right() <= row[2].x() && row[2].right() <= row[3].x(), "fila " + axis + " del desplazamiento: letra, -5, -1, valor, +1, +5 en ese orden");
            for (ElevatorMenuLayout.R r : row) {
                check(r.y() == lay.shiftValue[axis].y() && r.y() == lay.shiftAxis[axis].y(), "la fila " + axis + " del desplazamiento está a una sola altura");
            }
            if (axis > 0) {
                check(lay.shiftAxis[axis].y() > lay.shiftAxis[axis - 1].bottom() - 1, "filas X, Y, Z de arriba hacia abajo");
            }
        }
        check(lay.labelShift.bottom() <= lay.shiftAxis[0].y() && lay.side[ElevatorMenuLayout.SIDE_S].bottom() <= lay.labelShift.y(),
                "de arriba abajo: brújula, 'Mover la zona', filas X Y Z, nombre");
        check(lay.shiftAxis[2].bottom() <= lay.labelName.y(), "el nombre queda debajo de las filas del desplazamiento");

        // la brújula: norte arriba, oeste y este a los lados, sur abajo y el controlador en el centro
        ElevatorMenuLayout.R n = lay.side[ElevatorMenuLayout.SIDE_N];
        ElevatorMenuLayout.R west = lay.side[ElevatorMenuLayout.SIDE_W];
        ElevatorMenuLayout.R east = lay.side[ElevatorMenuLayout.SIDE_E];
        ElevatorMenuLayout.R s = lay.side[ElevatorMenuLayout.SIDE_S];
        check(n.bottom() <= lay.ctrl.y() && s.y() >= lay.ctrl.bottom(), "norte arriba y sur abajo del controlador");
        check(west.right() <= lay.ctrl.x() && east.x() >= lay.ctrl.right() && west.y() == lay.ctrl.y() && east.y() == lay.ctrl.y(), "oeste y este a los lados");
        check(n.x() == lay.ctrl.x() && s.x() == lay.ctrl.x(), "norte y sur alineados con el centro");

        // el orden de los tres renglones de tamaño y de los cuatro botones (-5, -1, +1, +5) de izquierda a derecha
        for (int i = 0; i < 3; i++) {
            for (int k = 1; k < 4; k++) {
                check(lay.size[i][k].x() > lay.size[i][k - 1].right() - 1 && lay.size[i][k].y() == lay.size[i][0].y(), "botones de tamaño en fila");
            }
            if (i > 0) {
                check(lay.size[i][0].y() > lay.size[i - 1][0].bottom(), "renglones de tamaño de arriba hacia abajo");
            }
        }
        // las capas: [<] etiqueta [>] en una fila debajo de la cuadrícula
        check(lay.layerPrev.y() == lay.layerNext.y() && lay.layerPrev.y() == lay.layerLabel.y(), "fila de capas");
        check(lay.layerPrev.right() <= lay.layerLabel.x() && lay.layerLabel.right() <= lay.layerNext.x(), "[<] etiqueta [>] en ese orden");
        check(lay.grid.bottom() <= lay.layerPrev.y() && lay.layerPrev.bottom() <= lay.counts.y() && lay.counts.bottom() <= lay.hover.y(),
                "de arriba abajo: cuadrícula, capas, cuenta y texto del mouse");
        check(lay.save.x() + lay.save.w() <= lay.close.x(), "Guardar a la izquierda de Cerrar");
    }

    private static void testPanelSizes() {
        int[][] screens = {{585, 270}, {468, 216}, {427, 240}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}, {780, 360}, {400, 200}};
        for (int[] s : screens) {
            int w = ElevatorMenuLayout.panelWidth(s[0]);
            int h = ElevatorMenuLayout.panelHeight(s[1]);
            check(w >= ElevatorMenuLayout.MIN_W && w <= ElevatorMenuLayout.PREFERRED_W && h >= ElevatorMenuLayout.MIN_H && h <= ElevatorMenuLayout.MAX_H,
                    "tamaño del panel dentro de lo permitido para " + s[0] + "x" + s[1]);
            if (s[0] >= ElevatorMenuLayout.MIN_W + 8) {
                check(w <= s[0] - 8, "el panel cabe a lo ancho en " + s[0] + "x" + s[1]);
            }
            if (s[1] >= ElevatorMenuLayout.MIN_H + 8) {
                check(h <= s[1] - 8, "el panel cabe a lo alto en " + s[0] + "x" + s[1]);
            }
            testLayout(w, h);
        }
        // las 'pantallas' de un celular de 2340x1080 con la escala de GUI 3, 4 y 5
        check(ElevatorMenuLayout.panelHeight(1080 / 4) <= 270 - 8, "con la escala 4 en un celular de 1080 px de alto cabe");
        testLayout(460, 214);
        testLayout(460, 250);
        testLayout(419, 220);
        testLayout(410, 214);
    }

    private static void dump(String[] args) {
        int[] widths = args.length > 2 ? new int[] {Integer.parseInt(args[1])} : new int[] {460, 419, 410};
        int dumpHeight = args.length > 2 ? Integer.parseInt(args[2]) : 214;
        for (int w : widths) {
            ElevatorMenuLayout lay = new ElevatorMenuLayout(w, dumpHeight);
            for (Map.Entry<String, ElevatorMenuLayout.R> e : lay.all().entrySet()) {
                ElevatorMenuLayout.R r = e.getValue();
                System.out.println("RECT " + w + " " + e.getKey() + " " + r.x() + " " + r.y() + " " + r.w() + " " + r.h());
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("dump")) {
            dump(args);
            return;
        }
        testGrid();
        testLayers();
        testShade();
        testGeometry();
        testMarker();
        testShift();
        testPanelSizes();
        System.out.println("ELEVATOR MENU OK (" + checks + " comprobaciones)");
    }
}
