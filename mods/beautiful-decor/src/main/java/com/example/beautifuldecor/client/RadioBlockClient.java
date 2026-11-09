package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.RadioTableBlock;
import com.example.beautifuldecor.block.entity.RadioSpeakerBlockEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.RadioBlockPacket;
import com.example.beautifuldecor.network.RadioStatePacket;
import com.example.beautifuldecor.radio.RadioEntry;
import com.example.beautifuldecor.radio.RadioPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * V4.2.0: radios de mesa y bafles en este cliente. El servidor manda el estado de cada radio (emisora, encendido, volumen); aquí se
 * guarda y se hace sonar. Cada radio abre UNA sola conexión de audio por cliente, y el sonido sale del emisor más cercano a ti de su
 * grupo (la radio o cualquiera de sus bafles): así no hay eco por tener la misma emisora en vivo desfasada, y si te alejas de la radio
 * pero hay un bafle cerca, sigue sonando lo mismo desde el bafle. Como mucho {@link RadioPicker#MAX_PLAYING} grupos a la vez.
 */
public final class RadioBlockClient {
    private static final long RETRY_MS = 15000;
    /** Más lejos que esto del emisor más cercano no se abre la conexión (el reproductor se calla a 30). */
    private static final double HEAR = 34.0;

    private static final Map<Long, RadioEntry> STATES = new HashMap<>();

    private static final class Playing {
        final HsAudioPlayer player = HsAudioPlayer.create();
        {
            player.setRange(6.0, 30.0);
        }
        String url = "";
        long retryAt;
        volatile Vec3 where;
    }

    private static final Map<Long, Playing> PLAYING = new HashMap<>();

    private RadioBlockClient() {
    }

    /** Llega del servidor. */
    public static void onState(RadioStatePacket packet) {
        if (packet.full) {
            STATES.clear();
        }
        for (RadioStatePacket.Item it : packet.sets) {
            STATES.put(it.pos(), new RadioEntry(it.url(), it.name(), it.on(), it.volume()));
        }
        for (long r : packet.removes) {
            STATES.remove(r);
        }
    }

    /** El estado que tiene el cliente de la radio en ese bloque (nunca null: sin datos es una radio apagada). */
    public static RadioEntry stateOf(BlockPos pos) {
        RadioEntry e = STATES.get(pos.asLong());
        return e == null ? new RadioEntry() : e;
    }

    public static HsAudioPlayer playerFor(BlockPos pos) {
        Playing p = PLAYING.get(pos.asLong());
        return p == null ? null : p.player;
    }

    /** Cada tick del cliente. Nunca lanza errores. */
    public static void tick(Minecraft minecraft) {
        try {
            tickInner(minecraft);
        } catch (Throwable t) {
            reset();
        }
    }

    private static void tickInner(Minecraft minecraft) {
        LocalPlayer me = minecraft.player;
        ClientLevel level = minecraft.level;
        if (me == null || level == null) {
            reset();
            return;
        }
        // 1) los emisores de cada radio encendida: la propia radio (si su chunk está cargado) y sus bafles
        Map<Long, List<Vec3>> groups = new HashMap<>();
        for (Map.Entry<Long, RadioEntry> e : STATES.entrySet()) {
            if (!e.getValue().playing()) {
                continue;
            }
            BlockPos p = BlockPos.of(e.getKey());
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof RadioTableBlock) {
                groups.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(Vec3.atCenterOf(p));
            }
        }
        for (RadioSpeakerBlockEntity be : RadioSpeakerBlockEntity.CLIENT) {
            BlockPos radio = be.getRadio();
            if (radio == null || be.isRemoved()) {
                continue;
            }
            RadioEntry st = STATES.get(radio.asLong());
            if (st != null && st.playing()) {
                groups.computeIfAbsent(radio.asLong(), k -> new ArrayList<>()).add(Vec3.atCenterOf(be.getBlockPos()));
            }
        }
        // 2) de cada grupo, el emisor más cercano a ti; y cuáles grupos suenan
        List<Long> keys = new ArrayList<>();
        Map<Long, Vec3> nearest = new HashMap<>();
        List<RadioPicker.Candidate> candidates = new ArrayList<>();
        Vec3 eye = me.position();
        for (Map.Entry<Long, List<Vec3>> g : groups.entrySet()) {
            Vec3 best = null;
            double bestD = Double.MAX_VALUE;
            for (Vec3 v : g.getValue()) {
                double d = v.distanceTo(eye);
                if (d < bestD) {
                    bestD = d;
                    best = v;
                }
            }
            if (best != null && bestD <= HEAR) {
                candidates.add(new RadioPicker.Candidate(keys.size(), bestD, false));
                keys.add(g.getKey());
                nearest.put(g.getKey(), best);
            }
        }
        List<Long> chosen = new ArrayList<>();
        for (int idx : RadioPicker.pick(candidates, RadioPicker.MAX_PLAYING)) {
            chosen.add(keys.get(idx));
        }
        for (Iterator<Map.Entry<Long, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Playing> e = it.next();
            if (!chosen.contains(e.getKey())) {
                e.getValue().player.stop();
                it.remove();
            }
        }
        long now = System.currentTimeMillis();
        for (long key : chosen) {
            RadioEntry st = STATES.get(key);
            Playing p = PLAYING.computeIfAbsent(key, k -> new Playing());
            p.where = nearest.get(key);
            int state = p.player.state();
            boolean changed = !st.url().equals(p.url);
            boolean failedRetry = state == HsAudioPlayer.ERROR && now >= p.retryAt;
            boolean stopped = state == HsAudioPlayer.IDLE && !changed;
            if (changed || failedRetry || stopped) {
                p.url = st.url();
                p.retryAt = now + RETRY_MS;
                final Playing playing = p;
                final long k = key;
                p.player.playFollowing(st.url(), st.name(), () -> playing.where,
                        () -> {
                            RadioEntry cur = STATES.get(k);
                            return cur != null && cur.playing();
                        }, level.dimension());
            }
            p.player.setVolume(st.volume() / 100.0f);
        }
        for (Playing p : PLAYING.values()) {
            p.player.tick(minecraft);
        }
    }

    /** Calla todo y olvida el estado (al salir del mundo). */
    public static void reset() {
        for (Playing p : PLAYING.values()) {
            try {
                p.player.stop();
            } catch (Throwable ignored) {
                // nada más que hacer
            }
        }
        PLAYING.clear();
        STATES.clear();
    }

    /** La radio de mesa vista por el menú de la radio. */
    public static final class TableSource implements RadioSource {
        private final BlockPos pos;

        public TableSource(BlockPos pos) {
            this.pos = pos.immutable();
        }

        @Override
        public Component label() {
            return Component.translatable("block.beautifuldecor.radio_table");
        }

        @Override
        public boolean valid(LocalPlayer player) {
            return player.level().getBlockState(this.pos).getBlock() instanceof RadioTableBlock
                    && player.distanceToSqr(Vec3.atCenterOf(this.pos)) <= 8.0 * 8.0;
        }

        @Override
        public boolean isOn() {
            return stateOf(this.pos).on();
        }

        @Override
        public String url() {
            return stateOf(this.pos).url();
        }

        @Override
        public String stationName() {
            return stateOf(this.pos).name();
        }

        @Override
        public int volume() {
            return stateOf(this.pos).volume();
        }

        @Override
        public boolean shouldPlay() {
            return stateOf(this.pos).playing();
        }

        @Override
        public HsAudioPlayer player() {
            return playerFor(this.pos);
        }

        @Override
        public void select(String url, String name) {
            ModMessages.sendToServer(RadioBlockPacket.select(this.pos, url, name));
        }

        @Override
        public void power(boolean on) {
            ModMessages.sendToServer(RadioBlockPacket.power(this.pos, on));
        }

        @Override
        public void setVolume(int volume) {
            ModMessages.sendToServer(RadioBlockPacket.volume(this.pos, volume));
        }
    }
}
