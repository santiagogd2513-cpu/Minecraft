package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.FreightRailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * V7.5.0: carga de puentes y vías elevadas. V7.6.0: ACTIVO desde el inicio; se apaga con {@code /gamerule bdBridgeLoads false}.
 *
 * <p>Cada medio segundo se mira qué hay sobre puentes: los bogies de los trenes y los vehículos que van sobre un tablero sin apoyo debajo.
 * Un "tramo" es el conjunto de bloques de tablero conectados que no tienen suelo o pilar debajo (una columna de 4 bloques sólidos hacia
 * abajo cuenta como apoyo). Su capacidad sale del largo, el ancho, el grosor y el material:
 * {@code capacidad (t) = 3 · resistencia · grosor · ancho / largo}. Si el peso encima pasa de la capacidad, el puente cruje y, si sigue
 * así, cede por donde está la carga: el tablero cae como bloques y los trenes caen con la física de caídas de la V7.4.0.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BridgeLoads {
    public static final GameRules.Key<GameRules.BooleanValue> RULE =
            GameRules.register("bdBridgeLoads", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    private static final int INTERVAL = 10;               // ticks entre revisiones
    private static final int MAX_CELLS = 1500;             // un tablero más grande que esto se considera una plaza: no cede
    private static final int MAX_FALLING = 150;            // bloques que caen como entidad en un derrumbe (el resto se rompe)
    private static final int STRESS_LIMIT = 30;            // ticks pasados de la capacidad antes de ceder

    private static final Map<ServerLevel, Map<Long, Integer>> STRESS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<Long, Long>> WARNED = new WeakHashMap<>();

    private BridgeLoads() {
    }

    /** El bloque de ese sitio; si el chunk no está cargado cuenta como aire (no se cargan chunks por mirar un puente). */
    private static BlockState st(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    public static void init() {
        // solo fuerza la carga de la clase para que la gamerule se registre antes de abrir mundos
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % INTERVAL != 0 || !level.getGameRules().getBoolean(RULE)) {
            return;
        }
        scan(level);
    }

    // =====================================================================================================

    /** Un tramo de tablero. */
    private static final class Span {
        final List<BlockPos> cells = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        double strength;
        double thickness;
        long key = Long.MAX_VALUE;
        double load;                                  // toneladas encima
        double cx;
        double cz;
        double cy;
        boolean huge;

        double length() {
            return Math.max(this.maxX - this.minX + 1, this.maxZ - this.minZ + 1);
        }

        double width() {
            return Math.max(1.0, this.cells.size() / this.length());
        }

        double capacity() {
            double l = this.length();
            double c = 3.0 * (this.strength / this.cells.size()) * (this.thickness / this.cells.size()) * this.width() / l;
            return l <= 4.0 ? c * 4.0 : c;
        }
    }

    private static void scan(ServerLevel level) {
        Map<BlockPos, Span> owner = new HashMap<>();
        Set<BlockPos> notSpan = new HashSet<>();
        List<Span> spans = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof TrainCarEntity car) {
                if (!car.onRails()) {
                    continue;
                }
                RailPath.Lookup lk = car.railLookup();
                RailPath.Cursor f = car.frontCursor();
                RailPath.Cursor r = car.rearCursor(lk);
                double half = car.totalMassKg() / 2000.0;
                for (RailPath.Cursor c : new RailPath.Cursor[] {f, r}) {
                    if (c != null) {
                        put(level, new BlockPos(c.x, c.y, c.z), half, car.getX(), car.getZ(), owner, notSpan, spans);
                    }
                }
            } else if (e instanceof VehicleEntity v && v.onGround() && !v.isRemoved()) {
                put(level, BlockPos.containing(v.getX(), v.getY() - 0.2, v.getZ()), v.totalMassKg() / 1000.0, v.getX(), v.getZ(), owner, notSpan, spans);
            }
        }
        Map<Long, Integer> stress = STRESS.computeIfAbsent(level, k -> new HashMap<>());
        Set<Long> seen = new HashSet<>();
        for (Span span : spans) {
            if (span.huge || span.cells.isEmpty() || span.load <= 0.0) {
                continue;
            }
            double ratio = span.load / Math.max(1.0, span.capacity());
            seen.add(span.key);
            if (ratio >= 0.7) {
                warn(level, span, ratio);
            }
            int s = stress.getOrDefault(span.key, 0);
            if (ratio > 1.0) {
                s += INTERVAL;
            } else {
                s = Math.max(0, s - INTERVAL);
            }
            stress.put(span.key, s);
            if (ratio > 1.8 || s >= STRESS_LIMIT) {
                collapse(level, span);
                stress.remove(span.key);
            }
        }
        stress.keySet().removeIf(k -> !seen.contains(k));
    }

    /** Anota {@code tonnes} de peso sobre el tablero que hay bajo ese bloque (rail o piso). */
    private static void put(ServerLevel level, BlockPos at, double tonnes, double x, double z, Map<BlockPos, Span> owner, Set<BlockPos> notSpan, List<Span> spans) {
        BlockPos deck = deckOf(level, at);
        if (deck == null || notSpan.contains(deck)) {
            return;
        }
        Span span = owner.get(deck);
        if (span == null) {
            if (anchor(level, deck)) {
                notSpan.add(deck);
                return;
            }
            span = flood(level, deck, owner);
            spans.add(span);
        }
        span.load += tonnes;
        span.cx += x * tonnes;
        span.cz += z * tonnes;
        span.cy += deck.getY() * tonnes;
    }

    /** El bloque que hace de tablero bajo algo que está en {@code at}: el sólido de abajo o, si no hay, el propio riel. */
    private static BlockPos deckOf(ServerLevel level, BlockPos at) {
        BlockState here = st(level, at);
        if (here.getBlock() instanceof FreightRailBlock) {
            BlockPos below = at.below();
            if (solid(level, below, st(level, below))) {
                return below;
            }
            return at;
        }
        return solid(level, at, here) ? at : null;
    }

    private static boolean deckable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty()
                && (state.getBlock() instanceof FreightRailBlock || solid(level, pos, state));
    }

    private static boolean solid(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** Hay suelo o pilar debajo: una columna de 4 bloques sólidos hacia abajo. */
    private static boolean anchor(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 4; i++) {
            BlockState st = st(level, p);
            if (!(solid(level, p, st)) || st.getBlock() instanceof FreightRailBlock) {
                return false;
            }
            if (st.getDestroySpeed(level, p) < 0.0f) {
                return true;                                  // roca madre y similares
            }
            p.move(0, -1, 0);
        }
        return true;
    }

    private static Span flood(ServerLevel level, BlockPos seed, Map<BlockPos, Span> owner) {
        Span span = new Span();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        owner.put(seed, span);
        while (!queue.isEmpty()) {
            BlockPos c = queue.poll();
            span.cells.add(c);
            BlockState st = st(level, c);
            span.minX = Math.min(span.minX, c.getX());
            span.maxX = Math.max(span.maxX, c.getX());
            span.minZ = Math.min(span.minZ, c.getZ());
            span.maxZ = Math.max(span.maxZ, c.getZ());
            span.key = Math.min(span.key, c.asLong());
            span.strength += strength(level, c, st);
            span.thickness += thickness(level, c);
            if (span.cells.size() + queue.size() > MAX_CELLS) {
                span.huge = true;
                return span;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = c.relative(d);
                BlockPos use = null;
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos q = n.above(dy);
                    if (deckable(level, q, st(level, q))) {
                        use = q;
                        break;
                    }
                }
                if (use == null || owner.containsKey(use) || anchor(level, use)) {
                    continue;
                }
                owner.put(use, span);
                queue.add(use);
            }
        }
        return span;
    }

    private static double thickness(ServerLevel level, BlockPos pos) {
        int n = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 3; i++) {
            BlockState st = st(level, p);
            if (!solid(level, p, st) || st.getBlock() instanceof FreightRailBlock) {
                break;
            }
            n++;
            p.move(0, -1, 0);
        }
        return Math.max(1, n);
    }

    /** Resistencia de un bloque de tablero (toneladas·bloque): crece con la dureza; la madera vale menos; un riel solo es muy débil. */
    private static double strength(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.getBlock() instanceof FreightRailBlock) {
            return 400.0;
        }
        float hardness = st.getDestroySpeed(level, pos);
        if (hardness < 0.0f) {
            hardness = 50.0f;
        }
        double s = 250.0 * (1.0 + Mth.clamp(hardness, 0.2f, 50.0f) * 1.4);
        if (st.is(BlockTags.PLANKS) || st.is(BlockTags.LOGS) || st.is(BlockTags.WOODEN_SLABS) || st.is(BlockTags.WOODEN_STAIRS)) {
            s *= 0.6;
        }
        return s;
    }

    private static void warn(ServerLevel level, Span span, double ratio) {
        double cx = span.cx / span.load;
        double cz = span.cz / span.load;
        double cy = span.cy / span.load + 1.0;
        level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 4, 1.5, 0.2, 1.5, 0.01);
        level.playSound(null, cx, cy, cz, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, (float) Math.min(0.8, 0.2 + ratio * 0.3), 0.4f);
        Map<Long, Long> warned = WARNED.computeIfAbsent(level, k -> new HashMap<>());
        long now = level.getGameTime();
        if (ratio >= 0.85 && now - warned.getOrDefault(span.key, -1000L) > 80L) {
            warned.put(span.key, now);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(cx, cy, cz) < 48.0 * 48.0) {
                    p.displayClientMessage(Component.translatable("hud.beautifuldecor.bridge_creaks"), true);
                }
            }
        }
    }

    /** El tablero cede alrededor de donde está la carga: cae como bloques y se llevan los rieles de encima. */
    private static void collapse(ServerLevel level, Span span) {
        double cx = span.cx / span.load;
        double cz = span.cz / span.load;
        double radius = Math.max(3.0, span.length() / 4.0);
        int falling = 0;
        for (BlockPos c : span.cells) {
            double dx = c.getX() + 0.5 - cx;
            double dz = c.getZ() + 0.5 - cz;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            BlockPos up = c.above();
            if (st(level, up).getBlock() instanceof FreightRailBlock) {
                level.setBlock(up, Blocks.AIR.defaultBlockState(), 3);
            }
            BlockState st = st(level, c);
            if (st.getBlock() instanceof FreightRailBlock) {
                level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
            } else if (st.getDestroySpeed(level, c) >= 0.0f) {
                if (falling < MAX_FALLING) {
                    falling++;
                    FallingBlockEntity.fall(level, c, st);
                } else {
                    level.destroyBlock(c, false);
                }
            }
        }
        double cy = span.cy / span.load + 1.0;
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 6, radius * 0.5, 0.5, radius * 0.5, 0.0);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(cx, cy, cz) < 80.0 * 80.0) {
                p.displayClientMessage(Component.translatable("hud.beautifuldecor.bridge_collapsed"), true);
            }
        }
    }
}
