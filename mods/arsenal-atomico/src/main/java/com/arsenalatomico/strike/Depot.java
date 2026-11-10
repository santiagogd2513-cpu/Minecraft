package com.arsenalatomico.strike;

import com.arsenalatomico.NukePlan;
import com.arsenalatomico.registry.ModBlocks;
import com.arsenalatomico.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.DyeColor;

/**
 * El depósito de suministros de la Base Sol Negro: un hangar a 400–800 bloques de la base con 10
 * misiles chiquitos y 6 medianos (no hay más: cuando se acaban, se acabaron), y la carretera que lo
 * une con el portón de la base, con puentes sobre ríos y lagos, túneles por las montañas, faroles y
 * letreros con la distancia.
 * <pre>
 *  carretera: sale del portón hacia atrás (tramo 1), dobla 90° (tramo 2) y llega a la puerta del hangar
 *  hangar (a hacia adentro, b a la derecha, desde la entrada): plataforma a -2..32, b ±16
 *          paredes a 4..30, b ±12, 10 de alto; portón |b| ≤ 4
 *          chiquitos en fila en b = -10, medianos en b = 10; estantes (donde se sacan) en a = 6, b = ∓7
 * </pre>
 */
public final class Depot {
    public static final int STOCK_SMALL = 10, STOCK_MEDIUM = 6;
    /** Donde empieza la carretera, justo afuera del portón de la base. */
    private static final int ROAD_A = -57;
    private static final int TUNNEL = 0, BRIDGE = 1, ROAD = 2;
    private static final int PAD_A0 = -2, PAD_A1 = 32, PAD_B = 16, SLOPE = 5;

    private Depot() {}

    // ------------------------------------------------------------------ elegir el sitio

    /** Busca el mejor lugar para el depósito: poca agua por el camino, terreno parejo. */
    public static void choose(ServerLevel level, SolNegro.BaseData b) {
        int[] ax = NukePlan.axes(b.yaw);
        int cx = Mth.floor(b.site.x), cz = Mth.floor(b.site.z);
        int gx = cx + ROAD_A * ax[0], gz = cz + ROAD_A * ax[1];
        double best = Double.MAX_VALUE;
        int bx = gx - 500 * ax[0], bz = gz - 500 * ax[1];
        for (int d1 = 300; d1 <= 650; d1 += 70) {
            for (int d2 = -300; d2 <= 300; d2 += 100) {
                int total = d1 + Math.abs(d2);
                if (total < 400 || total > 800) continue;
                int ex = gx - d1 * ax[0] + d2 * ax[2], ez = gz - d1 * ax[1] + d2 * ax[3];
                double score = score(level, gx, gz, gx - d1 * ax[0], gz - d1 * ax[1], ex, ez, Mth.floor(b.site.y));
                if (score < best) {
                    best = score;
                    bx = ex;
                    bz = ez;
                }
            }
        }
        b.depot = new Vec3(bx + 0.5, 0, bz + 0.5);
        b.stockSmall = STOCK_SMALL;
        b.stockMedium = STOCK_MEDIUM;
        b.depotBuilt = false;
        b.roadBuilt = false;
        Route r = route(level, b);
        b.depot = new Vec3(bx + 0.5, r.y[r.n - 1], bz + 0.5);
        b.depotYaw = (float) Math.toDegrees(Math.atan2(-r.dx, r.dz));
        b.setDirty();
    }

