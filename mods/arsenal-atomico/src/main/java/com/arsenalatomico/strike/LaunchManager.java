package com.arsenalatomico.strike;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.Config;
import com.arsenalatomico.SalvoPlan;
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
import net.minecraft.world.level.levelgen.Heightmap;
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
 * Lado servidor: elige dónde está la batería, lanza los misiles al compás de la cinemática y
 * hace invulnerable al jugador mientras la mira.
 */
@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID)
public final class LaunchManager {
    private static final String SELECTED_KEY = "arsenalatomico_weapon";
    private static final Map<UUID, Strike> BY_PLAYER = new HashMap<>();
    private static final List<Strike> ALL = new ArrayList<>();

    private static final class Strike {
        @Nullable final UUID owner;
        final ResourceKey<Level> dimension;
        final WeaponType type;
        final SalvoPlan plan;
        final long start;
        int launched;
        final long end;

        Strike(@Nullable UUID owner, ResourceKey<Level> dimension, WeaponType type, SalvoPlan plan, long start) {
            this.owner = owner;
            this.dimension = dimension;
            this.type = type;
            this.plan = plan;
            this.start = start;
            this.end = start + Math.max(type.ticks(), plan.lastImpactTick() + 40);
        }
    }

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

    public static void tryStart(ServerPlayer player) {
        if (busy(player)) {
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
        ServerLevel level = player.serverLevel();
        long seed = level.getRandom().nextLong();
        Vec3 site = launcherSite(level, player.position(), Vec3.atBottomCenterOf(target), RandomSource.create(seed));
        float yaw = yawTowards(site, Vec3.atCenterOf(target));
        Strike s = begin(level, player.getUUID(), type, Vec3.atBottomCenterOf(target).add(0, 0.5, 0), site, yaw, seed);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StartCinematicPacket(type, target, site, yaw, seed, s.type.ticks()));
        player.getCooldowns().addCooldown(ModItems.TARGET_MARKER.get(), Config.COOLDOWN_SECONDS.get() * 20);
        player.displayClientMessage(Component.translatable("message.arsenalatomico.locked", target.getX(), target.getY(), target.getZ())
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Empieza un ataque (también sin jugador: lo usa la prueba de GitHub). */
    public static Strike begin(ServerLevel level, @Nullable UUID owner, WeaponType type, Vec3 target, Vec3 site, float yaw, long seed) {
        SalvoPlan plan = new SalvoPlan(site, yaw, target, seed);
        Strike s = new Strike(owner, level.dimension(), type, plan, level.getGameTime());
        ALL.add(s);
        if (owner != null) BY_PLAYER.put(owner, s);
        return s;
    }

    /** Un lugar abierto a 70-110 bloques del jugador para la batería, lejos del blanco. */
    public static Vec3 launcherSite(ServerLevel level, Vec3 player, Vec3 target, RandomSource r) {
        Vec3 best = null;
        for (int tries = 0; tries < 24; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 70 + r.nextDouble() * 40;
            int x = (int) Math.floor(player.x + Math.cos(a) * d), z = (int) Math.floor(player.z + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 c = new Vec3(x + 0.5, y, z + 0.5);
            if (c.distanceTo(target) < 50) continue;
            boolean water = !level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty();
            if (best == null || !water) best = c;
            if (!water) break;
        }
        if (best == null) best = player.add(80, 0, 0);
        return best;
    }

    public static float yawTowards(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
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
                continue;
            }
            long t = level.getGameTime() - s.start;
            SalvoPlan p = s.plan;
            while (s.launched < SalvoPlan.COUNT && t >= p.launchTick[s.launched]) {
                // el rugido del despegue y del motor lo pone el propio misil en cada jugador
                MissileEntity.launch(level, p.paths[s.launched], 1.3f, 3.2f);
                s.launched++;
            }
            if (level.getGameTime() >= s.end) {
                it.remove();
                if (s.owner != null) BY_PLAYER.remove(s.owner);
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
        Strike s = BY_PLAYER.remove(e.getEntity().getUUID());
        if (s != null) ALL.remove(s);
    }
}
