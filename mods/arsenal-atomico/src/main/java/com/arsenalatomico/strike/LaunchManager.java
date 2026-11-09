package com.arsenalatomico.strike;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.Config;
import com.arsenalatomico.CondorPlan;
import com.arsenalatomico.MissilePath;
import com.arsenalatomico.NukePlan;
import com.arsenalatomico.SalvoPlan;
import com.arsenalatomico.TwinPlan;
import com.arsenalatomico.WeaponType;
import com.arsenalatomico.entity.MissileEntity;
import com.arsenalatomico.net.Network;
import com.arsenalatomico.net.SelectWeaponPacket;
import com.arsenalatomico.net.StartCinematicPacket;
import com.arsenalatomico.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor: elige dónde están las lanzadoras, lanza los misiles al compás de la cinemática,
 * los hace estallar a su hora (aunque estén lejos del jugador) y lo hace invulnerable mientras mira.
 */
@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID)
public final class LaunchManager {
    private static final String SELECTED_KEY = "arsenalatomico_weapon";
    private static final Map<UUID, Strike> BY_PLAYER = new HashMap<>();
    private static final List<Strike> ALL = new ArrayList<>();

    /** Un misil del ataque: cuándo sale, por dónde va y qué hace al llegar. */
    private static final class Shot {
        final int launch;
        final MissilePath path;
        final float scale, power;
        final int kind, ignite;
        final boolean big;
        MissileEntity entity;
        boolean fired, hit;

        Shot(int launch, MissilePath path, float scale, int kind, int ignite, float power, boolean big) {
            this.launch = launch;
            this.path = path;
            this.scale = scale;
            this.kind = kind;
            this.ignite = ignite;
            this.power = power;
            this.big = big;
        }
    }

    public static final class Strike {
        @Nullable final UUID owner;
        final ResourceKey<Level> dimension;
        final WeaponType type;
        final List<Shot> shots = new ArrayList<>();
        final long start;
        long end;
        final BlockPos ticketOwner;
        final List<ChunkPos> chunks = new ArrayList<>();
        /** El silo del Cóndor (se construye de a poco y al final se cierra). */
        SiloBuilder silo;
        Vec3 siloSite;
        /** El búnker del Protocolo Hongo y el cráter que deja la bomba. */
        BunkerBuilder bunker;
        NukeCrater crater;
        /** Sale de la Base Sol Negro (que ya existe): al final solo se cierra la compuerta de ese silo. */
        boolean thermo;
        int silo;
        float baseYaw;

        Strike(@Nullable UUID owner, ServerLevel level, WeaponType type, long start, int lastImpact, BlockPos ticketOwner) {
            this.owner = owner;
            this.dimension = level.dimension();
            this.type = type;
            this.start = start;
            this.end = start + Math.max(type.ticks(), lastImpact + 40);
            this.ticketOwner = ticketOwner;
        }

        /** ¿Ya cayeron todos? (lo usa la prueba de GitHub) */
        public boolean allHit() {
            for (Shot s : shots) if (!s.hit) return false;
            return true;
        }

        public long lastImpactGameTime() {
            long m = start;
            for (Shot s : shots) m = Math.max(m, start + s.launch + s.path.flightTicks);
            return m;
        }
    }

    /** Dónde se dispara: el lugar, hacia dónde mira y si es en tierra o en el mar. */
    public record Site(Vec3 pos, float yaw, int mode) {}

    private LaunchManager() {}

    public static WeaponType getSelected(Player p) {
        return WeaponType.byId(p.getPersistentData().getString(SELECTED_KEY));
    }

