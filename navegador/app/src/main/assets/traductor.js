/*
 * Nocturna · traductor dentro de la página.
 * Junta los textos visibles (sin código, scripts ni campos de escritura), Nocturna los traduce y
 * aquí se reemplazan conservando los espacios. «Ver original» lo deja todo como estaba.
 */
(function () {
  if (window.__noctTr) return;
  var T = window.__noctTr = {nodes: [], orig: []};
  var SKIP = /^(SCRIPT|STYLE|NOSCRIPT|CODE|PRE|TEXTAREA|KBD|SAMP|VAR|SVG|MATH|TITLE)$/;
  var LETTER = /[A-Za-zÀ-ɏͰ-ϿЀ-ӿ぀-ヿ㐀-鿿가-힯豈-﫿]/;

  /** Textos nuevos (que aún no se han traducido). Devuelve {start, texts}. */
  T.collect = function () {
    var start = T.nodes.length, out = [];
    if (!document.body) return JSON.stringify({start: start, texts: out});
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        var p = n.parentElement;
        if (!p || n.__noctTr) return NodeFilter.FILTER_REJECT;
        for (var e = p; e; e = e.parentElement) {
          if (SKIP.test(e.tagName) || e.isContentEditable || e.getAttribute('translate') === 'no' ||
              (e.classList && e.classList.contains('notranslate'))) return NodeFilter.FILTER_REJECT;
          if (e === document.body) break;
        }
        return LETTER.test(n.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      }
    });
    var n;
    while ((n = w.nextNode())) {
      n.__noctTr = 1;
      T.nodes.push(n);
      T.orig.push(n.nodeValue);
      out.push(n.nodeValue.trim());
    }
    // también los textos de ayuda de los campos y botones
    var els = document.querySelectorAll('input[placeholder],textarea[placeholder],input[type=submit][value],input[type=button][value]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.__noctTr) continue;
      el.__noctTr = 1;
      var attr = el.hasAttribute('placeholder') ? 'placeholder' : 'value';
      var v = el.getAttribute(attr) || '';
      if (!LETTER.test(v)) continue;
      T.nodes.push({el: el, attr: attr});
      T.orig.push(v);
      out.push(v.trim());
    }
    return JSON.stringify({start: start, texts: out});
  };

  T.apply = function (start, list) {
    for (var i = 0; i < list.length; i++) {
      var k = start + i, n = T.nodes[k], o = T.orig[k], tr = list[i];
      if (n == null || tr == null || tr === '') continue;
      var lead = o.match(/^\s*/)[0], trail = o.match(/\s*$/)[0];
      if (n.el) n.el.setAttribute(n.attr, tr);
      else n.nodeValue = lead + tr + trail;
    }
    document.documentElement.setAttribute('data-noct-traducido', '1');
  };

  T.restore = function () {
    for (var i = 0; i < T.nodes.length; i++) {
      var n = T.nodes[i];
      if (n.el) { n.el.setAttribute(n.attr, T.orig[i]); n.el.__noctTr = 0; }
      else { n.nodeValue = T.orig[i]; n.__noctTr = 0; }
    }
    T.nodes = [];
    T.orig = [];
    document.documentElement.removeAttribute('data-noct-traducido');
  };
})();
