import com.example.beautifuldecor.browser.HtmlParser;
import com.example.beautifuldecor.browser.Node;
import com.example.beautifuldecor.browser.js.DomHost;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/** Carga una página, ejecuta sus scripts y las acciones de un archivo .act, e imprime lo que pase. Uso: DomRun pagina.html [acciones.act] */
public class DomRun {
    static PrintStream ps;
    static String url = "http://test.local/dir/page.html?x=1#top";
    // medidas de mentira: cada elemento con data-rect="x,y,ancho,alto" tiene esa posición en la página
    static int[] vp = {1024, 768, 0, 0};
    static int[] pageSize;
    static boolean layoutFresh = true;
    static DomHost hostRef;
    static int historyLen = 1;
    // tiempo virtual: los temporizadores y las respuestas de red "falsas" se ejecutan al avanzar el reloj con la acción "wait"
    static long clock;
    static long seq;
    static final PriorityQueue<long[]> QUEUE = new PriorityQueue<>((x, y) -> x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(x[1], y[1]));
    static final java.util.Map<Long, Runnable> TASKS = new java.util.HashMap<>();

    static void later(long delay, Runnable r) {
        long id = ++seq;
        TASKS.put(id, r);
        QUEUE.add(new long[] {clock + Math.max(0, delay), id});
    }

    static void advance(long ms) {
        long target = clock + ms;
        while (!QUEUE.isEmpty() && QUEUE.peek()[0] <= target) {
            long[] t = QUEUE.poll();
            clock = Math.max(clock, t[0]);
            Runnable r = TASKS.remove(t[1]);
            if (r != null) {
                try {
                    r.run();
                } catch (RuntimeException | Error e) {
                    ps.println("TASK FAILED " + e);
                }
            }
        }
        clock = target;
    }

    static DomHost.NetResult mock(DomHost.NetRequest q) {
        DomHost.NetResult r = new DomHost.NetResult();
        r.url = q.url;
        String path = q.url.replaceFirst("^http://test\\.local", "");
        String type = "text/plain";
        String body = "";
        r.status = 200;
        if (path.startsWith("/api/json")) {
            type = "application/json";
            body = "{\"msg\":\"hola\",\"n\":[1,2,3]}";
        } else if (path.startsWith("/api/text")) {
            type = "text/plain; charset=utf-8";
            body = "texto plano \u00f1";
        } else if (path.startsWith("/api/slow")) {
            type = "text/plain";
            body = "lento";
        } else if (path.startsWith("/api/redirected")) {
            r.url = "http://test.local/api/json";
            type = "application/json";
            body = "{\"redirected\":true}";
        } else if (path.startsWith("/api/form")) {
            type = "application/x-www-form-urlencoded";
            body = "a=1&b=%C3%B1";
        } else if (path.startsWith("/api/echo")) {
            java.util.TreeMap<String, String> hs = new java.util.TreeMap<>();
            for (java.util.Map.Entry<String, String> e : q.headers.entrySet()) {
                hs.put(e.getKey().toLowerCase(), e.getValue());
            }
            StringBuilder sb = new StringBuilder(q.method);
            for (java.util.Map.Entry<String, String> e : hs.entrySet()) {
                String v = e.getValue();
                if (v.startsWith("multipart/form-data; boundary=")) {
                    v = "multipart/form-data; boundary=...";
                }
                sb.append(" ").append(e.getKey()).append("=").append(v).append(";");
            }
            String bd = q.body == null ? "" : new String(q.body, StandardCharsets.UTF_8);
            bd = bd.replaceAll("----HSBrowserFormBoundary[0-9a-f]+", "BOUNDARY").replace("\r\n", "~");
            sb.append(" body=").append(bd);
            body = sb.toString();
        } else if (path.startsWith("/api/fail")) {
            r.error = "connection refused";
            return r;
        } else {
            r.status = 404;
            body = "no existe " + path;
        }
        r.headers.put("content-type", java.util.List.of(type));
        r.headers.put("x-test", java.util.List.of("uno", "dos"));
        r.headers.put("set-cookie", java.util.List.of("secreto=1"));
        r.body = body.getBytes(StandardCharsets.UTF_8);
        return r;
    }