    private static double score(ServerLevel level, int x0, int z0, int x1, int z1, int x2, int z2, int top) {
        double s = 0;
        int prev = Integer.MIN_VALUE;
        int[][] legs = {{x0, z0, x1, z1}, {x1, z1, x2, z2}};
        for (int[] l : legs) {
            int len = Math.max(Math.abs(l[2] - l[0]), Math.abs(l[3] - l[1]));
            for (int t = 0; t <= len; t += 8) {
                int x = l[0] + Integer.signum(l[2] - l[0]) * t, z = l[1] + Integer.signum(l[3] - l[1]) * t;
                int[] h = plan(level, x, z);
                if (h[1] == 1) s += 6;
                if (prev != Integer.MIN_VALUE) s += Math.abs(h[0] - prev) * 0.8;
                prev = h[0];
            }
        }
        // el terreno del hangar
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int u = -16; u <= 16; u += 8) {
            for (int v = -16; v <= 16; v += 8) {
                int[] h = plan(level, x2 + u, z2 + v);
                if (h[1] == 1) s += 40;
                lo = Math.min(lo, h[0]);
                hi = Math.max(hi, h[0]);
            }
        }
        s += (hi - lo) * 2 + Math.abs(prev - top) * 0.3;
        return s;
    }

    /** La altura del terreno según el generador (sin cargar nada): {altura del aire, 1 si hay agua}. */
    private static int[] plan(ServerLevel level, int x, int z) {
        var src = level.getChunkSource();
        int surf = src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState());
        int floor = src.getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, src.randomState());
        return new int[]{surf, surf > floor ? 1 : 0};
    }

    // ------------------------------------------------------------------ la ruta

    static final class Route {
        int n, corner = -1, dx, dz;
        int[] x, z, y, px, pz;
    }

    /** Siempre la misma ruta para la misma base y el mismo depósito. */
    static Route route(ServerLevel level, SolNegro.BaseData b) {
        int[] ax = NukePlan.axes(b.yaw);
        int cx = Mth.floor(b.site.x), cz = Mth.floor(b.site.z), top = Mth.floor(b.site.y);
        int gx = cx + ROAD_A * ax[0], gz = cz + ROAD_A * ax[1];
        int ex = Mth.floor(b.depot.x), ez = Mth.floor(b.depot.z);
        int d1 = -((ex - gx) * ax[0] + (ez - gz) * ax[1]);
        int d2 = (ex - gx) * ax[2] + (ez - gz) * ax[3];
        int s2 = Integer.signum(d2);
        Route r = new Route();
        r.n = d1 + 1 + Math.abs(d2);
        r.x = new int[r.n];
        r.z = new int[r.n];
        r.y = new int[r.n];
        r.px = new int[r.n];
        r.pz = new int[r.n];
        for (int i = 0; i <= d1; i++) {
            r.x[i] = gx - i * ax[0];
            r.z[i] = gz - i * ax[1];
            r.px[i] = -ax[2];
            r.pz[i] = -ax[3];
        }
        for (int j = 1; j <= Math.abs(d2); j++) {
            int i = d1 + j;
            r.x[i] = r.x[d1] + j * s2 * ax[2];
            r.z[i] = r.z[d1] + j * s2 * ax[3];
            r.px[i] = -s2 * ax[0];
            r.pz[i] = -s2 * ax[1];
        }
        if (d2 != 0) {
            r.corner = d1;
            r.dx = s2 * ax[2];
            r.dz = s2 * ax[3];
        } else {
            r.dx = -ax[0];
            r.dz = -ax[1];
        }
        // perfil: lo que pide el terreno (sobre el agua, bien alto para el puente), suavizado
        int sea = level.getSeaLevel();
        double[] want = new double[r.n];
        for (int i = 0; i < r.n; i++) {
            int[] h = plan(level, r.x[i], r.z[i]);
            want[i] = h[1] == 1 ? Math.max(h[0], sea + 4) : Math.max(h[0], sea + 1);
        }
        double[] smooth = new double[r.n];
        for (int i = 0; i < r.n; i++) {
            double sum = 0;
            int c = 0;
            for (int k = Math.max(0, i - 12); k <= Math.min(r.n - 1, i + 12); k++) {
                sum += want[k];
                c++;
            }
            smooth[i] = sum / c;
        }
        // pendiente máxima de medio bloque por bloque (el camión sube de a un bloque)
        double y = top;
        for (int i = 0; i < r.n; i++) {
            y = Mth.clamp(smooth[i], y - 0.5, y + 0.5);
            r.y[i] = (int) Math.round(y);
        }
        return r;
    }

    // ------------------------------------------------------------------ la obra

    private static Work work;

    private static final class Work {
        final ServerLevel level;
        final SolNegro.BaseData b;
        final Route route;
        final int ex, ez, top, dx, dz, rx, rz;
        int column, sign = -1;
        boolean pendingSign;
        final int cols;

        Work(ServerLevel level, SolNegro.BaseData b) {
            this.level = level;
            this.b = b;
            route = route(level, b);
            ex = Mth.floor(b.depot.x);
            ez = Mth.floor(b.depot.z);
            top = Mth.floor(b.depot.y);
            dx = route.dx;
            dz = route.dz;
            rx = -dz;
            rz = dx;
            cols = (PAD_A1 - PAD_A0 + 1 + SLOPE * 2) * (PAD_B * 2 + 1 + SLOPE * 2);
        }

        BlockPos at(int a, int bb, int y) {
            return new BlockPos(ex + a * dx + bb * rx, y, ez + a * dz + bb * rz);
        }
    }

    /** Arranca (o sigue) la obra del depósito y la carretera si falta algo. */
    public static void ensure(ServerLevel level) {
        SolNegro.BaseData b = SolNegro.base(level);
        if (b.site == null || !b.built) return;
        if (b.depot == null) choose(level, b);
        if (b.depotBuilt && b.roadBuilt) return;
        if (work == null || work.level != level) work = new Work(level, b);
    }

    /** Cada tick del servidor: un pedacito de la obra. */
    public static void tick() {
        Work w = work;
        if (w == null) return;
        if (w.level.getServer() == null || w.b.depot == null) {
            work = null;
            return;
        }
        if (!w.b.depotBuilt) {
            int end = Math.min(w.cols, w.column + 36);
            while (w.column < end) hangarColumn(w, w.column++);
            if (w.column >= w.cols) {
                hangarSigns(w);
                w.b.depotBuilt = true;
                w.b.setDirty();
            }
        } else if (!w.b.roadBuilt) {
            if (w.sign < 0) w.sign = 0;
            int end = Math.min(w.route.n, w.sign + 4);
            while (w.sign < end) roadPoint(w, w.sign++);
            if (w.sign >= w.route.n) {
                w.b.roadBuilt = true;
                w.b.setDirty();
                work = null;
                return;
            }
        }
        if (w.level.getGameTime() % 40 == 0) {
            int pct = w.b.depotBuilt ? 30 + w.sign * 70 / Math.max(1, w.route.n) : w.column * 30 / w.cols;
            for (ServerPlayer p : w.level.players()) {
                p.displayClientMessage(Component.translatable("message.arsenalatomico.depot.building", pct).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    // ------------------------------------------------------------------ el hangar

    private static void hangarColumn(Work w, int idx) {
        int width = PAD_B * 2 + 1 + SLOPE * 2;
        int a = PAD_A0 - SLOPE + idx / width, bb = -PAD_B - SLOPE + idx % width;
        BlockPos base = w.at(a, bb, 0);
        int x = base.getX(), z = base.getZ(), Y = w.top;
        ServerLevel level = w.level;
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int out = Math.max(Math.max(PAD_A0 - a, a - PAD_A1), Math.abs(bb) - PAD_B);
        if (out > 0) {
            // loma alrededor
            if (g < Y) {
                int h = Y - 1 - out;
                for (int y = g; y < h; y++) set(level, x, y, z, Blocks.DIRT.defaultBlockState());
                if (h >= g) set(level, x, h, z, Blocks.GRASS_BLOCK.defaultBlockState());
            } else {
                int h = Y - 1 + out;
                if (g - 1 > h) {
                    set(level, x, h, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = h + 1; y <= Math.min(g + 1, h + 30); y++) set(level, x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
            return;
        }
        for (int y = Math.min(g, Y - 1) - 1; y < Y - 1; y++) set(level, x, y, z, Blocks.STONE.defaultBlockState());
        for (int y = Y; y <= Math.max(Y + 12, Math.min(g + 1, Y + 40)); y++) set(level, x, y, z, Blocks.AIR.defaultBlockState());
        boolean in = a >= 4 && a <= 30 && Math.abs(bb) <= 12;
        BlockState floor;
        if (a == PAD_A0 || a == PAD_A1 || Math.abs(bb) == PAD_B) floor = Blocks.YELLOW_CONCRETE.defaultBlockState();
        else if (!in && Math.abs(bb) <= 4) floor = bb == 0 && Math.floorMod(a, 4) < 2 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
        else if (in && a > 4 && a < 30 && Math.abs(bb) < 12) {
            floor = bb == -8 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : bb == 8 ? Blocks.ORANGE_CONCRETE.defaultBlockState()
                    : Blocks.SMOOTH_STONE.defaultBlockState();
        } else floor = Blocks.GRAY_CONCRETE.defaultBlockState();
        set(level, x, Y - 1, z, floor);
        // faroles en la plataforma
        if (a == 0 && Math.abs(bb) == 8) {
            for (int y = Y; y <= Y + 2; y++) set(level, x, y, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(level, x, Y + 3, z, Blocks.LANTERN.defaultBlockState());
        }
        if (!in) {
            // techo que sobresale un bloque
            if (a >= 3 && a <= 31 && Math.abs(bb) <= 13) set(level, x, Y + 10, z, Blocks.GRAY_CONCRETE.defaultBlockState());
            return;
        }
        boolean wall = a == 4 || a == 30 || Math.abs(bb) == 12;
        for (int y = Y; y <= Y + 9; y++) {
            BlockState s = null;
            if (wall) {
                boolean pillar = (Math.floorMod(a - 4, 6) == 0 && Math.abs(bb) == 12) || (Math.floorMod(bb, 6) == 0 && (a == 4 || a == 30));
                if (a == 4 && Math.abs(bb) <= 4 && y <= Y + 5) s = Blocks.AIR.defaultBlockState();
                else if (a == 4 && (Math.abs(bb) == 5 && y <= Y + 6 || Math.abs(bb) <= 5 && y == Y + 6)) s = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
                else if (pillar) s = Blocks.GRAY_CONCRETE.defaultBlockState();
                else if (y == Y) s = Math.floorMod(a + bb, 2) == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
                else if (Math.abs(bb) == 12 && (y == Y + 6 || y == Y + 7) && Math.floorMod(a - 4, 6) >= 2 && Math.floorMod(a - 4, 6) <= 4) s = Blocks.GLASS.defaultBlockState();
                else s = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            }
            if (s != null) set(level, x, y, z, s);
        }
        boolean light = !wall && Math.floorMod(a - 7, 6) == 0 && Math.floorMod(bb, 6) == 0;
        set(level, x, Y + 10, z, light ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
        if (wall) return;
        // los estantes, los misiles y unas cajas
        if (a == 6 && Math.abs(bb) == 7) set(level, x, Y, z, (bb < 0 ? ModBlocks.RACK_SMALL : ModBlocks.RACK_MEDIUM).get().defaultBlockState());
        for (int k = 0; k < STOCK_SMALL; k++) {
            if (a == 7 + 2 * k && bb == -10) missile(level, x, Y, z, false, k < w.b.stockSmall);
        }
        for (int k = 0; k < STOCK_MEDIUM; k++) {
            if (a == 7 + 3 * k && bb == 10) missile(level, x, Y, z, true, k < w.b.stockMedium);
        }
        if (a >= 26 && a <= 28 && Math.abs(bb) <= 4 && Math.floorMod(bb, 2) == 0) {
            set(level, x, Y, z, Blocks.BARREL.defaultBlockState());
            if (a == 27) set(level, x, Y + 1, z, Blocks.BARREL.defaultBlockState());
        }
    }

    /** Un misil de exhibición parado en su base (o solo la base, si ya se lo llevaron). */
    private static void missile(ServerLevel level, int x, int Y, int z, boolean medium, boolean there) {
        set(level, x, Y, z, Blocks.IRON_BLOCK.defaultBlockState());
        int body = medium ? 4 : 2;
        for (int i = 1; i <= body + 2; i++) {
            BlockState s = Blocks.AIR.defaultBlockState();
            if (there) {
                if (i <= body) s = (medium ? Blocks.GREEN_TERRACOTTA : Blocks.WHITE_CONCRETE).defaultBlockState();
                else if (i == body + 1) s = Blocks.RED_CONCRETE.defaultBlockState();
                else s = Blocks.LIGHTNING_ROD.defaultBlockState();
            }
            set(level, x, Y + i, z, s);
        }
    }

    private static void hangarSigns(Work w) {
        Direction out = dir(-w.dx, -w.dz), inward = dir(w.dx, w.dz);
        BlockPos front = w.at(3, 0, w.top + 7);
        set(w.level, front, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, out));
        write(w.level, front, true, "DEPÓSITO DE", "SUMINISTROS", "SOL NEGRO", "☢");
        for (int s = -1; s <= 1; s += 2) {
            BlockPos p = w.at(5, s * 7, w.top + 2);
            set(w.level, p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, inward));
        }
        updateSigns(w.level, w.b);
    }

    /** Los letreros de los estantes con cuántos misiles quedan. */
    static void updateSigns(ServerLevel level, SolNegro.BaseData b) {
        if (b.depot == null) return;
        int ex = Mth.floor(b.depot.x), ez = Mth.floor(b.depot.z), y = Mth.floor(b.depot.y) + 2;
        float r = (float) Math.toRadians(b.depotYaw);
        int dx = (int) Math.round(-Math.sin(r)), dz = (int) Math.round(Math.cos(r));
        int rx = -dz, rz = dx;
        for (int s = -1; s <= 1; s += 2) {
            BlockPos p = new BlockPos(ex + 5 * dx + s * 7 * rx, y, ez + 5 * dz + s * 7 * rz);
            int left = s < 0 ? b.stockSmall : b.stockMedium, max = s < 0 ? STOCK_SMALL : STOCK_MEDIUM;
            write(level, p, true, "MISILES", s < 0 ? "CHIQUITOS" : "MEDIANOS", left + " / " + max, left > 0 ? "clic al estante" : "AGOTADOS");
        }
    }

    // ------------------------------------------------------------------ sacar un misil

    public static void take(ServerPlayer player, boolean medium) {
        ServerLevel level = player.serverLevel();
        SolNegro.BaseData b = SolNegro.base(level);
        int left = medium ? b.stockMedium : b.stockSmall;
        String kind = medium ? "medium" : "small";
        if (left <= 0) {
            player.displayClientMessage(Component.translatable("message.arsenalatomico.depot.empty_" + kind).withStyle(ChatFormatting.RED), true);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1f, 0.8f);
            return;
        }
        left--;
        if (medium) b.stockMedium = left;
        else b.stockSmall = left;
        b.setDirty();
        ItemStack stack = new ItemStack((medium ? ModItems.MISSILE_MEDIUM : ModItems.MISSILE_SMALL).get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        // se quita el misil de exhibición
        if (b.depot != null) {
            int ex = Mth.floor(b.depot.x), ez = Mth.floor(b.depot.z), y = Mth.floor(b.depot.y);
            float r = (float) Math.toRadians(b.depotYaw);
            int dx = (int) Math.round(-Math.sin(r)), dz = (int) Math.round(Math.cos(r));
            int rx = -dz, rz = dx;
            int a = medium ? 7 + 3 * left : 7 + 2 * left, bb = medium ? 10 : -10;
            missile(level, ex + a * dx + bb * rx, y, ez + a * dz + bb * rz, medium, false);
        }
        updateSigns(level, b);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.7f);
        player.displayClientMessage(Component.translatable("message.arsenalatomico.depot.took_" + kind, left).withStyle(ChatFormatting.GREEN), false);
        if (left == 1) {
            player.displayClientMessage(Component.translatable("message.arsenalatomico.depot.orphan_" + kind).withStyle(ChatFormatting.GOLD), false);
        }
    }

    // ------------------------------------------------------------------ la carretera

    private static void roadPoint(Work w, int i) {
        Route r = w.route;
        ServerLevel level = w.level;
        int x = r.x[i], z = r.z[i], y = r.y[i];
        int gc = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean water = level.getFluidState(new BlockPos(x, gc - 1, z)).is(FluidTags.WATER);
        int mode = gc > y + 3 ? TUNNEL : (water || gc < y - 2) ? BRIDGE : ROAD;
        if (i == r.corner) {
            int qx = r.px[i - 1], qz = r.pz[i - 1], sx = r.px[i + 1 < r.n ? i + 1 : i], sz = r.pz[i + 1 < r.n ? i + 1 : i];
            for (int u = -4; u <= 4; u++) {
                for (int v = -4; v <= 4; v++) {
                    int wd = Math.max(Math.abs(u), Math.abs(v));
                    column(level, x + u * qx + v * sx, z + u * qz + v * sz, y, mode, wd, wd, i);
                }
            }
            return;
        }
        for (int s = -4; s <= 4; s++) column(level, x + s * r.px[i], z + s * r.pz[i], y, mode, Math.abs(s), s, i);
        // faroles
        if (i % 20 == 10 && mode != TUNNEL) {
            int k = mode == BRIDGE ? 3 : 4;
            int lx = x + k * r.px[i], lz = z + k * r.pz[i];
            if (mode == ROAD) set(level, lx, y - 1, lz, Blocks.STONE_BRICKS.defaultBlockState());
            for (int yy = y; yy <= y + 2; yy++) set(level, lx, yy, lz, Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(level, lx, y + 3, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        // letreros cada 100 bloques (en la parte normal de la carretera)
        if (i % 100 == 50) w.pendingSign = true;
        if (w.pendingSign && mode == ROAD && i > 0 && i < r.n - 20) {
            w.pendingSign = false;
            int sx = x - 4 * r.px[i], sz = z - 4 * r.pz[i];
            int fdx = r.x[i] - r.x[i - 1], fdz = r.z[i] - r.z[i - 1];
            set(level, sx, y - 1, sz, Blocks.STONE_BRICKS.defaultBlockState());
            // el frente mira hacia la base (lo lee quien va al depósito)
            int rot = Mth.floor(Math.toDegrees(Math.atan2(fdx, -fdz)) / 22.5 + 0.5) & 15;
            BlockPos sp = new BlockPos(sx, y, sz);
            set(level, sp, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot));
            write(level, sp, true, "▲ DEPÓSITO", (r.n - 1 - i + 20) + " m", "", "☢ SOL NEGRO");
            write(level, sp, false, "▲ BASE", (i - ROAD_A) + " m", "", "☢ SOL NEGRO");
        }
    }

    private static void column(ServerLevel level, int x, int z, int y, int mode, int wd, int signed, int i) {
        BlockState deck = wd == 0 && i % 8 < 4 ? Blocks.YELLOW_CONCRETE.defaultBlockState()
                : wd <= 2 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        switch (mode) {
            case TUNNEL -> {
                if (wd == 4) {
                    for (int yy = y - 1; yy <= y + 5; yy++) set(level, x, yy, z, Blocks.STONE_BRICKS.defaultBlockState());
                    return;
                }
                set(level, x, y - 2, z, Blocks.STONE.defaultBlockState());
                set(level, x, y - 1, z, deck);
                for (int yy = y; yy <= y + 4; yy++) set(level, x, yy, z, air);
                set(level, x, y + 5, z, wd == 0 && i % 8 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
            case BRIDGE -> {
                if (wd == 4) return;
                set(level, x, y - 2, z, Blocks.SMOOTH_STONE.defaultBlockState());
                set(level, x, y - 1, z, deck);
                for (int yy = y; yy <= y + 4; yy++) set(level, x, yy, z, wd == 3 && yy == y ? Blocks.STONE_BRICK_WALL.defaultBlockState() : air);
                // pilares cada 10 bloques
                if (wd == 2 && i % 10 == 0) {
                    for (int yy = y - 3; yy > y - 100 && yy > level.getMinBuildHeight(); yy--) {
                        BlockPos p = new BlockPos(x, yy, z);
                        BlockState s = level.getBlockState(p);
                        if (!s.isAir() && !s.canBeReplaced() && !s.is(net.minecraft.tags.BlockTags.LEAVES)) break;
                        set(level, p, Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            }
            default -> {
                int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (wd == 4) {
                    for (int yy = Math.max(g, y - 14); yy < y - 1; yy++) set(level, x, yy, z, Blocks.DIRT.defaultBlockState());
                    if (g <= y) set(level, x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int yy = y; yy <= Math.min(Math.max(g, y + 4), y + 12); yy++) set(level, x, yy, z, air);
                    return;
                }
                for (int yy = Math.max(g, y - 14); yy < y - 1; yy++) set(level, x, yy, z, Blocks.STONE.defaultBlockState());
                set(level, x, y - 1, z, deck);
                for (int yy = y; yy <= Math.min(Math.max(g, y + 5), y + 12); yy++) set(level, x, yy, z, air);
            }
        }
    }

    // ------------------------------------------------------------------ ayudas

    private static Direction dir(int dx, int dz) {
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        set(level, new BlockPos(x, y, z), s);
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        if (p.getY() <= level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()) return;
        BlockState old = level.getBlockState(p);
        if (old.is(Blocks.BEDROCK) || old.equals(s)) return;
        level.setBlock(p, s, 2 | 16);
    }

    private static void write(ServerLevel level, BlockPos p, boolean front, String... lines) {
        BlockEntity be = level.getBlockEntity(p);
        if (!(be instanceof SignBlockEntity sign)) return;
        SignText t = new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true);
        for (int i = 0; i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
        sign.setText(t, front);
        sign.setWaxed(true);
        sign.setChanged();
        BlockState s = level.getBlockState(p);
        level.sendBlockUpdated(p, s, s, 3);
    }
}
