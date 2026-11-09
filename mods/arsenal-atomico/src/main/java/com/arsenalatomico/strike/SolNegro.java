package com.arsenalatomico.strike;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.Config;
import com.arsenalatomico.NukePlan;
import com.arsenalatomico.TwinPlan;
import com.arsenalatomico.WeaponType;
import com.arsenalatomico.entity.TruckEntity;
import com.arsenalatomico.net.BasePacket;
import com.arsenalatomico.net.Network;
import com.arsenalatomico.net.OpenConsolePacket;
import com.arsenalatomico.net.RidePacket;
import com.arsenalatomico.net.StartCinematicPacket;
import com.arsenalatomico.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La quinta arma, el Protocolo Sol Negro (termonuclear). No es «clic y mirar»: es una misión.
 * <ol>
 *   <li>Se marca el blanco: un camión militar ruso viene por el jugador. Lo maneja un conductor al que
 *   nunca se le ve la cara.</li>
 *   <li>El jugador se sube de copiloto; el camión arranca y, horas después, llega a la Base Sol Negro
 *   (un búnker que queda en el mundo para siempre, lejos de donde va a caer la bomba).</li>
 *   <li>El jugador baja caminando por la caseta, la escalera y el pasillo hasta el centro de mando.</li>
 *   <li>En la consola levanta la tapa, gira la llave y oprime el botón: cuenta regresiva, despegue,
 *   la bomba y, al final, la cámara vuelve con él al centro de mando.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID)
public final class SolNegro {
    /** El Sol Negro usa el guion del Protocolo Hongo desde el segundo 90 (dentro del silo). */
    public static final int OFFSET = 90;
    /** Lo más cerca que puede caer la bomba de la base (el quemado llega a ~150 bloques). */
    public static final int SAFE = (int) Math.round(NukePlan.SCORCH_R * NukeCrater.THERMO) + 60;
    /** Segundos que dura cada parte del viaje. */
    public static final int RIDE_S = 16, ARRIVE_S = 12;

    private enum Phase { COMING, WAITING, RIDING, ARRIVING, WALKING, FIRING }

    private static final class Mission {
        final UUID owner;
        final Vec3 target;
        final BlockPos targetBlock;
        final long seed;
        Phase phase;
        long since;
        TruckEntity truck;
        BunkerBuilder builder;
        final List<ChunkPos> chunks = new ArrayList<>();
        BlockPos ticket;

        Mission(UUID owner, Vec3 target, BlockPos targetBlock, long seed) {
            this.owner = owner;
            this.target = target;
            this.targetBlock = targetBlock;
            this.seed = seed;
        }
    }

    private static final Map<UUID, Mission> MISSIONS = new HashMap<>();

    private SolNegro() {}

    public static boolean busy(Player p) {
        return MISSIONS.containsKey(p.getUUID());
    }

    // ------------------------------------------------------------------ la base (queda guardada en el mundo)

    /** Dónde está la Base Sol Negro de este mundo (en el Overworld o donde se haya creado). */
    public static final class BaseData extends SavedData {
        public Vec3 site;
        public float yaw;
        public boolean built;

        static BaseData load(CompoundTag t) {
            BaseData d = new BaseData();
            if (t.contains("x")) {
                d.site = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
                d.yaw = t.getFloat("yaw");
                d.built = t.getBoolean("built");
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            if (site != null) {
                t.putDouble("x", site.x);
                t.putDouble("y", site.y);
                t.putDouble("z", site.z);
                t.putFloat("yaw", yaw);
                t.putBoolean("built", built);
            }
            return t;
        }
    }

    public static BaseData base(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BaseData::load, BaseData::new, "arsenalatomico_sol_negro");
    }

    /** Del sistema de la base (a hacia el silo, b a la derecha, alto desde la plataforma) al mundo. */
    private static Vec3 at(BaseData b, double a, double side, double up) {
        return NukePlan.local(new Vec3(Mth.floor(b.site.x) + 0.5, Mth.floor(b.site.y), Mth.floor(b.site.z) + 0.5), b.yaw, a, side, up);
    }