    public static void main(String[] args) throws Exception {
        String html = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        List<String> acts = args.length > 1 ? Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8) : new ArrayList<>();
        ps = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        int[] code = {0};
        Thread t = new Thread(null, () -> code[0] = run(html, acts), "dom", 512L << 20);
        t.start();
        t.join();
        ps.flush();
        System.exit(code[0]);
    }

    static Node byId(Node n, String id) {
        if (n.isElement() && id.equals(n.attr("id"))) {
            return n;
        }
        for (Node c : n.children) {
            Node r = byId(c, id);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    static void findAt(Node n, int x, int y, Node[] best) {
        String v = n.isElement() ? n.attr("data-rect") : null;
        if (v != null) {
            String[] p = v.split(",");
            int rx = Integer.parseInt(p[0].trim());
            int ry = Integer.parseInt(p[1].trim());
            if (x >= rx && x < rx + Integer.parseInt(p[2].trim()) && y >= ry && y < ry + Integer.parseInt(p[3].trim())) {
                best[0] = n;
            }
        }
        for (Node c : n.children) {
            findAt(c, x, y, best);
        }
    }

    static void addStyles(Node n, com.example.beautifuldecor.browser.Css.Sheet sheet) {
        if (n.is("style")) {
            sheet.add(n.rawText());
        }
        for (Node c : n.children) {
            addStyles(c, sheet);
        }
    }

    static int run(String html, List<String> acts) {
        Node doc = HtmlParser.parse(html);
        com.example.beautifuldecor.browser.Css.Sheet sheet = new com.example.beautifuldecor.browser.Css.Sheet();
        addStyles(doc, sheet);
        DomHost.Env env = new DomHost.Env() {
            @Override
            public String url() {
                return url;
            }

            @Override
            public com.example.beautifuldecor.browser.Css.Sheet sheet() {
                return sheet;
            }

            @Override
            public void console(String level, String text) {
                ps.println(level.equals("log") || level.equals("info") ? text : "[" + level + "] " + text);
            }

            @Override
            public void navigate(String u, boolean replace) {
                ps.println("NAV " + (replace ? "replace " : "") + u);
            }

            @Override
            public void follow(String u, boolean download) {
                ps.println("FOLLOW " + (download ? "download " : "") + u);
            }

            @Override
            public void reload() {
                ps.println("RELOAD");
            }

            @Override
            public void historyGo(int d) {
                ps.println("HISTORY go " + d);
            }

            @Override
            public int historyLength() {
                return historyLen;
            }

            @Override
            public void historyPush(String u, Object state, boolean replace) {
                if (u != null) {
                    url = u;
                }
                if (!replace) {
                    historyLen++;
                }
                ps.println("HISTORY " + (replace ? "replace " : "push ") + (u == null ? "-" : u));
            }

            @Override
            public void submit(DomHost.Submission s) {
                StringBuilder sb = new StringBuilder();
                for (String[] f : s.fields) {
                    sb.append(f[0]).append('=').append(f[1]).append(';');
                }
                ps.println("SUBMIT " + s.method + " " + s.action + " [" + s.enctype + "] " + sb);
            }

            @Override
            public void alert(String m) {
                ps.println("ALERT " + m);
            }

            @Override
            public boolean confirm(String m) {
                ps.println("CONFIRM " + m);
                return true;
            }

            @Override
            public String prompt(String m, String def) {
                ps.println("PROMPT " + m);
                return def;
            }

            @Override
            public void status(String m) {
                ps.println("STATUS " + m);
            }

            @Override
            public void titleChanged(String m) {
                ps.println("TITLE " + m);
            }

            @Override
            public void scrollTo(int x, int y) {
                ps.println("SCROLL " + x + "," + y);
                int max = pageSize == null ? y : Math.max(0, pageSize[1] - vp[1]);
                int ny = Math.max(0, Math.min(y, max));
                if (ny != vp[3]) {
                    vp[3] = ny;
                    later(0, () -> hostRef.viewportChanged(true, false));
                }
            }

            @Override
            public int[] viewport() {
                return vp.clone();
            }

            @Override
            public int[] pageSize() {
                return pageSize == null ? null : new int[] {pageSize[0], pageSize[1], layoutFresh ? 1 : 0};
            }

            @Override
            public int[] rect(Node n) {
                String v = n.attr("data-rect");
                if (v == null || (!layoutFresh && n.attr("data-late") != null)) {
                    return null;                    // los elementos "late" no existían cuando se acomodó por última vez
                }
                String[] p = v.split(",");
                return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())};
            }

            @Override
            public Node nodeAt(int x, int y) {
                Node[] best = new Node[1];
                findAt(doc, x, y, best);
                return best[0];
            }

            @Override
            public boolean schedule(Runnable task, long delayMs) {
                later(delayMs, task);
                return true;
            }

            @Override
            public Runnable fetch(DomHost.NetRequest q, Consumer<DomHost.NetResult> done) {
                boolean[] cancelled = {false};
                later(q.url.contains("/api/slow") ? 50 : 5, () -> {
                    if (!cancelled[0]) {
                        done.accept(mock(q));
                    }
                });
                return () -> {
                    cancelled[0] = true;
                    ps.println("NET cancelled " + q.url);
                };
            }

            @Override
            public DomHost.NetResult fetchSync(DomHost.NetRequest q) {
                return mock(q);
            }
        };
        DomHost host = new DomHost(doc, env);
        hostRef = host;
        for (Node c : doc.children) {
            if (c.is("html")) {
                String pg = c.attr("data-page");
                if (pg != null) {
                    String[] p = pg.split(",");
                    pageSize = new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
                }
                String vw = c.attr("data-viewport");
                if (vw != null) {
                    String[] p = vw.split(",");
                    vp[0] = Integer.parseInt(p[0].trim());
                    vp[1] = Integer.parseInt(p[1].trim());
                }
            }
        }
        try {
            while (true) {
                List<Node> pending = host.pendingScripts();
                if (pending.isEmpty()) {
                    break;
                }
                Node s = pending.get(0);
                if (!DomHost.runnable(s) || s.attr("src") != null) {
                    host.markDone(s);
                    continue;
                }
                host.runScript(s, s.rawText());
            }
            host.setReadyState("interactive");
            host.fire(false, "DOMContentLoaded");
            host.setReadyState("complete");
            host.fire(true, "load");
            for (String line : acts) {
                String l = line.strip();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                int sp = l.indexOf(' ');
                String cmd = sp < 0 ? l : l.substring(0, sp);
                String arg = sp < 0 ? "" : l.substring(sp + 1);
                switch (cmd) {
                    case "click" -> {
                        Node n = byId(host.document(), arg);
                        if (n == null) {
                            ps.println("NO NODE " + arg);
                        } else {
                            host.click(n, 5, 5, 0, 0);
                        }
                    }
                    case "rclick" -> host.click(byId(host.document(), arg), 5, 5, 2, 0);
                    case "focus" -> host.engine().eval("document.getElementById('" + arg + "').focus()");
                    case "type" -> host.typeText(arg.replace("\\n", "\n"), 0);
                    case "key" -> {
                        int mods = 0;
                        String k = arg;
                        while (true) {
                            if (k.startsWith("ctrl+")) {
                                mods |= DomHost.MOD_CTRL;
                                k = k.substring(5);
                            } else if (k.startsWith("shift+")) {
                                mods |= DomHost.MOD_SHIFT;
                                k = k.substring(6);
                            } else {
                                break;
                            }
                        }
                        boolean used = host.keyDown(k, k, 0, mods, false);
                        host.keyUp(k, k, 0, mods);
                        ps.println("KEY " + k + " used=" + used);
                    }
                    case "choose" -> {
                        String[] p = arg.split(" ");
                        host.chooseOption(byId(host.document(), p[0]), Integer.parseInt(p[1]));
                    }
                    case "move" -> host.mouseMove(byId(host.document(), arg), 3, 4, 0);
                    case "paste" -> host.paste(arg);
                    case "blur" -> host.blurFocus();
                    case "pop" -> host.firePopState(null);
                    case "wait" -> advance(Long.parseLong(arg));
                    case "scroll" -> {
                        int ny = Integer.parseInt(arg.trim());
                        int max = pageSize == null ? ny : Math.max(0, pageSize[1] - vp[1]);
                        vp[3] = Math.max(0, Math.min(ny, max));
                        host.viewportChanged(true, false);
                    }
                    case "resize" -> {
                        String[] p = arg.split(" ");
                        vp[0] = Integer.parseInt(p[0]);
                        vp[1] = Integer.parseInt(p[1]);
                        host.viewportChanged(false, true);
                    }
                    case "layout" -> host.viewportChanged(false, false);
                    case "pagesize" -> {
                        String[] p = arg.split(" ");
                        pageSize = new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
                    }
                    case "stale" -> layoutFresh = false;
                    case "fresh" -> layoutFresh = true;
                    case "eval" -> {
                        host.engine().eval(arg);
                        host.runJobs();
                    }
                    default -> ps.println("?? " + l);
                }
            }
            host.runJobs();
            return 0;
        } catch (RuntimeException ex) {
            ps.println("CRASH " + ex);
            ex.printStackTrace(ps);
            return 1;
        } finally {
            host.close();
        }
    }
}
