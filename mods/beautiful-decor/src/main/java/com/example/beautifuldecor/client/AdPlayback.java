package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.ad.AdBrands;
import com.example.beautifuldecor.block.entity.AdScreenBlockEntity;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * V4.1.0: reproduce los anuncios en las pantallas de anuncios cercanas. Usa el mismo reproductor de los televisores
 * ({@link HsVideoPlayer}: decodifica el H.264, manda el AAC por {@link HsAudioPlayer}) con los videos que trae el mod
 * ("ads/&lt;marca&gt;.mp4", que se copian una vez a la carpeta "beautifuldecor-ads" del juego porque el reproductor lee archivos).
 * <p>
 * Cada pantalla encendida y cercana (hasta 26 + su tamaño en bloques; se suelta pasados 40) tiene su propio reproductor, con un
 * máximo de dos a la vez (las más cercanas). Al terminar un anuncio pasa al de la siguiente marca, en bucle; cada pantalla empieza
 * por una marca distinta según su posición. Las pantallas lejanas muestran el póster de cada marca (lo dibuja el renderizador).
 * <p>
 * Todo se llama desde el hilo principal del cliente y nada de lo que pase aquí puede cerrar el juego: si algo falla se anota en
 * el registro, la pantalla muestra el póster y se reintenta con el siguiente anuncio unos segundos después.
 */
public final class AdPlayback {
    private static final double STOP_BLOCKS = 40.0;
    private static final int MAX_ACTIVE = 2;
    private static final int COOLDOWN_TICKS = 100;
    private static final int RETRY_TICKS = 160;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Lo que hay en marcha en una pantalla. */
    public static final class Session {
        public final HsVideoPlayer video;
        /** Marca que toca ahora (número en {@link AdBrands#BRANDS}). */
        public int index;
        /** Tick en que algo falló, o -1. */
        long failedAt = -1L;

        Session(HsVideoPlayer video, int index) {
            this.video = video;
            this.index = index;
        }
    }

    private static final List<AdScreenBlockEntity> SCREENS = new CopyOnWriteArrayList<>();
    private static final Map<AdScreenBlockEntity, Session> SESSIONS = new IdentityHashMap<>();
    private static final Set<String> EXTRACTED = new HashSet<>();
    private static int cooldown;
    private static long ticks;

    private AdPlayback() {
    }

    public static void register(AdScreenBlockEntity screen) {
        if (!SCREENS.contains(screen)) {
            SCREENS.add(screen);
        }
    }

    public static void unregister(AdScreenBlockEntity screen) {
        SCREENS.remove(screen);
    }

    /** Lo que se reproduce en esa pantalla, o null si está lejos o apagada. */
    public static Session sessionOf(AdScreenBlockEntity screen) {
        return SESSIONS.get(screen);
    }

    /** Detiene todo (al salir del mundo). */
    public static void reset() {
        for (Session s : SESSIONS.values()) {
            release(s);
        }
        SESSIONS.clear();
        SCREENS.clear();
        cooldown = 0;
    }

    /** Número de marca por el que empieza una pantalla. */
    public static int startIndexOf(AdScreenBlockEntity screen) {
        BlockPos p = screen.getBlockPos();
        return AdBrands.startIndex(p.getX(), p.getY(), p.getZ());
    }

    // ------------------------------------------------------------------ cada tick del cliente

