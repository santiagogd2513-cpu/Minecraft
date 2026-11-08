package com.example.beautifuldecor.browser.audio;

import com.example.beautifuldecor.radio.RadioPicker;
import com.example.beautifuldecor.radio.RadioStations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Prueba de la V3.5.0 sin Minecraft: la radio de los vehículos.
 * <ol>
 *   <li>IcyStream: quita los bloques de títulos de la transmisión (el sonido sale idéntico, se lea como se lea) y avisa de cada título.</li>
 *   <li>AudioPipe de verdad contra un servidor de radio de mentira en esta máquina: con los títulos pedidos, manda la cabecera
 *       Icy-MetaData, el sonido llega limpio y los títulos se avisan; sin pedirlos (navegador y televisores), todo como antes.</li>
 *   <li>Las emisoras (las del mod y las propias), el volumen y qué radios suenan a la vez.</li>
 * </ol>
 */
public class RadioTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    // ------------------------------------------------------------------------------------------------ armar transmisiones

    private static byte[] audio(int length) {
        byte[] a = new byte[length];
        for (int i = 0; i < length; i++) {
            a[i] = (byte) ((i * 31 + 7) % 251);
        }
        return a;
    }

    /** Un bloque de metadatos como los manda Icecast: un byte con el largo / 16 y el texto relleno con ceros. */
    private static byte[] metaBlock(String text, boolean latin1) {
        if (text == null) {
            return new byte[]{0};
        }
        byte[] t = text.getBytes(latin1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
        int blocks = (t.length + 15) / 16;
        byte[] out = new byte[1 + blocks * 16];
        out[0] = (byte) blocks;
        System.arraycopy(t, 0, out, 1, t.length);
        return out;
    }

    /** El sonido con un bloque de metadatos cada {@code metaint} bytes; {@code metas[i]} es el bloque i (null = vacío). */
    private static byte[] interleave(byte[] sound, int metaint, String[] metas, boolean latin1) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int block = 0;
        for (int i = 0; i < sound.length; i += metaint) {
            int n = Math.min(metaint, sound.length - i);
            out.write(sound, i, n);
            if (n == metaint) {
                String m = block < metas.length ? metas[block] : null;
                byte[] mb = metaBlock(m, latin1);
                out.write(mb, 0, mb.length);
                block++;
            }
        }
        return out.toByteArray();
    }

    private static byte[] readAll(InputStream in, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (chunk <= 0) {
            int b;
            while ((b = in.read()) >= 0) {
                out.write(b);
            }
            return out.toByteArray();
        }
        byte[] buf = new byte[chunk];
        int n;
        while ((n = in.read(buf, 0, chunk)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Un InputStream que entrega de a poquitos (como la red), para que los bloques queden partidos. */
    private static InputStream trickle(byte[] data, int step) {
        return new InputStream() {
            int pos;

            @Override
            public int read() {
                return pos < data.length ? data[pos++] & 0xFF : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (pos >= data.length) {
                    return -1;
                }
                int n = Math.min(Math.min(len, step), data.length - pos);
                System.arraycopy(data, pos, b, off, n);
                pos += n;
                return n;
            }
        };
    }

    // ------------------------------------------------------------------------------------------------ IcyStream

    private static void testIcyStream() throws IOException {
        int metaint = 100;
        byte[] sound = audio(1037);
        String[] metas = {
                "StreamTitle='Artista Uno - Canción Ñandú';StreamUrl='';",
                null,
                "StreamTitle='Artista Uno - Canción Ñandú';",      // el mismo: no se vuelve a avisar
                "StreamTitle='Guns N' Roses - Sweet Child O' Mine';",
                null, null,
                "StreamTitle='';",                                    // vacío: se avisa "" (ya no hay título)
                "StreamTitle='Último tema';",
                null, null};
        byte[] wire = interleave(sound, metaint, metas, false);
        int[] chunks = {0, 1, 3, 16, 99, 100, 101, 257, 4096};
        for (int chunk : chunks) {
            for (int step : new int[]{1, 5, 64, 100000}) {
                List<String> titles = new ArrayList<>();
                IcyStream in = new IcyStream(trickle(wire, step), metaint, titles::add);
                byte[] got = readAll(in, chunk);
                check(java.util.Arrays.equals(got, sound), "IcyStream: el sonido no sale idéntico (trozos de " + chunk + ", red de a " + step + ")");
                check(titles.equals(List.of("Artista Uno - Canción Ñandú", "Guns N' Roses - Sweet Child O' Mine", "", "Último tema")),
                        "IcyStream: títulos " + titles + " (trozos de " + chunk + ", red de a " + step + ")");
                check(in.lastTitle().equals("Último tema"), "IcyStream: lastTitle");
            }
        }
        // skip y available respetan los bloques
        IcyStream s = new IcyStream(new ByteArrayInputStream(wire), metaint, null);
        check(s.skip(250) == 250 && s.read() == (sound[250] & 0xFF), "IcyStream: skip salta solo sonido");
        check(s.available() <= metaint, "IcyStream: available no pasa del siguiente bloque");
        check(!s.markSupported(), "IcyStream: sin mark");
        // la transmisión se corta a mitad de un bloque de metadatos: termina limpio
        byte[] cut = java.util.Arrays.copyOf(wire, metaint + 5);
        byte[] got = readAll(new IcyStream(new ByteArrayInputStream(cut), metaint, null), 37);
        check(java.util.Arrays.equals(got, java.util.Arrays.copyOf(sound, metaint)), "IcyStream: corte a mitad de bloque");
        // títulos en Latin-1 (emisoras viejas)
        List<String> t2 = new ArrayList<>();
        readAll(new IcyStream(new ByteArrayInputStream(interleave(audio(300), 100, new String[]{"StreamTitle='Canción en español';"}, true)), 100, t2::add), 50);
        check(t2.equals(List.of("Canción en español")), "IcyStream: título en Latin-1 " + t2);
        // análisis del texto
        check("A - B".equals(IcyStream.streamTitle("StreamTitle='A - B';".getBytes(StandardCharsets.UTF_8))), "streamTitle básico");
        check(IcyStream.streamTitle("StreamUrl='x';".getBytes(StandardCharsets.UTF_8)) == null, "sin StreamTitle: null");
        check("Tab Title".equals(IcyStream.streamTitle("StreamTitle='Tab\tTitle';".getBytes(StandardCharsets.UTF_8))), "sin caracteres de control");
        check(IcyStream.streamTitle(("StreamTitle='" + "x".repeat(400) + "';").getBytes(StandardCharsets.UTF_8)).length() == 120, "título largo se corta");
        check("Sin cierre".equals(IcyStream.streamTitle("StreamTitle='Sin cierre".getBytes(StandardCharsets.UTF_8))), "sin cierre: hasta el final");
        boolean threw = false;
        try {
            new IcyStream(new ByteArrayInputStream(new byte[0]), 0, null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "metaint 0 no vale");
        System.out.println("IcyStream: sonido idéntico con " + chunks.length * 4 + " formas de leer; títulos en UTF-8 y Latin-1, con comillas, vacíos y repetidos");
    }

    // ------------------------------------------------------------------------------------------------ AudioPipe contra un servidor de mentira

    /** WAV en vivo (16 bits, estéreo, 22050 Hz) con el largo "desconocido", como una radio. */
    private static byte[] wavHeader() {
        ByteArrayOutputStream h = new ByteArrayOutputStream();
        writeAscii(h, "RIFF");
        writeLe(h, 0xFFFFFFFF, 4);
        writeAscii(h, "WAVE");
        writeAscii(h, "fmt ");
        writeLe(h, 16, 4);
        writeLe(h, 1, 2);
        writeLe(h, 2, 2);
        writeLe(h, 22050, 4);
        writeLe(h, 22050 * 4, 4);
        writeLe(h, 4, 2);
        writeLe(h, 16, 2);
        writeAscii(h, "data");
        writeLe(h, 0xFFFFFFFF, 4);
        return h.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        out.write(b, 0, b.length);
    }

    private static void writeLe(ByteArrayOutputStream out, long v, int bytes) {
        for (int i = 0; i < bytes; i++) {
            out.write((int) (v >>> (8 * i)) & 0xFF);
        }
    }

    /** Servidor de radio de mentira: un solo cliente; si pide Icy-MetaData manda títulos cada {@code metaint} bytes. */
    private static final class FakeRadio implements AutoCloseable {
        final ServerSocket server;
        final AtomicReference<String> requestHeaders = new AtomicReference<>("");
        final Thread thread;

        FakeRadio(byte[] sound, int metaint, String[] metas) throws IOException {
            this.server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            this.thread = new Thread(() -> {
                try (Socket s = this.server.accept()) {
                    InputStream in = s.getInputStream();
                    StringBuilder req = new StringBuilder();
                    int prev = -1;
                    int b;
                    int newlines = 0;
                    while ((b = in.read()) >= 0) {
                        req.append((char) b);
                        if (b == '\n') {
                            newlines = prev == '\r' || prev == '\n' ? newlines + 1 : 1;
                            if (req.toString().endsWith("\r\n\r\n")) {
                                break;
                            }
                        }
                        prev = b;
                    }
                    this.requestHeaders.set(req.toString());
                    boolean wantsMeta = req.toString().toLowerCase(Locale.ROOT).contains("icy-metadata: 1");
                    OutputStream out = s.getOutputStream();
                    StringBuilder head = new StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nicy-name: Radio de Prueba\r\nConnection: close\r\n");
                    if (wantsMeta) {
                        head.append("icy-metaint: ").append(metaint).append("\r\n");
                    }
                    head.append("\r\n");
                    out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
                    byte[] body = new byte[wavHeader().length + sound.length];
                    System.arraycopy(wavHeader(), 0, body, 0, wavHeader().length);
                    System.arraycopy(sound, 0, body, wavHeader().length, sound.length);
                    byte[] wire = wantsMeta ? interleave(body, metaint, metas, false) : body;
                    for (int i = 0; i < wire.length; i += 777) {
                        out.write(wire, i, Math.min(777, wire.length - i));
                        out.flush();
                    }
                    Thread.sleep(3000);      // como una radio: no cierra enseguida
                } catch (IOException | InterruptedException ignored) {
                    // se cerró
                }
            }, "fake radio");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        String url() {
            return "http://127.0.0.1:" + this.server.getLocalPort() + "/live";
        }

        @Override
        public void close() throws IOException {
            this.server.close();
        }
    }

    /** Servidor de mentira que da una lista .pls apuntando a la radio (como las páginas de muchas emisoras). */
    private static final class FakeList implements AutoCloseable {
        final ServerSocket server;

        FakeList(String text) throws IOException {
            this.server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            Thread t = new Thread(() -> {
                try (Socket s = this.server.accept()) {
                    InputStream in = s.getInputStream();
                    StringBuilder req = new StringBuilder();
                    int b;
                    while ((b = in.read()) >= 0) {
                        req.append((char) b);
                        if (req.toString().endsWith("\r\n\r\n")) {
                            break;
                        }
                    }
                    byte[] bodyBytes = text.getBytes(StandardCharsets.UTF_8);
                    String head = "HTTP/1.1 200 OK\r\nContent-Type: audio/x-scpls\r\nContent-Length: " + bodyBytes.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.write(bodyBytes);
                    out.flush();
                } catch (IOException ignored) {
                    // se cerró
                }
            }, "fake list");
            t.setDaemon(true);
            t.start();
        }

        String url() {
            return "http://127.0.0.1:" + this.server.getLocalPort() + "/radio.pls";
        }

        @Override
        public void close() throws IOException {
            this.server.close();
        }
    }

    private static final class Collected {
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        final List<String> titles = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<String> station = new AtomicReference<>("");
        AudioPipe pipe;
    }

    private static Collected listen(String url, boolean icy, int wantBytes) throws Exception {
        Collected c = new Collected();
        AtomicBoolean active = new AtomicBoolean(true);
        AudioPipe.Env env = new AudioPipe.Env() {
            @Override
            public boolean active() {
                return active.get();
            }

            @Override
            public PcmSource ogg(byte[] data) throws IOException {
                throw new IOException("ogg");
            }

            @Override
            public String text(String key) {
                return key;
            }

            @Override
            public void progress(long loaded, long total) {
            }

            @Override
            public void title(String title) {
                c.titles.add(title);
            }

            @Override
            public void station(String name) {
                c.station.set(name);
            }
        };
        c.pipe = icy ? new AudioPipe(env, 100, true) : new AudioPipe(env, 100);
        Thread t = new Thread(() -> c.pipe.run(url, null), "pipe");
        t.setDaemon(true);
        t.start();
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline && c.pcm.size() < wantBytes && c.pipe.error == null) {
            byte[] chunk = c.pipe.queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (chunk != null) {
                c.pcm.write(chunk, 0, chunk.length);
            }
        }
        active.set(false);
        c.pipe.abort();
        t.join(3000);
        return c;
    }

    private static void testPipe() throws Exception {
        byte[] sound = audio(64000 - 64000 % 4);
        String[] metas = new String[200];
        metas[0] = "StreamTitle='Primera canción';";
        metas[3] = "StreamTitle='Segunda canción';";
        int metaint = 4000;
        try (FakeRadio radio = new FakeRadio(sound, metaint, metas)) {
            Collected c = listen(radio.url(), true, sound.length);
            check(c.pipe.error == null, "AudioPipe con títulos falló: " + c.pipe.error);
            check(radio.requestHeaders.get().toLowerCase(Locale.ROOT).contains("icy-metadata: 1"), "con títulos se debe pedir Icy-MetaData");
            check(c.pipe.live && c.pipe.channels == 2 && c.pipe.rate == 22050, "AudioPipe: formato de la radio de prueba");
            byte[] pcm = c.pcm.toByteArray();
            check(pcm.length >= sound.length - 4096, "AudioPipe: llegó muy poco sonido (" + pcm.length + " de " + sound.length + ")");
            int n = Math.min(pcm.length, sound.length);
            check(java.util.Arrays.equals(java.util.Arrays.copyOf(pcm, n), java.util.Arrays.copyOf(sound, n)), "AudioPipe: el sonido trae basura de los títulos");
            check(c.titles.contains("Primera canción") && c.titles.contains("Segunda canción"), "AudioPipe: títulos " + c.titles);
            check("Radio de Prueba".equals(c.station.get()), "AudioPipe: nombre de la emisora " + c.station.get());
        }
        try (FakeRadio radio = new FakeRadio(sound, metaint, metas)) {
            Collected c = listen(radio.url(), false, sound.length);
            check(c.pipe.error == null, "AudioPipe sin títulos falló: " + c.pipe.error);
            check(!radio.requestHeaders.get().toLowerCase(Locale.ROOT).contains("icy-metadata"), "sin títulos NO se debe pedir Icy-MetaData (navegador y televisores igual que antes)");
            byte[] pcm = c.pcm.toByteArray();
            int n = Math.min(pcm.length, sound.length);
            check(n > sound.length / 2 && java.util.Arrays.equals(java.util.Arrays.copyOf(pcm, n), java.util.Arrays.copyOf(sound, n)), "AudioPipe sin títulos: el sonido cambió");
            check(c.titles.isEmpty(), "sin títulos no se avisan títulos");
        }
        try (FakeRadio radio = new FakeRadio(sound, metaint, metas);
             FakeList list = new FakeList("[playlist]\nNumberOfEntries=1\nFile1=" + radio.url() + "\nTitle1=Prueba\nLength1=-1\n")) {
            Collected c = listen(list.url(), true, sound.length / 2);
            check(c.pipe.error == null, "lista .pls: falló " + c.pipe.error);
            byte[] pcm = c.pcm.toByteArray();
            int n = Math.min(pcm.length, sound.length);
            check(n >= sound.length / 2 && java.util.Arrays.equals(java.util.Arrays.copyOf(pcm, n), java.util.Arrays.copyOf(sound, n)), "lista .pls: el sonido no llegó bien");
            check(c.titles.contains("Primera canción"), "lista .pls: sin títulos " + c.titles);
        }
        check("http://a.example.com/live".equals(AudioPipe.playlistTarget("http://x.example.com/r.m3u", "#EXTM3U\n#EXTINF:-1,Radio\nhttp://a.example.com/live\n")), "m3u");
        check("https://b.example.com/s".equals(AudioPipe.playlistTarget("http://x.example.com/r.pls", "[playlist]\r\nNumberOfEntries=1\r\nFile1=https://b.example.com/s\r\nTitle1=X\r\n")), "pls");
        check("http://c.example.com/list/stream.mp3".equals(AudioPipe.playlistTarget("http://c.example.com/list/radio.m3u", "stream.mp3")), "m3u con dirección relativa");
        check(AudioPipe.playlistTarget("http://x.example.com/r.m3u8", "#EXTM3U\n#EXT-X-VERSION:3\nseg1.ts\n") == null, "HLS no se puede");
        check(AudioPipe.playlistTarget("http://x.example.com/r.m3u", "#EXTM3U\n\n") == null && AudioPipe.playlistTarget("http://x", null) == null, "lista vacía");
        check(AudioPipe.metaint(Map.of("icy-metaint", List.of("16000"))) == 16000, "metaint: 16000");
        check(AudioPipe.metaint(Map.of("icy-metaint", List.of(" 8192 "))) == 8192, "metaint con espacios");
        check(AudioPipe.metaint(Map.of("icy-metaint", List.of("abc"))) == 0 && AudioPipe.metaint(Map.of()) == 0 && AudioPipe.metaint(null) == 0, "metaint raro: 0");
        check(AudioPipe.metaint(Map.of("icy-metaint", List.of("0"))) == 0 && AudioPipe.metaint(Map.of("icy-metaint", List.of("99999999"))) == 0, "metaint fuera de rango: 0");
        System.out.println("AudioPipe contra una radio de prueba: con títulos el sonido llega idéntico y salen 'Primera canción' y 'Segunda canción'; sin pedirlos, igual que antes; también desde una lista .pls");
    }

    // ------------------------------------------------------------------------------------------------ emisoras, volumen, cuáles suenan

    private static void testStations() {
        List<String> seen = new ArrayList<>();
        for (RadioStations.Station s : RadioStations.BUILT_IN) {
            check(RadioStations.cleanUrl(s.url()) != null && s.url().startsWith("https://"), "emisora del mod con dirección mala: " + s.url());
            check(!s.name().isEmpty() && s.name().equals(RadioStations.cleanName(s.name())), "emisora del mod con nombre malo: " + s.name());
            check(!seen.contains(s.url()), "emisora repetida: " + s.url());
            seen.add(s.url());
        }
        check(RadioStations.BUILT_IN.size() >= 8, "pocas emisoras de fábrica");
        check("https://ice1.somafm.com/x".equals(RadioStations.cleanUrl("  https://ice1.somafm.com/x  ")), "cleanUrl recorta");
        check("HTTP://Radio.example.com:8000/live?x=1".equals(RadioStations.cleanUrl("HTTP://Radio.example.com:8000/live?x=1")), "cleanUrl acepta mayúsculas, puerto y consulta");
        for (String bad : new String[]{null, "", "ftp://x.com/a", "javascript:alert(1)", "http://", "https:///path", "http://exa mple.com", "http://x.com/\"a",
                "http://.x.com", "file:///etc/passwd", "radio.com/live", "https://" + "a".repeat(400)}) {
            check(RadioStations.cleanUrl(bad) == null, "cleanUrl debía rechazar: " + bad);
        }
        check(RadioStations.cleanName("  Mi   radio\tfavorita \n").equals("Mi radio favorita"), "cleanName junta espacios");
        check(RadioStations.cleanName("x".repeat(50)).length() == RadioStations.MAX_NAME && RadioStations.cleanName(null).isEmpty(), "cleanName corta y aguanta null");
        check(RadioStations.nameFromUrl("https://stream.radioparadise.com/mp3-128").equals("stream.radioparadise.com"), "nameFromUrl");
        check(RadioStations.nameFromUrl("http://user:pass@radio.example.com:8000/x").equals("radio.example.com"), "nameFromUrl sin usuario ni puerto");
        check(RadioStations.clampVolume(-5) == 0 && RadioStations.clampVolume(150) == 100 && RadioStations.clampVolume(40) == 40, "clampVolume");
        List<RadioStations.Station> mine = List.of(
                new RadioStations.Station("Mi emisora", "https://radio.example.com/live", ""),
                new RadioStations.Station("", "http://otra.example.com:8000/stream", ""));
        List<RadioStations.Station> back = RadioStations.parseCustom(RadioStations.formatCustom(mine));
        check(back.size() == 2 && back.get(0).name().equals("Mi emisora") && back.get(0).url().equals("https://radio.example.com/live")
                && back.get(1).name().equals("otra.example.com"), "guardar y leer las emisoras propias: " + back);
        List<String> messy = new ArrayList<>(List.of("# comentario", "", "Sin tab https://mala", "Buena\thttps://a.example.com/s", "Repetida\tHTTPS://A.EXAMPLE.COM/s",
                "\tftp://mala", "https://sola.example.com/r"));
        List<RadioStations.Station> parsed = RadioStations.parseCustom(messy);
        check(parsed.size() == 2 && parsed.get(0).name().equals("Buena") && parsed.get(1).url().equals("https://sola.example.com/r"), "parseCustom salta lo malo: " + parsed);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add("R" + i + "\thttps://r" + i + ".example.com/s");
        }
        check(RadioStations.parseCustom(many).size() == RadioStations.MAX_CUSTOM, "como mucho MAX_CUSTOM propias");
        System.out.println("emisoras: " + RadioStations.BUILT_IN.size() + " de fábrica (todas https, sin repetir); direcciones y nombres propios revisados; guardar y leer ida y vuelta");
    }

    private static void testPicker() {
        List<RadioPicker.Candidate> c = List.of(
                new RadioPicker.Candidate(5, 30.0, false),
                new RadioPicker.Candidate(9, 3.0, false),
                new RadioPicker.Candidate(2, 0.5, true),
                new RadioPicker.Candidate(7, 3.0, false),
                new RadioPicker.Candidate(4, 41.0, false),
                new RadioPicker.Candidate(8, 12.0, false));
        check(RadioPicker.pick(c, 3).equals(List.of(2, 7, 9)), "la propia primero y luego las más cercanas (a igual distancia, por id): " + RadioPicker.pick(c, 3));
        check(RadioPicker.pick(c, 10).equals(List.of(2, 7, 9, 8, 5)), "las de más de " + RadioPicker.RANGE + " bloques no suenan: " + RadioPicker.pick(c, 10));
        check(RadioPicker.pick(List.of(new RadioPicker.Candidate(1, 99.0, true)), 3).equals(List.of(1)), "la propia suena aunque el cálculo de distancia diga lejos");
        check(RadioPicker.pick(c, 0).isEmpty() && RadioPicker.pick(List.of(), 3).isEmpty(), "sin lugar o sin radios: ninguna");
        check(RadioPicker.MAX_PLAYING >= 1 && RadioPicker.MAX_PLAYING <= 4, "pocas radios a la vez (datos y batería del celular)");
        System.out.println("radios a la vez: la del vehículo propio primero, luego las más cercanas, hasta " + RadioPicker.MAX_PLAYING + " y a " + (int) RadioPicker.RANGE + " bloques");
    }

    public static void main(String[] args) throws Exception {
        testIcyStream();
        testStations();
        testPicker();
        testPipe();
        System.out.println("VEHICLE RADIO OK (" + checks + " comprobaciones)");
    }
}
