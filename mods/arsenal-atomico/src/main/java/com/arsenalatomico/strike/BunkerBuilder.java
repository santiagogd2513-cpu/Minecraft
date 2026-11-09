package com.arsenalatomico.strike;

import com.arsenalatomico.NukePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * El búnker del Protocolo Hongo, el más detallado del mod. Lo construye el servidor de a poco:
 * <ul>
 *   <li>una plataforma de concreto con líneas pintadas, cerca perimetral y cuatro torres de reflectores;</li>
 *   <li>el silo: un pozo de 32 bloques con anillos de luz, columnas de lámparas, tres pasarelas, el foso de
 *       las llamas y dos ductos que llevan el fuego hasta dos ventilas en la superficie;</li>
 *   <li>el cuarto de control bajo tierra, con una ventana blindada que mira al misil, consolas con
 *       pantallas, sillas, la mesa de mapas, dos estaciones de llaves separadas, racks de computadores y
 *       lámparas rojas de alarma;</li>
 *   <li>un pasillo con cables en el techo, franjas de peligro y una puerta blindada;</li>
 *   <li>una escalera de caracol que se puede caminar desde la caseta hasta el pasillo;</li>
 *   <li>la caseta de entrada: puerta, ventanas, escritorio de vigilancia, casilleros, faroles y antena.</li>
 * </ul>
 * Todo queda en el mundo después del ataque (la boca del silo se cierra con compuertas de hierro).
 */
public final class BunkerBuilder {
    private final ServerLevel level;
    private final int cx, cz, fx, fz, rx, rz;
    private final int top, bottom, floor, groundY;
    private final List<Column> columns = new ArrayList<>();
    private final List<Object[]> todo = new ArrayList<>();
    private int nextColumn, next;
    /** Base Sol Negro: lleva la consola de lanzamiento en el centro de mando. */
    private boolean console;

    /** Una columna del terreno: se mide la altura del suelo cuando le toca (no todas al empezar). */
    private record Column(int a, int b, double d, int outer) {}

    private BunkerBuilder(ServerLevel level, Vec3 site, float yaw, int groundY) {
        this.level = level;
        int[] ax = NukePlan.axes(yaw);
        fx = ax[0];
        fz = ax[1];
        rx = ax[2];
        rz = ax[3];
        cx = Mth.floor(site.x);
        cz = Mth.floor(site.z);
        top = Mth.floor(site.y);
        bottom = top - NukePlan.DEPTH;
        floor = bottom + NukePlan.ROOM_UP;
        this.groundY = groundY;
    }

    /** Dónde va el borde: en el suelo, o más arriba si el mundo no deja cavar tan hondo (superplano). */
    public static double rimHeight(ServerLevel level, double ground) {
        return Math.max(ground, level.getMinBuildHeight() + 4 + NukePlan.DEPTH);
    }

    public static BunkerBuilder build(ServerLevel level, Vec3 site, float yaw, int groundY) {
        return build(level, site, yaw, groundY, false);
    }

    /** {@code console}: la Base Sol Negro, con la consola de lanzamiento frente a la ventana del silo. */
    public static BunkerBuilder build(ServerLevel level, Vec3 site, float yaw, int groundY, boolean console) {
        BunkerBuilder b = new BunkerBuilder(level, site, yaw, groundY);
        b.console = console;
        b.terrain();
        b.vents();
        b.shaft();
        b.room();
        b.corridor();
        b.stairs();
        b.guardhouse();
        b.fence();
        b.towers();
        return b;
    }

    // ------------------------------------------------------------------ piezas

