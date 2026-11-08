import com.example.beautifuldecor.browser.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.example.beautifuldecor.browser.audio.WavSource;

public class BrowserTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLO: " + m); } }

    static final Layout.Metrics M = new Layout.Metrics() {
        public int width(String t, int f, int sc) { return (int) Math.round(t.length() * 5.0 * sc / 100.0); }
        public int lineHeight(int sc) { return Math.round(9 * sc / 100f) + 2; }
    };
    static final Layout.Images IMG = src -> src.endsWith("big.png") ? new int[]{1000, 500} : src.endsWith("bad.png") ? new int[]{0, 0} : null;

    static Page lay(String html, int w) {
        Node doc = HtmlParser.parse(html);
        Css.Sheet sheet = new Css.Sheet();
        for (Node s : all(doc, "style")) sheet.add(s.rawText());
        return Layout.layout(doc, "https://ex.com/dir/page.html", sheet, w, M, IMG, 100);
    }
    static List<Node> all(Node n, String tag) {
        List<Node> r = new ArrayList<>();
        if (n.is(tag)) r.add(n);
        for (Node c : n.children) r.addAll(all(c, tag));
        return r;
    }
    static String texts(Page p) {
        StringBuilder sb = new StringBuilder();
        for (Page.Item i : p.items) if (i.kind == Page.TEXT) sb.append('[').append(i.text).append(']');
        return sb.toString();
    }


    static byte[] wav(int ch, int rate, int bits, int fmtTag, byte[] data, boolean extraChunk, long dataLen) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(o);
        d.writeBytes("RIFF"); d.writeInt(0); d.writeBytes("WAVE");
        if (extraChunk) { d.writeBytes("LIST"); d.writeInt(Integer.reverseBytes(3)); d.write(new byte[]{1, 2, 3, 0}); }
        d.writeBytes("fmt "); d.writeInt(Integer.reverseBytes(16));
        d.writeShort(Short.reverseBytes((short) fmtTag)); d.writeShort(Short.reverseBytes((short) ch));
        d.writeInt(Integer.reverseBytes(rate)); d.writeInt(Integer.reverseBytes(rate * ch * bits / 8));
        d.writeShort(Short.reverseBytes((short) (ch * bits / 8))); d.writeShort(Short.reverseBytes((short) bits));
        d.writeBytes("data"); d.writeInt(Integer.reverseBytes((int) dataLen)); d.write(data);
        return o.toByteArray();
    }
    static byte[] readAll(WavSource w) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[64]; int n;
        while ((n = w.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }
    static void wavTests() throws Exception {
        byte[] pcm16 = {(byte) 0xE8, 0x03, 0x18, (byte) 0xFC, (byte) 0xD0, 0x07, 0x30, (byte) 0xF8};
        try (WavSource w = new WavSource(new ByteArrayInputStream(wav(2, 44100, 16, 1, pcm16, false, pcm16.length)))) {
            check(w.channels() == 2 && w.sampleRate() == 44100, "wav 16 estéreo cabecera");
            check(Arrays.equals(readAll(w), pcm16), "wav 16 datos");
        }
        try (WavSource w = new WavSource(new ByteArrayInputStream(wav(1, 22050, 16, 1, pcm16, true, pcm16.length)))) {
            check(w.channels() == 1 && w.sampleRate() == 22050, "wav con chunk LIST");
            check(Arrays.equals(readAll(w), pcm16), "wav con LIST datos");
        }
        try (WavSource w = new WavSource(new ByteArrayInputStream(wav(1, 8000, 8, 1, new byte[]{(byte) 128, (byte) 255, 0}, false, 3)))) {
            byte[] r = readAll(w);
            check(r.length == 6 && ((r[0] & 255) | (r[1] << 8)) == 0 && (short) ((r[2] & 255) | (r[3] << 8)) == 32512 && (short) ((r[4] & 255) | (r[5] << 8)) == -32768, "wav 8 bits: " + Arrays.toString(r));
        }
        try (WavSource w = new WavSource(new ByteArrayInputStream(wav(1, 8000, 24, 1, new byte[]{0x56, 0x34, 0x12}, false, 3)))) {
            byte[] r = readAll(w);
            check(r.length == 2 && (short) ((r[0] & 255) | (r[1] << 8)) == 0x1234, "wav 24 bits");
        }
        int half = Float.floatToIntBits(0.5f);
        byte[] f = {(byte) half, (byte) (half >> 8), (byte) (half >> 16), (byte) (half >> 24)};
        try (WavSource w = new WavSource(new ByteArrayInputStream(wav(1, 8000, 32, 3, f, false, 4)))) {
            byte[] r = readAll(w);
            check(r.length == 2 && (short) ((r[0] & 255) | (r[1] << 8)) == 16384, "wav float32");
        }
        try (WavSource w = new WavSource(new ByteArrayInputStream(wav(2, 44100, 16, 1, pcm16, false, 0xFFFFFFFFL)))) {
            check(Arrays.equals(readAll(w), pcm16), "wav de largo desconocido");
        }
        try { new WavSource(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})).close(); check(false, "wav falso debía fallar"); }
        catch (IOException expected) { }
        try { new WavSource(new ByteArrayInputStream(wav(2, 44100, 16, 85, pcm16, false, 8))).close(); check(false, "wav comprimido debía fallar"); }
        catch (IOException expected) { }
    }

    public static void main(String[] a) throws Exception {
        wavTests();
        // ---- Url
        check("https://ex.com/a/b.html".equals(Url.resolve("https://ex.com/a/c.html", "b.html")), "resolve relativo");
        check("https://ex.com/x".equals(Url.resolve("https://ex.com/a/c.html", "/x")), "resolve raíz");
        check("https://o.com/".equals(Url.resolve("https://ex.com/a", "//o.com/")), "resolve //");
        check(Url.resolve("https://ex.com/a", "javascript:void(0)") == null, "javascript:");
        check(Url.resolve("https://ex.com/a", "mailto:x@y.z") == null, "mailto");
        check("#top".equals(Url.resolve("https://ex.com/a", "#top")), "ancla");
        check(Url.normalizeInput("ejemplo.com").startsWith("https://"), "dominio -> https: " + Url.normalizeInput("ejemplo.com"));
        check(Url.normalizeInput("localhost:8080/x").startsWith("http://"), "localhost -> http: " + Url.normalizeInput("localhost:8080/x"));
        check(Url.normalizeInput("192.168.1.2").startsWith("http://"), "ip -> http");
        check(Url.normalizeInput("recetas de pan").contains("duckduckgo"), "búsqueda: " + Url.normalizeInput("recetas de pan"));
        System.out.println("unwrap: " + Url.unwrapRedirect("https://duckduckgo.com/l/?uddg=https%3A%2F%2Fex.com%2Fa%3Fb%3D1&rut=x"));
        check("https://ex.com/a?b=1".equals(Url.unwrapRedirect("https://duckduckgo.com/l/?uddg=https%3A%2F%2Fex.com%2Fa%3Fb%3D1&rut=x")), "unwrap ddg");
        check(Url.isAudioName("a.MP3") && Url.isAudioName("b.ogg") && Url.isAudioName("c.wav") && !Url.isAudioName("d.zip"), "isAudioName");
        System.out.println("fileName: " + Url.fileName("https://ex.com/a/mi%20canci%C3%B3n.mp3?x=1", null, null));
        System.out.println("sanitize: " + Url.sanitizeFileName("../../etc/pa:ss*wd?.txt"));
        String sn = Url.sanitizeFileName("../../etc/pa:ss*wd?.txt"); check(!sn.contains("/") && !sn.contains("\\") && !sn.startsWith(".") && !sn.contains(":"), "sanitize seguro: " + sn);

        // ---- parse + layout
        String html = "<html><head><title>Hola &amp; adiós</title><style>.oculto{display:none} p.x{margin-left:20px}</style></head><body>"
            + "<h1>Título</h1><p>Un párrafo con <a href='/otra'>enlace</a> y <b>negrita</b> y <i>cursiva</i>.</p>"
            + "<p class='oculto'>NO SE VE</p><script>alert('no')</script>"
            + "<ul><li>uno</li><li>dos<ul><li>anidado</li></ul></li></ul><ol start='3'><li>tres</li><li>cuatro</li></ol>"
            + "<pre>  a\n    b\n</pre><hr><img src='big.png' alt='grande'><img src='bad.png' alt='roto'><img src='t.gif' width=1 height=1>"
            + "<table border=1><tr><th>A</th><th>B</th></tr><tr><td>1</td><td colspan=1>dos dos dos</td></tr></table>"
            + "<input type=text placeholder='buscar'><button>Ir</button><audio src='/m/cancion.mp3'></audio><video src='/m/clip.mp4'></video>"
            + "<p id='fin'>Final</p></body></html>";
        Page p = lay(html, 300);
        System.out.println("título: " + p.title + " alto " + p.height + " items " + p.items.size());
        System.out.println(texts(p));
        check("Hola & adiós".equals(p.title), "título decodificado");
        String t = texts(p);
        check(!t.contains("NO SE VE") && !t.contains("alert"), "oculto/script fuera");
        check(t.contains("[Título]"), "h1");
        check(t.contains("anidado"), "lista anidada");
        check(t.contains("3.") && t.contains("4."), "ol start=3: " + t);
        check(t.contains("[roto]"), "alt de imagen rota");
        check(p.anchors.containsKey("fin"), "ancla fin");
        boolean link = false, img = false, ctl = false, play = false, playVideo = false;
        for (Page.Item i : p.items) {
            if (i.kind == Page.TEXT && "https://ex.com/otra".equals(i.href)) link = true;
            if (i.kind == Page.IMAGE && i.src.endsWith("big.png")) { img = true; check(i.w <= 288 && i.h * 2 == i.w, "imagen ajustada " + i.w + "x" + i.h); }
            if (i.kind == Page.CONTROL) ctl = true;
            if (i.href != null && i.href.startsWith("hs:play?src=")) play = true;
            if (i.href != null && i.href.startsWith("hs:playvideo?src=") && i.href.contains("clip.mp4")) playVideo = true;
            check(i.x >= 0 && i.y >= 0 && i.w >= 0 && i.h >= 0, "geometría negativa en " + i.kind + " " + i.text);
        }
        check(link, "enlace resuelto"); check(img, "imagen"); check(ctl, "controles"); check(play, "audio -> hs:play"); check(playVideo, "video -> hs:playvideo");
        check(p.imageSrcs.contains("https://ex.com/dir/big.png"), "imageSrcs: " + p.imageSrcs);
        check(!p.imageSrcs.contains("https://ex.com/dir/t.gif"), "pixel de seguimiento ignorado");
        // el texto no se sale del ancho
        int maxRight = 0;
        for (Page.Item i : p.items) if (i.kind == Page.TEXT) maxRight = Math.max(maxRight, i.x + i.w);
        check(maxRight <= 300, "texto dentro del ancho: " + maxRight);

        // palabra larguísima
        Page q = lay("<p>" + "x".repeat(500) + "</p>", 200);
        int mr = 0; for (Page.Item i : q.items) if (i.kind == Page.TEXT) mr = Math.max(mr, i.x + i.w);
        check(mr <= 200, "palabra larga partida: " + mr);
        // CJK
        Page c = lay("<p>" + "日本語のテキスト".repeat(20) + "</p>", 150);
        int rows = new TreeSet<>(){{ for (Page.Item i : c.items) if (i.kind == Page.TEXT) add(i.y); }}.size();
        check(rows > 1, "CJK parte líneas: " + rows);
        // tablas anidadas profundas no revientan
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 12; i++) deep.append("<table><tr><td>");
        deep.append("fondo");
        for (int i = 0; i < 12; i++) deep.append("</td></tr></table>");
        Page d = lay(deep.toString(), 300);
        check(texts(d).contains("fondo"), "tablas anidadas");
        // basura
        Page g = lay("<<< <p>>>&&&<table><td><tr></p></b></i>texto suelto<select><option>uno<option selected>dos</select>", 200);
        System.out.println("roto: " + texts(g)); check(texts(g).contains("texto suelto"), "html roto");
        // vacío
        Page e = lay("", 200);
        check(e.height >= 0, "vacío");
        // página enorme
        StringBuilder big = new StringBuilder("<body>");
        for (int i = 0; i < 20000; i++) big.append("<p>palabra ").append(i).append(" otra más</p>");
        long t0 = System.currentTimeMillis();
        Page bg = lay(big.toString(), 300);
        System.out.println("enorme: " + bg.items.size() + " items en " + (System.currentTimeMillis() - t0) + " ms, alto " + bg.height);
        check(bg.items.size() <= 90500, "tope de items");

        // ---- Http contra servidor local
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = srv.getAddress().getPort();
        String root = "http://127.0.0.1:" + port;
        srv.createContext("/redir", ex -> { ex.getResponseHeaders().add("Location", "/dest"); ex.sendResponseHeaders(302, -1); ex.close(); });
        srv.createContext("/loop", ex -> { ex.getResponseHeaders().add("Location", "/loop"); ex.sendResponseHeaders(302, -1); ex.close(); });
        srv.createContext("/dest", ex -> { byte[] b = "<p>destino</p>".getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8"); ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        srv.createContext("/gz", ex -> {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream g2 = new GZIPOutputStream(bo)) { g2.write("<p>comprimido ñandú</p>".getBytes(StandardCharsets.UTF_8)); }
            byte[] b = bo.toByteArray();
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8"); ex.getResponseHeaders().add("Content-Encoding", "gzip");
            ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        srv.createContext("/latin", ex -> { byte[] b = "<html><head><meta charset='iso-8859-1'></head><p>camión</p>".getBytes(StandardCharsets.ISO_8859_1); ex.getResponseHeaders().add("Content-Type", "text/html"); ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        srv.createContext("/cookie", ex -> {
            String ck = ex.getRequestHeaders().getFirst("Cookie");
            byte[] b = ("cookie=" + ck).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Set-Cookie", "sid=abc; Path=/");
            ex.getResponseHeaders().add("Content-Type", "text/plain");
            ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        srv.createContext("/404", ex -> { byte[] b = "<p>no hay</p>".getBytes(); ex.getResponseHeaders().add("Content-Type", "text/html"); ex.sendResponseHeaders(404, b.length); ex.getResponseBody().write(b); ex.close(); });
        srv.start();
        try {
            try (Http.Response r = Http.get(root + "/redir", null)) {
                check(r.url.endsWith("/dest"), "redirección: " + r.url);
                check(r.status == 200, "status " + r.status);
                String body = Http.decode(Http.readLimited(r.body, 1 << 20, new boolean[1]), r.charset, true);
                check(body.contains("destino"), "cuerpo redirección");
            }
            try (Http.Response r = Http.get(root + "/gz", null)) {
                String body = Http.decode(Http.readLimited(r.body, 1 << 20, new boolean[1]), r.charset, true);
                check(body.contains("comprimido ñandú"), "gzip + utf8: " + body);
            }
            try (Http.Response r = Http.get(root + "/latin", null)) {
                String body = Http.decode(Http.readLimited(r.body, 1 << 20, new boolean[1]), r.charset, true);
                check(body.contains("camión"), "charset por meta: " + body);
            }
            try (Http.Response r = Http.get(root + "/404", null)) {
                check(r.status == 404, "404 pasa con su status");
            }
            try { Http.get(root + "/loop", null).close(); check(false, "bucle de redirecciones debía fallar"); }
            catch (IOException ex) { System.out.println("bucle ok: " + ex.getMessage()); }
            try (Http.Response r1 = Http.get(root + "/cookie", null)) { r1.body.readAllBytes(); }
            try (Http.Response r2 = Http.get(root + "/cookie", null)) {
                String b = new String(r2.body.readAllBytes(), StandardCharsets.UTF_8);
                check(b.contains("sid=abc"), "cookie de sesión: " + b);
            }
            try { Http.get("http://127.0.0.1:1/", null).close(); check(false, "conexión rechazada debía fallar"); }
            catch (IOException ex) { System.out.println("rechazo ok: " + ex.getMessage()); }
        } finally { srv.stop(0); }

        System.out.println(fails == 0 ? "TODO OK" : fails + " fallos");
        System.exit(fails == 0 ? 0 : 1);
    }
}