    public static void setSelected(ServerPlayer p, WeaponType t) {
        if (!t.ready) {
            p.displayClientMessage(Component.translatable("message.arsenalatomico.not_ready").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        p.getPersistentData().putString(SELECTED_KEY, t.id);
        p.displayClientMessage(Component.translatable("message.arsenalatomico.selected", Component.translatable(t.nameKey()))
                .withStyle(ChatFormatting.GOLD), true);
    }

    public static boolean busy(Player p) {
        return BY_PLAYER.containsKey(p.getUUID());
    }

    /** Ocupado con un ataque o con la misión del Sol Negro. */
    public static boolean anyBusy(Player p) {
        return busy(p) || SolNegro.busy(p);
    }

    public static void tryStart(ServerPlayer player) {
        if (anyBusy(player)) {
            player.displayClientMessage(Component.translatable("message.arsenalatomico.busy").withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos target = Targeting.find(player, Math.max(32, Config.MAX_RANGE.get()), 1f);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.arsenalatomico.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        WeaponType type = getSelected(player);
        if (!type.ready) type = WeaponType.SALVO;
        if (type == WeaponType.THERMO) {
            // el Sol Negro es una misión: camión, base y botón (lo maneja SolNegro)
            SolNegro.request(player, target);
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = level.getRandom().nextLong();
        Vec3 t = Vec3.atBottomCenterOf(target).add(0, 0.5, 0);
        Site site = chooseSite(level, type, player.position(), t, RandomSource.create(seed));
        Strike s = begin(level, player.getUUID(), type, t, site, seed);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StartCinematicPacket(type, target, site.pos(), site.yaw(), site.mode(), seed, s.type.ticks()));
        player.getCooldowns().addCooldown(ModItems.TARGET_MARKER.get(), Config.COOLDOWN_SECONDS.get() * 20);
        player.displayClientMessage(Component.translatable("message.arsenalatomico.locked", target.getX(), target.getY(), target.getZ())
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Busca dónde poner las lanzadoras (cerca del jugador, para que su juego las tenga dibujadas). */
    public static Site chooseSite(ServerLevel level, WeaponType type, Vec3 player, Vec3 target, RandomSource r) {
        if (type == WeaponType.NUKE) {
            // el búnker es enorme: lejos del jugador (para no enterrarlo) y muy lejos del blanco (para que la bomba no lo alcance)
            Vec3 land = null;
            for (int tries = 0; tries < 12 && land == null; tries++) {
                Vec3 c = Sites.land(level, player, target, r, 80, 125, 14, 14);
                if (Math.hypot(c.x - target.x, c.z - target.z) >= NukePlan.SCORCH_R + 70) land = c;
            }
            if (land == null) {
                // todo cerca del blanco: el búnker va del otro lado del jugador
                Vec3 away = player.subtract(target).multiply(1, 0, 1);
                away = away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
                Vec3 c = player.add(away.scale(100));
                Integer y = Sites.ground(level, Mth.floor(c.x), Mth.floor(c.z));
                land = new Vec3(Mth.floor(c.x) + 0.5, y != null ? y : player.y, Mth.floor(c.z) + 0.5);
            }
            Vec3 rim = new Vec3(land.x, BunkerBuilder.rimHeight(level, land.y), land.z);
            return new Site(rim, NukePlan.snap(Sites.yawTowards(rim, target)), TwinPlan.LAND);
        }
        if (type == WeaponType.ICBM) {
            // el silo: suelo firme y parejo, cerca del jugador; el borde va en el suelo o más arriba (loma)
            Vec3 land = Sites.land(level, player, target, r, 40, 70, 9, 9);
            Vec3 rim = new Vec3(land.x, SiloBuilder.rimHeight(level, land.y), land.z);
            return new Site(rim, Sites.yawTowards(rim, target), TwinPlan.LAND);
        }
        if (type == WeaponType.TWIN) {
            Vec3 sea = Sites.sea(level, player, target, r);
            if (sea != null) return new Site(sea, Sites.yawTowards(sea, target), TwinPlan.SEA);
            Vec3 land = Sites.land(level, player, target, r, 45, 80, 12.5, 9.5);
            return new Site(land, Sites.yawTowards(land, target), TwinPlan.LAND);
        }
        Vec3 land = Sites.land(level, player, target, r, 45, 75, 4, 2);
        return new Site(land, Sites.yawTowards(land, target), TwinPlan.LAND);
    }

    /** Empieza un ataque (también sin jugador: lo usa la prueba de GitHub). */
    public static Strike begin(ServerLevel level, @Nullable UUID owner, WeaponType type, Vec3 target, Site site, long seed) {
        long now = level.getGameTime();
        Strike s;
        if (type == WeaponType.THERMO) {
            // desde la Base Sol Negro: «mode» es el silo (0 = Sol Negro, 1–3 chiquitos, 4–5 medianos)
            int silo = site.mode();
            com.arsenalatomico.BasePlan plan = new com.arsenalatomico.BasePlan(site.pos(), site.yaw(), target, seed, silo);
            s = new Strike(owner, level, type, now, plan.impactTick, BlockPos.containing(target));
            s.end = now + plan.duration;
            int kind = silo == 0 ? 4 : com.arsenalatomico.BaseLayout.small(silo) ? 5 : 6;
            s.shots.add(new Shot(plan.launchTick, plan.path, com.arsenalatomico.BaseLayout.missileScale(silo), kind, 0, 0f, true));
            s.siloSite = site.pos();
            s.thermo = true;
            s.silo = silo;
            s.baseYaw = site.yaw();
            BaseBuilder.open(level, site.pos(), site.yaw(), silo);
            int reach = (int) Math.ceil(NukePlan.SCORCH_R * com.arsenalatomico.BaseLayout.size(silo) / 16) + 1;
            keepLoaded(level, s, target, reach);
            keepLoaded(level, s, plan.siloTop(), 2);
        } else if (type == WeaponType.NUKE) {
            NukePlan plan = new NukePlan(site.pos(), site.yaw(), target, seed);
            s = new Strike(owner, level, type, now, plan.impactTick(), BlockPos.containing(target));
            s.shots.add(new Shot(plan.launchTick, plan.path, 1f, 3, 0, 0f, true));
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(site.pos().x), Mth.floor(site.pos().z));
            s.bunker = BunkerBuilder.build(level, site.pos(), plan.yaw, ground);
            s.siloSite = site.pos();
            // el cráter y lo quemado llegan a 104 bloques del blanco; el búnker y su loma también son grandes
            keepLoaded(level, s, target, 7);
            keepLoaded(level, s, site.pos(), 5);
        } else if (type == WeaponType.ICBM) {
            CondorPlan plan = new CondorPlan(site.pos(), site.yaw(), target, seed);
            s = new Strike(owner, level, type, now, plan.impactTick(), BlockPos.containing(target));
            s.shots.add(new Shot(plan.launchTick, plan.path, CondorPlan.SCALE, 2, 0, 12f, true));
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(site.pos().x), Mth.floor(site.pos().z));
            s.silo = SiloBuilder.build(level, site.pos(), ground);
            s.siloSite = site.pos();
        } else if (type == WeaponType.TWIN) {
            TwinPlan plan = new TwinPlan(site.mode(), site.pos(), site.yaw(), target, seed);
            s = new Strike(owner, level, type, now, plan.lastImpactTick(), BlockPos.containing(target));
            for (int i = 0; i < 2; i++) {
                s.shots.add(new Shot(plan.launchTick[i], plan.paths[i], plan.scale(), 1, plan.ignite(), 9f, true));
            }
        } else {
            SalvoPlan plan = new SalvoPlan(site.pos(), site.yaw(), target, seed);
            s = new Strike(owner, level, type, now, plan.lastImpactTick(), BlockPos.containing(target));
            for (int i = 0; i < SalvoPlan.COUNT; i++) {
                s.shots.add(new Shot(plan.launchTick[i], plan.paths[i], 1.3f, 0, 0, 3.2f, false));
            }
        }
        // el blanco y las lanzadoras quedan cargados y funcionando aunque el jugador esté lejos
        keepLoaded(level, s, target, 2);
        keepLoaded(level, s, site.pos(), 2);
        ALL.add(s);
        if (owner != null) BY_PLAYER.put(owner, s);
        return s;
    }

    private static void keepLoaded(ServerLevel level, Strike s, Vec3 at, int radius) {
        int cx = Mth.floor(at.x) >> 4, cz = Mth.floor(at.z) >> 4;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                ChunkPos c = new ChunkPos(x, z);
                if (s.chunks.contains(c)) continue;
                if (ForgeChunkManager.forceChunk(level, ArsenalAtomico.MODID, s.ticketOwner, x, z, true, true)) s.chunks.add(c);
            }
        }
    }

    private static void finish(MinecraftServer server, Strike s) {
        ServerLevel level = server.getLevel(s.dimension);
        for (Shot shot : s.shots) if (shot.entity != null && !shot.entity.isRemoved()) shot.entity.discard();
        if (level != null && s.silo != null) {
            s.silo.step(Integer.MAX_VALUE);
            SiloBuilder.close(level, s.siloSite);
        }
        if (level != null && s.bunker != null) {
            s.bunker.step(Integer.MAX_VALUE);
            BunkerBuilder.close(level, s.siloSite);
        }
        if (level != null && s.thermo) BaseBuilder.close(level, s.siloSite, s.baseYaw, s.silo);
        if (s.crater != null) s.crater.step(Integer.MAX_VALUE);
        if (level != null) {
            for (ChunkPos c : s.chunks) ForgeChunkManager.forceChunk(level, ArsenalAtomico.MODID, s.ticketOwner, c.x, c.z, false, true);
        }
        s.chunks.clear();
        if (s.owner != null) BY_PLAYER.remove(s.owner);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ALL.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Strike> it = ALL.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null) {
                it.remove();
                finish(server, s);
                continue;
            }
            long t = level.getGameTime() - s.start;
            if (s.silo != null && !s.silo.done()) s.silo.step(3000);
            if (s.bunker != null && !s.bunker.done()) s.bunker.step(8000);
            if (s.crater != null && !s.crater.done()) s.crater.step(900);
            for (Shot shot : s.shots) {
                if (!shot.fired && t >= shot.launch) {
                    shot.fired = true;
                    shot.entity = MissileEntity.launch(level, shot.path, shot.scale, shot.kind, shot.ignite);
                }
                // el impacto va por reloj: no depende de que el servidor esté moviendo el misil
                if (shot.fired && !shot.hit && t >= shot.launch + shot.path.flightTicks) {
                    shot.hit = true;
                    if (shot.entity != null) shot.entity.discard();
                    if (shot.kind >= 4) s.crater = NukeCrater.detonate(level, shot.path.p3, com.arsenalatomico.BaseLayout.size(s.silo));
                    else if (shot.kind == 3) s.crater = NukeCrater.detonate(level, shot.path.p3);
                    else if (shot.kind == 2) Impacts.huge(level, shot.path.p3, shot.power);
                    else if (shot.big) Impacts.big(level, shot.path.p3, shot.power);
                    else Impacts.missile(level, shot.path.p3, shot.power);
                }
            }
            if (level.getGameTime() >= s.end) {
                it.remove();
                finish(server, s);
            }
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && busy(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SelectWeaponPacket(getSelected(p)));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        String id = e.getOriginal().getPersistentData().getString(SELECTED_KEY);
        if (!id.isEmpty()) e.getEntity().getPersistentData().putString(SELECTED_KEY, id);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Strike s = BY_PLAYER.get(e.getEntity().getUUID());
        if (s != null) {
            ALL.remove(s);
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) finish(server, s);
            BY_PLAYER.remove(e.getEntity().getUUID());
        }
    }
}
