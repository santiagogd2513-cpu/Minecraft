package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.browser.Downloads;
import com.example.beautifuldecor.browser.Http;
import com.example.beautifuldecor.browser.Url;
import com.example.beautifuldecor.browser.video.Mp4;
import com.example.beautifuldecor.browser.video.Mp4AacSource;
import com.example.beautifuldecor.browser.video.Mp4Video;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Reproductor de video MP4 del navegador HS (V2.5.0).
 * <p>
 * El video se baja completo a un archivo temporal de "HS Downloads" (las cabeceras de un MP4 pueden estar al final, así que no se
 * puede empezar antes). Luego un hilo propio decodifica el H.264 en cuadros y los deja en una cola corta, y el sonido AAC pasa por
 * {@link HsAudioPlayer}, el mismo reproductor del audio del navegador. El reloj del audio manda: cada vez que la pantalla dibuja,
 * toma de la cola el cuadro que toca y lo sube a una textura. Si el teléfono no alcanza, se saltan cuadros (nunca se atrasa el
 * sonido); si el archivo no trae sonido, suena un silencio que hace de reloj.
 * <p>
 * Cualquier fallo queda como un mensaje en el lugar del video; nunca tumba el juego.
 * <p>
 * V3.0.2: todo lo que corre en el hilo principal está protegido (un error se vuelve mensaje, no cierre del juego); los videos de más
 * de {@link #FULL_RES_PIXELS} píxeles (por ejemplo 1280x720) se bajan a la mitad de ancho y alto en el hilo del video, antes de
 * subirlos a la GPU (la subida de cuadros enteros es lo más pesado en un teléfono); no se suben más de unos 35 cuadros por segundo;
 * y el estado queda anotado en el registro del juego (latest.log) cada pocos segundos.
 * <p>
 * V3.1.0: la descarga se verifica y se reanuda. Antes, si el servidor cortaba la conexión a medias, Java lo trataba como si el archivo
 * hubiera terminado: el video arrancaba bien y "se moría" justo donde se acababan los datos ("muestra fuera del archivo"). Ahora se
 * compara lo bajado con el tamaño que dijo el servidor (o con lo que el propio MP4 necesita), se pide el resto con una petición
 * "Range" (hasta {@link #MAX_PASSES} veces) y, si no se puede completar, se avisa al empezar y no a mitad del video.
 * <p>
 * Desde la V2.11.0 puede haber varios reproductores a la vez: {@link #get()} es el del navegador y {@link #create()} da uno propio
 * (con su propio {@link HsAudioPlayer}) para cada televisor, que dibuja el cuadro en la pantalla del bloque.
 */
public final class HsVideoPlayer {
    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int PLAYING = 2;
    public static final int ENDED = 3;
    public static final int ERROR = 4;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HsVideoPlayer INSTANCE = new HsVideoPlayer(HsAudioPlayer.get());
    private static final long MAX_BYTES = 768L << 20;
    private static final long LATE_US = 100_000L;
    /** Más píxeles que esto (1024x576 pasa; 1280x720 no) y el video se sube a la GPU a la mitad de ancho y alto. */
    static final int FULL_RES_PIXELS = 600_000;
    /** Se puede apagar con -Dbeautifuldecor.video.fullres=true en los argumentos de Java (video siempre a tamaño completo). */
    private static final boolean FULL_RES = Boolean.getBoolean("beautifuldecor.video.fullres");
    /** Tiempo mínimo entre dos subidas de cuadro a la GPU (28 ms: hasta unos 35 cuadros por segundo). */
    private static final long MIN_UPLOAD_GAP_US = 28_000L;
    private static final long STATS_EVERY_US = 5_000_000L;
    /** Intentos de bajada (el primero y las reanudaciones) antes de rendirse, y cuántos seguidos pueden no traer ni un byte. */
    private static final int MAX_PASSES = 16;
    private static final int MAX_STALLS = 3;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)");
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final String TEMP_PREFIX = "stream-";
    private static final String TEMP_SUFFIX = ".mp4.part";
    /** Archivos temporales de los reproductores que están sonando ahora: otro reproductor no los borra al limpiar. */
    private static final Set<Path> IN_USE = ConcurrentHashMap.newKeySet();

    /** El sonido de este video (es también su reloj). */
    private final HsAudioPlayer audio;

    private volatile int state = IDLE;
    private volatile String title = "";
    private volatile String message = "";
    /** Aviso que se muestra sobre el video mientras se reproduce (p. ej. que el archivo llegó cortado), o "". */
    private volatile String notice = "";
    private volatile long loaded;
    private volatile long total = -1;
    /** Tamaño de la textura (el del video, o la mitad si se reduce). */
    private volatile int width;
    private volatile int height;
    /** 1 = el video se sube tal cual; 2 = se reduce a la mitad de ancho y alto antes de subirlo. */
    private volatile int shrink = 1;
    private volatile long token;
    private volatile boolean decodeDone;

    // para el registro (solo informativo)
    private volatile long decoded;
    private volatile long skippedLate;
    private volatile long uploaded;
    private long lastUploadUs;
    private long lastStatsUs;
    private boolean firstFrameLogged;

    /**
     * Un cuadro con la pista (token) que lo hizo, para descartar los que lleguen tarde de un video anterior.
     * {@code px} es null si el cuadro se omitió por llegar tarde; si no, son los píxeles ABGR del tamaño de la textura.
     */
    private record Item(long token, long ptsUs, int[] px) {
    }

    private final ArrayBlockingQueue<Item> queue = new ArrayBlockingQueue<>(4);
    /** Arreglos de píxeles a tamaño completo del video, para reutilizar (los usa solo el hilo del video si se reduce). */
    private final ConcurrentLinkedQueue<int[]> spares = new ConcurrentLinkedQueue<>();
    /** Arreglos del tamaño de la textura cuando el video se reduce a la mitad, para reutilizar. */
    private final ConcurrentLinkedQueue<int[]> smalls = new ConcurrentLinkedQueue<>();

    // reloj (se actualiza desde el hilo de la pantalla y desde el del decodificador)
    private volatile boolean audioStarted;
    private volatile boolean audioRan;
    private volatile long baseUs;
    private volatile long baseWallUs;

    // solo hilo principal
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private int texW;
    private int texH;
    private boolean shown;
    private volatile boolean releasePending;
    private volatile Path tempFile;

    private HsVideoPlayer(HsAudioPlayer audio) {
        this.audio = audio;
    }

    /** El reproductor del navegador. */
    public static HsVideoPlayer get() {
        return INSTANCE;
    }

    /** Un reproductor nuevo, con su propio sonido (uno por televisor). */
    public static HsVideoPlayer create() {
        return new HsVideoPlayer(HsAudioPlayer.create());
    }

    /** El sonido de este video (para mantenerlo atado al bloque con {@link HsAudioPlayer#tick}). */
    public HsAudioPlayer audio() {
        return this.audio;
    }

    // ------------------------------------------------------------------ lo que ve la pantalla

    public int state() {
        return this.state;
    }

    public boolean active() {
        return this.state != IDLE;
    }

    public String title() {
        return this.title;
    }

    public String message() {
        return this.message;
    }

    /** Aviso sobre el video que se está viendo (por ejemplo "llegó cortado, se ve hasta donde alcanza"), o "" si no hay. */
    public String notice() {
        return this.notice;
    }

    /** Avance de la descarga (0 a 1), o -1 si no se sabe. */
    public float loadProgress() {
        long t = this.total;
        return t > 0 ? Math.min(1f, this.loaded / (float) t) : -1f;
    }

    public boolean hasFrame() {
        return this.shown && this.textureId != null;
    }

    /** La textura con el cuadro actual (solo tiene sentido si {@link #hasFrame()}). */
    public ResourceLocation textureId() {
        return this.textureId;
    }

    /** Tamaño del video en píxeles (0 mientras no se sepa). */
    public int videoWidth() {
        return this.texW;
    }

    public int videoHeight() {
        return this.texH;
    }

    // ------------------------------------------------------------------ control

    /** Empieza un video: de una dirección web (se baja primero) o de un archivo ya descargado. Reemplaza cualquier audio o video. */
    public void play(String url, Path file, String name, BlockPos computer, ResourceKey<Level> dim) {
        stop();
        this.audio.stop();                          // si sonaba una radio, se calla mientras se baja el video
        long my;
        synchronized (this) {
            my = ++this.token;
            this.state = LOADING;
            this.title = name == null ? "" : name;
            this.message = "";
            this.notice = "";
            this.loaded = 0;
            this.total = -1;
            this.width = 0;
            this.height = 0;
            this.shrink = 1;
            this.decodeDone = false;
            this.audioStarted = false;
            this.audioRan = false;
            this.baseUs = 0;
            this.baseWallUs = 0;
            this.decoded = 0;
            this.skippedLate = 0;
            this.uploaded = 0;
            this.queue.clear();
        }
        this.lastUploadUs = 0;
        this.lastStatsUs = 0;
        this.firstFrameLogged = false;
        LOGGER.info("[BeautifulDecor] video: empieza «{}» ({})", name == null ? "" : name, url != null ? "desde internet" : "archivo local");
        Thread t = new Thread(() -> run(my, url, file, computer, dim), "HS Video");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);        // que el decodificador no le quite tiempo al dibujo del juego
        t.start();
    }

    /**
     * Para el video y su sonido y borra el archivo temporal. Se puede llamar desde cualquier hilo; la textura se suelta en la
     * siguiente llamada a {@link #update()} (que corre en el hilo principal).
     */
    public void stop() {
        boolean was;
        synchronized (this) {
            was = this.state != IDLE;
            this.token++;
            this.state = IDLE;
            this.message = "";
            this.notice = "";
            this.queue.clear();
            this.decodeDone = false;
        }
        if (was) {
            LOGGER.info("[BeautifulDecor] video: parado (se subieron {} cuadros)", this.uploaded);
            this.audio.stop();
        }
        this.releasePending = true;
        deleteTemp();
    }

    /** Pausa o sigue (con el sonido, que es el reloj del video). */
    public void togglePause() {
        if (this.state == PLAYING) {
            this.audio.togglePause();
        }
    }

    private boolean current(long my) {
        return this.token == my;
    }

    // ------------------------------------------------------------------ reloj

    private long clockUs() {
        if (!this.audioStarted) {
            return 0;
        }
        HsAudioPlayer audio = this.audio;
        int s = audio.state();
        long now = System.nanoTime() / 1000L;
        if (s == HsAudioPlayer.PLAYING || s == HsAudioPlayer.PAUSED) {
            this.audioRan = true;
            this.baseUs = audio.elapsedMs() * 1000L;
            this.baseWallUs = now;
            return this.baseUs;
        }
        if (s == HsAudioPlayer.LOADING) {
            this.baseWallUs = now;                  // el sonido aún no suena (o se está llenando): el video espera
            return this.baseUs;
        }
        // el sonido se acabó (o falló): el video sigue solo con el reloj del sistema
        if (!this.audioRan) {
            this.audioRan = true;
            this.baseWallUs = now;
        }
        return this.baseUs + (now - this.baseWallUs);
    }

    // ------------------------------------------------------------------ hilo del video

    private void run(long my, String url, Path file, BlockPos computer, ResourceKey<Level> dim) {
        Mp4Video video = null;
        try {
            Path path = file;
            if (path == null) {
                path = download(my, url);
            }
            if (!current(my)) {
                return;
            }
            try {
                video = Mp4Video.open(path);
            } catch (Mp4Video.Unsupported u) {
                throw new IOException(describe(u.getMessage()), u);
            } catch (Mp4Video.Truncated c) {
                LOGGER.error("[BeautifulDecor] video: archivo cortado, mide {} bytes y el video llega hasta el {}; no hay ni un cuadro completo", c.have, c.need);
                throw new IOException(Component.translatable("gui.beautifuldecor.browser.video.incomplete", mb(c.have), mb(c.need)).getString(), c);
            }
            if (video.isCut()) {
                // El archivo (tal como lo entrega el servidor) mide menos de lo que el propio video dice: se reproduce el principio que sí hay.
                LOGGER.warn("[BeautifulDecor] video: archivo cortado, mide {} bytes y el video llega hasta el {}; se reproduce solo el principio ({} cuadros, {})",
                        video.fileLength(), video.requiredBytes(), video.frameCount(), clock(video.durationUs()));
                this.notice = Component.translatable("gui.beautifuldecor.browser.video.cut", mb(video.fileLength()), mb(video.requiredBytes()),
                        clock(video.durationUs())).getString();
            }
            int vw = video.width();
            int vh = video.height();
            int k = !FULL_RES && (long) vw * vh > FULL_RES_PIXELS && vw >= 2 && vh >= 2 ? 2 : 1;
            int tw = vw / k;
            int th = vh / k;
            this.shrink = k;
            this.width = tw;
            this.height = th;
            Runtime rt = Runtime.getRuntime();
            LOGGER.info("[BeautifulDecor] video: {}x{}, {} cuadros; textura {}x{}; memoria de Java {} de {} MB", vw, vh, video.frameCount(), tw, th,
                    (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
            this.state = PLAYING;
            if (!current(my)) {
                return;
            }
            this.audio.play(null, path, this.title, computer, dim);
            this.audioStarted = true;
            while (current(my)) {
                int[] spare = this.spares.poll();
                Mp4Video.Frame f = video.next(clockUs() - LATE_US, spare);
                if (f == null) {
                    break;
                }
                this.decoded++;
                int[] px = f.abgr;
                if (px == null) {
                    this.skippedLate++;
                    if (spare != null) {
                        this.spares.offer(spare);
                    }
                } else if (k > 1) {
                    int[] small = this.smalls.poll();
                    if (small == null || small.length < tw * th) {
                        small = new int[tw * th];
                    }
                    halve(px, vw, small, tw, th);
                    this.spares.offer(px);                  // el cuadro entero vuelve: se usa de nuevo para el siguiente
                    px = small;
                }
                boolean put = false;
                Item item = new Item(my, f.ptsUs, px);
                while (current(my) && !put) {
                    put = this.queue.offer(item, 50, TimeUnit.MILLISECONDS);
                }
            }
            if (current(my)) {
                this.decodeDone = true;
                LOGGER.info("[BeautifulDecor] video: decodificado hasta el final ({} cuadros, {} omitidos por llegar tarde)", this.decoded,
                        this.skippedLate);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            if (current(my)) {
                LOGGER.error("[BeautifulDecor] video: error en el hilo del video", e);
                this.message = friendly(e);
                this.state = ERROR;
                this.audio.stop();
            }
        } finally {
            if (video != null) {
                try {
                    video.close();
                } catch (IOException | RuntimeException ignored) {
                    // ya estaba cerrado
                }
            }
        }
    }

    /**
     * Reduce una imagen ABGR a la mitad de ancho y alto promediando cada cuadrito de 2x2 píxeles (dos canales a la vez en un entero).
     * {@code dw} y {@code dh} son la mitad (hacia abajo) del ancho {@code sw} y del alto; el alfa queda opaco.
     */
    static void halve(int[] src, int sw, int[] dst, int dw, int dh) {
        for (int y = 0; y < dh; y++) {
            int r0 = 2 * y * sw;
            int r1 = r0 + sw;
            int o = y * dw;
            for (int x = 0; x < dw; x++) {
                int a = src[r0];
                int b = src[r0 + 1];
                int c = src[r1];
                int d = src[r1 + 1];
                r0 += 2;
                r1 += 2;
                int rb = (a & 0x00FF00FF) + (b & 0x00FF00FF) + (c & 0x00FF00FF) + (d & 0x00FF00FF) + 0x00020002;
                int g = (a & 0x0000FF00) + (b & 0x0000FF00) + (c & 0x0000FF00) + (d & 0x0000FF00) + 0x00000200;
                dst[o + x] = 0xFF000000 | ((rb >>> 2) & 0x00FF00FF) | ((g >>> 2) & 0x0000FF00);
            }
        }
    }

    private Path download(long my, String url) throws IOException {
        Path dir = Downloads.folder();
        Files.createDirectories(dir);
        purgeOldTemps(dir);
        Path tmp = dir.resolve(TEMP_PREFIX + IDS.incrementAndGet() + TEMP_SUFFIX);
        synchronized (this) {
            if (!current(my)) {
                throw new IOException("cancelado");
            }
            this.tempFile = tmp;
            IN_USE.add(tmp);
        }
        byte[] buf = new byte[65536];
        byte[] head = new byte[12];
        int headLen = 0;
        long done = 0;                                  // bytes que ya están en el archivo
        long expected = -1;                             // cuánto debe medir (lo que dijo el servidor o lo que pide el MP4), o -1
        long best = 0;                                  // lo más lejos que se llegó en algún intento
        int stalls = 0;
        int passes = 0;
        boolean gaveUpBlind = false;                    // se rindió sin saber cuánto mide el archivo
        boolean noCache = false;                        // se pide saltarse cachés intermedias (como un "recargar" forzado del navegador)
        boolean triedNoCache = false;
        for (; passes < MAX_PASSES; passes++) {
            boolean resume = done > 0;
            long startedAt = done;
            IOException cut = null;
            // Como hace un navegador con un video: siempre con "Range" (bytes=0- la primera vez). Un servidor que no lo entiende responde 200 con el
            // archivo entero; uno que sí, 206. Hay servidores que a un pedido sin Range le contestan distinto (por ejemplo, solo el principio).
            Map<String, String> ask = new java.util.HashMap<>();
            ask.put("Range", "bytes=" + done + "-");
            if (noCache) {
                ask.put("Cache-Control", "no-cache");
                ask.put("Pragma", "no-cache");
            }
            try (Http.Response r = Http.request("GET", url, null, ask, null)) {
                if (resume && r.status == 416) {
                    break;                              // se pidió más allá del final: el servidor no tiene más
                }
                if (r.status >= 400) {
                    throw new IOException("HTTP " + r.status);
                }
                boolean partial = r.status == 206;
                if (partial) {
                    long from = rangeStart(r);
                    if (from >= 0 && from != done) {
                        throw new IOException("HTTP 206 con otro trozo (empieza en " + from + " y se pidió desde " + done + ")");
                    }
                }
                if (resume && !partial) {
                    if (r.length >= 0 && r.length <= done) {
                        break;                          // el servidor no sabe reanudar pero su archivo entero no es más largo que lo que ya hay
                    }
                    done = 0;                           // el servidor no sabe reanudar: se baja otra vez desde el principio
                    startedAt = 0;
                }
                long total = partial ? totalFromRange(r, done) : r.length;
                if (total > MAX_BYTES) {
                    throw new IOException(Component.translatable("gui.beautifuldecor.browser.video.file_too_big", MAX_BYTES >> 20).getString());
                }
                if (total > 0) {
                    expected = total;
                }
                this.total = expected;
                if (passes == 0) {
                    LOGGER.info("[BeautifulDecor] video: direcciones de {}: {}", Url.hostOf(r.url), addresses(Url.hostOf(r.url)));
                    LOGGER.info("[BeautifulDecor] video: bajando {}; el servidor responde {} con {} bytes ({}); content-range={}, accept-ranges={}, server={}, via={}, "
                            + "content-encoding={}, transfer-encoding={}", r.url, r.status, r.length, r.mime, header(r, "content-range"), header(r, "accept-ranges"),
                            header(r, "server"), header(r, "via"), header(r, "content-encoding"), header(r, "transfer-encoding"));
                }
                if (startedAt == 0) {
                    headLen = 0;
                }
                InputStream in = r.body;
                try (OutputStream out = resume && partial ? Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
                        : Files.newOutputStream(tmp)) {
                    while (true) {
                        int n;
                        try {
                            n = in.read(buf);
                        } catch (IOException e) {
                            if (!current(my)) {
                                throw e;
                            }
                            cut = e;                    // la conexión se cayó a medias: se intenta reanudar
                            break;
                        }
                        if (n < 0) {
                            break;
                        }
                        if (!current(my)) {
                            throw new IOException("cancelado");
                        }
                        if (headLen < head.length && startedAt == 0) {
                            int take = Math.min(n, head.length - headLen);
                            System.arraycopy(buf, 0, head, headLen, take);
                            headLen += take;
                            if (headLen >= head.length && !Mp4AacSource.looksLikeMp4(head)) {
                                throw new IOException(Component.translatable("gui.beautifuldecor.browser.video.not_mp4").getString());
                            }
                        }
                        out.write(buf, 0, n);
                        done += n;
                        this.loaded = done;
                        if (done > MAX_BYTES) {
                            throw new IOException(Component.translatable("gui.beautifuldecor.browser.video.file_too_big", MAX_BYTES >> 20).getString());
                        }
                    }
                }
            }
            if (done < 16 || !Mp4AacSource.looksLikeMp4(head)) {
                throw new IOException(Component.translatable("gui.beautifuldecor.browser.video.not_mp4").getString());
            }
            // El propio MP4 dice hasta dónde debe llegar el archivo. Si es más de lo que anunció el servidor, se intenta pedir el resto (Range):
            // el servidor a veces corta la respuesta y anuncia ese tamaño recortado como si fuera el archivo entero.
            long byMp4 = neededBytes(tmp);              // -1 si aún no se deja leer
            long need = Math.max(expected, byMp4);
            if (expected <= 0 && byMp4 > done) {
                expected = byMp4;
            }
            boolean unknown = expected <= 0 && byMp4 < 0;   // sin tamaño del servidor y con un MP4 que aún no se deja leer (le falta el final: moov)
            if (!triedNoCache && cut == null && expected > 0 && done >= expected && byMp4 > done) {
                // El servidor dice que ya entregó todo el archivo pero el propio video necesita más: puede ser una copia incompleta en una caché
                // intermedia. Una vez se vuelve a pedir desde cero como lo hace un navegador al recargar (Cache-Control/Pragma: no-cache).
                LOGGER.warn("[BeautifulDecor] video: el servidor entregó {} bytes como si fueran todo, pero el video necesita {}; se pide de nuevo sin cachés", done, byMp4);
                triedNoCache = true;
                noCache = true;
                done = 0;
                best = 0;
                stalls = 0;
                expected = -1;
                continue;
            }
            if ((need > 0 && done < need) || unknown) {
                if (done > best) {
                    best = done;
                    stalls = 0;
                } else {
                    stalls++;                           // este intento no llegó más lejos que los anteriores
                }
                if (unknown) {
                    LOGGER.warn("[BeautifulDecor] video: el archivo no se puede leer con {} bytes (intento {}); se pide el resto", done, passes + 1);
                } else {
                    LOGGER.warn("[BeautifulDecor] video: la descarga se cortó en {} de {} bytes (intento {}); se pide el resto", done, need, passes + 1);
                }
                // Sin saber el tamaño y sin que se haya cortado, un archivo ilegible probablemente está dañado, no cortado: solo se insiste una vez.
                if (stalls >= (unknown && cut == null ? 1 : MAX_STALLS)) {
                    gaveUpBlind = unknown;
                    break;
                }
                continue;
            }
            break;
        }
        long byMp4 = neededBytes(tmp);
        long need = Math.max(expected, byMp4);
        if (byMp4 < 0 && ((expected > 0 && done < expected) || gaveUpBlind)) {
            // El archivo ni siquiera se puede abrir (muchos MP4 guardan su índice al final) y falta parte: no hay nada que reproducir.
            LOGGER.error("[BeautifulDecor] video: no se pudo completar la descarga ({} de {} bytes tras {} intentos)", done, need > 0 ? String.valueOf(need) : "?",
                    passes + 1);
            throw new IOException(Component.translatable("gui.beautifuldecor.browser.video.incomplete", mb(done), need > 0 ? mb(need) : "?").getString());
        }
        if (need > 0 && done < need) {
            // El MP4 se puede abrir pero el archivo es más corto de lo que dice: se reproduce el principio (ver run()).
            LOGGER.warn("[BeautifulDecor] video: la descarga quedó incompleta ({} de {} bytes tras {} intentos); se reproduce lo que hay", done, need, passes + 1);
            return tmp;
        }
        LOGGER.info("[BeautifulDecor] video: descarga completa, {} bytes en {} intento(s)", done, passes + 1);
        return tmp;
    }

    /** Las direcciones IP con las que se resuelve un servidor (para el registro), o el motivo si no se pudo. */
    private static String addresses(String host) {
        try {
            StringBuilder b = new StringBuilder();
            for (java.net.InetAddress a : java.net.InetAddress.getAllByName(host)) {
                if (b.length() > 0) {
                    b.append(", ");
                }
                b.append(a.getHostAddress());
            }
            return b.toString();
        } catch (IOException | RuntimeException e) {
            return "?(" + e.getClass().getSimpleName() + ")";
        }
    }

    /** El valor de una cabecera de la respuesta (en minúsculas el nombre) para el registro, o "-" si no vino. */
    private static String header(Http.Response r, String name) {
        List<String> v = r.headers.get(name);
        return v == null || v.isEmpty() ? "-" : String.join(",", v);
    }

    /** El primer byte que anuncia "Content-Range: bytes a-b/total" de una respuesta parcial, o -1 si no lo dice. */
    private static long rangeStart(Http.Response r) {
        List<String> values = r.headers.get("content-range");
        if (values != null) {
            for (String v : values) {
                Matcher m = CONTENT_RANGE.matcher(v == null ? "" : v);
                if (m.find()) {
                    try {
                        return Long.parseLong(m.group(1));
                    } catch (NumberFormatException ignored) {
                        // sigue
                    }
                }
            }
        }
        return -1;
    }

    /** El tamaño total que anuncia "Content-Range: bytes a-b/total" de una respuesta parcial, o lo que falta sumado a lo que ya hay. */
    private static long totalFromRange(Http.Response r, long have) {
        List<String> values = r.headers.get("content-range");
        if (values != null) {
            for (String v : values) {
                Matcher m = CONTENT_RANGE.matcher(v == null ? "" : v);
                if (m.find() && !m.group(3).equals("*")) {
                    try {
                        return Long.parseLong(m.group(3));
                    } catch (NumberFormatException ignored) {
                        // sigue con lo otro
                    }
                }
            }
        }
        return r.length >= 0 ? have + r.length : -1;
    }

    /** Hasta qué byte debe llegar el archivo según su propio MP4 (cabecera, tablas de muestras), o -1 si no se puede leer todavía. */
    private static long neededBytes(Path file) {
        try (Mp4 m = Mp4.open(file)) {
            return m.requiredBytes();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Una duración en microsegundos como m:ss. */
    private static String clock(long us) {
        long s = Math.max(0, us / 1_000_000L);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0);
    }

    private static void purgeOldTemps(Path dir) {
        try (var s = Files.list(dir)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(TEMP_PREFIX) && n.endsWith(TEMP_SUFFIX);
            }).filter(p -> !IN_USE.contains(p)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException | RuntimeException ignored) {
                    // en uso o sin permiso: queda
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // carpeta ilegible: no importa
        }
    }

    private void deleteTemp() {
        Path t = this.tempFile;
        this.tempFile = null;
        if (t != null) {
            IN_USE.remove(t);
            try {
                Files.deleteIfExists(t);
            } catch (IOException | RuntimeException e) {
                t.toFile().deleteOnExit();
            }
        }
    }

    private static String describe(String reason) {
        if (reason == null || reason.isEmpty()) {
            return Component.translatable("gui.beautifuldecor.browser.video.no_video").getString();
        }
        if (reason.startsWith("size ")) {
            return Component.translatable("gui.beautifuldecor.browser.video.too_big", reason.substring(5)).getString();
        }
        return Component.translatable("gui.beautifuldecor.browser.video.unsupported", reason).getString();
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
        return Component.translatable("gui.beautifuldecor.browser.video.error", reason).getString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ hilo principal: textura y dibujo

    /**
     * Desde el hilo principal, cada vez que la pantalla dibuja (y al cerrarla, tras {@link #stop()}, para soltar la textura):
     * toma de la cola el cuadro que toca y lo sube a la textura. Nunca lanza errores: si algo falla, el video queda en ERROR con su mensaje.
     */
    public void update() {
        try {
            updateInner();
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] video: error al actualizar el cuadro", t);
            this.message = friendly(t);
            this.state = ERROR;
            try {
                this.audio.stop();
            } catch (Throwable ignored) {
                // ya está detenido o no se puede: el mensaje de error es lo que importa
            }
        }
    }

    private void updateInner() {
        if (this.releasePending) {
            releaseTexture();
        }
        int s = this.state;
        if (s != PLAYING && s != ENDED) {
            return;
        }
        long nowUs = System.nanoTime() / 1000L;
        // no se suben más de unos 35 cuadros por segundo: lo que sobra se queda en la cola y el decodificador lo omite si llega tarde
        boolean throttled = this.shown && nowUs - this.lastUploadUs < MIN_UPLOAD_GAP_US;
        if (!throttled) {
            long clock = clockUs();
            boolean poster = !this.shown;
            long my = this.token;
            Item best = null;
            Item it;
            while ((it = this.queue.peek()) != null && (it.token() != my || it.ptsUs() <= clock || (poster && best == null))) {
                this.queue.poll();
                if (it.token() != my) {
                    continue;
                }
                if (best != null && best.px() != null) {
                    recycle(best.px());
                }
                best = it;
            }
            if (best != null && best.px() != null) {
                try {
                    upload(best.px());
                    this.lastUploadUs = nowUs;
                } catch (Throwable t) {
                    LOGGER.error("[BeautifulDecor] video: no se pudo subir el cuadro a la GPU", t);
                    this.message = friendly(t);
                    this.state = ERROR;
                    this.audio.stop();
                }
            }
        }
        if (s == PLAYING && this.decodeDone && this.queue.isEmpty()) {
            this.state = ENDED;
            LOGGER.info("[BeautifulDecor] video: terminó ({} cuadros subidos)", this.uploaded);
        }
        if (this.lastStatsUs == 0) {
            this.lastStatsUs = nowUs;                       // la primera nota va a los pocos segundos, no en el primer cuadro
        }
        if (this.state == PLAYING && nowUs - this.lastStatsUs >= STATS_EVERY_US) {
            this.lastStatsUs = nowUs;
            Runtime rt = Runtime.getRuntime();
            LOGGER.info("[BeautifulDecor] video: {} cuadros decodificados, {} omitidos por tarde, {} subidos; cola {}; sonido {} en {} s; "
                            + "memoria de Java {} de {} MB", this.decoded, this.skippedLate, this.uploaded, this.queue.size(),
                    this.audio.state(), this.audio.elapsedMs() / 1000, (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
        }
    }

    /** Devuelve un arreglo de píxeles al lote de reutilización que le toca. */
    private void recycle(int[] px) {
        (this.shrink > 1 ? this.smalls : this.spares).offer(px);
    }

    private void upload(int[] px) {
        int w = this.width;
        int h = this.height;
        if (this.texture == null || this.texW != w || this.texH != h) {
            releaseTexture();
            DynamicTexture tex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, w, h, false));
            tex.setFilter(true, false);
            this.textureId = new ResourceLocation(BeautifulDecor.MOD_ID, "hs_browser/video_" + IDS.incrementAndGet());
            Minecraft.getInstance().getTextureManager().register(this.textureId, tex);
            this.texture = tex;
            this.texW = w;
            this.texH = h;
        }
        NativeImage img = this.texture.getPixels();
        if (img != null && px.length >= w * h) {
            int i = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setPixelRGBA(x, y, px[i++]);
                }
            }
            this.texture.upload();
            this.shown = true;
            this.uploaded++;
            if (!this.firstFrameLogged) {
                this.firstFrameLogged = true;
                LOGGER.info("[BeautifulDecor] video: primer cuadro en pantalla ({}x{})", w, h);
            }
        }
        recycle(px);
    }

    private void releaseTexture() {
        this.releasePending = false;
        this.shown = false;
        ResourceLocation id = this.textureId;
        this.texture = null;
        this.textureId = null;
        this.texW = 0;
        this.texH = 0;
        if (id != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(id);
            } catch (Throwable ignored) {
                // el motor gráfico ya no existe (cerrando el juego): no importa
            }
        }
    }

    /** Dibuja el cuadro actual ajustado dentro del rectángulo (sin deformar). Devuelve false si todavía no hay ninguno. */
    public boolean draw(GuiGraphics gui, int x, int y, int w, int h) {
        if (!hasFrame() || this.texW <= 0 || this.texH <= 0) {
            return false;
        }
        try {
            float scale = Math.min(w / (float) this.texW, h / (float) this.texH);
            int dw = Math.max(1, Math.round(this.texW * scale));
            int dh = Math.max(1, Math.round(this.texH * scale));
            int dx = x + (w - dw) / 2;
            int dy = y + (h - dh) / 2;
            gui.blit(this.textureId, dx, dy, dw, dh, 0.0f, 0.0f, this.texW, this.texH, this.texW, this.texH);
            return true;
        } catch (Throwable t) {
            LOGGER.error("[BeautifulDecor] video: error al dibujar el cuadro en la pantalla", t);
            this.message = friendly(t);
            this.state = ERROR;
            this.audio.stop();
            return false;
        }
    }
}
