package com.example.beautifuldecor.news;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.NewsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * V6.11.0: de dónde salen las noticias de HS News: construcciones grandes, incendios forestales provocados por un jugador y jugadores que
 * llegan o vuelven al mundo (el micrófono de HS News y el clima se agregan aparte). Todo es del servidor; las pantallas reciben el resultado
 * por {@link NewsPacket}.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NewsEvents {
    private static final int BUILD_BLOCKS = 100;
    private static final long BUILD_WINDOW = 12000L;
    private static final long BUILD_COOLDOWN = 36000L;
    private static final long FIRE_COOLDOWN = 6000L;
    private static final String SEEN = "beautifuldecor_news_seen";

    private static final Map<Long, long[]> BUILD = new HashMap<>();
    private static final Map<Long, Long> FIRE_ANNOUNCED = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int tick;

    private record Pending(ResourceKey<Level> dim, BlockPos pos, long due) {
    }

    private NewsEvents() {
    }

    // ------------------------------------------------------------------ publicar y enviar

    public static void announce(MinecraftServer server, Component text) {
        NewsData.get(server).add(text);
        broadcast(server);
    }

    public static void broadcast(MinecraftServer server) {
        NewsPacket packet = build(server);
        ModMessages.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendTo(ServerPlayer player) {
        ModMessages.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), build(player.server));
    }

    private static NewsPacket build(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        int rainTime = overworld.getLevelData() instanceof ServerLevelData data ? data.getRainTime() : -1;
        return new NewsPacket(overworld.isRaining(), overworld.isThundering(), rainTime, NewsData.get(server).latest(8));
    }

    // ------------------------------------------------------------------ eventos del mundo

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = level.getGameTime();
        long key = ChunkPos.asLong(event.getPos());
        long[] state = BUILD.computeIfAbsent(key, k -> new long[]{0L, now, -BUILD_COOLDOWN});
        if (now - state[1] > BUILD_WINDOW) {
            state[0] = 0L;
            state[1] = now;
        }
        state[0]++;
        if (state[0] >= BUILD_BLOCKS && now - state[2] > BUILD_COOLDOWN) {
            state[0] = 0L;
            state[2] = now;
            announce(level.getServer(), Component.translatable("news.construction", player.getDisplayName(), event.getPos().getX(), event.getPos().getZ()));
        }
        if (BUILD.size() > 4096) {
            BUILD.clear();
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if ((event.getItemStack().is(Items.FLINT_AND_STEEL) || event.getItemStack().is(Items.FIRE_CHARGE)) && PENDING.size() < 64) {
            PENDING.add(new Pending(level.dimension(), event.getPos(), level.getGameTime() + 100L));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        boolean first = !persisted.getBoolean(SEEN);
        persisted.putBoolean(SEEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        announce(player.server, Component.translatable(first ? "news.new_player" : "news.back", player.getDisplayName()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        MinecraftServer server = event.getServer();
        if (tick % 20 == 0 && !PENDING.isEmpty()) {
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                ServerLevel level = server.getLevel(p.dim());
                if (level == null) {
                    it.remove();
                } else if (level.getGameTime() >= p.due()) {
                    it.remove();
                    checkWildfire(server, level, p.pos());
                }
            }
        }
        if (tick % 1200 == 0) {
            broadcast(server);                              // el clima cambia: las pantallas lo muestran al día
        }
    }

    /** ¿Hay un incendio de verdad (varias llamas cerca de mucha madera u hojas)? Se mira una sola vez, 5 segundos después de encender el fuego. */
    private static void checkWildfire(MinecraftServer server, ServerLevel level, BlockPos center) {
        int fire = 0;
        int wood = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-10, -5, -10), center.offset(10, 12, 10))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof BaseFireBlock) {
                fire++;
            } else if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
                wood++;
            }
        }
        if (fire < 5 || wood < 12) {
            return;
        }
        long key = ChunkPos.asLong(center);
        long now = level.getGameTime();
        Long last = FIRE_ANNOUNCED.get(key);
        if (last != null && now - last < FIRE_COOLDOWN) {
            return;
        }
        FIRE_ANNOUNCED.put(key, now);
        announce(server, Component.translatable("news.wildfire", center.getX(), center.getZ()));
    }
}
