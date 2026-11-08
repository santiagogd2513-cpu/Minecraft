package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.VehicleEntity;
import com.example.beautifuldecor.radio.RadioPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * V3.5.0: hace sonar en este cliente las radios de los vehículos cercanos, con el reproductor de audio del mod (HsAudioPlayer). Cada
 * radio sale del vehículo: el volumen baja con la distancia y se calla lejos, si se apaga, si cambia de emisora o si el vehículo se queda
 * sin nadie. Como mucho {@link RadioPicker#MAX_PLAYING} a la vez (la propia primero). Si una emisora falla, se reintenta cada tanto.
 */
public final class VehicleRadioClient {
    private static final long RETRY_MS = 15000;

    private static final class Playing {
        final HsAudioPlayer player = HsAudioPlayer.create();
        String url = "";
        long retryAt;
    }

    private static final Map<Integer, Playing> PLAYING = new HashMap<>();

    private VehicleRadioClient() {
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
        Entity ridden = me.getVehicle();
        List<RadioPicker.Candidate> candidates = new ArrayList<>();
        Map<Integer, VehicleEntity> byId = new HashMap<>();
        for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, me.getBoundingBox().inflate(RadioPicker.RANGE))) {
            if (v.radioShouldPlay()) {
                candidates.add(new RadioPicker.Candidate(v.getId(), v.distanceTo(me), v == ridden));
                byId.put(v.getId(), v);
            }
        }
        List<Integer> chosen = RadioPicker.pick(candidates, RadioPicker.MAX_PLAYING);
        // las que ya no tocan: se callan
        for (Iterator<Map.Entry<Integer, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Playing> e = it.next();
            if (!chosen.contains(e.getKey())) {
                e.getValue().player.stop();
                it.remove();
            }
        }
        long now = System.currentTimeMillis();
        for (int id : chosen) {
            VehicleEntity v = byId.get(id);
            Playing p = PLAYING.computeIfAbsent(id, k -> new Playing());
            String url = v.getRadioUrl();
            int state = p.player.state();
            boolean changed = !url.equals(p.url);
            boolean failedRetry = state == HsAudioPlayer.ERROR && now >= p.retryAt;
            boolean stopped = state == HsAudioPlayer.IDLE && !changed;
            if (changed || failedRetry || stopped) {
                p.url = url;
                p.retryAt = now + RETRY_MS;
                final VehicleEntity vehicle = v;
                p.player.playFollowing(url, v.getRadioName(), () -> vehicle.isRemoved() ? null : vehicle.position(),
                        vehicle::radioShouldPlay, level.dimension());
            }
            p.player.setVolume(v.getRadioVolume() / 100.0f);
        }
        for (Playing p : PLAYING.values()) {
            p.player.tick(minecraft);
        }
    }

    /** El reproductor de la radio de ese vehículo en este cliente, o null si no está sonando aquí (para el menú). */
    public static HsAudioPlayer playerFor(Entity vehicle) {
        Playing p = PLAYING.get(vehicle.getId());
        return p == null ? null : p.player;
    }

    /** Calla todo (al salir del mundo). */
    public static void reset() {
        for (Playing p : PLAYING.values()) {
            try {
                p.player.stop();
            } catch (Throwable ignored) {
                // nada más que hacer
            }
        }
        PLAYING.clear();
    }
}