    /** Cada tick del cliente. Nunca lanza errores. */
    public static void tick(Minecraft mc) {
        ticks++;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        try {
            tickInner(mc);
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] pantallas de anuncios: error en la gestión; se sueltan todas", t);
            try {
                reset();
            } catch (Throwable ignored) {
                SESSIONS.clear();
            }
            cooldown = COOLDOWN_TICKS;
        }
    }

    private record Candidate(AdScreenBlockEntity screen, double distance) {
    }

    private static void tickInner(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!SESSIONS.isEmpty() || !SCREENS.isEmpty()) {
                reset();
            }
            return;
        }
        SCREENS.removeIf(s -> s.isRemoved() || s.getLevel() != mc.level);
        if (SCREENS.isEmpty() && SESSIONS.isEmpty()) {
            return;
        }
        Vec3 me = mc.player.position();
        List<Candidate> want = new ArrayList<>();
        for (AdScreenBlockEntity screen : SCREENS) {
            if (!screen.isOn()) {
                continue;
            }
            double d = me.distanceTo(screen.center());
            double limit = SESSIONS.containsKey(screen) ? STOP_BLOCKS : 26.0 + screen.sizeBlocks();
            if (d <= limit) {
                want.add(new Candidate(screen, d));
            }
        }
        want.sort(Comparator.comparingDouble(Candidate::distance));
        List<AdScreenBlockEntity> keep = new ArrayList<>();
        for (int i = 0; i < want.size() && i < MAX_ACTIVE; i++) {
            keep.add(want.get(i).screen());
        }

        for (Iterator<Map.Entry<AdScreenBlockEntity, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<AdScreenBlockEntity, Session> e = it.next();
            if (!keep.contains(e.getKey())) {
                release(e.getValue());
                it.remove();
            }
        }
        for (AdScreenBlockEntity screen : keep) {
            Session s = SESSIONS.get(screen);
            if (s == null) {
                s = new Session(HsVideoPlayer.create(), startIndexOf(screen));
                SESSIONS.put(screen, s);
                playCurrent(mc, screen, s);
            }
        }
        for (Map.Entry<AdScreenBlockEntity, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
            advance(mc, e.getKey(), e.getValue());
        }
    }

    /** Actualiza el reproductor de una pantalla y pasa al siguiente anuncio cuando termina o falla. */
    private static void advance(Minecraft mc, AdScreenBlockEntity screen, Session s) {
        try {
            s.video.audio().tick(mc);                       // el volumen baja con la distancia
            s.video.update();
            int st = s.video.state();
            if (s.failedAt >= 0L) {
                if (ticks - s.failedAt >= RETRY_TICKS) {
                    s.index = AdBrands.next(s.index);
                    playCurrent(mc, screen, s);
                }
            } else if (st == HsVideoPlayer.ENDED) {
                s.index = AdBrands.next(s.index);
                playCurrent(mc, screen, s);
            } else if (st == HsVideoPlayer.ERROR) {
                LOGGER.warn("[BeautifulDecor] anuncio '{}' falló: {}", AdBrands.brand(s.index), s.video.message());
                s.failedAt = ticks;
            }
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] pantalla de anuncios en {}: falló el video", screen.getBlockPos(), t);
            s.failedAt = ticks;
        }
    }

    private static void playCurrent(Minecraft mc, AdScreenBlockEntity screen, Session s) {
        s.failedAt = -1L;
        try {
            String brand = AdBrands.brand(s.index);
            Path file = extract(mc, brand);
            BlockPos anchor = BlockPos.containing(screen.center());
            ResourceKey<Level> dim = mc.level.dimension();
            s.video.play(null, file, brand + ".mp4", anchor, dim);
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] pantalla de anuncios en {}: no se pudo empezar el anuncio", screen.getBlockPos(), t);
            s.failedAt = ticks;
        }
    }

    private static void release(Session s) {
        if (s != null) {
            try {
                s.video.stop();
                s.video.update();                           // suelta la textura
            } catch (Throwable t) {
                LOGGER.error("[BeautifulDecor] pantallas de anuncios: error al soltar el reproductor", t);
            }
        }
    }

    // ------------------------------------------------------------------ los videos del mod

    /** Copia el video del mod a la carpeta del juego (una sola vez por marca y por sesión, y solo si cambió) y devuelve su ruta. */
    private static Path extract(Minecraft mc, String brand) throws IOException {
        Path dir = mc.gameDirectory.toPath().resolve("beautifuldecor-ads");
        Path file = dir.resolve(brand + ".mp4");
        if (EXTRACTED.contains(brand) && Files.isRegularFile(file)) {
            return file;
        }
        Files.createDirectories(dir);
        ResourceLocation rl = new ResourceLocation(BeautifulDecor.MOD_ID, AdBrands.videoPath(brand));
        byte[] data;
        try (InputStream in = mc.getResourceManager().open(rl)) {
            data = in.readAllBytes();
        }
        if (!Files.isRegularFile(file) || Files.size(file) != data.length) {
            Path tmp = dir.resolve(brand + ".mp4.tmp");
            Files.write(tmp, data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        EXTRACTED.add(brand);
        return file;
    }
}
