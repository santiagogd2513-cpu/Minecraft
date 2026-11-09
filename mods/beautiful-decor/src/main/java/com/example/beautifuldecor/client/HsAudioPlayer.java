package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.HsComputers;
import com.example.beautifuldecor.browser.audio.AudioPipe;
import com.example.beautifuldecor.browser.audio.PcmSource;
import com.example.beautifuldecor.client.audio.OggSource;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Reproductor de audio del navegador HS (V2.4.0, con transmisión en vivo desde la V2.4.1). Desde la V2.11.0 también lo usan los
 * televisores: {@link #get()} es el del navegador y {@link #create()} da uno propio.
 * <p>
 * Funciona con dos hilos propios además del hilo de Minecraft: uno decodifica (MP3, OGG o WAV) lo que llega por internet o desde
 * "HS Downloads" y va dejando trozos de sonido ya listo en una cola; el otro los manda por OpenAL, el mismo sistema de sonido de
 * Minecraft, con su propia fuente. Así una radio en vivo suena a medida que llega: se espera un colchón de unos segundos antes de
 * empezar, si la red se atrasa se vuelve a llenar el colchón, y si la transmisión se corta se reconecta sola (hasta 5 veces).
 * Los MP3 y WAV se leen sin bajarlos enteros; un OGG se baja completo antes de sonar (y los OGG en vivo no se pueden).
 * <p>
 * El volumen sigue al volumen general y al de "Jukebox/Bloques de notas" de las opciones, y baja con la distancia al computador.
 * Si el jugador se aleja demasiado, cambia de dimensión o el computador se apaga, el sonido se detiene solo.
 * Cualquier fallo queda como un mensaje en la barra del navegador; nunca tumba el juego (V3.0.2: también {@link #tick} está protegido,
 * y el arranque, los errores de OpenAL y el final del sonido quedan anotados en el registro del juego).
 */
public final class HsAudioPlayer {
    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int PLAYING = 2;
    public static final int PAUSED = 3;
    public static final int ERROR = 4;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HsAudioPlayer INSTANCE = new HsAudioPlayer();
    private static final int BUFFERS = 8;
    private static final int PREBUFFER_FILE = 6;
    private static final int PREBUFFER_LIVE = 16;
    private static final double FULL_VOLUME_BLOCKS = 12.0;
    private static final double STOP_BLOCKS = 40.0;

    private volatile int state = IDLE;
    private volatile String title = "";
    private volatile String message = "";
    private volatile float volume = 0.8f;
    private volatile float distanceGain = 1.0f;
    // V4.1.2: alcance propio de cada reproductor. El del navegador sigue con la bajada lineal de siempre (12 -> 40 bloques); los de
    // televisores, anuncios y radios de vehículo usan la curva de AudioFalloff con el alcance que se les ponga con setRange.
    private volatile boolean curved;
    private volatile double fullBlocks = FULL_VOLUME_BLOCKS;
    private volatile double maxBlocks = STOP_BLOCKS;
    private volatile boolean paused;
    private volatile boolean live;
    private volatile long elapsedMs;
    private volatile long loaded;
    private volatile long total = -1;
    private volatile long token;
    private volatile BlockPos anchor;
    private volatile ResourceKey<Level> dimension;
    // V3.5.0: radio de vehículo: el sonido sigue a algo que se mueve (el vehículo) en vez de a un bloque, y pide los títulos.
    private volatile Supplier<Vec3> follow;
    private volatile BooleanSupplier followAlive;
    private volatile boolean icyTitles;
    private volatile String nowPlaying = "";
    private volatile String stationName = "";

    private HsAudioPlayer() {
    }

    public static HsAudioPlayer get() {
        return INSTANCE;
    }

    /** Un reproductor aparte (V2.11.0: cada televisor tiene el suyo, para que no se pisen con el del navegador). */
    public static HsAudioPlayer create() {
        return new HsAudioPlayer();
    }

    // ------------------------------------------------------------------ lo que ve la pantalla

    public int state() {
        return this.state;
    }

    public String title() {
        return this.title;
    }

    public String message() {
        return this.message;
    }

    public float volume() {
        return this.volume;
    }

    public void setVolume(float v) {
        this.volume = Math.max(0f, Math.min(1f, v));
    }

    public long elapsedMs() {
        return this.elapsedMs;
    }

    /** V3.5.0: la canción que suena en la radio en vivo ("" si la emisora no la manda). */
    public String nowPlaying() {
        return this.nowPlaying;
    }

    /** V3.5.0: el nombre que dice tener la emisora ("" si no lo manda). */
    public String stationName() {
        return this.stationName;
    }

    /** Cierto si es una transmisión en vivo (no tiene duración). */
    public boolean live() {
        return this.live;
    }

    /** Avance de la descarga de un OGG (0 a 1), o -1 si no se sabe. */
    public float loadProgress() {
        long t = this.total;
        return t > 0 ? Math.min(1f, this.loaded / (float) t) : -1f;
    }

    // ------------------------------------------------------------------ control

    public void play(String url, Path file, String name, BlockPos computer, ResourceKey<Level> dim) {
        long my;
        synchronized (this) {
            my = ++this.token;
            this.state = LOADING;
            this.title = name == null ? "" : name;
            this.message = "";
            this.paused = false;
            this.live = false;
            this.elapsedMs = 0;
            this.loaded = 0;
            this.total = -1;
            this.anchor = computer;
            this.dimension = dim;
            this.distanceGain = 1.0f;
            this.alErrors = 0;
            this.follow = null;
            this.followAlive = null;
            this.icyTitles = false;
            this.nowPlaying = "";
            this.stationName = "";
        }
        Thread t = new Thread(() -> run(my, url, file), "HS Audio");
        t.setDaemon(true);
        t.start();
    }

    /**
     * V3.5.0: toca una radio en vivo que sigue a algo que se mueve (la radio de un vehículo). {@code where} da su posición en cada tick
     * (null = ya no existe) y {@code alive} dice si tiene que seguir sonando; el volumen baja con la distancia como con el computador.
     * También pide los títulos de lo que suena ({@link #nowPlaying()}).
     */
    public void playFollowing(String url, String name, Supplier<Vec3> where, BooleanSupplier alive, ResourceKey<Level> dim) {
        long my;
        synchronized (this) {
            my = ++this.token;
            this.state = LOADING;
            this.title = name == null ? "" : name;
            this.message = "";
            this.paused = false;
            this.live = false;
            this.elapsedMs = 0;
            this.loaded = 0;
            this.total = -1;
            this.anchor = null;
            this.follow = where;
            this.followAlive = alive;
            this.dimension = dim;
            this.distanceGain = 1.0f;
            this.alErrors = 0;
            this.icyTitles = true;
            this.nowPlaying = "";
            this.stationName = "";
        }
        Thread t = new Thread(() -> run(my, url, null), "HS Radio");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        this.token++;
        this.state = IDLE;
        this.message = "";
        this.paused = false;
    }

    public synchronized void togglePause() {
        if (this.state == PLAYING) {
            this.paused = true;
            this.state = PAUSED;
        } else if (this.state == PAUSED) {
            this.paused = false;
            this.state = PLAYING;
        }
    }

    /** Cada tick del cliente: mantiene el sonido atado al computador (o al televisor). Nunca lanza errores. */
    public void tick(Minecraft minecraft) {
        try {
            tickInner(minecraft);
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] audio: error en el tick; se detiene el sonido", t);
            try {
                stop();
            } catch (Throwable ignored) {
                // nada más que hacer
            }
        }
    }

    private void tickInner(Minecraft minecraft) {
        int s = this.state;
        if (s == IDLE || s == ERROR) {
            return;
        }
        Supplier<Vec3> where = this.follow;
        if (where != null) {
            // V3.5.0: radio de vehículo
            BooleanSupplier alive = this.followAlive;
            Vec3 at = minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != this.dimension ? null : where.get();
            if (at == null || (alive != null && !alive.getAsBoolean())) {
                stop();
                return;
            }
            double d = minecraft.player.position().distanceTo(at);
            if (d > this.maxBlocks) {
                stop();
                return;
            }
            this.distanceGain = distanceGainAt(d);
            return;
        }
        BlockPos pos = this.anchor;
        if (minecraft.level == null || minecraft.player == null || pos == null || minecraft.level.dimension() != this.dimension) {
            stop();
            return;
        }
        double d = minecraft.player.position().distanceTo(Vec3.atCenterOf(pos));
        if (d > this.maxBlocks) {
            stop();
            return;
        }
        if (minecraft.level.isLoaded(pos) && !HsComputers.isOn(minecraft.level.getBlockState(pos))) {
            stop();
            return;
        }
        this.distanceGain = distanceGainAt(d);
    }

    /**
     * V4.1.2: este reproductor se oye al máximo hasta {@code full} bloques y nada a partir de {@code max}, con la curva de
     * {@link com.example.beautifuldecor.ad.AudioFalloff}. Más allá de {@code max} el sonido se detiene solo, sin importar la distancia
     * de renderizado del juego.
     */
    public void setRange(double full, double max) {
        this.fullBlocks = full;
        this.maxBlocks = Math.max(max, full + 1.0);
        this.curved = true;
    }

    private float distanceGainAt(double d) {
        if (this.curved) {
            return com.example.beautifuldecor.ad.AudioFalloff.gain(d, this.fullBlocks, this.maxBlocks);
        }
        return d <= FULL_VOLUME_BLOCKS ? 1.0f
                : (float) Math.max(0.0, 1.0 - (d - FULL_VOLUME_BLOCKS) / (STOP_BLOCKS - FULL_VOLUME_BLOCKS));
    }

    private float gain() {
        float g = this.volume * this.distanceGain;
        try {
            var options = Minecraft.getInstance().options;
            g *= options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(SoundSource.RECORDS);
        } catch (RuntimeException ignored) {
            // si no se puede leer, queda el volumen propio
        }
        return Math.max(0f, Math.min(1f, g));
    }

    private boolean current(long my) {
        return this.token == my;
    }

    // ------------------------------------------------------------------ lo que le falta a AudioPipe

    private AudioPipe.Env env(long my) {
        return new AudioPipe.Env() {
            @Override
            public boolean active() {
                return current(my);
            }

            @Override
            public PcmSource ogg(byte[] data) throws IOException {
                return new OggSource(data);
            }

            @Override
            public String text(String key) {
                return Component.translatable("gui.beautifuldecor.browser.audio." + key).getString();
            }

            @Override
            public void title(String t) {
                if (current(my)) {
                    HsAudioPlayer.this.nowPlaying = t == null ? "" : t;
                }
            }

            @Override
            public void station(String name) {
                if (current(my)) {
                    HsAudioPlayer.this.stationName = name == null ? "" : name;
                }
            }

            @Override
            public void progress(long done, long all) {
                if (current(my)) {
                    HsAudioPlayer.this.loaded = done;
                    HsAudioPlayer.this.total = all;
                }
            }
        };
    }

    // ------------------------------------------------------------------ hilo que manda el sonido a OpenAL

    private void run(long my, String url, Path file) {
        AudioPipe pipe = new AudioPipe(env(my), 1500, this.icyTitles);
        Thread decoder = new Thread(() -> pipe.run(url, file), "HS Audio decoder");
        decoder.setDaemon(true);
        decoder.start();
        int source = 0;
        int[] buffers = new int[BUFFERS];
        int made = 0;
        ByteBuffer direct = null;
        try {
            while (current(my) && !pipe.ready && pipe.error == null) {
                sleep(25);
            }
            if (!current(my)) {
                return;
            }
            if (!pipe.ready) {
                throw asIo(pipe.error);
            }
            int channels = pipe.channels;
            int rate = pipe.rate;
            boolean isLive = pipe.live;
            LOGGER.info("[BeautifulDecor] audio: «{}» {} canal(es), {} Hz{}", this.title, channels, rate, isLive ? ", en vivo" : "");
            int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            int frameBytes = 2 * channels;
            source = AL10.alGenSources();
            if (source == 0) {
                throw new IOException(Component.translatable("gui.beautifuldecor.browser.audio.no_channel").getString());
            }
            for (; made < BUFFERS; made++) {
                buffers[made] = AL10.alGenBuffers();
            }
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcef(source, AL10.AL_GAIN, gain());
            direct = MemoryUtil.memAlloc(AudioPipe.CHUNK);
            alCheck("al preparar la fuente");

            int[] sizes = new int[BUFFERS];
            ArrayDeque<Integer> free = new ArrayDeque<>();
            for (int i = 0; i < BUFFERS; i++) {
                free.add(i);
            }
            int threshold = isLive ? PREBUFFER_LIVE : PREBUFFER_FILE;
            boolean buffering = true;
            boolean started = false;
            boolean pausedAl = false;
            long playedFrames = 0;
            int underruns = 0;
            this.live = isLive;

            while (current(my)) {
                if (this.paused) {
                    if (!pausedAl && started) {
                        AL10.alSourcePause(source);
                        pausedAl = true;
                    }
                    sleep(50);
                    continue;
                }
                if (pausedAl) {
                    AL10.alSourcePlay(source);
                    pausedAl = false;
                }
                AL10.alSourcef(source, AL10.AL_GAIN, gain());

                // buffers que ya sonaron: quedan libres
                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                while (processed-- > 0) {
                    int buf = AL10.alSourceUnqueueBuffers(source);
                    for (int i = 0; i < BUFFERS; i++) {
                        if (buffers[i] == buf) {
                            playedFrames += sizes[i] / frameBytes;
                            sizes[i] = 0;
                            free.add(i);
                            break;
                        }
                    }
                }
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                boolean drained = pipe.queue.isEmpty();
                if (queued == 0 && started && drained && !pipe.eof && pipe.error == null && !buffering) {
                    buffering = true;                           // la red se atrasó: se vuelve a llenar el colchón
                    setPlayingState(my, LOADING);
                }
                if (buffering && (pipe.queue.size() >= threshold || pipe.eof || pipe.error != null)) {
                    buffering = false;
                }
                if (!buffering) {
                    while (!free.isEmpty()) {
                        byte[] data = pipe.queue.poll();
                        if (data == null) {
                            break;
                        }
                        int i = free.poll();
                        direct.clear();
                        direct.put(data, 0, data.length);
                        direct.flip();
                        AL10.alBufferData(buffers[i], format, direct, rate);
                        sizes[i] = data.length;
                        AL10.alSourceQueueBuffers(source, buffers[i]);
                        alCheck("al encolar sonido");
                    }
                    queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                    if (queued == 0 && drained && pipe.queue.isEmpty()) {
                        if (pipe.eof) {
                            break;                              // se terminó el archivo
                        }
                        if (pipe.error != null) {
                            throw asIo(pipe.error);
                        }
                    }
                    if (queued > 0 && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                        AL10.alSourcePlay(source);
                        alCheck("al empezar a sonar");
                        if (!started) {
                            LOGGER.info("[BeautifulDecor] audio: empezó a sonar");
                        } else {
                            underruns++;
                            if (underruns <= 5) {
                                LOGGER.info("[BeautifulDecor] audio: se quedó sin datos y se reanudó ({} veces)", underruns);
                            }
                        }
                        started = true;
                    }
                    if (queued > 0) {
                        setPlayingState(my, PLAYING);
                    }
                }
                if (started) {
                    this.elapsedMs = (long) ((playedFrames + AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET) * rate) * 1000.0 / rate);
                }
                sleep(25);
            }
        } catch (Throwable e) {
            if (current(my)) {
                LOGGER.error("[BeautifulDecor] audio: error en el hilo del sonido", e);
                this.message = friendly(e);
                this.state = ERROR;
            }
        } finally {
            LOGGER.info("[BeautifulDecor] audio: terminó o se detuvo (llegó a {} s)", this.elapsedMs / 1000);
            pipe.abort();
            cleanup(source, buffers, made);
            if (direct != null) {
                MemoryUtil.memFree(direct);
            }
            synchronized (this) {
                if (current(my) && this.state != ERROR) {
                    this.state = IDLE;
                }
            }
        }
    }

    private int alErrors;

    /** Anota (las primeras veces) un error de OpenAL; nunca lanza nada. */
    private void alCheck(String when) {
        try {
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR && this.alErrors++ < 8) {
                LOGGER.warn("[BeautifulDecor] audio: OpenAL dio el error 0x{} {}", Integer.toHexString(err), when);
            }
        } catch (Throwable ignored) {
            // sin OpenAL no hay nada que anotar
        }
    }

    private synchronized void setPlayingState(long my, int wanted) {
        if (current(my) && !this.paused && (this.state == LOADING || this.state == PLAYING)) {
            this.state = wanted;
        }
    }

    private static IOException asIo(Throwable t) {
        if (t instanceof IOException io) {
            return io;
        }
        return new IOException(t == null ? "?" : t.toString(), t);
    }

    private static void cleanup(int source, int[] buffers, int made) {
        try {
            if (source != 0) {
                AL10.alSourceStop(source);
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                for (int i = 0; i < queued; i++) {
                    AL10.alSourceUnqueueBuffers(source);
                }
                AL10.alDeleteSources(source);
            }
            for (int i = 0; i < made; i++) {
                AL10.alDeleteBuffers(buffers[i]);
            }
        } catch (Throwable ignored) {
            // el sonido de Minecraft ya no existe (cerrando el juego): no importa
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String friendly(Throwable e) {
        String reason;
        if (e instanceof UnknownHostException) {
            reason = Component.translatable("gui.beautifuldecor.browser.audio.no_server").getString();
        } else if (e instanceof SocketTimeoutException) {
            reason = Component.translatable("gui.beautifuldecor.browser.audio.timeout").getString();
        } else if (e instanceof OutOfMemoryError) {
            reason = Component.translatable("gui.beautifuldecor.browser.audio.too_big").getString();
        } else {
            String m = e.getMessage();
            reason = m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
        }
        return Component.translatable("gui.beautifuldecor.browser.audio.error", reason).getString();
    }
}
