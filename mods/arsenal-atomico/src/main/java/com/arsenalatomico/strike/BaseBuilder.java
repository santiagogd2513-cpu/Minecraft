package com.arsenalatomico.strike;

import com.arsenalatomico.BaseLayout;
import com.arsenalatomico.NukePlan;
import com.arsenalatomico.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye la Base Sol Negro (ver {@link BaseLayout}) de a poco, para que el servidor no se congele.
 * Va en este orden: el terreno de la plataforma (columna por columna), las cáscaras de todas las salas,
 * los huecos de adentro, los muebles y luces, y por último los letreros.
 */
public final class BaseBuilder {
    private static final int F = BaseLayout.FLOOR;
    /** La plataforma: de a = -56 a 42 y de b = -44 a 44, y unos bloques de loma alrededor. */
    private static final int A0 = -56, A1 = 42, B0 = -44, B1 = 44, SLOPE = 8;

    private final ServerLevel level;
    private final int cx, cz, fx, fz, rx, rz, top;
    private final List<int[]> columns = new ArrayList<>();
    private final List<Object[]> shell = new ArrayList<>(), inside = new ArrayList<>(), deco = new ArrayList<>();
    private final List<Object[]> signs = new ArrayList<>();
    private List<Object[]> ops;
    private int nextColumn, next, nextSign;

    private BaseBuilder(ServerLevel level, Vec3 site, float yaw) {
        this.level = level;
        int[] ax = NukePlan.axes(yaw);
        fx = ax[0];
        fz = ax[1];
        rx = ax[2];
        rz = ax[3];
        cx = Mth.floor(site.x);
        cz = Mth.floor(site.z);
        top = Mth.floor(site.y);
    }

    public static BaseBuilder build(ServerLevel level, Vec3 site, float yaw) {
        BaseBuilder b = new BaseBuilder(level, site, yaw);
        for (int a = A0 - SLOPE; a <= A1 + SLOPE; a++) {
            for (int bb = B0 - SLOPE; bb <= B1 + SLOPE; bb++) b.columns.add(new int[]{a, bb});
        }
        b.underground();
        b.silos();
        b.surface();
        b.ops = new ArrayList<>(b.shell.size() + b.inside.size() + b.deco.size());
        b.ops.addAll(b.shell);
        b.ops.addAll(b.inside);
        b.ops.addAll(b.deco);
        return b;
    }

    /** Pone hasta «budget» bloques; true cuando terminó todo. */
    public boolean step(int budget) {
        int placed = 0;
        while (nextColumn < columns.size() && placed < budget) placed += column(columns.get(nextColumn++));
        while (nextColumn >= columns.size() && next < ops.size() && placed < budget) {
            Object[] o = ops.get(next++);
            set((BlockPos) o[0], (BlockState) o[1]);
            placed++;
        }
        if (nextColumn >= columns.size() && next >= ops.size()) {
            while (nextSign < signs.size()) writeSign(signs.get(nextSign++));
        }
        return done();
    }

    public boolean done() {
        return nextColumn >= columns.size() && next >= ops.size() && nextSign >= signs.size();
    }

    // ------------------------------------------------------------------ terreno y plataforma

    private int column(int[] c) {
        int a = c[0], b = c[1];
        int x = cx + a * fx + b * rx, z = cz + a * fz + b * rz;
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int n = 0;
        int out = Math.max(Math.max(A0 - a, a - A1), Math.max(B0 - b, b - B1));
        if (out <= 0) {
            int clear = Math.min(top + 40, Math.max(top + 14, g + 2));
            for (int y = Math.min(g, top - 2) - 1; y < top - 1; y++) n += set(x, y, z, Blocks.STONE.defaultBlockState());
            n += set(x, top - 1, z, pad(a, b));
            for (int y = top; y <= clear; y++) n += set(x, y, z, Blocks.AIR.defaultBlockState());
            return Math.max(1, n);
        }
        // loma alrededor: rellena hacia abajo o corta hacia arriba, un bloque por cada bloque de distancia
        if (g < top - 1) {
            int h = top - 1 - out;
            if (h <= g) return 1;
            for (int y = g; y < h; y++) n += set(x, y, z, Blocks.DIRT.defaultBlockState());
            n += set(x, h, z, Blocks.GRASS_BLOCK.defaultBlockState());
        } else {
            int h = top - 1 + out;
            if (g <= h) return 1;
            n += set(x, h, z, Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = h + 1; y <= Math.min(g + 1, h + 30); y++) n += set(x, y, z, Blocks.AIR.defaultBlockState());
        }
        return Math.max(1, n);
    }

