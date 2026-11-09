package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.entity.TelevisionBlockEntity;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Reproducción de video en los televisores (V2.11.0). Es el mismo sistema del navegador HS ({@link HsVideoPlayer}: baja el MP4,
 * decodifica el H.264 y manda el sonido AAC por {@link HsAudioPlayer}), pero en vez de una pantalla del juego el cuadro se dibuja
 * en la pantalla del bloque (ver {@link TelevisionRenderer}).
 * <p>
 * Cada jugador baja y reproduce el video por su cuenta: el servidor solo guarda el enlace y si el televisor está reproduciendo.
 * Cada televisor que reproduce y está cerca (hasta 32 bloques; se suelta pasados 40) tiene su propio reproductor, con un máximo
 * de dos a la vez (los más cercanos) para no cargar el teléfono. Si te alejas, el video se detiene y al volver empieza otra vez.
 * <p>
 * Todo se llama desde el hilo principal del cliente.
 * <p>
 * V3.0.2: nada de lo que pase aquí puede cerrar el juego. Si un televisor falla (al empezar, al actualizar o al dibujarse), su
 * reproductor se suelta, el error queda en el registro del juego y la pantalla del televisor muestra el mensaje; el televisor no se
 * vuelve a intentar hasta que alguien vuelva a pulsar "Reproducir" o cambie el enlace. Si falla la gestión en sí, se suelta todo y se
 * espera unos segundos antes de volver a mirar.
 */
public final class TvPlayback {
    private static final double START_BLOCKS = 32.0;
    private static final double STOP_BLOCKS = 40.0;
    private static final int MAX_ACTIVE = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks que se espera tras un fallo de la gestión antes de volver a mirar los televisores (20 ticks = 1 segundo). */
    private static final int COOLDOWN_TICKS = 100;

    /** Lo que hay en marcha en un televisor: su reproductor (o el error si ni siquiera se pudo empezar). */
    public static final class Session {
        /** El reproductor; null si el enlace no sirvió (entonces {@link #error} dice por qué). */
        public final HsVideoPlayer video;
        /** Un error que no viene del reproductor (por ejemplo, una ruta de archivo que no existe), o null. */
        public final String error;
        final int token;
        final String path;
        /** Cierto si ni siquiera se pudo dibujar el mensaje de error: ya no se dibuja nada de este televisor. */
        final boolean crashed;

        Session(HsVideoPlayer video, String error, int token, String path) {
            this(video, error, token, path, false);
        }

        Session(HsVideoPlayer video, String error, int token, String path, boolean crashed) {
            this.video = video;
            this.error = error;
            this.token = token;
            this.path = path;
            this.crashed = crashed;
        }

    }

    private record Candidate(TelevisionBlockEntity tv, double distance) {
    }

    private static final List<TelevisionBlockEntity> TVS = new CopyOnWriteArrayList<>();
    private static final Map<TelevisionBlockEntity, Session> SESSIONS = new IdentityHashMap<>();
    private static int cooldown;

    private TvPlayback() {
    }

    // ------------------------------------------------------------------ registro (lo llama el block entity en el cliente)

    public static void register(TelevisionBlockEntity tv) {
        if (!TVS.contains(tv)) {
            TVS.add(tv);
        }
    }

    public static void unregister(TelevisionBlockEntity tv) {
        TVS.remove(tv);
    }

    /** Lo que está sonando o cargando en ese televisor, o null. */
    public static Session sessionOf(TelevisionBlockEntity tv) {
        return SESSIONS.get(tv);
    }

    /** Detiene todo (al salir del mundo). */
    public static void reset() {
        for (Session s : SESSIONS.values()) {
            release(s);
        }
        SESSIONS.clear();
        TVS.clear();
        cooldown = 0;
    }

    // ------------------------------------------------------------------ cada tick del cliente

