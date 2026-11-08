package com.example.beautifuldecor.megaphone;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.MicrophoneBlock;
import com.example.beautifuldecor.block.PaSpeakerBlock;
import com.example.beautifuldecor.block.entity.MicrophoneBlockEntity;
import com.example.beautifuldecor.block.entity.PaSpeakerBlockEntity;
import com.example.beautifuldecor.network.MicControlPacket;
import com.example.beautifuldecor.network.MicAudioPacket;
import com.example.beautifuldecor.network.MicStatePacket;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.SpeakerAudioPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V4.0.0: la parte del servidor de la megafonía. Controla quién habla por cada micrófono, valida lo que manda el cliente
 * (distancia, ritmo, tamaño) y reparte la voz a los jugadores que tengan alguna bocina vinculada a su alcance.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PaServer {
    /** Hasta dónde (bloques) se puede estar del micrófono para empezar a hablar o usar su menú. */
    public static final double TALK_RANGE = 12.0;
    /** Si quien habla se aleja más de esto, se le corta el micrófono. */
    public static final double KEEP_RANGE = 16.0;
    /** Radio (bloques) en el que «Vincular bocinas cercanas» busca bocinas. */
    public static final int LINK_RADIUS = 12;
    private static final int MAX_PACKETS_PER_SECOND = 60;
    private static final long SILENCE_TIMEOUT_TICKS = 100L;      // 5 s sin voz y se corta (el cliente cayó o se colgó)
    private static final long WINDOW_TICKS = 20L;

    /** Una sesión de habla: quién, por qué micrófono y cuánto ha mandado. */
    private static final class Talk {
        final UUID player;
        final ResourceKey<Level> dimension;
        final BlockPos mic;
        long lastAudio;
        long windowStart;
        int windowCount;

        Talk(UUID player, ResourceKey<Level> dimension, BlockPos mic, long now) {
            this.player = player;
            this.dimension = dimension;
            this.mic = mic;
            this.lastAudio = now;
            this.windowStart = now;
        }
    }

    private static final Map<UUID, Talk> TALKS = new HashMap<>();

    private PaServer() {}

    // ------------------------------------------------------------------ órdenes del menú

    public static void handleControl(ServerPlayer player, BlockPos pos, byte action) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic)) {
            sendState(player, pos, null, "message.beautifuldecor.pa.mic_missing", 0);
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > TALK_RANGE * TALK_RANGE) {
            sendState(player, pos, mic, "message.beautifuldecor.pa.too_far", 0);
            return;
        }
        switch (action) {
            case MicControlPacket.START_TALK -> startTalk(player, level, pos, mic);
            case MicControlPacket.STOP_TALK -> {
                stopTalkOf(level.getServer(), player.getUUID(), pos, false);
                sendState(player, pos, mic, "", 0);
            }
            case MicControlPacket.LINK_NEARBY -> {
                int added = linkNearby(level, pos, mic);
                String message = added < 0 ? "message.beautifuldecor.pa.mic_full_short" : added == 0 ? "message.beautifuldecor.pa.none_nearby"
                        : "message.beautifuldecor.pa.linked_n";
                sendState(player, pos, mic, message, Math.max(added, 0));
            }
            case MicControlPacket.UNLINK_ALL -> {
                unlinkAll(level, pos, mic);
                sendState(player, pos, mic, "message.beautifuldecor.pa.unlinked_all", 0);
            }
            case MicControlPacket.QUERY -> sendState(player, pos, mic, "", 0);
            default -> { }                                  // órdenes desconocidas (de otra versión): se ignoran
        }
    }

    private static void startTalk(ServerPlayer player, ServerLevel level, BlockPos pos, MicrophoneBlockEntity mic) {
        UUID current = mic.getTalker();
        if (current != null && !current.equals(player.getUUID())) {
            ServerPlayer other = level.getServer().getPlayerList().getPlayer(current);
            if (other != null && TALKS.containsKey(current)) {
                sendState(player, pos, mic, "message.beautifuldecor.pa.busy", 0);
                return;
            }
        }
        // Una persona solo habla por un micrófono a la vez.
        Talk previous = TALKS.get(player.getUUID());
        if (previous != null && !previous.mic.equals(pos)) {
            stopTalkOf(level.getServer(), player.getUUID(), previous.mic, false);
        }
        long now = level.getGameTime();
        TALKS.put(player.getUUID(), new Talk(player.getUUID(), level.dimension(), pos.immutable(), now));
        mic.setTalker(player.getUUID());
        setTalkingLight(level, pos, true);
        sendState(player, pos, mic, mic.getSpeakerCount() == 0 ? "message.beautifuldecor.pa.no_speakers" : "", 0);
    }

    /**
     * Termina la sesión de habla de ese jugador (si la tiene, y si es por ese micrófono; {@code expectedMic == null} = por cualquiera) y apaga
     * la luz del micrófono. Con {@code notify} se le avisa al cliente de que lo cortó el servidor (se alejó, se colgó, etc.).
     */
    private static void stopTalkOf(MinecraftServer server, UUID player, BlockPos expectedMic, boolean notify) {
        Talk talk = TALKS.get(player);
        if (talk == null || (expectedMic != null && !talk.mic.equals(expectedMic))) {
            return;
        }
        TALKS.remove(player);
        ServerLevel level = server.getLevel(talk.dimension);
        if (level != null && level.isLoaded(talk.mic) && level.getBlockEntity(talk.mic) instanceof MicrophoneBlockEntity mic) {
            if (player.equals(mic.getTalker())) {
                mic.setTalker(null);
            }
            setTalkingLight(level, talk.mic, false);
        }
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (notify && p != null) {
            ModMessages.sendToPlayer(p, new MicStatePacket(talk.mic, 0, MicrophoneBlockEntity.MAX_SPEAKERS, "", false,
                    "message.beautifuldecor.pa.cut", 0));
        }
    }

    private static void setTalkingLight(ServerLevel level, BlockPos pos, boolean talking) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MicrophoneBlock && state.getValue(MicrophoneBlock.TALKING) != talking) {
            level.setBlock(pos, MicrophoneBlockEntity.withTalking(state, talking), 3);
        }
    }

    // ------------------------------------------------------------------ vincular

    /** @return cuántas bocinas nuevas se vincularon; -1 si el micrófono se llenó sin poder vincular ninguna. */
    private static int linkNearby(ServerLevel level, BlockPos micPos, MicrophoneBlockEntity mic) {
        int added = 0;
        boolean full = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -LINK_RADIUS; dx <= LINK_RADIUS && !full; dx++) {
            for (int dz = -LINK_RADIUS; dz <= LINK_RADIUS && !full; dz++) {
                for (int dy = -LINK_RADIUS; dy <= LINK_RADIUS; dy++) {
                    cursor.set(micPos.getX() + dx, micPos.getY() + dy, micPos.getZ() + dz);
                    if (!level.isLoaded(cursor) || !(level.getBlockState(cursor).getBlock() instanceof PaSpeakerBlock)) {
                        continue;
                    }
                    if (!(level.getBlockEntity(cursor) instanceof PaSpeakerBlockEntity speaker)) {
                        continue;
                    }
                    BlockPos other = speaker.getMicrophone();
                    boolean mine = micPos.equals(other);
                    if (other != null && !mine && level.isLoaded(other) && level.getBlockState(other).getBlock() instanceof MicrophoneBlock) {
                        continue;                       // ya es de otro micrófono que existe: no se la quitamos
                    }
                    if (mine && mic.getSpeakers().contains(cursor)) {
                        continue;                       // ya estaba
                    }
                    if (!mic.addSpeaker(cursor)) {
                        full = true;
                        break;
                    }
                    speaker.setMicrophone(micPos);
                    added++;
                }
            }
        }
        return added == 0 && full ? -1 : added;
    }

    private static void unlinkAll(ServerLevel level, BlockPos micPos, MicrophoneBlockEntity mic) {
        for (BlockPos pos : new ArrayList<>(mic.getSpeakers())) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PaSpeakerBlockEntity speaker && micPos.equals(speaker.getMicrophone())) {
                speaker.setMicrophone(null);
            }
        }
        mic.clearSpeakers();
    }

    private static void sendState(ServerPlayer player, BlockPos pos, MicrophoneBlockEntity mic, String message, int arg) {
        String talker = "";
        boolean you = false;
        if (mic != null && mic.getTalker() != null) {
            you = mic.getTalker().equals(player.getUUID()) && TALKS.containsKey(player.getUUID());
            ServerPlayer p = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(mic.getTalker());
            talker = p == null ? "" : p.getGameProfile().getName();
        }
        ModMessages.sendToPlayer(player, new MicStatePacket(pos, mic == null ? 0 : mic.getSpeakerCount(), MicrophoneBlockEntity.MAX_SPEAKERS,
                talker, you, message, arg));
    }

    // ------------------------------------------------------------------ la voz

    public static void handleAudio(ServerPlayer player, BlockPos pos, byte[] data) {
        Talk talk = TALKS.get(player.getUUID());
        if (talk == null || !talk.mic.equals(pos) || !(player.level() instanceof ServerLevel level) || !level.dimension().equals(talk.dimension)) {
            return;
        }
        if (data.length < ImaAdpcm.HEADER + 2 || data.length > MicAudioPacket.MAX_BYTES) {
            return;
        }
        long now = level.getGameTime();
        if (now - talk.windowStart >= WINDOW_TICKS) {
            talk.windowStart = now;
            talk.windowCount = 0;
        }
        if (++talk.windowCount > MAX_PACKETS_PER_SECOND) {
            return;                                    // demasiado seguido: se ignora el exceso
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > KEEP_RANGE * KEEP_RANGE) {
            return;
        }
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic)) {
            return;
        }
        talk.lastAudio = now;
        List<BlockPos> speakers = mic.getSpeakers();
        if (speakers.isEmpty()) {
            return;
        }
        double range = PaDsp.HEAR_RANGE * PaDsp.HEAR_RANGE;
        for (ServerPlayer listener : level.players()) {
            long[] near = new long[speakers.size()];
            int count = 0;
            for (BlockPos speaker : speakers) {
                if (listener.distanceToSqr(speaker.getX() + 0.5, speaker.getY() + 0.5, speaker.getZ() + 0.5) <= range) {
                    near[count++] = speaker.asLong();
                }
            }
            if (count > 0) {
                long[] trimmed = new long[count];
                System.arraycopy(near, 0, trimmed, 0, count);
                ModMessages.sendToPlayer(listener, new SpeakerAudioPacket(pos, trimmed, data));
            }
        }
    }

    // ------------------------------------------------------------------ cuidado del micrófono (cada 10 ticks)

    public static void tickMicrophone(Level level, BlockPos pos, BlockState state, MicrophoneBlockEntity mic) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % 200L == 0L) {
            mic.dropMissingSpeakers(serverLevel);
        }
        UUID talker = mic.getTalker();
        if (talker == null) {
            if (state.hasProperty(MicrophoneBlock.TALKING) && state.getValue(MicrophoneBlock.TALKING)) {
                setTalkingLight(serverLevel, pos, false);
            }
            return;
        }
        Talk talk = TALKS.get(talker);
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(talker);
        boolean ok = talk != null && talk.mic.equals(pos) && player != null && player.isAlive() && player.level() == serverLevel
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= KEEP_RANGE * KEEP_RANGE
                && serverLevel.getGameTime() - talk.lastAudio <= SILENCE_TIMEOUT_TICKS;
        if (!ok) {
            if (talk != null && talk.mic.equals(pos)) {
                stopTalkOf(serverLevel.getServer(), talker, pos, true);
            } else {
                mic.setTalker(null);
                setTalkingLight(serverLevel, pos, false);
            }
        }
    }

    /** Se quitó el micrófono: se corta a quien hablaba. */
    public static void microphoneRemoved(Level level, BlockPos pos, MicrophoneBlockEntity mic) {
        UUID talker = mic.getTalker();
        if (talker != null && level.getServer() != null) {
            stopTalkOf(level.getServer(), talker, pos, true);
        }
        mic.setTalker(null);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            stopTalkOf(player.getServer(), player.getUUID(), null, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TALKS.clear();
    }
}
