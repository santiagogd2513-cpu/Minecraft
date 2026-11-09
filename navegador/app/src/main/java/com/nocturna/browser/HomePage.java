package com.nocturna.browser;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Página de inicio: cielo nocturno, buscador y accesos rápidos (favoritos). */
final class HomePage {
    static final String URL = "nocturna://inicio";

    private HomePage() {}

    /** Los efectos de temporada de la página (los mismos de SeasonFx.java, en JavaScript). */
    private static final String SEASON_JS = """
// Efectos de temporada para páginas web (Nocturna y Arnis): lo mismo que SeasonFx.java.
// season: "navidad", "velitas", "valentin" o "amistad".
//   SeasonFx.sky(season)          nieve que cae o chispitas que suben, por toda la pantalla
//   SeasonFx.card(el, season, r)  borde de nieve con lucecitas, o guirnalda de faroles, arriba de el
//                                 (noPad: no correr el contenido hacia abajo)
var SeasonFx = (function () {
  var D = window.devicePixelRatio || 1;

  function heart(x, cx, cy, s) {
    x.beginPath();
    x.moveTo(cx, cy + s * 0.75);
    x.bezierCurveTo(cx - s * 1.9, cy - s * 0.4, cx - s * 0.9, cy - s * 1.7, cx, cy - s * 0.65);
    x.bezierCurveTo(cx + s * 0.9, cy - s * 1.7, cx + s * 1.9, cy - s * 0.4, cx, cy + s * 0.75);
    x.closePath();
  }
  function star(x, cx, cy, s) {
    x.beginPath();
    for (var k = 0; k < 10; k++) {
      var rr = k % 2 ? s * 0.45 : s, a = -Math.PI / 2 + k * Math.PI / 5;
      if (k) x.lineTo(cx + rr * Math.cos(a), cy + rr * Math.sin(a)); else x.moveTo(cx + rr * Math.cos(a), cy + rr * Math.sin(a));
    }
    x.closePath();
  }

  function sky(season) {
    var c = document.createElement("canvas");
    c.className = "season-sky";
    c.style.cssText = "position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:2147482000";
    document.body.appendChild(c);
    var x = c.getContext("2d"), P = [], W = 0, H = 0, last = 0;
    var warm = ["#FFD27A", "#FFA63D", "#FF7A1A", "#FFE9A8"];
    var pink = ["#FF4D8D", "#FF7AA8", "#E63946", "#FFB3CC", "#C9184A"];
    var party = ["#FF4D6D", "#FFC23D", "#3DDC84", "#3FB8FF", "#B06BFF", "#FF7AC8", "#FF8A3D"];
    function spawn(p, anywhere) {
      p.x = Math.random() * W;
      p.ph = Math.random() * 6.28;
      if (season === "navidad") {
        p.r = (1.2 + Math.random() * 2.4) * D;
        p.v = (16 + Math.random() * 34) * D * (p.r / (2.4 * D));
        p.y = anywhere ? Math.random() * H : -p.r * 2;
        p.a = (90 + Math.random() * 120) / 255;
      } else if (season === "valentin") {
        p.r = (3.5 + Math.random() * 4) * D;
        p.v = (16 + Math.random() * 22) * D;
        p.y = anywhere ? Math.random() * H : H + p.r * 2;
        p.c = pink[(Math.random() * pink.length) | 0];
      } else if (season === "amistad") {
        p.k = Math.random() < 0.6 ? 0 : Math.random() < 0.6 ? 1 : 2;
        p.r = (p.k === 0 ? 2.5 + Math.random() * 2.5 : 4 + Math.random() * 4) * D;
        p.v = (22 + Math.random() * 36) * D;
        p.y = anywhere ? Math.random() * H : -p.r * 3;
        p.c = party[(Math.random() * party.length) | 0];
      } else {
        p.r = (1 + Math.random() * 1.8) * D;
        p.v = (14 + Math.random() * 26) * D;
        p.y = anywhere ? H - Math.random() * H * 0.6 : H + p.r * 2;
        p.c = warm[(Math.random() * warm.length) | 0];
      }
    }
    function size() {
      W = c.width = innerWidth * D;
      H = c.height = innerHeight * D;
      P = [];
      var n = season === "navidad" ? 46 : season === "valentin" ? 22 : season === "amistad" ? 40 : 30;
      for (var i = 0; i < n; i++) { var p = {}; spawn(p, true); P.push(p); }
    }
    size();
    addEventListener("resize", size);
    (function f(now) {
      var dt = last ? Math.min(0.05, (now - last) / 1000) : 0, t = now / 1000;
      last = now;
      x.clearRect(0, 0, W, H);
      if (season === "velitas") {
        var g = x.createLinearGradient(0, H, 0, H - 170 * D);
        g.addColorStop(0, "rgba(255,154,46,.25)");
        g.addColorStop(1, "rgba(255,154,46,0)");
        x.fillStyle = g;
        x.fillRect(0, H - 170 * D, W, 170 * D);
      }
      for (var i = 0; i < P.length; i++) {
        var p = P[i];
        if (season === "navidad") {
          p.y += p.v * dt;
          var sx = p.x + Math.sin(t * 0.9 + p.ph) * 10 * D;
          if (p.y > H + p.r * 2) spawn(p, false);
          x.globalAlpha = p.a;
          x.fillStyle = "#fff";
          x.beginPath(); x.arc(sx, p.y, p.r, 0, 7); x.fill();
          if (p.r > 2.6 * D) { x.globalAlpha = 0.06; x.beginPath(); x.arc(sx, p.y, p.r * 2, 0, 7); x.fill(); }
        } else if (season === "valentin") {
          p.y -= p.v * dt;
          if (p.y < -p.r * 2) spawn(p, false);
          var hx = p.x + Math.sin(t * 1.1 + p.ph) * 14 * D;
          var fade = Math.min(1, Math.max(0, p.y / (H * 0.35)));
          x.save();
          x.translate(hx, p.y); x.rotate(Math.sin(t * 1.3 + p.ph) * 0.25);
          x.globalAlpha = Math.min(0.9, (110 + p.r / D * 8) / 255) * fade;
          x.fillStyle = p.c; heart(x, 0, 0, p.r); x.fill();
          x.restore();
        } else if (season === "amistad") {
          p.y += p.v * dt;
          if (p.y > H + p.r * 3) spawn(p, false);
          var cx2 = p.x + Math.sin(t * 1.4 + p.ph) * 12 * D;
          x.save();
          x.translate(cx2, p.y); x.rotate(((t * 90 + p.ph * 57) % 360) * Math.PI / 180);
          x.globalAlpha = 0.82; x.fillStyle = p.c;
          if (p.k === 0) { x.scale(Math.cos(t * 4 + p.ph), 1); x.fillRect(-p.r, -p.r * 0.45, p.r * 2, p.r * 0.9); }
          else if (p.k === 1) { heart(x, 0, 0, p.r); x.fill(); }
          else { star(x, 0, 0, p.r); x.fill(); }
          x.restore();
        } else {
          p.y -= p.v * dt;
          var ex = p.x + Math.sin(t * 1.6 + p.ph) * 6 * D;
          var life = (H - p.y) / (H * 0.62);
          if (life >= 1 || p.y < 0) { spawn(p, false); continue; }
          var fl = 0.65 + 0.35 * Math.sin(t * 9 + p.ph * 3);
          x.fillStyle = p.c;
          x.globalAlpha = Math.max(0, 0.9 * (1 - life) * fl);
          x.beginPath(); x.arc(ex, p.y, p.r, 0, 7); x.fill();
          x.globalAlpha = Math.max(0, 0.18 * (1 - life) * fl);
          x.beginPath(); x.arc(ex, p.y, p.r * 3, 0, 7); x.fill();
        }
      }
      x.globalAlpha = 1;
      requestAnimationFrame(f);
    })(0);
  }

  function card(el, season, radius, noPad) {
    if (getComputedStyle(el).position === "static") el.style.position = "relative";
    if (!noPad) el.style.paddingTop = (parseFloat(getComputedStyle(el).paddingTop) + 20) + "px";
    var c = document.createElement("canvas");
    c.style.cssText = "position:absolute;left:0;top:0;width:100%;height:30px;pointer-events:none;z-index:3;border-radius:"
      + radius + "px " + radius + "px 0 0";
    el.appendChild(c);
    var x = c.getContext("2d"), w = 0, drips = [];
    function size() {
      w = el.clientWidth;
      c.width = w * D;
      c.height = 30 * D;
      var n = Math.max(3, (w / 34) | 0);
      drips = [];
      for (var i = 0; i < n; i++) drips.push([(i + 0.2 + Math.random() * 0.6) * w / n, 2 + Math.random() * 6]);
    }
    size();
    addEventListener("resize", size);
    function sag(px, span, y0, depth) { var u = (px % span) / span; return y0 + depth * 4 * u * (1 - u); }
    function roundClip() {
      x.beginPath();
      x.moveTo(0, 30); x.lineTo(0, radius); x.arcTo(0, 0, radius, 0, radius);
      x.lineTo(w - radius, 0); x.arcTo(w, 0, w, radius, radius); x.lineTo(w, 30); x.closePath();
      x.clip();
    }
    (function f(now) {
      var t = now / 1000;
      x.setTransform(D, 0, 0, D, 0, 0);
      x.clearRect(0, 0, w, 30);
      x.save();
      roundClip();
      if (season === "navidad") christmas(t);
      else if (season === "valentin") hearts(t);
      else if (season === "amistad") bunting(t);
      else velitas(t);
      x.restore();
      requestAnimationFrame(f);
    })(0);

    function christmas(t) {
      var cap = 7, span = Math.max(60, w / Math.max(1, Math.round(w / 80))), y0 = cap + 2, depth = 7;
      x.strokeStyle = "#1E3B22"; x.lineWidth = 1.3;
      x.beginPath(); x.moveTo(0, y0);
      for (var px = 0; px <= w; px += 3) x.lineTo(px, sag(px, span, y0, depth));
      x.stroke();
      var cols = [[255, 59, 78], [53, 224, 122], [255, 210, 63], [76, 180, 255], [255, 122, 217]], i = 0;
      for (var bx = 10; bx < w - 4; bx += 21, i++) {
        var by = sag(bx, span, y0, depth), col = cols[i % cols.length];
        var on = 0.35 + 0.65 * Math.max(0, Math.sin(t * 2.4 + i * 1.7));
        var g = x.createRadialGradient(bx, by + 6, 0, bx, by + 6, 9);
        g.addColorStop(0, "rgba(" + col + "," + (0.47 * on) + ")");
        g.addColorStop(1, "rgba(" + col + ",0)");
        x.fillStyle = g; x.beginPath(); x.arc(bx, by + 6, 9, 0, 7); x.fill();
        x.fillStyle = "#24402A"; x.fillRect(bx - 1.6, by - 0.5, 3.2, 3);
        var k = 0.45 + 0.55 * on;
        x.fillStyle = "rgb(" + Math.round(58 + (col[0] - 58) * k) + "," + Math.round(58 + (col[1] - 58) * k) + "," + Math.round(58 + (col[2] - 58) * k) + ")";
        x.beginPath(); x.ellipse(bx, by + 5.75, 2.4, 3.75, 0, 0, 7); x.fill();
        x.fillStyle = "rgba(255,255,255,.6)"; x.beginPath(); x.arc(bx - 0.8, by + 4.2, 0.8, 0, 7); x.fill();
      }
      var sg = x.createLinearGradient(0, 0, 0, cap + 2);
      sg.addColorStop(0, "#fff"); sg.addColorStop(1, "#D9E8FF");
      x.fillStyle = sg;
      x.beginPath(); x.moveTo(0, 0); x.lineTo(0, cap);
      for (var sx = 0; sx <= w; sx += 4) x.lineTo(sx, cap + Math.sin(sx / 13) * 1.6);
      x.lineTo(w, 0); x.closePath(); x.fill();
      drips.forEach(function (d) {
        x.beginPath();
        if (x.roundRect) x.roundRect(d[0] - 3.2, cap - 3, 6.4, d[1] + 3, 3.2); else x.rect(d[0] - 3.2, cap - 3, 6.4, d[1] + 3);
        x.fill();
      });
      drips.forEach(function (d, k) {
        x.fillStyle = "rgba(255,255,255," + (0.4 + 0.4 * Math.sin(t * 3 + k)) + ")";
        x.beginPath(); x.arc(d[0] + 6, 2.5, 0.9, 0, 7); x.fill();
      });
    }

    function hearts(t) {
      var span = Math.max(60, w / Math.max(1, Math.round(w / 90))), y0 = 2, depth = 6;
      x.strokeStyle = "#B0406A"; x.lineWidth = 1.3;
      x.beginPath(); x.moveTo(0, y0);
      for (var px = 0; px <= w; px += 3) x.lineTo(px, sag(px, span, y0, depth));
      x.stroke();
      var cols = [[255, 45, 85], [255, 122, 168], [230, 57, 70], [255, 179, 204]], i = 0;
      for (var hx = 14; hx < w - 8; hx += 30, i++) {
        var hy = sag(hx, span, y0, depth), col = cols[i % cols.length];
        var ph = (t * 1.2 + i * 0.17) % 1;
        var beat = ph < 0.12 ? Math.sin(ph / 0.12 * Math.PI) : (ph > 0.2 && ph < 0.32 ? 0.7 * Math.sin((ph - 0.2) / 0.12 * Math.PI) : 0);
        var s = 5.2 + 1.2 * beat;
        x.fillStyle = "#8A3A5A"; x.fillRect(hx - 0.5, hy, 1, 4);
        var g = x.createRadialGradient(hx, hy + 10, 0, hx, hy + 10, 12);
        g.addColorStop(0, "rgba(" + col + "," + ((60 + 70 * beat) / 255) + ")"); g.addColorStop(1, "rgba(" + col + ",0)");
        x.fillStyle = g; x.beginPath(); x.arc(hx, hy + 10, 12, 0, 7); x.fill();
        x.fillStyle = "rgb(" + col + ")"; heart(x, hx, hy + 10, s); x.fill();
        x.fillStyle = "rgba(255,255,255,.55)"; x.beginPath(); x.arc(hx - s * 0.55, hy + 10 - s * 0.55, s * 0.22, 0, 7); x.fill();
      }
    }

    function bunting(t) {
      var span = Math.max(60, w / Math.max(1, Math.round(w / 100))), y0 = 2, depth = 7, fw = 7;
      x.strokeStyle = "#E8D7B0"; x.lineWidth = 1.3;
      x.beginPath(); x.moveTo(0, y0);
      for (var px = 0; px <= w; px += 3) x.lineTo(px, sag(px, span, y0, depth));
      x.stroke();
      var cols = ["#FF4D6D", "#FFC23D", "#3DDC84", "#3FB8FF", "#B06BFF", "#FF7AC8"], i = 0;
      for (var bx = 9; bx < w - fw; bx += 19, i++) {
        var ya = sag(bx - fw, span, y0, depth), yb = sag(bx + fw, span, y0, depth);
        var sway = Math.sin(t * 2.2 + i * 0.8) * 3, len = 14 + Math.sin(t * 1.7 + i) * 1.2;
        x.fillStyle = cols[i % cols.length];
        x.beginPath(); x.moveTo(bx - fw, ya); x.lineTo(bx + fw, yb); x.lineTo(bx + sway, (ya + yb) / 2 + len); x.closePath(); x.fill();
        x.fillStyle = "rgba(255,255,255,.8)";
        if (i % 3 === 1) { heart(x, bx + sway * 0.3, (ya + yb) / 2 + len * 0.36, 2.2); x.fill(); }
        else { x.beginPath(); x.arc(bx + sway * 0.3, (ya + yb) / 2 + len * 0.36, 1.4, 0, 7); x.fill(); }
      }
    }

    function velitas(t) {
      var span = Math.max(60, w / Math.max(1, Math.round(w / 90))), y0 = 2, depth = 6;
      x.strokeStyle = "#8A6A3A"; x.lineWidth = 1.3;
      x.beginPath(); x.moveTo(0, y0);
      for (var px = 0; px <= w; px += 3) x.lineTo(px, sag(px, span, y0, depth));
      x.stroke();
      var cols = ["#FF4D4D", "#FFB020", "#3DDC84", "#B06BFF", "#FF7AC8", "#3FB8FF"], i = 0;
      for (var lx = 16; lx < w - 8; lx += 34, i++) {
        var ly = sag(lx, span, y0, depth);
        var swing = Math.sin(t * 1.3 + i * 0.9) * 7 * Math.PI / 180;
        var fl = 0.75 + 0.25 * Math.sin(t * 11 + i * 2.3) * Math.sin(t * 7.3 + i);
        x.save();
        x.translate(lx, ly); x.rotate(swing);
        var g = x.createRadialGradient(0, 9, 0, 0, 9, 14);
        g.addColorStop(0, "rgba(255,210,122," + (0.43 * fl) + ")"); g.addColorStop(1, "rgba(255,210,122,0)");
        x.fillStyle = g; x.beginPath(); x.arc(0, 9, 14, 0, 7); x.fill();
        x.fillStyle = "#6B4A22"; x.fillRect(-0.5, 0, 1, 2);
        x.fillStyle = "#3A2614"; x.fillRect(-3.5, 2, 7, 1.6);
        var lg = x.createRadialGradient(0, 9, 0, 0, 9, 7);
        lg.addColorStop(0, "#FFF0C0"); lg.addColorStop(1, cols[i % cols.length]);
        x.globalAlpha = 0.8 + 0.2 * fl;
        x.fillStyle = lg;
        x.beginPath();
        if (x.roundRect) x.roundRect(-5.5, 3.4, 11, 11.6, 4.5); else x.rect(-5.5, 3.4, 11, 11.6);
        x.fill();
        x.globalAlpha = 1;
        x.fillStyle = "rgba(0,0,0,.2)";
        for (var k = -1; k <= 1; k++) x.fillRect(k * 2.6 - 0.3, 4, 0.6, 10.5);
        x.fillStyle = "#3A2614"; x.fillRect(-3, 14.6, 6, 1.4);
        x.restore();
      }
    }
  }

  return { sky: sky, card: card };
})();
""";