    /** Cada tick del cliente. Nunca lanza errores. */
    public static void tick(Minecraft mc) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        try {
            tickInner(mc);
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] televisores: error en la gestión de los videos; se sueltan todos", t);
            cooldown = COOLDOWN_TICKS;
            try {
                reset();
                cooldown = COOLDOWN_TICKS;
            } catch (Throwable ignored) {
                SESSIONS.clear();
            }
        }
    }

    private static void tickInner(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!SESSIONS.isEmpty() || !TVS.isEmpty()) {
                reset();
            }
            return;
        }
        TVS.removeIf(tv -> tv.isRemoved() || tv.getLevel() != mc.level);
        if (TVS.isEmpty() && SESSIONS.isEmpty()) {
            return;
        }
        Vec3 me = mc.player.position();
        List<Candidate> want = new ArrayList<>();
        for (TelevisionBlockEntity tv : TVS) {
            if (!tv.isPlaying() || tv.getVideoPath().trim().isEmpty()) {
                continue;
            }
            double d = me.distanceTo(Vec3.atCenterOf(tv.getBlockPos()));
            double limit = SESSIONS.containsKey(tv) ? STOP_BLOCKS : START_BLOCKS;
            if (d <= limit) {
                want.add(new Candidate(tv, d));
            }
        }
        want.sort(Comparator.comparingDouble(Candidate::distance));
        List<TelevisionBlockEntity> keep = new ArrayList<>();
        for (int i = 0; i < want.size() && i < MAX_ACTIVE; i++) {
            keep.add(want.get(i).tv());
        }

        for (Iterator<Map.Entry<TelevisionBlockEntity, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<TelevisionBlockEntity, Session> e = it.next();
            if (!keep.contains(e.getKey())) {
                release(e.getValue());
                it.remove();
            }
        }
        for (TelevisionBlockEntity tv : keep) {
            Session s = SESSIONS.get(tv);
            if (s != null && (s.token != tv.getStartCount() || !s.path.equals(tv.getVideoPath().trim()))) {
                release(s);                                 // otro "Reproducir" o un enlace nuevo: se empieza de cero
                SESSIONS.remove(tv);
                s = null;
            }
            if (s == null) {
                SESSIONS.put(tv, startSafely(mc, tv));
            }
        }
        for (Map.Entry<TelevisionBlockEntity, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
            Session s = e.getValue();
            if (s.video != null) {
                try {
                    s.video.audio().tick(mc);               // el volumen baja con la distancia y se calla si el televisor se apaga
                    s.video.update();                       // si no se dibuja, igual se vacía la cola de cuadros
                } catch (Throwable t) {
                    fail(e.getKey(), t);
                }
            }
        }
    }

    /**
     * Un televisor falló (al actualizar o al dibujarse): se suelta su reproductor y queda un mensaje de error en su pantalla.
     * Si ya era un mensaje de error y tampoco se pudo dibujar, el televisor se deja en blanco. Nunca lanza errores.
     */
    static void fail(TelevisionBlockEntity tv, Throwable t) {
        try {
            Session old = SESSIONS.get(tv);
            if (old == null) {
                return;
            }
            if (old.video == null) {
                if (!old.crashed) {
                    LOGGER.error("[BeautifulDecor] televisor: no se pudo dibujar el mensaje de error; la pantalla queda en blanco", t);
                    SESSIONS.put(tv, new Session(null, old.error, old.token, old.path, true));
                }
                return;
            }
            LOGGER.error("[BeautifulDecor] televisor en {}: falló el video; se suelta el reproductor", tv.getBlockPos(), t);
            release(old);
            String reason = t.getMessage() == null || t.getMessage().isBlank() ? t.getClass().getSimpleName() : t.getMessage();
            SESSIONS.put(tv, new Session(null, Component.translatable("gui.beautifuldecor.browser.video.error", reason).getString(),
                    old.token, old.path));
        } catch (Throwable ignored) {
            // lo último que falta es que el aviso del fallo tumbe el juego
        }
    }

    // ------------------------------------------------------------------ empezar y soltar

    private static Session startSafely(Minecraft mc, TelevisionBlockEntity tv) {
        try {
            return start(mc, tv);
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] televisor en {}: no se pudo empezar el video", tv.getBlockPos(), t);
            String reason = t.getMessage() == null || t.getMessage().isBlank() ? t.getClass().getSimpleName() : t.getMessage();
            return new Session(null, Component.translatable("gui.beautifuldecor.browser.video.error", reason).getString(),
                    tv.getStartCount(), tv.getVideoPath().trim());
        }
    }

    private static Session start(Minecraft mc, TelevisionBlockEntity tv) {
        String path = tv.getVideoPath().trim();
        int token = tv.getStartCount();
        BlockPos pos = tv.getBlockPos();
        ResourceKey<Level> dim = mc.level.dimension();
        HsVideoPlayer video = HsVideoPlayer.create();
        video.audio().setRange(6.0, 32.0);                  // V4.1.2: el sonido baja con la distancia y se corta a 32 bloques
        if (isWeb(path)) {
            video.play(path, null, nameOf(path), pos, dim);
            return new Session(video, null, token, path);
        }
        Path file = localFile(path);
        if (file != null) {
            video.play(null, file, String.valueOf(file.getFileName()), pos, dim);
            return new Session(video, null, token, path);
        }
        return new Session(null, Component.translatable("gui.beautifuldecor.tv.not_found").getString(), token, path);
    }

    private static void release(Session s) {
        if (s != null && s.video != null) {
            try {
                s.video.stop();
                s.video.update();                           // suelta la textura
            } catch (Throwable t) {
                LOGGER.error("[BeautifulDecor] televisor: error al soltar el reproductor", t);
            }
        }
    }

    // ------------------------------------------------------------------ enlaces y rutas

    private static boolean isWeb(String path) {
        return path.regionMatches(true, 0, "http://", 0, 7) || path.regionMatches(true, 0, "https://", 0, 8);
    }

    /** La ruta de un archivo de este equipo ("/storage/emulated/0/Movies/video.mp4", "C:/Videos/video.mp4" o file:///...), o null. */
    private static Path localFile(String text) {
        String p = text;
        if (p.regionMatches(true, 0, "file://", 0, 7)) {
            p = p.substring(7);
            if (p.matches("/[A-Za-z]:.*")) {
                p = p.substring(1);                         // file:///C:/...
            }
        }
        try {
            Path path = Paths.get(p);
            return Files.isRegularFile(path) ? path : null;
        } catch (InvalidPathException | SecurityException e) {
            return null;
        }
    }

    private static String nameOf(String url) {
        String u = url;
        int cut = u.indexOf('?');
        if (cut >= 0) {
            u = u.substring(0, cut);
        }
        cut = u.indexOf('#');
        if (cut >= 0) {
            u = u.substring(0, cut);
        }
        int slash = u.lastIndexOf('/');
        String name = slash >= 0 ? u.substring(slash + 1) : u;
        return name.isEmpty() ? "TV" : name;
    }
}
