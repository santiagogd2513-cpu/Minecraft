import com.example.beautifuldecor.browser.BrowserSession;
import com.example.beautifuldecor.browser.Css;
import com.example.beautifuldecor.browser.Layout;
import com.example.beautifuldecor.browser.Node;
import com.example.beautifuldecor.browser.Page;
import com.example.beautifuldecor.browser.PageRuntime;
import com.example.beautifuldecor.browser.ScrollAnchor;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Maneja una BrowserSession contra un servidor local, como lo haría la pantalla. Uso: SessionRun acciones.sact */
public class SessionRun {
    static PrintStream ps;
    static BrowserSession session;
    static final Layout.Metrics METRICS = new Layout.Metrics() {
        @Override
        public int width(String text, int flags, int scale) {
            return text.length() * 6 * scale / 100;
        }

        @Override
        public int lineHeight(int scale) {
            return 10 * scale / 100;
        }
    };

    static Page layout() {
        PageRuntime rt = session.runtime();
        if (rt != null && !rt.tryLock()) {
            sleep(50);
            return layout();
        }
        try {
            return Layout.layout(session.doc(), session.baseUrl(), session.sheet(), 600, METRICS, src -> new int[] {0, 0}, 100);
        } finally {
            if (rt != null) {
                rt.unlock();
            }
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void waitLoaded() {
        sleep(100);
        for (int i = 0; i < 300 && session.loading(); i++) {
            sleep(50);
        }
        sleep(150);
    }

    static String mark = "";
    static int drained;
    static int viewW = 600;
    static int viewH = 100;
    static int scrollY;
    static Page lastPage;
    static ScrollAnchor captured;

    static String describe(Node n) {
        if (n == null) {
            return "-";
        }
        String id = n.attr("id");
        return n.tag + (id == null ? "" : "#" + id);
    }

    static String pageText() {
        Page p = layout();
        StringBuilder sb = new StringBuilder();
        for (Page.Item it : p.items) {
            if (it.kind == Page.TEXT || it.kind == Page.CONTROL) {
                if (it.text != null && !it.text.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(" | ");
                    }
                    sb.append(it.kind == Page.CONTROL ? "[" + it.text + "]" : it.text);
                }
            }
        }
        return sb.toString();
    }

    static Page.Item find(Page p, String text) {
        for (Page.Item it : p.items) {
            if (it.text != null && it.text.contains(text) && it.kind != Page.RECT) {
                return it;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        ps = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        List<String> acts = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        session = new BrowserSession(new BrowserSession.Audio() {
            @Override
            public void playUrl(String url, String title) {
            }

            @Override
            public void playFile(Path file) {
            }

            @Override
            public void playVideoUrl(String url, String title) {
            }

            @Override
            public void playVideoFile(Path file) {
            }
        });
        for (String line : acts) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            int sp = l.indexOf(' ');
            String cmd = sp < 0 ? l : l.substring(0, sp);
            String arg = sp < 0 ? "" : l.substring(sp + 1);
            PageRuntime rt = session.runtime();
            switch (cmd) {
                case "go" -> {
                    session.go(arg);
                    waitLoaded();
                }
                case "click" -> {
                    Page p = layout();
                    Page.Item it = find(p, arg);
                    if (it == null) {
                        ps.println("NO ITEM " + arg);
                        break;
                    }
                    int x = it.x + Math.max(1, it.w / 2);
                    int y = it.y + Math.max(1, it.h / 2);
                    Node n = p.nodeAt(x, y);
                    ps.println("CLICK " + arg + " -> " + (n == null ? "null" : n.tag));
                    if (rt != null) {
                        rt.click(n, x, y, 0, 0);
                    } else if (it.href != null) {
                        session.open(it.href);
                    }
                    waitLoaded();
                }
                case "type" -> {
                    ps.println("TYPE used=" + (rt != null && rt.typeText(arg, 0)));
                    sleep(150);
                }
                case "key" -> {
                    ps.println("KEY " + arg + " used=" + (rt != null && rt.keyDown(arg, arg, 0, 0, false)));
                    waitLoaded();
                }
                case "choose" -> {
                    String[] a = arg.split(" ");
                    Page p = layout();
                    Page.Item it = find(p, a[0]);
                    if (it != null && it.node != null && rt != null) {
                        PageRuntime.SelectInfo info = rt.selectInfo(it.node);
                        ps.println("OPTIONS " + String.join(",", info.labels));
                        rt.chooseOption(it.node, Integer.parseInt(a[1]));
                        sleep(200);
                    } else {
                        ps.println("NO SELECT");
                    }
                }
                case "back" -> {
                    session.back();
                    waitLoaded();
                }
                case "forward" -> {
                    session.forward();
                    waitLoaded();
                }
                case "wait" -> sleep(Long.parseLong(arg));
                case "pause" -> session.setPaused(true);
                case "resume" -> session.setPaused(false);
                case "url" -> ps.println("URL " + session.url());
                case "title" -> ps.println("TITLE " + session.title());
                case "status" -> ps.println("STATUS " + session.status());
                case "history" -> ps.println("CANBACK " + session.canBack() + " CANFORWARD " + session.canForward());
                case "console" -> {
                    for (String c : session.consoleLines()) {
                        ps.println("CONSOLE " + c);
                    }
                }
                case "text" -> ps.println("PAGE " + pageText());
                case "mark" -> mark = pageText();
                case "compare" -> ps.println(mark.equals(pageText()) ? "SAME" : "CHANGED");
                case "scrollreq" -> ps.println("SCROLLREQ " + session.takeScrollRequest());
                case "drain" -> {
                    List<String> all = session.consoleLines();
                    for (int i = drained; i < all.size(); i++) {
                        ps.println("NEW " + all.get(i));
                    }
                    drained = all.size();
                }
                case "view" -> {
                    String[] p = arg.split(" ");
                    viewW = Integer.parseInt(p[0]);
                    viewH = Integer.parseInt(p[1]);
                    session.setViewport(viewW, viewH, 0, scrollY);
                }
                case "scroll" -> {
                    scrollY = Integer.parseInt(arg.trim());
                    session.setViewport(viewW, viewH, 0, scrollY);
                }
                case "publish" -> {
                    lastPage = layout();
                    session.publishLayout(lastPage);
                    ps.println("PUBLISHED height=" + lastPage.height + " items=" + lastPage.items.size());
                }
                case "cap" -> {
                    captured = ScrollAnchor.capture(lastPage, scrollY);
                    ps.println("ANCHOR " + (captured == null ? "none" : describe(captured.node) + " ordinal=" + captured.ordinal + " offset=" + captured.offset));
                }
                case "restore" -> {
                    Page np = layout();
                    float v = captured == null ? -1f : captured.restore(np);
                    ps.println("RESTORED " + v + " (antes " + scrollY + ", la página pasó de " + (lastPage == null ? -1 : lastPage.height) + " a " + np.height + ")");
                    lastPage = np;
                    session.publishLayout(np);
                }
                case "forget" -> {
                    ps.println("NEW-NODE-AFTER " + (ScrollAnchor.capture(lastPage, scrollY) == null ? "none" : "some"));
                }
                case "anchor" -> ps.println("ANCHOR " + session.takeAnchor());
                default -> ps.println("?? " + l);
            }
            ps.flush();
        }
        if (session.runtime() != null) {
            session.runtime().close();
        }
        ps.flush();
        System.exit(0);
    }
}
