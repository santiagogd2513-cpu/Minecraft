import com.example.beautifuldecor.browser.*;
import com.example.beautifuldecor.browser.audio.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** V2.4.1: radios en vivo (ICY, transmisión sin fin, reconexión), listas .m3u/.pls, HLS y AAC. */
public class StreamTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLO: " + m); } }
    static final byte[] MP3 = Base64.getDecoder().decode("//MoxAALwALNv0EYAqkAZLh/+AAD4Pg+D58EIPg+CDjkuD4Pg/wQdg+f/5QP8HDmIAf1g4cyAP8CO5/oaUoF//4NAE9feP39//MoxAwQMOaIAZuQADCi84RmBhkcYogUUPHFQHyDeEPhAcYt6ADqHcxAhzhzij/kVIqZF4vf+iiYiI99YKiI9/BV///7kadq//MoxAYOaHY4Ad4QAG5AXCMAUCYBA1GBsC6YWA8ppwDDG/6QMYr4uhhzBXmEkJGFwdTAuAFR9ep+rUyKu////0qld5dpf0Kg//MoxAcNYGowAAe2LAgIATMAMC4wFgbzBZFuMmryg1QR5jBGBdMdBDhrMys1BSMiK06Kzwxv//////6q///9yh+3tYSIgIYB//MoxAwOmG4sAOf4gA2YUHRjlDmlM8YaChKGN6BGRgZ4FqZnNJqaAGOSIBiegY09+JfSAn////0f/r2V///9yRsrSlLiYFHi//MoxAwOcG4sAN+4gLDFwBY5+QoapoTx2mAfGI2C+ZFQZhDUBdEEgTVSbDJJuoGXf//////s/////6H///3TP6zpIYtyCQUY//MoxA0MsGo0AOe2gBBCYbKxmCVmULkyauwxpgrgfGECZtN4Y8bAoxTWcWLWR7P///+9///9U8UgZmSHMKA4wKKzDhmMs0ky//MoxBULsG40AOe2gRDW00dRvzCECENGQDgZ0yYjDjRSbyReWWwx///9R96HRZKi2HDgCSzUhI9FXNJw6M52QjTD2BgMgHIx//MoxCENGG4wAN+4gC28AHMUBS23oj8xXD3////R//0K///9UrtM5SFLhAADmCQ0YkJpnOJGXBd0bHItRg4AWgUgNc2zGUEB//MoxCcLkG40AOe2gRUnS40VtcpqsSN+WQpOCMAsEARGAaCyYGwlBix4aGYIMgYLoN5t1p8MRoRg0rW5C5y3YCP////T//21//MoxDMMmG44AAe0LP///cxH488yvAwHMUADORc5hoM3dIw3agnjC5BPMYlMxNDgCTguAW4ROYrgg///////9ar///3WhlrS//MoxDsMyGo4AN+4gKZCSWWMIDzIzY3DVMsuWM2BhGzBzASICU0PUMMPAMKrqd2XWep////6Kv///7cHNOTSKgFFAsCRuYKP//MoxEIMEG44AN+2gMYxyJgmyB6YVGENmAxAShpRoc6rmZAo8mItuZC5y2Wq///9TcASV8krwUGGNhpoBcdbTmeo3acGYd5h//MoxEwMEG4wAOf2gaQNBkAxGQ5eYKLYAAjA30ldJXD3////Tf///Uy4S7UHSzRgwGYyKGfIZ1fsZ+laxw9iiGFoAOFCMZMm//MoxFYMqG40AN+4gKYTMJhMDJEuVGaYWLX///zjjgMEQnllwMA2YEgLxg2BPGJ8OYYZd/BiXhtGA2BKYFYD5gUAMgIEIwLQ//MoxF4L4GowAN+4gRsFABprxCMSwIIVFN01QuEwGAo2GwAAH/GIxKYz9WQNbcF0iF477VMz1Kk6VIAxA3o8Aq4QTHeTQ5w5//MoxGkOEHI4AV4QAd5FCKGiYs0UCKBId9N6Ec4c4mSKkV//Mi8XjEul3+oGhKEvpuSDQlCQNN/ReiBToNJuOYgw9RClUiQv//MoxGsYKSatv5mYAEJCmx8ianCujSWj9IKdU9dYhMSeUSgYBBSTkSJHZIkZajkshDcQXoL8I7J/gtVMQU1FMy4xMDBVVVVV//MoxEUNyLoUAc8wAVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV");

    static byte[] wav(int rate, int samples) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int data = samples * 2;
        try {
            DataOutputStream d = new DataOutputStream(o);
            d.writeBytes("RIFF"); d.writeInt(Integer.reverseBytes(36 + data)); d.writeBytes("WAVEfmt ");
            d.writeInt(Integer.reverseBytes(16)); d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) 1));
            d.writeInt(Integer.reverseBytes(rate)); d.writeInt(Integer.reverseBytes(rate * 2));
            d.writeShort(Short.reverseBytes((short) 2)); d.writeShort(Short.reverseBytes((short) 16));
            d.writeBytes("data"); d.writeInt(Integer.reverseBytes(data));
            for (int i = 0; i < samples; i++) d.writeShort(Short.reverseBytes((short) (Math.sin(i * 0.1) * 8000)));
        } catch (IOException e) { throw new RuntimeException(e); }
        return o.toByteArray();
    }

    /** Servidor ICY crudo: responde "ICY 200 OK" y manda el MP3 una y otra vez (o una vez, si once). */
    static final class Icy implements Closeable {
        final ServerSocket ss;
        final AtomicInteger connections = new AtomicInteger();
        final boolean once;
        volatile boolean stop;
        Icy(boolean once) throws IOException {
            this.once = once;
            this.ss = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            Thread t = new Thread(this::loop);
            t.setDaemon(true);
            t.start();
        }
        int port() { return ss.getLocalPort(); }
        void loop() {
            while (!stop) {
                try {
                    Socket s = ss.accept();
                    connections.incrementAndGet();
                    Thread h = new Thread(() -> serve(s));
                    h.setDaemon(true);
                    h.start();
                } catch (IOException e) { return; }
            }
        }
        void serve(Socket s) {
            try (s) {
                InputStream in = s.getInputStream();
                int last = 0, n = 0, c;
                while ((c = in.read()) >= 0) { n = (c == '\n' && last == '\n') ? n : n; if (c == '\n') { if (last == '\n') break; last = '\n'; } else if (c != '\r') last = 0; }
                OutputStream out = s.getOutputStream();
                out.write("ICY 200 OK\r\nicy-name: Radio Prueba\r\nicy-br: 16\r\nContent-Type: audio/mpeg\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                do {
                    out.write(MP3);
                    out.flush();
                    Thread.sleep(5);
                } while (!once && !stop);
            } catch (Exception ignored) { }
        }
        public void close() { stop = true; try { ss.close(); } catch (IOException ignored) { } }
    }

    static final class FakeEnv implements AudioPipe.Env {
        volatile boolean active = true;
        public boolean active() { return active; }
        public PcmSource ogg(byte[] d) throws IOException { throw new IOException("sin ogg en la prueba"); }
        public String text(String k) { return k; }
        public void progress(long a, long b) { }
    }

    static long drain(AudioPipe pipe, FakeEnv env, long wantBytes, long timeoutMs) throws Exception {
        long got = 0, t0 = System.currentTimeMillis();
        while (got < wantBytes && System.currentTimeMillis() - t0 < timeoutMs && pipe.error == null) {
            byte[] c = pipe.queue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (c != null) got += c.length;
            else if (pipe.eof) break;
        }
        return got;
    }

    static void waitFor(Thread t, long ms) throws Exception { t.join(ms); check(!t.isAlive(), "el hilo de lectura termina"); }

    static void send(HttpExchange ex, int code, String type, byte[] b) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
        ex.close();
    }

    static String text(Node n) { return n.textContent(); }

    static void waitLoad(BrowserSession s) throws Exception {
        long t = System.currentTimeMillis();
        while (s.loading() && System.currentTimeMillis() - t < 15000) Thread.sleep(20);
        check(!s.loading(), "carga terminó");
    }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("hsstream");
        Downloads.init(tmp);

        // ---- 1. servidor ICY: Http lo entiende y lo marca en vivo
        try (Icy icy = new Icy(false)) {
            String url = "http://127.0.0.1:" + icy.port() + "/stream";
            try (Http.Response r = Http.get(url, null)) {
                check(r.status == 200, "ICY status 200: " + r.status);
                check(r.icy, "ICY detectado");
                check("Radio Prueba".equals(r.icyName), "nombre ICY: " + r.icyName);
                check(r.isLiveStream(), "ICY es en vivo");
            }
            // ---- 2. AudioPipe lee la radio sin fin y entrega PCM mientras llega
            FakeEnv env = new FakeEnv();
            AudioPipe pipe = new AudioPipe(env, 20);
            Thread t = new Thread(() -> pipe.run(url, null));
            t.start();
            long got = drain(pipe, env, 60000, 15000);
            check(pipe.error == null, "sin error: " + pipe.error);
            check(pipe.ready && pipe.live, "listo y en vivo");
            check(pipe.channels == 1 && pipe.rate == 16000, "formato 16000 Hz mono: " + pipe.channels + "/" + pipe.rate);
            check(got >= 60000, "PCM recibido en vivo: " + got);
            env.active = false;
            pipe.abort();
            waitFor(t, 5000);
            check(pipe.error == null, "parar no deja error: " + pipe.error);
        }

        // ---- 3. cortes: la radio manda un MP3 y cierra; AudioPipe reconecta solo
        try (Icy icy = new Icy(true)) {
            String url = "http://127.0.0.1:" + icy.port() + "/s";
            FakeEnv env = new FakeEnv();
            AudioPipe pipe = new AudioPipe(env, 20);
            Thread t = new Thread(() -> pipe.run(url, null));
            t.start();
            long got = drain(pipe, env, 200000, 15000);
            check(icy.connections.get() >= 3, "reconectó varias veces: " + icy.connections.get());
            check(got >= 200000, "siguió sonando tras los cortes: " + got);
            env.active = false;
            pipe.abort();
            waitFor(t, 5000);
        }

        // ---- 4. la radio desaparece: tras varios intentos sin sonido, error "lost"
        Icy dead = new Icy(true);
        String deadUrl = "http://127.0.0.1:" + dead.port() + "/s";
        {
            FakeEnv env = new FakeEnv();
            AudioPipe pipe = new AudioPipe(env, 10);
            Thread t = new Thread(() -> pipe.run(deadUrl, null));
            t.start();
            long got = drain(pipe, env, 5000, 10000);
            check(got > 0, "sonó antes de caerse");
            dead.close();
            long t0 = System.currentTimeMillis();
            while (pipe.error == null && System.currentTimeMillis() - t0 < 15000) { pipe.queue.poll(20, java.util.concurrent.TimeUnit.MILLISECONDS); }
            check(pipe.error != null && "lost".equals(pipe.error.getMessage()), "error lost: " + pipe.error);
            env.active = false;
            waitFor(t, 5000);
        }

        // ---- 5. HTTP normal: transmisión sin Content-Length y sin extensión = en vivo; archivo .mp3 con largo = no
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String root = "http://127.0.0.1:" + srv.getAddress().getPort();
        AtomicInteger liveHits = new AtomicInteger();
        srv.createContext("/live", ex -> {
            liveHits.incrementAndGet();
            ex.getResponseHeaders().add("Content-Type", "audio/mpeg");
            ex.sendResponseHeaders(200, 0); // 0 = chunked, sin largo
            try (OutputStream o = ex.getResponseBody()) {
                for (int i = 0; i < 400; i++) { o.write(MP3); o.flush(); Thread.sleep(5); }
            } catch (Exception ignored) { }
        });
        srv.createContext("/fixed.mp3", ex -> send(ex, 200, "audio/mpeg", MP3));
        srv.createContext("/wavfile", ex -> send(ex, 200, "audio/wav", wav(8000, 4000)));
        srv.createContext("/junk", ex -> send(ex, 200, "audio/mpeg", "esto no es audio, es texto plano de relleno para la prueba".getBytes()));
        srv.createContext("/ogglive", ex -> {
            ex.getResponseHeaders().add("Content-Type", "audio/ogg");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream o = ex.getResponseBody()) { o.write("OggS-fake".getBytes()); o.flush(); Thread.sleep(300); } catch (Exception ignored) { }
        });
        srv.createContext("/aac", ex -> {
            ex.getResponseHeaders().add("Content-Type", "audio/aacp");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream o = ex.getResponseBody()) { o.write(new byte[]{(byte) 0xff, (byte) 0xf1, 0, 0}); o.flush(); Thread.sleep(300); } catch (Exception ignored) { }
        });
        srv.createContext("/radio.m3u", ex -> send(ex, 200, "audio/x-mpegurl", ("#EXTM3U\n#EXTINF:-1,Mi Emisora\n" + root + "/live\n").getBytes()));
        srv.createContext("/radio.pls", ex -> send(ex, 200, "audio/x-scpls",
            ("[playlist]\nNumberOfEntries=1\nFile1=" + root + "/live\nTitle1=Emisora PLS\nLength1=-1\nVersion=2\n").getBytes()));
        srv.createContext("/hls.m3u8", ex -> send(ex, 200, "application/vnd.apple.mpegurl",
            "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nseg1.ts\n".getBytes()));
        srv.createContext("/vacia.m3u", ex -> send(ex, 200, "audio/x-mpegurl", "#EXTM3U\n# nada\n".getBytes()));
        srv.createContext("/rel.m3u", ex -> send(ex, 200, "audio/x-mpegurl", "live\n".getBytes()));
        srv.start();
        try {
            try (Http.Response r = Http.get(root + "/live", null)) { check(r.isLiveStream(), "chunked sin extensión es en vivo"); }
            try (Http.Response r = Http.get(root + "/fixed.mp3", null)) { check(!r.isLiveStream(), "mp3 con largo no es en vivo"); }

            // 5a. archivo MP3 con largo: termina solo (eof) y entrega todo
            {
                FakeEnv env = new FakeEnv();
                AudioPipe pipe = new AudioPipe(env, 10);
                Thread t = new Thread(() -> pipe.run(root + "/fixed.mp3", null));
                t.start();
                long got = drain(pipe, env, Long.MAX_VALUE, 10000);
                waitFor(t, 5000);
                check(pipe.error == null && pipe.eof && !pipe.live, "archivo termina bien: " + pipe.error);
                check(got > 10000, "bytes del MP3 de archivo: " + got);
            }
            // 5b. WAV por la red
            {
                FakeEnv env = new FakeEnv();
                AudioPipe pipe = new AudioPipe(env, 10);
                Thread t = new Thread(() -> pipe.run(root + "/wavfile", null));
                t.start();
                long got = drain(pipe, env, Long.MAX_VALUE, 10000);
                waitFor(t, 5000);
                check(pipe.error == null && pipe.eof && pipe.rate == 8000, "wav ok: " + pipe.error + " rate " + pipe.rate);
                check(got == 8000, "wav entrega 4000 muestras: " + got);
            }
            // 5c. archivo local en disco
            {
                Path f = tmp.resolve("t.wav");
                Files.write(f, wav(11025, 2000));
                FakeEnv env = new FakeEnv();
                AudioPipe pipe = new AudioPipe(env, 10);
                Thread t = new Thread(() -> pipe.run(null, f));
                t.start();
                long got = drain(pipe, env, Long.MAX_VALUE, 10000);
                waitFor(t, 5000);
                check(pipe.error == null && pipe.eof && pipe.rate == 11025 && got == 4000, "archivo local: " + pipe.error + " " + got);
            }
            // 5d. radio en vivo por HTTP chunked
            {
                FakeEnv env = new FakeEnv();
                AudioPipe pipe = new AudioPipe(env, 10);
                Thread t = new Thread(() -> pipe.run(root + "/live", null));
                t.start();
                long got = drain(pipe, env, 40000, 15000);
                check(pipe.live && got >= 40000 && pipe.error == null, "chunked en vivo: " + got + " " + pipe.error);
                env.active = false;
                pipe.abort();
                waitFor(t, 5000);
            }
            // 5e. contenido que no es audio
            {
                FakeEnv env = new FakeEnv();
                AudioPipe pipe = new AudioPipe(env, 10);
                Thread t = new Thread(() -> pipe.run(root + "/junk", null));
                t.start();
                long t0 = System.currentTimeMillis();
                while (pipe.error == null && !pipe.eof && System.currentTimeMillis() - t0 < 10000) Thread.sleep(20);
                check(pipe.error != null, "texto no es audio: eof=" + pipe.eof);
                waitFor(t, 5000);
            }
            // 5f. OGG en vivo no se admite
            {
                FakeEnv env = new FakeEnv();
                AudioPipe pipe = new AudioPipe(env, 10);
                Thread t = new Thread(() -> pipe.run(root + "/ogglive", null));
                t.start();
                long t0 = System.currentTimeMillis();
                while (pipe.error == null && System.currentTimeMillis() - t0 < 10000) Thread.sleep(20);
                check(pipe.error != null && "live_ogg".equals(pipe.error.getMessage()), "ogg en vivo: " + pipe.error);
                waitFor(t, 5000);
            }

            // ---- 6. la sesión del navegador
            List<String> played = new ArrayList<>();
            BrowserSession.Audio audio = new BrowserSession.Audio() {
                public void playUrl(String url, String title) { played.add(url + "|" + title); }
                public void playFile(Path f) { played.add("file:" + f.getFileName()); }
            public void playVideoUrl(String url, String title) { played.add("video:" + url + "|" + title); }
            public void playVideoFile(Path f) { played.add("videofile:" + f.getFileName()); }
            };
            BrowserSession s = new BrowserSession(audio);

            s.go(root + "/live"); waitLoad(s);
            check(played.size() == 1 && played.get(0).startsWith(root + "/live|"), "radio en vivo se reproduce: " + played);
            check(s.url().startsWith("hs:track?src=") && s.url().endsWith("&live=1"), "página de pista en vivo: " + s.url());
            check(text(s.doc()).toLowerCase().contains("vivo") || text(s.doc()).toLowerCase().contains("live"), "la página dice en vivo: " + text(s.doc()));

            s.go(root + "/fixed.mp3"); waitLoad(s);
            check(played.size() == 2 && !s.url().endsWith("&live=1"), "mp3 normal no es en vivo: " + s.url());

            played.clear();
            s.go(root + "/radio.m3u"); waitLoad(s);
            check(played.size() == 1 && played.get(0).equals(root + "/live|Mi Emisora"), ".m3u: " + played);
            check(s.url().endsWith("&live=1"), ".m3u en vivo: " + s.url());

            played.clear();
            s.go(root + "/radio.pls"); waitLoad(s);
            check(played.size() == 1 && played.get(0).equals(root + "/live|Emisora PLS"), ".pls: " + played);

            played.clear();
            s.go(root + "/rel.m3u"); waitLoad(s);
            check(played.size() == 1 && played.get(0).startsWith(root + "/live|"), ".m3u con ruta relativa: " + played);

            played.clear();
            s.go(root + "/hls.m3u8"); waitLoad(s);
            check(played.isEmpty() && text(s.doc()).contains("HLS"), "HLS se rechaza con aviso: " + played);

            s.go(root + "/vacia.m3u"); waitLoad(s);
            check(played.isEmpty() && !text(s.doc()).isBlank(), "lista vacía avisa");

            s.go(root + "/aac"); waitLoad(s);
            check(played.isEmpty() && text(s.doc()).contains("AAC"), "AAC en vivo se rechaza con aviso: " + played);

            // ICY a través de la sesión (sin extensión ni tipo conocido)
            try (Icy icy = new Icy(false)) {
                s.go("http://127.0.0.1:" + icy.port() + "/;stream.nsv"); waitLoad(s);
                check(played.size() == 1 && played.get(0).endsWith("|Radio Prueba"), "ICY por la sesión usa el nombre de la emisora: " + played);
                check(s.url().endsWith("&live=1"), "ICY en vivo: " + s.url());
            }
        } finally {
            srv.stop(0);
        }
        System.out.println(fails == 0 ? "TODO OK" : fails + " fallos");
        System.exit(fails == 0 ? 0 : 1);
    }
}
