import com.example.beautifuldecor.browser.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SessionTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLO: " + m); } }
    static void send(HttpExchange ex, int code, String type, byte[] b, String... extra) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        for (int i = 0; i + 1 < extra.length; i += 2) ex.getResponseHeaders().add(extra[i], extra[i + 1]);
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
        ex.close();
    }
    static void wait(BrowserSession s) throws Exception {
        long t = System.currentTimeMillis();
        while (s.loading() && System.currentTimeMillis() - t < 15000) Thread.sleep(20);
        check(!s.loading(), "carga terminó");
    }
    static String text(Node n) { return n.textContent(); }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("hsgame");
        Downloads.init(tmp);
        check(Files.isDirectory(tmp.resolve("HS Downloads")), "carpeta HS Downloads creada");
        List<String> played = new ArrayList<>();
        BrowserSession.Audio audio = new BrowserSession.Audio() {
            public void playUrl(String url, String title) { played.add(url + "|" + title); }
            public void playFile(Path f) { played.add("file:" + f.getFileName()); }
            public void playVideoUrl(String url, String title) { played.add("video:" + url + "|" + title); }
            public void playVideoFile(Path f) { played.add("videofile:" + f.getFileName()); }
        };
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = srv.getAddress().getPort();
        String root = "http://127.0.0.1:" + port;
        srv.createContext("/page", ex -> send(ex, 200, "text/html; charset=utf-8",
            ("<html><head><title>Pagina</title><link rel=stylesheet href='/s.css'></head><body><h1>Hola</h1><p class=oculto>secreto</p><p id=fin>fin</p></body></html>").getBytes(StandardCharsets.UTF_8)));
        srv.createContext("/s.css", ex -> send(ex, 200, "text/css", ".oculto{display:none}".getBytes()));
        srv.createContext("/redir", ex -> { ex.getResponseHeaders().add("Location", "/page"); ex.sendResponseHeaders(302, -1); ex.close(); });
        srv.createContext("/song.mp3", ex -> send(ex, 200, "audio/mpeg", new byte[]{1, 2, 3}));
        srv.createContext("/clip.mp4", ex -> send(ex, 200, "video/mp4", new byte[]{1, 2, 3}));
        srv.createContext("/clip2", ex -> send(ex, 200, "application/octet-stream", new byte[]{1, 2, 3}, "Content-Disposition", "inline; filename=\"peli.mp4\""));
        srv.createContext("/noext", ex -> send(ex, 200, "application/octet-stream", new byte[]{1, 2, 3}, "Content-Disposition", "attachment; filename=\"mi archivo.bin\""));
        srv.createContext("/file.zip", ex -> send(ex, 200, "application/zip", "PK-fake-zip-data".getBytes()));
        srv.createContext("/plain", ex -> send(ex, 200, "text/plain; charset=utf-8", "línea1\nlínea2".getBytes(StandardCharsets.UTF_8)));
        srv.createContext("/nf", ex -> send(ex, 404, "text/html", "<p>no existe</p>".getBytes()));
        srv.createContext("/meta", ex -> send(ex, 200, "text/html", "<meta http-equiv='refresh' content='0; url=/page'><p>espera</p>".getBytes()));
        srv.createContext("/pic", ex -> send(ex, 200, "image/png", new byte[]{1, 2, 3}));
        srv.start();
        try {
            BrowserSession s = new BrowserSession(audio);
            check(Url.HOME.equals(s.url()), "empieza en hs:home");
            check(text(s.doc()).contains("HS Browser"), "home");
            s.go(root + "/redir");
            wait(s);
            check(s.url().equals(root + "/page"), "redirección actualiza la dirección: " + s.url());
            check("Pagina".equals(s.title()), "título: " + s.title());
            check(s.sheet().size() > 0, "hoja externa cargada: " + s.sheet().size());
            check(s.canBack(), "puede volver");
            s.setScroll(77);
            s.go(root + "/plain");
            wait(s);
            check(text(s.doc()).contains("línea1"), "texto plano: " + text(s.doc()));
            s.back();
            wait(s);
            check(s.url().equals(root + "/page"), "back -> page");
            check(s.takeScrollTarget() == 77, "scroll restaurado");
            s.forward();
            wait(s);
            check(s.url().equals(root + "/plain"), "forward -> plain");
            s.go(root + "/nf");
            wait(s);
            check(s.status().contains("404"), "status 404: " + s.status());
            check(text(s.doc()).contains("no existe"), "cuerpo del 404");
            s.go(root + "/meta");
            wait(s);
            check(s.url().equals(root + "/page"), "meta refresh: " + s.url());
            s.go(root + "/pic");
            wait(s);
            check(s.url().equals(root + "/pic"), "imagen conserva la dirección");
            int before = played.size();
            s.go(root + "/song.mp3");
            wait(s);
            check(played.size() == before + 1 && played.get(played.size() - 1).endsWith("song.mp3"), "audio reproducido: " + played);
            check(s.url().startsWith("hs:track?src="), "pista: " + s.url());
            int beforeVideo = played.size();
            s.go(root + "/clip.mp4");
            wait(s);
            check(played.size() == beforeVideo + 1 && played.get(played.size() - 1).startsWith("video:" + root + "/clip.mp4|"), "video MP4 reproducido: " + played);
            check(s.url().startsWith("hs:video?src="), "pagina del video: " + s.url());
            check(text(s.doc()).contains("clip.mp4"), "la pagina del video nombra el archivo: " + text(s.doc()));
            s.go(root + "/clip2");
            wait(s);
            check(played.size() == beforeVideo + 2 && played.get(played.size() - 1).startsWith("video:"), "video por nombre de la cabecera: " + played);
            s.go("hs:playvideo?src=" + Url.encode(root + "/clip.mp4"));
            wait(s);
            check(played.size() == beforeVideo + 3 && s.url().startsWith("hs:video?src="), "hs:playvideo: " + played + " " + s.url());
            s.go(root + "/file.zip");
            wait(s);
            check("hs:downloads".equals(s.url()), "descarga -> hs:downloads: " + s.url());
            Thread.sleep(600);
            s.go(root + "/file.zip");
            wait(s);
            s.go(root + "/noext");
            wait(s);
            Thread.sleep(800);
            List<String> names = new ArrayList<>();
            for (Downloads.FileInfo f : Downloads.files()) names.add(f.name);
            Collections.sort(names);
            System.out.println("archivos: " + names);
            check(names.contains("file.zip") && names.contains("file (1).zip") && names.contains("mi archivo.bin"), "nombres únicos");
            check(Files.readString(Downloads.folder().resolve("file.zip")).equals("PK-fake-zip-data"), "contenido guardado");
            s.go("hs:downloads");
            wait(s);
            check(text(s.doc()).contains("file.zip"), "lista de descargas");
            // desde una página web no se puede mandar a hs:cancel
            s.go(root + "/page");
            wait(s);
            s.open("hs:cancel?id=1");
            check(s.url().equals(root + "/page"), "hs: bloqueado desde la web");
            check(Url.resolve(root + "/page", "hs:downloads") == null, "resolve bloquea hs: en la web");
            // ancla
            s.open("#fin");
            check("fin".equals(s.takeAnchor()), "ancla");
            // fallo de conexión
            s.go("http://127.0.0.1:1/");
            wait(s);
            check(text(s.doc()).contains("conectar") || text(s.doc()).contains("Connection"), "error de conexión: " + text(s.doc()));
            // búsqueda
            check(Url.normalizeInput("hola mundo").startsWith("https://html.duckduckgo.com/html/?q=hola"), "búsqueda");
        } finally { srv.stop(0); }
        System.out.println(fails == 0 ? "TODO OK" : fails + " fallos");
        System.exit(fails == 0 ? 0 : 1);
    }
}
