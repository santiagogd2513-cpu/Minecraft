package com.amatista.arsenal.strike;

import com.amatista.arsenal.Config;
import com.amatista.arsenal.StrikeType;
import com.amatista.arsenal.net.SelectWeaponPacket;
import com.amatista.arsenal.net.BeamPacket;
import com.amatista.arsenal.net.Network;
import com.amatista.arsenal.net.StartCinematicPacket;
import com.amatista.arsenal.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Lado servidor: programa el rayo y el impacto para que coincidan con la cinemática del jugador. */
public final class OrbitalStrikeManager {
    private static final Map<UUID, Strike> ACTIVE = new HashMap<>();

    private static final class Strike {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final BlockPos target;
        final StrikeType type;
        long beamTick;
        long impactTick;
        long endTick;
        boolean beamSent;
        boolean impacted;

        Strike(UUID owner, ResourceKey<Level> dimension, BlockPos target, StrikeType type) {
            this.owner = owner;
            this.dimension = dimension;
            this.target = target;
            this.type = type;
        }
    }

    private static final String SELECTED_KEY = "arsenalorbital_weapon";

    /** Arma elegida por el jugador en el menú (H); se guarda con el jugador. */
    public static StrikeType getSelected(Player player) {
        return StrikeType.byId(player.getPersistentData().getString(SELECTED_KEY));
    }

    public static void setSelected(ServerPlayer player, StrikeType type) {
        player.getPersistentData().putString(SELECTED_KEY, type.id);
        player.displayClientMessage(Component.translatable("message.arsenalorbital.selected",
                Component.translatable(type.nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SelectWeaponPacket(getSelected(player)));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        String id = event.getOriginal().getPersistentData().getString(SELECTED_KEY);
        if (!id.isEmpty()) event.getEntity().getPersistentData().putString(SELECTED_KEY, id);
    }

    public static boolean isBusy(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void tryStart(ServerPlayer player) {
        if (ACTIVE.containsKey(player.getUUID())) return;
        HitResult hit = player.pick(Config.MAX_RANGE.get(), 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            player.displayClientMessage(Component.translatable("message.arsenalorbital.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos target = blockHit.getBlockPos();
        long now = level.getGameTime();
        StrikeType type = getSelected(player);
        Strike strike = new Strike(player.getUUID(), level.dimension(), target, type);
        if (Config.CINEMATIC_ENABLED.get()) {
            int duration = Config.CINEMATIC_SECONDS.get() * 20;
            strike.beamTick = now + Math.round(duration * type.worldVisualStart);
            strike.impactTick = now + Math.round(duration * type.impact);
            strike.endTick = now + duration;
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StartCinematicPacket(target, duration, type));
        } else {
            strike.beamTick = now;
            strike.impactTick = now + (type == StrikeType.PHOBOS ? 140 : 50);
            strike.endTick = strike.impactTick + 10;
        }
        ACTIVE.put(player.getUUID(), strike);
        player.getCooldowns().addCooldown(ModItems.ORBITAL_DESIGNATOR.get(), Config.COOLDOWN_SECONDS.get() * 20);
        player.displayClientMessage(Component.translatable("message.arsenalorbital.locked",
                target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** El jugador omitió la cinemática: el rayo cae en 1,5 segundos. */
    public static void skip(ServerPlayer player) {
        Strike strike = ACTIVE.get(player.getUUID());
        if (strike == null || strike.impacted) return;
        long now = player.serverLevel().getGameTime();
        strike.beamTick = Math.min(strike.beamTick, now);
        strike.impactTick = Math.min(strike.impactTick, now + 30);
        strike.endTick = strike.impactTick + 40;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ImpactEffects.tickLingering();
        if (ACTIVE.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Strike> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            if (!s.beamSent && now >= s.beamTick) {
                s.beamSent = true;
                int ticksToImpact = (int) Math.max(1, s.impactTick - now);
                Network.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(s.target)),
                        new BeamPacket(s.target, ticksToImpact, s.type));
            }
            if (!s.impacted && now >= s.impactTick) {
                s.impacted = true;
                ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
                if (s.type == StrikeType.PHOBOS) ImpactEffects.moonImpact(level, s.target, owner);
                else ImpactEffects.impact(level, s.target, owner);
            }
            if (s.impacted && now >= s.endTick) it.remove();
        }
    }

    /** Durante la cinemática el jugador no recibe daño (no puede defenderse). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private OrbitalStrikeManager() {}
}