    /** Sobre el suelo en esa columna (para el camión fuera de la plataforma). */
    private static Vec3 onGround(ServerLevel level, Vec3 p) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        return new Vec3(p.x, y + 0.05, p.z);
    }

    // ------------------------------------------------------------------ 1. marcar el blanco

    /** El jugador marcó el blanco con el Sol Negro elegido. */
    public static void request(ServerPlayer player, BlockPos targetBlock) {
        if (busy(player)) {
            player.displayClientMessage(Component.translatable("message.arsenalatomico.busy").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 target = Vec3.atBottomCenterOf(targetBlock).add(0, 0.5, 0);
        BaseData base = base(level);
        long seed = level.getRandom().nextLong();
        if (base.site == null) {
            LaunchManager.Site site = chooseBase(level, player.position(), target, RandomSource.create(seed));
            base.site = site.pos();
            base.yaw = site.yaw();
            base.built = false;
            base.setDirty();
            Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new BasePacket(base.site, base.yaw));
        } else {
            double d = Math.hypot(base.site.x - target.x, base.site.z - target.z);
            if (d < SAFE) {
                player.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.too_close", (int) d, SAFE)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
        }
        Mission m = new Mission(player.getUUID(), target, targetBlock, seed);
        m.ticket = BlockPos.containing(base.site);
        MISSIONS.put(player.getUUID(), m);
        player.getCooldowns().addCooldown(ModItems.TARGET_MARKER.get(), Config.COOLDOWN_SECONDS.get() * 20);
        player.displayClientMessage(Component.translatable("message.arsenalatomico.locked", targetBlock.getX(), targetBlock.getY(), targetBlock.getZ())
                .withStyle(ChatFormatting.GOLD), true);
        if (!base.built) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(base.site.x), Mth.floor(base.site.z));
            m.builder = BunkerBuilder.build(level, base.site, base.yaw, ground, true);
            keepLoaded(level, m, base.site, 5);
        }
        // ya está en la base: no hace falta el camión
        if (player.position().distanceTo(base.site) < 60) {
            setPhase(m, Phase.WALKING, level);
            player.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.at_base").withStyle(ChatFormatting.GOLD), false);
            return;
        }
        // el camión aparece a unos 26 bloques y viene por el jugador
        Vec3 from = truckSpawn(level, player);
        float yaw = (float) (Mth.atan2(-(player.getX() - from.x), player.getZ() - from.z) * Mth.RAD_TO_DEG);
        m.truck = TruckEntity.create(level, from, yaw, true);
        level.addFreshEntity(m.truck);
        Vec3 stopAt = player.position().add(from.subtract(player.position()).normalize().scale(4.5));
        m.truck.driveTo(List.of(stopAt));
        setPhase(m, Phase.COMING, level);
        player.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.coming").withStyle(ChatFormatting.GOLD), false);
    }

    /** La base: lejos del blanco (para que la bomba no la alcance) y cerca del jugador si se puede. */
    private static LaunchManager.Site chooseBase(ServerLevel level, Vec3 player, Vec3 target, RandomSource r) {
        Vec3 land = null;
        for (int tries = 0; tries < 16 && land == null; tries++) {
            Vec3 c = Sites.land(level, player, target, r, 90, 160, 14, 14);
            if (Math.hypot(c.x - target.x, c.z - target.z) >= SAFE + 20) land = c;
        }
        if (land == null) {
            // todo cerca del blanco: la base va del otro lado del jugador, a una distancia segura
            Vec3 away = player.subtract(target).multiply(1, 0, 1);
            away = away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 c = target.add(away.scale(SAFE + 40));
            Integer y = Sites.ground(level, Mth.floor(c.x), Mth.floor(c.z));
            land = new Vec3(Mth.floor(c.x) + 0.5, y != null ? y : player.y, Mth.floor(c.z) + 0.5);
        }
        Vec3 rim = new Vec3(land.x, BunkerBuilder.rimHeight(level, land.y), land.z);
        return new LaunchManager.Site(rim, NukePlan.snap(Sites.yawTowards(rim, target)), TwinPlan.LAND);
    }

    private static Vec3 truckSpawn(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            Vec3 c = player.position().add(Math.cos(ang) * 26, 0, Math.sin(ang) * 26);
            Integer y = Sites.ground(level, Mth.floor(c.x), Mth.floor(c.z));
            if (y != null && Math.abs(y - player.getY()) < 6) return new Vec3(c.x, y + 0.05, c.z);
        }
        Vec3 c = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(12));
        return onGround(level, c);
    }

    // ------------------------------------------------------------------ 2. subirse al camión

    public static void board(Player player, TruckEntity truck) {
        Mission m = MISSIONS.get(player.getUUID());
        if (m == null || m.truck != truck || (m.phase != Phase.WAITING && m.phase != Phase.COMING)) {
            player.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.not_yours").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!(player instanceof ServerPlayer sp)) return;
        truck.stop();
        if (!sp.startRiding(truck, true)) return;
        // arranca: sigue derecho hacia donde mira, unos 120 bloques
        Vec3 ahead = truck.position().add(Vec3.directionFromRotation(0, truck.getYRot()).scale(120));
        truck.driveTo(List.of(ahead));
        setPhase(m, Phase.RIDING, sp.serverLevel());
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new RidePacket(truck.getId(), 0, RIDE_S * 20, base(sp.serverLevel()).site, base(sp.serverLevel()).yaw, m.targetBlock));
    }

    // ------------------------------------------------------------------ 3. la consola

    public static void console(ServerPlayer player, BlockPos pos) {
        Mission m = MISSIONS.get(player.getUUID());
        if (m == null || m.phase != Phase.WALKING) {
            player.displayClientMessage(Component.translatable(m != null && m.phase == Phase.FIRING
                    ? "message.arsenalatomico.busy" : "message.arsenalatomico.thermo.no_mission").withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenConsolePacket(m.targetBlock));
    }

    /** El jugador oprimió el botón rojo. */
    public static void fire(ServerPlayer player) {
        Mission m = MISSIONS.get(player.getUUID());
        if (m == null || m.phase != Phase.WALKING) return;
        ServerLevel level = player.serverLevel();
        BaseData b = base(level);
        if (b.site == null || player.position().distanceTo(Vec3.atCenterOf(BunkerBuilder.consolePos(b.site, b.yaw))) > 8) return;
        if (m.builder != null) {
            m.builder.step(Integer.MAX_VALUE);
            b.built = true;
            b.setDirty();
        }
        setPhase(m, Phase.FIRING, level);
        LaunchManager.Site site = new LaunchManager.Site(b.site, b.yaw, TwinPlan.LAND);
        LaunchManager.begin(level, player.getUUID(), WeaponType.THERMO, m.target, site, m.seed);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StartCinematicPacket(WeaponType.THERMO, m.targetBlock, b.site, b.yaw, TwinPlan.LAND, m.seed, WeaponType.THERMO.ticks()));
        release(level, m);
    }

    // ------------------------------------------------------------------ cada tick

    private static void setPhase(Mission m, Phase p, ServerLevel level) {
        m.phase = p;
        m.since = level.getGameTime();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || MISSIONS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Mission> it = MISSIONS.values().iterator();
        while (it.hasNext()) {
            Mission m = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(m.owner);
            if (p == null) {
                cancel(null, m, null);
                it.remove();
                continue;
            }
            ServerLevel level = p.serverLevel();
            if (!tick(level, p, m)) it.remove();
        }
    }

    /** false = la misión terminó. */
    private static boolean tick(ServerLevel level, ServerPlayer p, Mission m) {
        BaseData b = base(level);
        if (b.site == null) {
            cancel(level, m, p);
            return false;
        }
        if (m.builder != null && !m.builder.done()) {
            m.builder.step(6000);
            if (m.builder.done()) {
                b.built = true;
                b.setDirty();
            }
        }
        long t = level.getGameTime() - m.since;
        switch (m.phase) {
            case COMING -> {
                if (m.truck == null || m.truck.isRemoved()) {
                    cancel(level, m, p);
                    return false;
                }
                // si se atasca o tarda mucho, aparece al lado del jugador
                if (m.truck.stuck() > 40 || t > 20 * 25) {
                    Vec3 near = onGround(level, p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(5)));
                    m.truck.teleportTo(near.x, near.y, near.z);
                    m.truck.stop();
                }
                if (m.truck.arrived() || m.truck.position().distanceTo(p.position()) < 6) {
                    m.truck.stop();
                    setPhase(m, Phase.WAITING, level);
                    honk(level, m.truck);
                    p.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.board").withStyle(ChatFormatting.GOLD), false);
                }
            }
            case WAITING -> {
                if (m.truck == null || m.truck.isRemoved()) {
                    cancel(level, m, p);
                    return false;
                }
                if (t == 20 * 20) honk(level, m.truck);
                if (t > 20 * 90) {
                    p.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.left").withStyle(ChatFormatting.RED), false);
                    cancel(level, m, p);
                    return false;
                }
            }
            case RIDING -> {
                if (!p.isPassenger() && m.truck != null) p.startRiding(m.truck, true);
                if (t >= RIDE_S * 20L) arrive(level, p, m, b);
            }
            case ARRIVING -> {
                if (!p.isPassenger() && m.truck != null && t < ARRIVE_S * 20L - 10) p.startRiding(m.truck, true);
                if (t >= ARRIVE_S * 20L) {
                    if (m.truck != null) {
                        m.truck.stop();
                        p.stopRiding();
                        // queda parqueado y suelto: el jugador lo puede manejar
                        m.truck.setMission(false);
                    }
                    Vec3 door = at(b, NukePlan.STAIR_A + 6, 2, 0.1);
                    p.teleportTo(level, door.x, door.y, door.z, b.yaw + 180, 5);
                    setPhase(m, Phase.WALKING, level);
                    p.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.walk").withStyle(ChatFormatting.GOLD), false);
                }
            }
            case WALKING -> {
                if (t > 20 * 60 * 15) {
                    p.displayClientMessage(Component.translatable("message.arsenalatomico.thermo.cancel").withStyle(ChatFormatting.RED), false);
                    cancel(level, m, p);
                    return false;
                }
            }
            case FIRING -> {
                return LaunchManager.busy(p);
            }
        }
        return true;
    }

    /** Horas después: el camión aparece afuera del portón de la base y entra hasta la caseta. */
    private static void arrive(ServerLevel level, ServerPlayer p, Mission m, BaseData b) {
        keepLoaded(level, m, b.site, 5);
        if (m.builder != null) {
            m.builder.step(Integer.MAX_VALUE);
            b.built = true;
            b.setDirty();
        }
        Vec3 start = onGround(level, at(b, -50, 0, 0));
        float yaw = b.yaw;   // mirando hacia el silo
        if (m.truck == null || m.truck.isRemoved()) {
            m.truck = TruckEntity.create(level, start, yaw, true);
            level.addFreshEntity(m.truck);
        }
        p.stopRiding();
        m.truck.teleportTo(start.x, start.y, start.z);
        m.truck.setYRot(yaw);
        p.teleportTo(level, start.x, start.y + 1.5, start.z, yaw, 0);
        p.startRiding(m.truck, true);
        // por el portón (detrás de la caseta), rodea la caseta y para frente a la puerta
        double top = Math.floor(b.site.y) + 0.05;
        // (entre el portón y la caseta hay 6 bloques: el camión pasa por a = -32)
        Vec3 gate = at(b, -38, 0, 0), in = at(b, -32, 0, 0.05);
        m.truck.driveTo(List.of(onGround(level, gate), new Vec3(in.x, top, in.z), at(b, -32, 8, 0.05), at(b, -17, 8, 0.05)));
        setPhase(m, Phase.ARRIVING, level);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RidePacket(m.truck.getId(), 1, ARRIVE_S * 20, b.site, b.yaw, m.targetBlock));
    }

    private static void honk(ServerLevel level, TruckEntity t) {
        level.playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 3f, 0.6f);
        level.playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.NEUTRAL, 3f, 0.7f);
    }

    private static void cancel(ServerLevel level, Mission m, ServerPlayer p) {
        if (m.truck != null && !m.truck.isRemoved() && m.truck.mission()) {
            m.truck.ejectPassengers();
            m.truck.discard();
        }
        if (m.builder != null && level != null) m.builder.step(Integer.MAX_VALUE);
        if (level != null && m.builder != null) {
            BaseData b = base(level);
            b.built = true;
            b.setDirty();
        }
        if (level != null) release(level, m);
    }

    private static void keepLoaded(ServerLevel level, Mission m, Vec3 at, int radius) {
        int cx = Mth.floor(at.x) >> 4, cz = Mth.floor(at.z) >> 4;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                ChunkPos c = new ChunkPos(x, z);
                if (m.chunks.contains(c)) continue;
                if (ForgeChunkManager.forceChunk(level, ArsenalAtomico.MODID, m.ticket, x, z, true, true)) m.chunks.add(c);
            }
        }
    }

    private static void release(ServerLevel level, Mission m) {
        for (ChunkPos c : m.chunks) ForgeChunkManager.forceChunk(level, ArsenalAtomico.MODID, m.ticket, c.x, c.z, false, true);
        m.chunks.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            BaseData b = base(p.serverLevel());
            if (b.site != null) Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new BasePacket(b.site, b.yaw));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Mission m = MISSIONS.remove(e.getEntity().getUUID());
        if (m != null && e.getEntity() instanceof ServerPlayer p) cancel(p.serverLevel(), m, p);
    }
}