    /** El concreto de la plataforma con sus líneas pintadas. */
    private BlockState pad(int a, int b) {
        // borde amarillo
        if (a == A0 + 2 || a == A1 - 2 || b == B0 + 2 || b == B1 - 2) return Blocks.YELLOW_CONCRETE.defaultBlockState();
        // la calle del portón al edificio, con la línea del medio
        if (a < -42 && Math.abs(b) <= 5) {
            if (b == 0 && Math.floorMod(a, 4) < 2) return Blocks.YELLOW_CONCRETE.defaultBlockState();
            return Blocks.BLACK_CONCRETE.defaultBlockState();
        }
        // el parqueadero del camión
        if (a >= BaseLayout.PARK_A - 5 && a <= BaseLayout.PARK_A + 5 && (b == BaseLayout.PARK_B - 3 || b == BaseLayout.PARK_B + 3)) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        // anillos de peligro alrededor de cada silo
        for (double[] s : BaseLayout.SILOS) {
            double d = Math.hypot(a - s[0], b - s[1]);
            if (d >= s[2] && d < s[2] + 2.2) {
                int stripe = (int) Math.floor((Math.atan2(b - s[1], a - s[0]) + Math.PI) / (Math.PI / 8));
                return stripe % 2 == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
            }
        }
        // el helipuerto
        double hd = Math.hypot(a - 30, b + 26);
        if (hd >= 6 && hd < 7) return Blocks.WHITE_CONCRETE.defaultBlockState();
        if (hd < 6 && ((Math.abs(b + 26) == 2 && Math.abs(a - 30) <= 3) || (a == 30 && Math.abs(b + 26) <= 2))) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
        }
        return (Math.floorMod(a, 8) == 0 || Math.floorMod(b, 8) == 0) ? Blocks.GRAY_CONCRETE.defaultBlockState()
                : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    }

    // ------------------------------------------------------------------ el nivel de abajo

    private static final BlockState WALL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
            FLOOR_STONE = Blocks.POLISHED_ANDESITE.defaultBlockState(),
            CEIL = Blocks.WHITE_CONCRETE.defaultBlockState(),
            AIR = Blocks.AIR.defaultBlockState();

    private void underground() {
        // escalera desde el edificio de acceso: 22 escalones bajando hacia adelante
        for (int i = 1; i <= 22; i++) {
            int a = -36 + i, y = -i;
            for (int b = -2; b <= 2; b++) {
                for (int yy = y - 1; yy <= y + 5; yy++) add(shell, a, b, yy, WALL);
            }
            for (int b = -1; b <= 1; b++) {
                add(inside, a, b, y, stair(Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState(), -1, 0));
                for (int yy = y + 1; yy <= y + 4; yy++) add(inside, a, b, yy, AIR);
            }
            if (i % 4 == 0) add(deco, a, 0, y + 5, Blocks.SEA_LANTERN.defaultBlockState());
            // franja verde de salida en las paredes
            add(deco, a, -2, y + 1, Blocks.LIME_CONCRETE.defaultBlockState());
            add(deco, a, 2, y + 1, Blocks.LIME_CONCRETE.defaultBlockState());
        }
        space(-13, -1, -12, 1, 4, WALL, FLOOR_STONE);
        // galería (cruza de un ala a la otra)
        space(-11, -22, -7, 22, 4, WALL, FLOOR_STONE);
        // espina hacia el centro de mando
        space(-6, -2, -5, 2, 4, WALL, FLOOR_STONE);
        // alas de los silos
        space(-11, -22, 20, -18, 4, WALL, FLOOR_STONE);
        space(-11, 18, 20, 22, 4, WALL, FLOOR_STONE);
        // enlaces del centro de mando con las alas
        space(-1, -17, 1, -13, 4, WALL, FLOOR_STONE);
        space(-1, 13, 1, 17, 4, WALL, FLOOR_STONE);
        // centro de mando: más alto y más oscuro
        space(-4, -12, 6, 12, 7, Blocks.GRAY_CONCRETE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        // la ventana al silo del Sol Negro (túnel de vidrio)
        for (int a = 7; a <= 10; a++) {
            for (int b = -5; b <= 5; b++) for (int y = F - 1; y <= F + 5; y++) add(shell, a, b, y, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        // cabinas de los silos
        for (int s = 1; s < BaseLayout.COUNT; s++) {
            int a = (int) BaseLayout.SILOS[s][0];
            boolean left = BaseLayout.SILOS[s][1] < 0;
            int b0 = left ? -26 : 24, b1 = left ? -24 : 26, door = left ? -23 : 23;
            space(a - 2, b0, a + 2, b1, 4, WALL, FLOOR_STONE);
            for (int b = a - 1; b <= a + 1; b++) for (int y = F; y <= F + 2; y++) add(inside, b, door, y, AIR);
        }
        // dormitorio y cuarto de generadores, detrás de la galería
        space(-19, -18, -13, -6, 4, WALL, Blocks.SPRUCE_PLANKS.defaultBlockState());
        space(-19, 6, -13, 18, 4, WALL, Blocks.SMOOTH_STONE.defaultBlockState());
        for (int b = -13; b <= -11; b++) for (int y = F; y <= F + 2; y++) add(inside, -12, b, y, AIR);
        for (int b = 11; b <= 13; b++) for (int y = F; y <= F + 2; y++) add(inside, -12, b, y, AIR);

        lights();
        stripes();
        frames();
        command();
        booths();
        rooms();
        undergroundSigns();
    }

    /** Una sala: cáscara (paredes, piso y techo) y el hueco de adentro de {@code tall} de alto. */
    private void space(int a0, int b0, int a1, int b1, int tall, BlockState wall, BlockState floor) {
        for (int a = a0 - 1; a <= a1 + 1; a++) {
            for (int b = b0 - 1; b <= b1 + 1; b++) {
                for (int y = F - 1; y <= F + tall; y++) add(shell, a, b, y, y == F - 1 ? floor : y == F + tall ? CEIL : wall);
            }
        }
        for (int a = a0; a <= a1; a++) {
            for (int b = b0; b <= b1; b++) {
                add(inside, a, b, F - 1, floor);
                for (int y = F; y < F + tall; y++) add(inside, a, b, y, AIR);
            }
        }
    }

    private void lights() {
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        for (int b = -20; b <= 20; b += 4) add(deco, -9, b, F + 4, lamp);
        for (int a = -7; a <= 20; a += 4) {
            add(deco, a, -20, F + 4, lamp);
            add(deco, a, 20, F + 4, lamp);
        }
        for (int a = -3; a <= 5; a += 3) for (int b = -11; b <= 11; b += 4) add(deco, a, b, F + 7, lamp);
        for (int s = 1; s < BaseLayout.COUNT; s++) {
            int a = (int) BaseLayout.SILOS[s][0];
            add(deco, a, BaseLayout.SILOS[s][1] < 0 ? -25 : 25, F + 4, lamp);
        }
        add(deco, -16, -12, F + 4, lamp);
        add(deco, -16, 12, F + 4, lamp);
        add(deco, -13, 0, F + 4, lamp);
        add(deco, -6, 0, F + 4, lamp);
    }

    /** Franjas de color en el piso: rojo al centro de mando, amarillo a los silos chicos, naranja a los medianos. */
    private void stripes() {
        BlockState red = Blocks.RED_CONCRETE.defaultBlockState(), yellow = Blocks.YELLOW_CONCRETE.defaultBlockState(),
                orange = Blocks.ORANGE_CONCRETE.defaultBlockState(), white = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int a = -13; a <= 1; a++) add(deco, a, 0, F - 1, red);
        for (int b = -21; b <= -1; b++) add(deco, -9, b, F - 1, b > -3 ? red : yellow);
        for (int b = 1; b <= 21; b++) add(deco, -9, b, F - 1, b < 3 ? red : orange);
        for (int a = -8; a <= 20; a++) {
            add(deco, a, -20, F - 1, yellow);
            add(deco, a, 20, F - 1, orange);
        }
        for (int b = -19; b <= -13; b++) add(deco, 0, b, F - 1, yellow);
        for (int b = 13; b <= 19; b++) add(deco, 0, b, F - 1, orange);
        // el círculo de seguridad alrededor de la consola
        for (int a = -2; a <= 4; a++) {
            for (int b = -3; b <= 3; b++) {
                double d = Math.hypot(a - BaseLayout.CONSOLE_A, b);
                if (d >= 2 && d < 3) add(deco, a, b, F - 1, ((a + b) & 1) == 0 ? yellow : Blocks.BLACK_CONCRETE.defaultBlockState());
            }
        }
    }

    /** Marcos iluminados en cada puerta, del color de su zona (así nunca se pierden los pasillos). */
    private void frames() {
        BlockState cmd = Blocks.SHROOMLIGHT.defaultBlockState(), small = Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
                med = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), exit = Blocks.VERDANT_FROGLIGHT.defaultBlockState();
        // galería → espina → centro de mando
        for (int a : new int[]{-6, -5}) {
            for (int y = F; y <= F + 3; y++) {
                add(deco, a, -3, y, cmd);
                add(deco, a, 3, y, cmd);
            }
        }
        // salida (la escalera)
        for (int y = F; y <= F + 3; y++) {
            add(deco, -12, -2, y, exit);
            add(deco, -12, 2, y, exit);
        }
        // enlaces del centro de mando
        for (int side = -1; side <= 1; side += 2) {
            for (int bb : new int[]{13, 17}) {
                for (int y = F; y <= F + 3; y++) {
                    add(deco, -2, side * bb, y, side < 0 ? small : med);
                    add(deco, 2, side * bb, y, side < 0 ? small : med);
                }
            }
        }
        // cabinas
        for (int s = 1; s < BaseLayout.COUNT; s++) {
            int a = (int) BaseLayout.SILOS[s][0];
            int door = BaseLayout.SILOS[s][1] < 0 ? -23 : 23;
            BlockState l = BaseLayout.small(s) ? small : med;
            for (int y = F; y <= F + 3; y++) {
                add(deco, a - 2, door, y, l);
                add(deco, a + 2, door, y, l);
            }
            for (int aa = a - 1; aa <= a + 1; aa++) add(deco, aa, door, F + 3, l);
        }
        // dormitorio y generadores
        for (int bb : new int[]{-14, -10, 10, 14}) for (int y = F; y <= F + 3; y++) add(deco, -12, bb, y, exit);
    }

    /** El centro de mando del Sol Negro. */
    private void command() {
        BlockState desk = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), screen = Blocks.BLACK_CONCRETE.defaultBlockState();
        // la consola, en medio, mirando a la ventana
        add(deco, BaseLayout.CONSOLE_A, BaseLayout.CONSOLE_B, F, ModBlocks.LAUNCH_CONSOLE.get().defaultBlockState());
        // dos estaciones de llaves a los lados
        for (int side = -1; side <= 1; side += 2) {
            add(deco, BaseLayout.CONSOLE_A, side * 3, F, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            add(deco, BaseLayout.CONSOLE_A, side * 3, F + 1, Blocks.LODESTONE.defaultBlockState());
        }
        // dos filas de puestos de operadores con sus sillas
        for (int a : new int[]{-2, 4}) {
            for (int side = -1; side <= 1; side += 2) {
                for (int b = 5; b <= 10; b++) {
                    add(deco, a, side * b, F, desk);
                    add(deco, a, side * b, F + 1, screen);
                    if (b % 2 == 1) add(deco, a - 1, side * b, F, stair(Blocks.SPRUCE_STAIRS.defaultBlockState(), 1, 0));
                }
            }
        }
        // la mesa del mapa, atrás
        for (int b = -1; b <= 1; b++) add(deco, -3, b, F, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        // la pared de atrás: la pantalla grande (la dibuja el cliente) sobre un fondo negro
        for (int b = -8; b <= 8; b++) for (int y = F + 1; y <= F + 5; y++) add(deco, -5, b, y, screen);
        // la ventana al silo
        for (int a = 7; a <= 10; a++) {
            for (int b = -4; b <= 4; b++) {
                for (int y = F + 1; y <= F + 4; y++) {
                    if (Math.hypot(a - BaseLayout.SILOS[0][0], b) >= BaseLayout.SILOS[0][2]) add(deco, a, b, y, Blocks.GLASS.defaultBlockState());
                }
            }
        }
        // lámparas rojas de alarma (prendidas por un bloque de redstone en el techo)
        for (int a : new int[]{-3, 5}) {
            for (int side = -1; side <= 1; side += 2) {
                add(deco, a, side * 11, F + 7, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
                add(deco, a, side * 11, F + 8, Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }
        // racks de computadores en las esquinas
        for (int a = 0; a <= 4; a++) {
            for (int side = -1; side <= 1; side += 2) {
                if (a == 0 || a == 1) continue;
                Direction in = dir(0, -side);
                add(deco, a, side * 12, F, Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, in));
                add(deco, a, side * 12, F + 1, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, in));
            }
        }
    }

    /** Las cabinas de los silos: el panel frente a la ventana blindada. */
    private void booths() {
        for (int s = 1; s < BaseLayout.COUNT; s++) {
            double[] si = BaseLayout.SILOS[s];
            int a = (int) si[0];
            boolean left = si[1] < 0;
            int panelB = left ? -25 : 25, wall = left ? -27 : 27, ring = left ? -28 : 28;
            add(deco, a, panelB, F, ModBlocks.SILO_PANEL.get().defaultBlockState());
            for (int aa = a - 1; aa <= a + 1; aa++) {
                for (int y = F + 1; y <= F + 2; y++) {
                    add(deco, aa, wall, y, Blocks.GLASS.defaultBlockState());
                    if (Math.hypot(aa - si[0], ring - si[1]) >= si[2]) add(deco, aa, ring, y, Blocks.GLASS.defaultBlockState());
                }
            }
            add(deco, a - 2, panelB, F, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(0, left ? 1 : -1)));
        }
    }

    private void rooms() {
        // dormitorio: literas, casilleros y una mesa
        for (int i = 0; i < 4; i++) {
            int b = -17 + i * 3;
            bed(-18, b, dir(1, 0));
        }
        for (int b = -17; b <= -7; b += 2) add(deco, -14, b, F, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(-1, 0)));
        add(deco, -16, -8, F, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        add(deco, -16, -7, F, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        add(deco, -16, -9, F + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // generadores: turbinas, tubos y piso de peligro
        for (int b = 8; b <= 16; b += 4) {
            for (int y = F; y <= F + 2; y++) add(deco, -17, b, y, Blocks.IRON_BLOCK.defaultBlockState());
            add(deco, -17, b, F + 3, Blocks.LIGHTNING_ROD.defaultBlockState());
            add(deco, -16, b, F, Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, dir(1, 0)));
            for (int a = -19; a <= -13; a++) add(deco, a, b + 2, F - 1, (a & 1) == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
        }
        for (int b = 7; b <= 17; b++) add(deco, -19, b, F + 3, Blocks.CHAIN.defaultBlockState());
    }

    private void bed(int a, int b, Direction facing) {
        add(deco, a, b, F, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT));
        add(deco, a + 1, b, F, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void undergroundSigns() {
        // al bajar la escalera
        sign(-7, 3, F + 2, -1, 0, "CENTRO DE", "MANDO", "↑ de frente", "");
        sign(-7, -5, F + 2, -1, 0, "←", "SILOS", "TÁCTICOS", "(3)");
        sign(-7, 5, F + 2, -1, 0, "SILOS", "MEDIANOS", "→", "(2)");
        sign(-11, 3, F + 2, 1, 0, "↑ SALIDA", "(escalera)", "", "");
        // donde empiezan las alas
        sign(-11, -19, F + 2, 1, 0, "SILOS", "TÁCTICOS", "1 · 2 · 3", "→");
        sign(-11, 19, F + 2, 1, 0, "SILOS", "MEDIANOS", "4 · 5", "→");
        // las cabinas
        for (int s = 1; s < BaseLayout.COUNT; s++) {
            int a = (int) BaseLayout.SILOS[s][0];
            boolean left = BaseLayout.SILOS[s][1] < 0;
            int b = left ? -22 : 22;
            sign(a + 3, b, F + 2, 0, left ? 1 : -1, "SILO " + s, BaseLayout.small(s) ? "TÁCTICO" : "MEDIANO", "nuclear", "");
        }
        // los enlaces y el centro de mando
        sign(3, -18, F + 2, 0, -1, "CENTRO DE", "MANDO", "→", "");
        sign(3, 18, F + 2, 0, 1, "CENTRO DE", "MANDO", "←", "");
        sign(-4, -11, F + 3, 1, 0, "PROTOCOLO", "SOL NEGRO", "centro de", "mando");
        // dormitorio y generadores
        sign(-11, -15, F + 2, 1, 0, "DORMITORIO", "", "", "");
        sign(-11, 15, F + 2, 1, 0, "GENERADORES", "", "", "");
    }

    // ------------------------------------------------------------------ los silos

    private void silos() {
        for (int s = 0; s < BaseLayout.COUNT; s++) {
            double[] si = BaseLayout.SILOS[s];
            int ca = (int) si[0], cb = (int) si[1], depth = (int) si[3];
            double r = si[2], wall = r + (s == 0 ? 2 : 1);
            int R = Mth.ceil(wall);
            BlockState wallBlock = s == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
            for (int a = ca - R; a <= ca + R; a++) {
                for (int b = cb - R; b <= cb + R; b++) {
                    double d = Math.hypot(a - ca, b - cb);
                    if (d > wall) continue;
                    for (int y = -depth - 1; y <= -2; y++) add(shell, a, b, y, y == -depth - 1 ? Blocks.OBSIDIAN.defaultBlockState() : wallBlock);
                    if (d < r) {
                        for (int y = -depth; y <= -2; y++) add(inside, a, b, y, AIR);
                        // las compuertas: trampillas de hierro sobre la boca
                        add(deco, a, b, -1, hatch());
                    }
                }
            }
            // anillos de luz cada 6 bloques en la pared
            for (int y = -depth + 3; y <= -4; y += 6) {
                for (int k = 0; k < 16; k++) {
                    double ang = Math.PI * 2 * k / 16;
                    int a = ca + (int) Math.round(Math.cos(ang) * (r + 0.4)), b = cb + (int) Math.round(Math.sin(ang) * (r + 0.4));
                    if (Math.hypot(a - ca, b - cb) >= r) add(deco, a, b, y, Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
            // el foso de las llamas en el fondo
            add(deco, ca, cb, -depth - 1, Blocks.MAGMA_BLOCK.defaultBlockState());
        }
    }

    private static BlockState hatch() {
        return Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.FACING, Direction.NORTH);
    }

    // ------------------------------------------------------------------ la superficie

    private void surface() {
        building();
        fence();
        towers();
        flag();
    }

    /** El edificio de acceso: lobby, garita y la escalera que baja. */
    private void building() {
        int a0 = -42, a1 = -28, b0 = -9, b1 = 9;
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState(), trim = Blocks.GRAY_CONCRETE.defaultBlockState();
        for (int a = a0; a <= a1; a++) {
            for (int b = b0; b <= b1; b++) {
                boolean edge = a == a0 || a == a1 || b == b0 || b == b1;
                for (int y = 0; y <= 5; y++) {
                    if (edge) {
                        boolean corner = (a == a0 || a == a1) && (b == b0 || b == b1);
                        boolean window = !corner && (y == 2 || y == 3) && (b == b0 || b == b1) && Math.floorMod(a, 3) == 0;
                        add(deco, a, b, y, corner ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : y == 5 ? trim : window ? Blocks.GLASS.defaultBlockState() : wall);
                    } else {
                        add(deco, a, b, y, AIR);
                    }
                }
                add(deco, a, b, 6, edge ? trim : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
        }
        // la puerta (atrás, hacia el portón) con su marco iluminado
        for (int b = -1; b <= 1; b++) for (int y = 0; y <= 2; y++) add(deco, a0, b, y, AIR);
        for (int y = 0; y <= 3; y++) {
            add(deco, a0, -2, y, Blocks.VERDANT_FROGLIGHT.defaultBlockState());
            add(deco, a0, 2, y, Blocks.VERDANT_FROGLIGHT.defaultBlockState());
        }
        for (int b = -1; b <= 1; b++) add(deco, a0, b, 3, Blocks.VERDANT_FROGLIGHT.defaultBlockState());
        sign(a0 - 1, 0, 4, -1, 0, "BASE", "SOL NEGRO", "☢", "");
        // el hueco de la escalera con baranda
        for (int a = -35; a <= -29; a++) {
            if (a > -35) for (int b = -1; b <= 1; b++) add(deco, a, b, -1, AIR);
            add(deco, a, -2, 0, Blocks.IRON_BARS.defaultBlockState());
            add(deco, a, 2, 0, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int b = -2; b <= 2; b++) add(deco, -28 - 0, b, 0, wall);
        sign(-29, 0, 2, -1, 0, "↓ CENTRO", "DE MANDO", "Y SILOS", "");
        // garita, casilleros y luces
        for (int b = 4; b <= 7; b++) add(deco, -39, b, 0, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        add(deco, -38, 6, 0, stair(Blocks.SPRUCE_STAIRS.defaultBlockState(), -1, 0));
        for (int a = -40; a <= -36; a += 2) add(deco, a, -8, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, dir(0, 1)));
        for (int a = -40; a <= -30; a += 5) for (int b = -6; b <= 6; b += 6) add(deco, a, b, 5, Blocks.SEA_LANTERN.defaultBlockState());
        // antena en el techo (el radar lo dibuja el cliente)
        for (int y = 7; y <= 11; y++) add(deco, -31, 6, y, Blocks.IRON_BARS.defaultBlockState());
        add(deco, -31, 6, 12, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        add(deco, -31, 6, 13, Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private void fence() {
        for (int a = A0; a <= A1; a++) {
            for (int b = B0; b <= B1; b++) {
                if (a != A0 && a != A1 && b != B0 && b != B1) continue;
                if (a == A0 && Math.abs(b) <= 5) continue;   // el portón
                boolean post = Math.floorMod(a + b, 6) == 0;
                add(deco, a, b, 0, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
                add(deco, a, b, 1, post ? Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
                add(deco, a, b, 2, post ? Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
                if (post) add(deco, a, b, 3, Blocks.LANTERN.defaultBlockState());
            }
        }
        // pilares del portón
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 0; y <= 4; y++) add(deco, A0, side * 6, y, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            add(deco, A0, side * 6, 5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            add(deco, A0, side * 6, 6, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }
    }

    private void towers() {
        int[][] at = {{A0 + 4, B0 + 4}, {A0 + 4, B1 - 4}, {A1 - 4, B0 + 4}, {A1 - 4, B1 - 4}};
        for (int[] t : at) {
            for (int y = 0; y <= 8; y++) add(deco, t[0], t[1], y, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            add(deco, t[0], t[1], 9, Blocks.IRON_BLOCK.defaultBlockState());
            add(deco, t[0], t[1], 10, Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    /** La bandera de Rusia en su asta, junto a la calle. */
    private void flag() {
        int a = -48, b = -9;
        for (int y = 0; y <= 9; y++) add(deco, a, b, y, Blocks.IRON_BARS.defaultBlockState());
        BlockState[] rows = {Blocks.WHITE_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(), Blocks.RED_WOOL.defaultBlockState()};
        for (int i = 0; i < 3; i++) for (int k = 1; k <= 4; k++) add(deco, a, b - k, 9 - i, rows[i]);
    }

    // ------------------------------------------------------------------ ayudas

    private Direction dir(int da, int db) {
        int dx = da * fx + db * rx, dz = da * fz + db * rz;
        Direction d = Direction.fromDelta(Integer.signum(dx), 0, Integer.signum(dz));
        return d == null ? Direction.NORTH : d;
    }

    private BlockState stair(BlockState s, int da, int db) {
        return s.setValue(StairBlock.FACING, dir(da, db)).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private BlockPos pos(int a, int b, int y) {
        return new BlockPos(cx + a * fx + b * rx, top + y, cz + a * fz + b * rz);
    }

    private void add(List<Object[]> list, int a, int b, int y, BlockState s) {
        list.add(new Object[]{pos(a, b, y), s});
    }

    /** Letrero de pared (con letras que brillan), mirando hacia (da, db). */
    private void sign(int a, int b, int y, int da, int db, String l1, String l2, String l3, String l4) {
        add(deco, a, b, y, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, dir(da, db)));
        signs.add(new Object[]{pos(a, b, y), new String[]{l1, l2, l3, l4}});
    }

    private void writeSign(Object[] o) {
        BlockPos p = (BlockPos) o[0];
        String[] lines = (String[]) o[1];
        BlockEntity be = level.getBlockEntity(p);
        if (!(be instanceof SignBlockEntity sign)) return;
        SignText t = new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true);
        for (int i = 0; i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
        sign.setText(t, true);
        sign.setChanged();
        BlockState s = level.getBlockState(p);
        level.sendBlockUpdated(p, s, s, 3);
    }

    private int set(int x, int y, int z, BlockState s) {
        return set(new BlockPos(x, y, z), s);
    }

    private int set(BlockPos p, BlockState s) {
        if (p.getY() <= level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()) return 0;
        BlockState old = level.getBlockState(p);
        if (old.is(Blocks.BEDROCK) || old.equals(s)) return 0;
        level.setBlock(p, s, 2 | 16);
        return 1;
    }

    // ------------------------------------------------------------------ las compuertas

    /** Abre la compuerta de un silo (quita las trampillas) antes de lanzar. */
    public static void open(ServerLevel level, Vec3 site, float yaw, int silo) {
        hatches(level, site, yaw, silo, false);
    }

    /** Vuelve a cerrar la compuerta de un silo. */
    public static void close(ServerLevel level, Vec3 site, float yaw, int silo) {
        hatches(level, site, yaw, silo, true);
    }

    private static void hatches(ServerLevel level, Vec3 site, float yaw, int silo, boolean closed) {
        double[] si = BaseLayout.SILOS[silo];
        int r = Mth.ceil(si[2]);
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (Math.hypot(a, b) >= si[2]) continue;
                Vec3 w = BaseLayout.local(site, yaw, si[0] + a, si[1] + b, -1);
                BlockPos p = BlockPos.containing(w.x, w.y, w.z);
                BlockState now = level.getBlockState(p);
                if (closed && now.isAir()) level.setBlock(p, hatch(), 2);
                if (!closed && now.is(Blocks.IRON_TRAPDOOR)) level.setBlock(p, AIR, 2);
            }
        }
    }

    /** ¿Qué silo controla el panel que está en esa posición? (-1 si ninguno) */
    public static int siloAt(Vec3 site, float yaw, BlockPos panel) {
        for (int s = 1; s < BaseLayout.COUNT; s++) {
            double[] p = BaseLayout.panel(s);
            Vec3 w = BaseLayout.local(site, yaw, p[0], p[1], F);
            if (BlockPos.containing(w.x, w.y, w.z).equals(panel)) return s;
        }
        return -1;
    }

    /** Dónde queda la consola del Sol Negro. */
    public static BlockPos consolePos(Vec3 site, float yaw) {
        Vec3 w = BaseLayout.local(site, yaw, BaseLayout.CONSOLE_A, BaseLayout.CONSOLE_B, F);
        return BlockPos.containing(w.x, w.y, w.z);
    }
}
