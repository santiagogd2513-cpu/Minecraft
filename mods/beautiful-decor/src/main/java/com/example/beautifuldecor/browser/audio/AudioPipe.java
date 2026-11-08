package com.example.beautifuldecor.browser.audio;

import com.example.beautifuldecor.browser.Http;
import com.example.beautifuldecor.browser.Url;
import com.example.beautifuldecor.browser.video.Mp4AacSource;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Del archivo o la transmisión en vivo a trozos de sonido ya decodificado (PCM de 16 bits), sin tocar nada de Minecraft.
 * Un hilo llama a {@link #run} y va dejando trozos en {@link #queue}; otro (el que manda el sonido a OpenAL) los saca.
 * Reconoce el formato por los primeros bytes (OGG, WAV o MP3), lee MP3 y WAV a medida que llegan, baja un OGG completo antes de
 * decodificarlo, y si una transmisión en vivo se corta la vuelve a abrir (hasta {@link #MAX_RECONNECTS} veces seguidas sin sonido).
 */
public final class AudioPipe {
    /** Lo que esta pieza necesita de afuera: cuándo parar, el decodificador de OGG (usa LWJGL) y los textos de los errores. */
    public interface Env {
        /** Falso cuando hay que dejar todo (se paró o empezó otra pista). */
        boolean active();

        PcmSource ogg(byte[] data) throws IOException;

        /** Texto de error: format, format_changed, lost, live_ogg o too_big. */
        String text(String key);

        void progress(long loaded, long total);

        /** V3.5.0: la canción que está sonando en una radio en vivo (solo si se pidieron los títulos). */
        default void title(String title) {
        }

        /** V3.5.0: el nombre que la emisora dice tener (cabecera icy-name), si lo manda. */
        default void station(String name) {
        }
    }

    public static final int CHUNK = 32768;
    public static final int QUEUE_CHUNKS = 48;
    public static final int MAX_RECONNECTS = 5;
    public static final long MAX_OGG_BYTES = 96L << 20;

    public final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CHUNKS);
    public volatile boolean ready;
    public volatile int channels;
    public volatile int rate;
    public volatile boolean live;
    public volatile boolean eof;
    public volatile Throwable error;
    private volatile Closeable connection;

    private final Env env;
    private final long retryDelayMs;
    /** V3.5.0: pedirle a la radio en vivo el título de lo que suena (Icy-MetaData) y quitarlo de la transmisión (IcyStream). */
    private final boolean icyTitles;

    private static final class Opened {
        final PcmSource source;
        final Closeable connection;
        final boolean live;

        Opened(PcmSource source, Closeable connection, boolean live) {
            this.source = source;
            this.connection = connection;
            this.live = live;
        }
    }

    public AudioPipe(Env env, long retryDelayMs) {
        this(env, retryDelayMs, false);
    }

    /** V3.5.0: con {@code icyTitles} se piden y se avisan los títulos de una radio en vivo (la radio de los vehículos). */
    public AudioPipe(Env env, long retryDelayMs, boolean icyTitles) {
        this.env = env;
        this.retryDelayMs = retryDelayMs;
        this.icyTitles = icyTitles;
    }

    /** Corta la conexión que esté abierta (para que un hilo bloqueado leyendo de la red salga ya). */
    public void abort() {
        Closeable c = this.connection;
        if (c != null) {
            try {
                c.close();
            } catch (IOException | RuntimeException ignored) {
                // ya estaba cerrada
            }
        }
    }

    /** Lee y decodifica hasta que se acaba, falla o {@link Env#active()} da falso. Se llama en un hilo propio. */
    public void run(String url, Path file) {
        int reconnects = 0;
        try {
            while (this.env.active()) {
                Opened opened = null;
                boolean gotData = false;
                try {
                    opened = open(url, file);
                    PcmSource src = opened.source;
                    if (!this.ready) {
                        this.live = opened.live;
                        this.channels = src.channels();
                        this.rate = src.sampleRate();
                        this.ready = true;
                    } else if (this.channels != src.channels() || this.rate != src.sampleRate()) {
                        throw new IOException(this.env.text("format_changed"));
                    }
                    int frameBytes = 2 * this.channels;
                    byte[] chunk = new byte[CHUNK];
                    int n;
                    while (this.env.active() && (n = src.read(chunk)) > 0) {
                        n -= n % frameBytes;
                        if (n <= 0) {
                            continue;
                        }
                        gotData = true;
                        if (!put(Arrays.copyOf(chunk, n))) {
                            return;
                        }
                    }
                } catch (IOException e) {
                    if (!this.ready || !this.live || file != null) {
                        throw e;
                    }
                    // una transmisión en vivo se cortó: se intenta reconectar
                } finally {
                    if (opened != null) {
                        try {
                            opened.source.close();
                        } catch (IOException | RuntimeException ignored) {
                            // nada que hacer
                        }
                        try {
                            opened.connection.close();
                        } catch (IOException | RuntimeException ignored) {
                            // nada que hacer
                        }
                    }
                    this.connection = null;
                }
                if (!this.env.active() || !this.live || file != null) {
                    break;
                }
                reconnects = gotData ? 1 : reconnects + 1;
                if (reconnects > MAX_RECONNECTS) {
                    throw new IOException(this.env.text("lost"));
                }
                try {
                    Thread.sleep(this.retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            this.eof = true;
        } catch (Throwable t) {
            this.error = t;
        }
    }

    private boolean put(byte[] data) {
        try {
            while (this.env.active()) {
                if (this.queue.offer(data, 100, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private Opened open(String url, Path file) throws IOException {
        if (file != null) {
            byte[] head = new byte[12];
            int got;
            try (InputStream probe = Files.newInputStream(file)) {
                got = probe.readNBytes(head, 0, head.length);
            }
            if (got >= 8 && Mp4AacSource.looksLikeMp4(head)) {
                return new Opened(Mp4AacSource.open(file), () -> { }, false);       // sonido de un video MP4
            }
            long size = Files.size(file);
            InputStream in = new BufferedInputStream(Files.newInputStream(file), 65536);
            try {
                return build(in, in, size, false);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw e;
            }
        }
        return openUrl(url, 0);
    }

    private Opened openUrl(String url, int depth) throws IOException {
        Http.Response r = this.icyTitles ? Http.request("GET", url, null, Map.of("Icy-MetaData", "1"), null) : Http.get(url, null);
        this.connection = r;
        if (this.icyTitles && depth < 2 && r.status < 400
                && (Url.isPlaylistMime(r.mime) || Url.isPlaylistName(Url.fileName(r.url, r.disposition, null)))) {
            // V3.5.0: muchas emisoras dan una lista .pls / .m3u: se toma la primera dirección (como el navegador HS)
            String target;
            try {
                byte[] data = Http.readLimited(r.body, 65536, new boolean[1]);
                target = playlistTarget(r.url, Http.decode(data, r.charset, false));
            } finally {
                r.close();
            }
            if (target == null) {
                throw new IOException(this.env.text("format"));
            }
            return openUrl(target, depth + 1);
        }
        try {
            if (r.status >= 400) {
                throw new IOException("HTTP " + r.status);
            }
            InputStream body = r.body;
            if (this.icyTitles) {
                if (r.icyName != null) {
                    this.env.station(r.icyName);
                }
                int metaint = metaint(r.headers);
                if (metaint > 0) {
                    body = new IcyStream(body, metaint, this.env::title);   // V3.5.0: el sonido sin los títulos intercalados
                }
            }
            return build(new BufferedInputStream(body, 32768), r, r.length, r.isLiveStream());
        } catch (IOException | RuntimeException e) {
            r.close();
            throw e;
        }
    }

    /**
     * V3.5.0: la primera dirección de una lista de radio (.m3u o .pls), resuelta contra {@code base}; null si no trae ninguna o si es una
     * lista HLS (.m3u8 con #EXT-X-, que el reproductor no sabe tocar).
     */
    static String playlistTarget(String base, String text) {
        if (text == null || text.contains("#EXT-X-")) {
            return null;
        }
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            String candidate = null;
            if (lower.startsWith("file") && lower.contains("=")) {
                candidate = line.substring(line.indexOf('=') + 1).trim();
            } else if (!lower.contains("=") || lower.startsWith("http")) {
                candidate = line;
            }
            if (candidate != null && !candidate.isEmpty()) {
                String abs = Url.resolve(base, candidate);
                if (abs != null && Url.isWeb(abs)) {
                    return abs;
                }
            }
        }
        return null;
    }

    /** V3.5.0: cada cuántos bytes manda la emisora su bloque de títulos (cabecera icy-metaint), o 0 si no los manda. */
    static int metaint(Map<String, List<String>> headers) {
        if (headers == null) {
            return 0;
        }
        List<String> values = headers.get("icy-metaint");
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0;
        }
        try {
            int n = Integer.parseInt(values.get(0).trim());
            return n > 0 && n <= (1 << 20) ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Mira los primeros bytes para saber el formato y arma el decodificador. */
    private Opened build(InputStream raw, Closeable connection, long length, boolean live) throws IOException {
        BufferedInputStream in = raw instanceof BufferedInputStream b ? b : new BufferedInputStream(raw, 32768);
        in.mark(16);
        byte[] head = in.readNBytes(12);
        in.reset();
        PcmSource src;
        if (head.length >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') {
            if (live) {
                throw new IOException(this.env.text("live_ogg"));
            }
            src = this.env.ogg(readAll(in, length));
        } else if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') {
            src = new WavSource(in);
        } else if (Mp4AacSource.looksLikeMp4(head)) {
            throw new IOException(this.env.text("format"));
        } else if (Mp3Source.available()) {
            try {
                src = new Mp3Source(in);
            } catch (IOException e) {
                throw new IOException(this.env.text("format"), e);
            }
        } else {
            throw new IOException(this.env.text("format"));
        }
        return new Opened(src, connection, live);
    }

    /** Baja un OGG completo (con su avance) porque el decodificador necesita el archivo entero. */
    private byte[] readAll(InputStream in, long length) throws IOException {
        if (length > MAX_OGG_BYTES) {
            throw new IOException(this.env.text("too_big"));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(length > 0 ? (int) length : 1 << 20);
        byte[] buf = new byte[32768];
        int n;
        long loaded = 0;
        while ((n = in.read(buf)) >= 0) {
            if (!this.env.active()) {
                throw new IOException("cancelado");
            }
            out.write(buf, 0, n);
            loaded += n;
            this.env.progress(loaded, length);
            if (out.size() > MAX_OGG_BYTES) {
                throw new IOException(this.env.text("too_big"));
            }
        }
        this.env.progress(0, -1);
        return out.toByteArray();
    }
}