    static String html(Store store) {
        return html(store, Season.NONE);
    }

    /** {@code season}: la temporada con decoraciones que se ve ahora (o {@link Season#NONE}). */
    static String html(Store store, int season) {
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
        String[] palette = season == Season.NAVIDAD
                ? new String[]{"#E63946", "#2DC653", "#FFD23F", "#E63946", "#2DC653", "#FF7A7A", "#4CB4FF", "#2DC653"}
                : season == Season.VELITAS
                ? new String[]{"#FF4D4D", "#FFB020", "#3DDC84", "#B06BFF", "#FF7AC8", "#3FB8FF", "#FFD27A", "#FF7A1A"}
                : season == Season.VALENTIN
                ? new String[]{"#FF2D55", "#FF7AA8", "#E63946", "#FFB3CC", "#C9184A", "#FF4D8D", "#FF8FA3", "#B0406A"}
                : season == Season.AMISTAD
                ? new String[]{"#FF4D6D", "#FFC23D", "#3DDC84", "#3FB8FF", "#B06BFF", "#FF7AC8", "#FF8A3D", "#36D1FF"}
                : new String[]{"#3D8BFF", "#36D1FF", "#7B8CFF", "#35D08A", "#FFB547", "#FF6A7A", "#B58CFF", "#5CC8FF"};
        String name = season == Season.NAVIDAD ? "navidad" : season == Season.VELITAS ? "velitas"
                : season == Season.VALENTIN ? "valentin" : season == Season.AMISTAD ? "amistad" : null;
        // gorro de Papá Noel sobre la luna, o dos velitas al lado
        String logoExtra = season == Season.NAVIDAD
                ? "<g transform='translate(34,42) rotate(-36) scale(.72)'>"
                + "<path fill='#E8323F' d='M1,-2 C3,-14 11,-24 21,-23 C28,-22 32,-15 35,-7 L30,-5 C28,-10 26,-13 22,-13 C25,-9 27,-5 28,-2 Z'/>"
                + "<path fill='#F4F7FF' d='M-1,-4 Q-3,-4 -3,-0.5 Q-3,3 0,3 L30,3 Q33,3 33,-0.5 Q33,-4 30,-4 Z'/>"
                + "<circle cx='35' cy='-6' r='4.4' fill='#fff'/></g>"
                : season == Season.VELITAS
                ? "<g transform='translate(70,68) scale(.9)'><rect y='-12' width='6' height='12' fill='#FFF3D6'/>"
                + "<path fill='#FF8A1A' d='M3,-21 Q6,-16.5 5,-14.5 Q4.2,-13.2 3,-13.2 Q1.8,-13.2 1,-14.5 Q0,-16.5 3,-21 Z'><animate attributeName='opacity' values='1;.7;1;.85;1' dur='.9s' repeatCount='indefinite'/></path></g>"
                + "<g transform='translate(77,70) scale(.9)'><rect y='-8' width='6' height='8' fill='#FFF3D6'/>"
                + "<path fill='#FF8A1A' d='M3,-17 Q6,-12.5 5,-10.5 Q4.2,-9.2 3,-9.2 Q1.8,-9.2 1,-10.5 Q0,-12.5 3,-17 Z'><animate attributeName='opacity' values='.8;1;.75;1;.8' dur='1.1s' repeatCount='indefinite'/></path></g>"
                : season == Season.VALENTIN
                ? "<g transform='translate(73,66) scale(.85)'>"
                + "<path fill='#FF2D55' d='M0,6.75 C-17.1,-3.6 -8.1,-15.3 0,-5.85 C8.1,-15.3 17.1,-3.6 0,6.75 Z'>"
                + "<animateTransform attributeName='transform' type='scale' values='1;1.15;1;1.08;1;1' dur='1.2s' repeatCount='indefinite'/></path>"
                + "<path stroke='#7A4A10' stroke-width='1' d='M-15,9 L14,-9'/><path fill='#FFD23F' d='M14,-9 L9.5,-8.6 L12.3,-6 Z M14,-9 L13.6,-4.5 L11,-7.3 Z'/></g>"
                : season == Season.AMISTAD
                ? "<g transform='translate(74,76) scale(.9)'><path fill='#FF4D6D' d='M-10,-12 L10,-12 L10,3 L-10,3 Z'/>"
                + "<path fill='#FF7A90' d='M-11.5,-16 L11.5,-16 L11.5,-11.5 L-11.5,-11.5 Z'/><path fill='#FFD23F' d='M-1.8,-16 L1.8,-16 L1.8,3 L-1.8,3 Z'/>"
                + "<path fill='#FFC23D' d='M0,-16 C-4,-23 -10,-20 -6.5,-16 Z M0,-16 C4,-23 10,-20 6.5,-16 Z'/></g>"
                : "";
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
                + "<path fill='#fff' d='M70,32 L71.6,37.4 L77,39 L71.6,40.6 L70,46 L68.4,40.6 L63,39 L68.4,37.4 Z'/>" + logoExtra + "</svg>"
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
                + "</script>"
                + (name == null ? "" : "<script>" + SEASON_JS + "SeasonFx.sky('" + name + "');"
                + "SeasonFx.card(document.querySelector('form'),'" + name + "',28);</script>")
                + "</body></html>";
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