    private void terrain() {
        int outer = NukePlan.PAD_R + Math.max(0, top - groundY) + 2;
        for (int a = -outer; a <= outer; a++) {
            for (int b = -outer; b <= outer; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d <= outer) columns.add(new Column(a, b, d, outer));
            }
        }
    }

    /** Plataforma y loma: se hace columna por columna cuando le toca (así no se cargan todos los chunks de golpe). Devuelve cuántos bloques puso. */
    private int column(Column c) {
        int before = placed;
        int x = cx + c.a * fx + c.b * rx, z = cz + c.a * fz + c.b * rz;
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (c.d <= NukePlan.PAD_R) {
            int clear = Math.min(top + 40, Math.max(top + 12, g + 2));
            for (int y = Math.min(g, top - 1) - 1; y < top - 1; y++) apply(x, y, z, y < top - 3 ? Blocks.STONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            apply(x, top - 1, z, pad(c.a, c.b, c.d));
            for (int y = top; y <= clear; y++) apply(x, y, z, Blocks.AIR.defaultBlockState());
        } else {
            int h = top - 1 - (int) (c.d - NukePlan.PAD_R);
            if (h < groundY - 1 || h < g - 1) return placed - before;
            for (int y = g - 1; y < h; y++) apply(x, y, z, Blocks.DIRT.defaultBlockState());
            apply(x, h, z, Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = h + 1; y < h + 4; y++) apply(x, y, z, Blocks.AIR.defaultBlockState());
        }
        return placed - before;
    }

    /** El dibujo del piso: concreto gris con juntas, el camino amarillo a la caseta y el círculo rojo del silo. */
    private BlockState pad(int a, int b, double d) {
        if (d <= NukePlan.WALL_R + 2.5) return Blocks.RED_CONCRETE.defaultBlockState();
        if (d <= NukePlan.WALL_R + 3.5) return Blocks.WHITE_CONCRETE.defaultBlockState();
        if (a < -6 && a > NukePlan.STAIR_A + 4 && Math.abs(b) == 2) return Blocks.YELLOW_CONCRETE.defaultBlockState();
        if (Math.abs(d - NukePlan.FENCE_R + 2.5) < 0.5) return Blocks.YELLOW_CONCRETE.defaultBlockState();
        if (Math.floorMod(a, 7) == 0 || Math.floorMod(b, 7) == 0) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        return Blocks.GRAY_CONCRETE.defaultBlockState();
    }

    /** Foso de llamas bajo el silo, el ducto ancho hacia adelante y las dos ventilas que suben hasta arriba. */
    private void vents() {
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        // el ducto (plenum) del fondo: de a = 2 a a = 11, b de -6 a 6, tres bloques de alto
        box(2, -7, bottom - 2, 12, 7, bottom + 3, wall);
        box(2, -6, bottom, 11, 6, bottom + 2, Blocks.AIR.defaultBlockState());
        for (int a = 2; a <= 11; a++) for (int b = -6; b <= 6; b++) put(a, b, bottom - 1, (a + b) % 3 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
        // las dos chimeneas
        for (int side = -1; side <= 1; side += 2) {
            int vb = side * NukePlan.VENT_B, va = NukePlan.VENT_A;
            box(va - 2, vb - 2, bottom + 3, va + 2, vb + 2, top - 1, wall);
            box(va - 1, vb - 1, bottom, va + 1, vb + 1, top - 1, Blocks.AIR.defaultBlockState());
            // rejilla arriba y el borde con franjas
            for (int a = va - 1; a <= va + 1; a++) for (int b = vb - 1; b <= vb + 1; b++) put(a, b, top - 1, Blocks.IRON_BARS.defaultBlockState());
            for (int a = va - 2; a <= va + 2; a++) {
                for (int b = vb - 2; b <= vb + 2; b++) {
                    if (Math.abs(a - va) == 2 || Math.abs(b - vb) == 2) {
                        put(a, b, top - 1, (a + b) % 2 == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                    }
                }
            }
        }
    }

    private void shaft() {
        double sr = NukePlan.SHAFT_R, wr = NukePlan.WALL_R;
        int R = Mth.ceil(wr);
        for (int a = -R; a <= R; a++) {
            for (int b = -R; b <= R; b++) {
                double d = Math.sqrt(a * a + b * b);
                double ang = Math.atan2(b, a);
                if (d <= sr) {
                    put(a, b, bottom - 1, d < 1.6 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                    put(a, b, bottom - 2, Blocks.BLACK_CONCRETE.defaultBlockState());
                    for (int y = bottom; y < top; y++) {
                        int k = y - bottom;
                        // tres pasarelas de servicio pegadas a la pared
                        boolean walk = (k == 8 || k == 16 || k == 24) && d >= 2.6;
                        put(a, b, y, walk ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
                                : Blocks.AIR.defaultBlockState());
                    }
                } else if (d <= wr) {
                    int seg8 = (int) Math.round((ang + Math.PI) / (Math.PI / 4)) % 8;
                    boolean strip = d <= sr + 1.2 && Math.abs(ang - Math.round(ang / (Math.PI / 4)) * (Math.PI / 4)) < 0.2;
                    for (int y = bottom - 2; y < top; y++) {
                        int k = y - bottom;
                        BlockState s = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                        if (strip && k % 3 == 1 && k > 0) s = Blocks.SEA_LANTERN.defaultBlockState();     // columnas de lámparas
                        else if (k % 8 == 0) s = Blocks.POLISHED_DEEPSLATE.defaultBlockState();          // anillos
                        else if (k % 8 == 4 && seg8 % 2 == 0) s = Blocks.YELLOW_CONCRETE.defaultBlockState();  // marcas de altura
                        else if (y >= top - 3) s = Blocks.RED_CONCRETE.defaultBlockState();
                        put(a, b, y, s);
                    }
                    int seg = (int) Math.floor((ang + Math.PI) / (Math.PI / 12));
                    put(a, b, top - 1, seg % 2 == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                }
            }
        }
        // la boca del ducto en el fondo del pozo (hacia adelante)
        box(2, -2, bottom, 6, 2, bottom + 2, Blocks.AIR.defaultBlockState());
        // rieles de las compuertas a los dos lados de la boca
        for (int side = -1; side <= 1; side += 2) {
            for (int b = -12; b <= 12; b++) {
                put(side * 5, b, top - 1, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
        }
    }

    /** El cuarto de control: a de -15 a -7, b de -7 a 7, cinco bloques de alto. */
    private void room() {
        int f = floor;
        // cascarón
        box(-15, -7, f - 1, -7, 7, f + 5, Blocks.WHITE_CONCRETE.defaultBlockState());
        box(-14, -6, f, -8, 6, f + 4, Blocks.AIR.defaultBlockState());
        for (int a = -14; a <= -8; a++) {
            for (int b = -6; b <= 6; b++) {
                // piso a cuadros y techo con paneles de luz
                put(a, b, f - 1, (a + b) % 2 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                put(a, b, f + 5, Math.floorMod(a, 3) == 0 && Math.floorMod(b, 3) == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // zócalo oscuro y franja gris arriba
        for (int a = -15; a <= -7; a++) {
            for (int b = -7; b <= 7; b++) {
                if (a != -15 && a != -7 && Math.abs(b) != 7) continue;
                put(a, b, f, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                put(a, b, f + 4, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
        }
        // lámparas rojas de alarma en el techo (prendidas por un bloque de redstone encima)
        for (int a : new int[]{-9, -13}) {
            for (int side = -1; side <= 1; side += 2) {
                put(a, side * 5, f + 5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
                put(a, side * 5, f + 6, Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }
        // la ventana blindada hacia el silo: un túnel de vidrio con marco negro
        box(-7, -4, f - 1, -3, 4, f + 5, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), NukePlan.SHAFT_R);
        for (int a = -7; a <= -3; a++) {
            for (int b = -3; b <= 3; b++) {
                for (int y = f + 1; y <= f + 3; y++) {
                    if (Math.sqrt(a * a + b * b) > NukePlan.SHAFT_R) put(a, b, y, Blocks.GLASS.defaultBlockState());
                }
            }
        }
        // consolas debajo de la ventana (bajitas, para no tapar la vista) y a los lados con pantallas
        for (int b = -6; b <= 6; b++) {
            if (Math.abs(b) <= 3) {
                put(-8, b, f, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
            } else {
                put(-8, b, f, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                put(-8, b, f + 1, Blocks.BLACK_CONCRETE.defaultBlockState());
            }
        }
        // sillas de los operadores (miran hacia la ventana)
        for (int b : new int[]{-5, -2, 2, 5}) put(-9, b, f, stair(Blocks.SPRUCE_STAIRS.defaultBlockState(), -1, 0));
        // la mesa de mapas en el centro
        for (int b = -1; b <= 1; b++) put(-11, b, f, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        // Base Sol Negro: la consola de lanzamiento, entre las sillas del medio, mirando a la ventana
        if (console) put(-9, 0, f, com.arsenalatomico.registry.ModBlocks.LAUNCH_CONSOLE.get().defaultBlockState());
        // las dos estaciones de llaves, separadas (nadie puede girar las dos solo)
        for (int side = -1; side <= 1; side += 2) {
            put(-13, side * 4, f, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            put(-13, side * 4, f + 1, Blocks.LODESTONE.defaultBlockState());
        }
        // racks de computadores contra las paredes de los lados
        for (int a = -13; a <= -10; a++) {
            for (int side = -1; side <= 1; side += 2) {
                Direction in = dir(0, -side);
                BlockState rack = (a % 2 == 0 ? Blocks.BLAST_FURNACE : Blocks.SMOKER).defaultBlockState().setValue(AbstractFurnaceBlock.FACING, in);
                put(a, side * 6, f, rack);
                put(a, side * 6, f + 1, (a % 2 == 0 ? Blocks.SMOKER : Blocks.BLAST_FURNACE).defaultBlockState().setValue(AbstractFurnaceBlock.FACING, in));
                put(a, side * 6, f + 2, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        // casilleros junto a la puerta de atrás
        for (int side = -1; side <= 1; side += 2) {
            put(-14, side * 4, f, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(1, 0)));
            put(-14, side * 4, f + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(1, 0)));
        }
        // la puerta hacia el pasillo, con marco de hierro
        box(-15, -2, f, -15, 2, f + 4, Blocks.IRON_BLOCK.defaultBlockState());
        box(-15, -1, f, -15, 1, f + 3, Blocks.AIR.defaultBlockState());
    }

    /** El pasillo de la escalera al cuarto: a de -22 a -16, cuatro de alto, con cables y una puerta blindada. */
    private void corridor() {
        int f = floor;
        box(-23, -2, f - 1, -15, 2, f + 4, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        box(-22, -1, f, -16, 1, f + 3, Blocks.AIR.defaultBlockState());
        for (int a = -22; a <= -16; a++) {
            put(a, 0, f - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (int side = -1; side <= 1; side += 2) {
                // franjas de peligro a los lados del piso
                put(a, side, f - 1, a % 2 == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                // cables en las esquinas de arriba
                put(a, side, f + 3, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axisOf(1, 0)));
                // tubería en la pared
                put(a, side * 2, f + 2, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
            put(a, 0, f + 4, Math.floorMod(a, 3) == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
            // lámparas rojas en las paredes
            if (Math.floorMod(a, 4) == 0) {
                for (int side = -1; side <= 1; side += 2) {
                    put(a, side * 2, f + 1, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
                    put(a, side * 3, f + 1, Blocks.REDSTONE_BLOCK.defaultBlockState());
                }
            }
        }
        // la puerta blindada a mitad del pasillo (marco de hierro grueso)
        box(-19, -2, f, -19, 2, f + 4, Blocks.IRON_BLOCK.defaultBlockState());
        box(-19, -1, f, -19, 1, f + 2, Blocks.AIR.defaultBlockState());
    }

    /** La escalera de caracol: un hueco de 3×3 con un pilar en el medio, de la caseta al pasillo. */
    private void stairs() {
        int f = floor, sa = NukePlan.STAIR_A, g = top;
        // paredes, piso del fondo y la salida al pasillo
        box(sa - 2, -2, f - 1, sa + 2, 2, g - 2, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        box(sa - 1, -1, f, sa + 1, 1, g + 3, Blocks.AIR.defaultBlockState());
        box(sa + 2, -1, f, sa + 2, 1, f + 3, Blocks.AIR.defaultBlockState());
        for (int y = f - 1; y <= g + 3; y++) {
            int k = y - f;
            put(sa, 0, y, k > 0 && k % 4 == 2 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }
        // los escalones: bajando, las celdas del anillo en este orden; el último queda junto a la salida
        int[][] ring = {{1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}};
        int n = g - f;                         // escalones de g-1 hasta f
        int off = Math.floorMod(2 - (n - 1), 8);
        for (int k = 0; k < n; k++) {
            int[] c = ring[(k + off) % 8], up = ring[Math.floorMod(k + off - 1, 8)];
            BlockState st = stair(Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState(), up[0] - c[0], up[1] - c[1]);
            put(sa + c[0], c[1], g - 1 - k, st);
        }
        // descanso arriba (cuatro celdas a ras del piso de la caseta)
        for (int j = 1; j <= 4; j++) {
            int[] c = ring[Math.floorMod(off - j, 8)];
            put(sa + c[0], c[1], g - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
    }

    /** La caseta de entrada sobre la escalera: 9×9, con puerta hacia el silo. */
    private void guardhouse() {
        int sa = NukePlan.STAIR_A, g = top;
        int a0 = sa - 4, a1 = sa + 4;
        // piso adentro (sin tapar el hueco de la escalera)
        for (int a = a0 + 1; a < a1; a++) {
            for (int b = -3; b <= 3; b++) {
                if (Math.abs(a - sa) <= 1 && Math.abs(b) <= 1) continue;
                put(a, b, g - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        for (int a = a0; a <= a1; a++) {
            for (int b = -4; b <= 4; b++) {
                boolean edgeA = a == a0 || a == a1, edgeB = Math.abs(b) == 4;
                if (!edgeA && !edgeB) continue;
                boolean corner = edgeA && edgeB;
                for (int y = g; y <= g + 3; y++) {
                    BlockState s;
                    if (corner) s = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                    else if (y == g) s = Blocks.GRAY_CONCRETE.defaultBlockState();
                    else if (y == g + 3) s = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                    else {
                        boolean window = (edgeA && Math.abs(b) == 2) || (edgeB && (a == sa - 2 || a == sa + 2));
                        s = window ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                    }
                    put(a, b, y, s);
                }
            }
        }
        // techo con alero
        for (int a = a0 - 1; a <= a1 + 1; a++) {
            for (int b = -5; b <= 5; b++) {
                boolean eave = a == a0 - 1 || a == a1 + 1 || Math.abs(b) == 5;
                put(a, b, g + 4, eave ? Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // la puerta, mirando al silo, y una luz encima
        Direction out = dir(1, 0);
        put(a1, 0, g, Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, out)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        put(a1, 0, g + 1, Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, out)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        put(a1, 0, g + 3, Blocks.SEA_LANTERN.defaultBlockState());
        put(a1 + 1, -1, g - 1, Blocks.YELLOW_CONCRETE.defaultBlockState());
        put(a1 + 1, 1, g - 1, Blocks.YELLOW_CONCRETE.defaultBlockState());
        // adentro: escritorio de vigilancia con pantallas, silla, casilleros y faroles colgando
        for (int b = -3; b <= -2; b++) {
            put(a0 + 1, b, g, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            put(a0 + 1, b, g + 1, Blocks.BLACK_CONCRETE.defaultBlockState());
        }
        put(a0 + 2, -3, g, stair(Blocks.SPRUCE_STAIRS.defaultBlockState(), 1, 0));
        put(a0 + 1, 3, g, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(1, 0)));
        put(a0 + 1, 3, g + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(1, 0)));
        put(a1 - 1, 3, g, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(-1, 0)));
        for (int a : new int[]{a0 + 2, a1 - 2}) {
            for (int b : new int[]{-3, 3}) put(a, b, g + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // antena en el techo
        for (int y = g + 5; y <= g + 9; y++) put(a0 + 1, -3, y, Blocks.IRON_BARS.defaultBlockState());
        put(a0 + 1, -3, g + 10, Blocks.LIGHTNING_ROD.defaultBlockState());
        // mástil del radar (el plato que gira lo dibuja el cliente)
        for (int y = g + 5; y <= g + 6; y++) put(a1 - 1, 3, y, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
    }

    /** Cerca de barrotes alrededor de todo, con postes y un portón atrás. */
    private void fence() {
        int g = top, R = NukePlan.FENCE_R;
        for (int a = -R - 1; a <= R + 1; a++) {
            for (int b = -R - 1; b <= R + 1; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (Math.abs(d - R) >= 0.5) continue;
                if (a < 0 && Math.abs(b) <= 2) continue;     // el portón, detrás de la caseta
                boolean post = Math.floorMod((int) Math.round(Math.atan2(b, a) * R), 6) == 0;
                put(a, b, g, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
                put(a, b, g + 1, post ? Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
                put(a, b, g + 2, post ? Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
                if (post) put(a, b, g + 3, Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    /** Cuatro torres de reflectores en las esquinas (los haces de luz los dibuja el cliente). */
    private void towers() {
        int g = top;
        for (int i = 0; i < 4; i++) {
            int a = (i < 2 ? 1 : -1) * 22, b = (i % 2 == 0 ? 1 : -1) * 22;
            for (int y = g; y <= g + 6; y++) put(a, b, y, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            put(a, b, g + 7, Blocks.IRON_BLOCK.defaultBlockState());
            put(a, b, g + 8, Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    /** Quita las compuertas de hierro de la boca del pozo (antes de otro lanzamiento desde la misma base). */
    public static void open(ServerLevel level, Vec3 site) {
        int x0 = Mth.floor(site.x), z0 = Mth.floor(site.z), top = Mth.floor(site.y);
        int r = Mth.ceil(NukePlan.SHAFT_R);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > NukePlan.SHAFT_R * NukePlan.SHAFT_R) continue;
                BlockPos p = new BlockPos(x0 + dx, top - 1, z0 + dz);
                if (level.getBlockState(p).is(Blocks.IRON_TRAPDOOR)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** Dónde queda la consola de lanzamiento de la Base Sol Negro. */
    public static BlockPos consolePos(Vec3 site, float yaw) {
        Vec3 p = NukePlan.local(new Vec3(Mth.floor(site.x), Mth.floor(site.y), Mth.floor(site.z)), yaw, -9, 0,
                -NukePlan.DEPTH + NukePlan.ROOM_UP);
        return BlockPos.containing(p.x, p.y, p.z);
    }

    /** Compuertas de hierro sobre la boca del pozo (al final del ataque). */
    public static void close(ServerLevel level, Vec3 site) {
        int x0 = Mth.floor(site.x), z0 = Mth.floor(site.z), top = Mth.floor(site.y);
        BlockState door = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.FACING, Direction.NORTH);
        int r = Mth.ceil(NukePlan.SHAFT_R);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > NukePlan.SHAFT_R * NukePlan.SHAFT_R) continue;
                BlockPos p = new BlockPos(x0 + dx, top - 1, z0 + dz);
                if (level.getBlockState(p).isAir()) level.setBlock(p, door, 2);
            }
        }
    }

    // ------------------------------------------------------------------ ayudas

    /** Dirección del mundo para un paso (da, db) en el sistema del búnker. */
    private Direction dir(int da, int db) {
        int dx = da * fx + db * rx, dz = da * fz + db * rz;
        Direction d = Direction.fromDelta(Integer.signum(dx), 0, Integer.signum(dz));
        return d == null ? Direction.NORTH : d;
    }

    private Direction.Axis axisOf(int da, int db) {
        return dir(da, db).getAxis();
    }

    /** Escalón que sube hacia (da, db). */
    private BlockState stair(BlockState s, int da, int db) {
        return s.setValue(StairBlock.FACING, dir(da, db)).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private void box(int a0, int b0, int y0, int a1, int b1, int y1, BlockState s) {
        box(a0, b0, y0, a1, b1, y1, s, -1);
    }

    /** Llena una caja; si «skipInside» > 0, no toca lo que quede a menos de ese radio del centro del silo. */
    private void box(int a0, int b0, int y0, int a1, int b1, int y1, BlockState s, double skipInside) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) {
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) {
                if (skipInside > 0 && Math.sqrt(a * a + b * b) <= skipInside) continue;
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) put(a, b, y, s);
            }
        }
    }

    private void put(int a, int b, int y, BlockState s) {
        set(cx + a * fx + b * rx, y, cz + a * fz + b * rz, s);
    }

    private void set(int x, int y, int z, BlockState s) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
        todo.add(new Object[]{new BlockPos(x, y, z), s});
    }

    private int placed;

    /** Pone un bloque ya (la roca madre no se toca; barrotes y muros se unen con los de al lado). */
    private void apply(int x, int y, int z, BlockState s) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
        apply(new BlockPos(x, y, z), s);
    }

    private void apply(BlockPos p, BlockState s) {
        placed++;
        BlockState old = level.getBlockState(p);
        if (old.is(Blocks.BEDROCK) || old.equals(s)) return;
        if (s.getBlock() instanceof IronBarsBlock || s.getBlock() instanceof WallBlock || s.getBlock() instanceof FenceBlock) {
            level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), 3);
        } else {
            level.setBlock(p, s, 2);
        }
    }

    /** Pone otro pedazo (primero el terreno, columna por columna, y luego el búnker); true cuando terminó. */
    public boolean step(int perTick) {
        int budget = perTick;
        while (nextColumn < columns.size() && budget > 0) budget -= 1 + column(columns.get(nextColumn++));
        while (next < todo.size() && budget > 0) {
            Object[] op = todo.get(next++);
            apply((BlockPos) op[0], (BlockState) op[1]);
            budget--;
        }
        return done();
    }

    public boolean done() {
        return nextColumn >= columns.size() && next >= todo.size();
    }
}
