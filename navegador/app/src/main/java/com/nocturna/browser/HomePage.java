package com.nocturna.browser;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Página de inicio: cielo nocturno, buscador y accesos rápidos (favoritos). */
final class HomePage {
    static final String URL = "nocturna://inicio";

    private HomePage() {}

    static String html(Store store) {
        List<String[]> tiles = new ArrayList<>();
        for (Store.Entry e : store.bookmarks()) {
            if (tiles.size() >= 8) break;
            tiles.add(new String[]{e.title(), e.url()});
        }
        String[][] defaults = {
                {"YouTube", "https://m.youtube.com"}, {"Google", "https://www.google.com"},
                {"GitHub", "https://github.com"}, {"Wikipedia", "https://es.wikipedia.org"},
                {"Modrinth", "https://modrinth.com"}, {"CurseForge", "https://www.curseforge.com/minecraft"},
                {"Reddit", "https://www.reddit.com"}, {"Gmail", "https://mail.google.com"},
        };
        for (String[] d : defaults) {
            if (tiles.size() >= 8) break;
            boolean dup = false;
            for (String[] t : tiles) if (t[1].equals(d[1])) dup = true;
            if (!dup) tiles.add(d);
        }
        StringBuilder grid = new StringBuilder();
        String[] palette = {"#3D8BFF", "#36D1FF", "#7B8CFF", "#35D08A", "#FFB547", "#FF6A7A", "#B58CFF", "#5CC8FF"};
        for (int i = 0; i < tiles.size(); i++) {
            String title = tiles.get(i)[0], url = tiles.get(i)[1];
            String host = Uri.parse(url).getHost();
            String letter = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase();
            grid.append("<a class='tile' href='").append(esc(url)).append("'>")
                    .append("<span class='ico' style='background:").append(palette[i % palette.length]).append("'>")
                    .append("<img src='https://www.google.com/s2/favicons?sz=64&domain=").append(esc(host == null ? "" : host))
                    .append("' onerror=\"this.remove()\">").append("<b>").append(esc(letter)).append("</b></span>")
                    .append("<span class='t'>").append(esc(title)).append("</span></a>");
        }
        String engine = Store.ENGINES[store.engine()][1];
        String engineName = Store.ENGINES[store.engine()][0];
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta name='color-scheme' content='dark'><title>Nocturna</title><style>"
                + "html,body{margin:0;height:100%;background:#070b13;color:#e6edf7;font-family:system-ui,sans-serif}"
                + "body{background:radial-gradient(1200px 600px at 80% -10%,#16306b 0%,rgba(7,11,19,0) 60%),"
                + "radial-gradient(900px 500px at -10% 110%,#0d2a4a 0%,rgba(7,11,19,0) 55%),#070b13}"
                + "#sky{position:fixed;inset:0;pointer-events:none}"
                + ".wrap{position:relative;max-width:560px;margin:0 auto;padding:12vh 20px 40px}"
                + ".logo{display:flex;align-items:center;gap:12px;justify-content:center;margin-bottom:26px}"
                + ".logo h1{font-size:34px;font-weight:600;letter-spacing:.5px;margin:0;"
                + "background:linear-gradient(90deg,#8fd3ff,#3d8bff);-webkit-background-clip:text;color:transparent}"
                + "form{display:flex;align-items:center;background:#121926;border:1px solid #263147;border-radius:28px;"
                + "padding:4px 6px 4px 18px;box-shadow:0 10px 40px rgba(61,139,255,.15)}"
                + "input{flex:1;background:transparent;border:0;outline:0;color:#e6edf7;font-size:17px;padding:12px 0}"
                + "button{border:0;border-radius:22px;background:linear-gradient(90deg,#3d8bff,#36d1ff);color:#06101f;"
                + "font-weight:700;padding:11px 18px;font-size:15px}"
                + ".hint{color:#6f86ae;font-size:12px;text-align:center;margin:10px 0 30px}"
                + ".grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px 8px}"
                + ".tile{display:flex;flex-direction:column;align-items:center;gap:8px;text-decoration:none;color:#c9d4e6}"
                + ".ico{width:54px;height:54px;border-radius:18px;display:flex;align-items:center;justify-content:center;"
                + "position:relative;box-shadow:0 6px 18px rgba(0,0,0,.35)}"
                + ".ico img{width:28px;height:28px;border-radius:6px;position:relative;z-index:1;background:#fff;padding:3px}"
                + ".ico b{position:absolute;color:#06101f;font-size:22px}"
                + ".t{font-size:12px;max-width:72px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
                + "</style></head><body><canvas id='sky'></canvas><div class='wrap'>"
                + "<div class='logo'><svg width='44' height='44' viewBox='0 0 108 108'><defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>"
                + "<stop offset='0' stop-color='#8fd3ff'/><stop offset='.5' stop-color='#3d8bff'/><stop offset='1' stop-color='#2a4fd6'/></linearGradient></defs>"
                + "<path fill='url(#g)' d='M58,30 A24,24 0 1,0 78,66 A19,19 0 1,1 58,30 Z'/>"
                + "<path fill='#fff' d='M70,32 L71.6,37.4 L77,39 L71.6,40.6 L70,46 L68.4,40.6 L63,39 L68.4,37.4 Z'/></svg>"
                + "<h1>Nocturna</h1></div>"
                + "<form onsubmit='go();return false'><input id='q' placeholder='Busca o escribe una dirección' autocomplete='off'>"
                + "<button>Ir</button></form><div class='hint'>Buscando con " + esc(engineName) + "</div>"
                + "<div class='grid'>" + grid + "</div></div>"
                + "<script>"
                + "function go(){var q=document.getElementById('q').value.trim();if(!q)return;"
                + "var url=/^[a-z]+:\\/\\//i.test(q)?q:(/^[^\\s]+\\.[^\\s]{2,}$/.test(q)?'https://'+q:'" + engine + "'+encodeURIComponent(q));"
                + "location.href=url;}"
                // estrellas que titilan
                + "var c=document.getElementById('sky'),x=c.getContext('2d'),S=[];"
                + "function rs(){c.width=innerWidth*devicePixelRatio;c.height=innerHeight*devicePixelRatio;S=[];"
                + "for(var i=0;i<140;i++)S.push([Math.random()*c.width,Math.random()*c.height,Math.random()*1.4+.3,Math.random()*6.28]);}"
                + "rs();addEventListener('resize',rs);"
                + "(function f(t){x.clearRect(0,0,c.width,c.height);for(var i=0;i<S.length;i++){var s=S[i];"
                + "x.globalAlpha=.35+.65*Math.abs(Math.sin(t/1400+s[3]));x.fillStyle=i%5?'#cfe3ff':'#8fd3ff';"
                + "x.beginPath();x.arc(s[0],s[1],s[2]*devicePixelRatio,0,7);x.fill();}requestAnimationFrame(f);})(0);"
                + "</script></body></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }

    static String errorPage(String url, String reason) {
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta name='color-scheme' content='dark'><style>body{margin:0;background:#070b13;color:#e6edf7;"
                + "font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh}"
                + ".b{max-width:420px;padding:24px;text-align:center}h2{margin:16px 0 8px}p{color:#8b97ab;font-size:14px;word-break:break-all}"
                + "a{display:inline-block;margin-top:18px;padding:12px 22px;border-radius:22px;text-decoration:none;color:#06101f;"
                + "font-weight:700;background:linear-gradient(90deg,#3d8bff,#36d1ff)}</style></head><body><div class='b'>"
                + "<svg width='64' height='64' viewBox='0 0 24 24'><path fill='#3d8bff' d='M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z'/></svg>"
                + "<h2>No se pudo abrir la página</h2><p>" + esc(reason) + "</p><p>" + esc(url) + "</p>"
                + "<a href='" + esc(url) + "'>Reintentar</a></div></body></html>";
    }
}
