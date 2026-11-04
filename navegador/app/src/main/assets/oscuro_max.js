/*
 * Nocturna · modo oscuro MÁXIMO.
 * Oscurece TODO, pero conserva la esencia de cada página: a cada elemento solo se le invierte la
 * claridad del color, nunca el tono ni la fuente. Un fondo blanco queda casi negro, un texto negro
 * queda claro, un texto rojo oscuro queda rojo claro, un botón azul sigue siendo azul.
 */
(function () {
  if (window.__noctMax) return;
  window.__noctMax = 1;
  var done = new WeakSet();
  var passed = false;
  var SKIP = /^(IMG|VIDEO|CANVAS|PICTURE|IFRAME|SCRIPT|STYLE|NOSCRIPT|SOURCE|TRACK|OBJECT|EMBED|svg|image|use|path)$/;

  // mientras se prepara la página: fondo oscuro y nada de destello blanco
  function hold(on) {
    var r = document.documentElement;
    if (!r) return;
    var h = document.getElementById('__noct_hold');
    if (on && !h) {
      h = document.createElement('style');
      h.id = '__noct_hold';
      h.textContent = 'html{background:#0b0f17!important}body{opacity:0!important}';
      (document.head || r).appendChild(h);
    } else if (!on && h) {
      h.remove();
    }
  }

  function parse(c) {
    var m = (c || '').match(/[\d.]+/g);
    if (!m || m.length < 3) return null;
    return {r: +m[0], g: +m[1], b: +m[2], a: m.length > 3 ? +m[3] : 1};
  }

  function toHsl(c) {
    var r = c.r / 255, g = c.g / 255, b = c.b / 255;
    var mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2, h = 0, s = 0;
    if (mx !== mn) {
      var d = mx - mn;
      s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn);
      h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
      h /= 6;
    }
    return [h * 360, s * 100, l];
  }

  function hsla(h, s, l, a) {
    return 'hsla(' + h.toFixed(1) + ',' + s.toFixed(1) + '%,' + (l * 100).toFixed(1) + '%,' + a + ')';
  }

  // fondos claros → oscuros (mismo tono). Blanco → casi negro azulado.
  function darkBg(c) {
    var x = toHsl(c);
    if (x[2] <= 0.4) return null;
    // los colores vivos de tono medio (botones, insignias) son parte de la página: se dejan
    if (x[1] >= 45 && x[2] <= 0.68) return null;
    var s = x[1], h = x[0];
    if (s < 8) { h = 222; s = 30; }           // grises y blanco: noche azulada de Nocturna
    return hsla(h, Math.min(s, 75), 0.045 + (1 - x[2]) * 0.32, c.a);
  }

  // textos oscuros → claros (mismo tono). Negro → casi blanco.
  function lightFg(c) {
    var x = toHsl(c);
    if (x[2] >= 0.55) return null;
    return hsla(x[0], x[1], 0.6 + (0.55 - x[2]) * 0.62, c.a);
  }

  function darkBorder(c) {
    var x = toHsl(c);
    if (x[2] <= 0.45) return null;
    return hsla(x[0], Math.min(x[1], 60), 0.13 + (1 - x[2]) * 0.22, c.a);
  }

  function fix(el) {
    if (done.has(el)) return;
    done.add(el);
    if (SKIP.test(el.tagName)) return;
    var cs = getComputedStyle(el), v;
    var bg = parse(cs.backgroundColor);
    if (bg && bg.a > 0.04 && (v = darkBg(bg))) el.style.setProperty('background-color', v, 'important');
    var fg = parse(cs.color);
    if (fg && (v = lightFg(fg))) el.style.setProperty('color', v, 'important');
    if (parseFloat(cs.borderTopWidth) > 0 || parseFloat(cs.borderBottomWidth) > 0 ||
        parseFloat(cs.borderLeftWidth) > 0 || parseFloat(cs.borderRightWidth) > 0) {
      var bc = parse(cs.borderTopColor);
      if (bc && (v = darkBorder(bc))) el.style.setProperty('border-color', v, 'important');
    }
    // fondos con degradados claros: se atenúan sin perder el dibujo
    if (cs.backgroundImage && cs.backgroundImage.indexOf('gradient') >= 0 && !el.__noctGrad) {
      el.__noctGrad = 1;
      el.style.setProperty('background-image', cs.backgroundImage.replace(/rgba?\([^)]*\)/g, function (m) {
        var c = parse(m);
        return c ? (darkBg(c) || m) : m;
      }), 'important');
    }
  }

  function root() {
    var r = document.documentElement;
    if (!r) return;
    var cs = getComputedStyle(r), bg = parse(cs.backgroundColor);
    if (!bg || bg.a < 0.04) r.style.setProperty('background-color', '#0b0f17', 'important');
    if (!document.getElementById('__noct_meta')) {
      var m = document.createElement('meta');
      m.id = '__noct_meta';
      m.name = 'color-scheme';
      m.content = 'dark';
      (document.head || r).appendChild(m);
    }
  }

  // procesa por partes para no trabar páginas enormes
  var queue = [], busy = false;
  function enqueue(list) {
    for (var i = 0; i < list.length; i++) queue.push(list[i]);
    if (!busy) { busy = true; setTimeout(work, 0); }
  }
  function work() {
    var end = Date.now() + 12;
    while (queue.length && Date.now() < end) fix(queue.shift());
    if (queue.length) setTimeout(work, 0); else busy = false;
  }

  function firstPass() {
    if (passed) return;
    passed = true;
    root();
    fix(document.documentElement);
    if (document.body) fix(document.body);
    var all = document.querySelectorAll('body *');
    // lo primero que se ve, de una; el resto por partes
    var n = Math.min(all.length, 2500);
    for (var i = 0; i < n; i++) fix(all[i]);
    hold(false);
    var rest = [];
    for (var j = n; j < all.length; j++) rest.push(all[j]);
    enqueue(rest);
  }

  hold(true);
  setTimeout(function () { hold(false); }, 2500);   // por si acaso, nunca se queda oculta
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', firstPass);
  else firstPass();

  // contenido nuevo o elementos que cambian de clase
  new MutationObserver(function (muts) {
    if (!passed) {
      // la página apenas está naciendo: se mantiene oscura hasta el primer repaso
      hold(true);
      return;
    }
    var add = [];
    for (var i = 0; i < muts.length; i++) {
      var m = muts[i];
      if (m.type === 'childList') {
        for (var k = 0; k < m.addedNodes.length; k++) {
          var nd = m.addedNodes[k];
          if (nd.nodeType !== 1) continue;
          add.push(nd);
          var inner = nd.querySelectorAll ? nd.querySelectorAll('*') : [];
          for (var q = 0; q < inner.length; q++) add.push(inner[q]);
        }
      } else if (m.type === 'attributes' && m.target.nodeType === 1) {
        var t = m.target;
        if (!done.has(t)) continue;
        done.delete(t);
        t.style.removeProperty('background-color');
        t.style.removeProperty('color');
        t.style.removeProperty('border-color');
        add.push(t);
      }
    }
    if (add.length) enqueue(add);
  }).observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['class']});
})();
