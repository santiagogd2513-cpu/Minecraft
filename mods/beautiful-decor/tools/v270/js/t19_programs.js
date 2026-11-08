// Calculadora con parser recursivo
function calc(src) {
  const tokens = src.match(/\d+\.?\d*|[-+*/()^%]/g); let i = 0;
  const peek = () => tokens[i], next = () => tokens[i++];
  function primary() { const t = next(); if (t === "(") { const v = expr(); next(); return v; } if (t === "-") return -primary(); return parseFloat(t); }
  function power() { let b = primary(); while (peek() === "^") { next(); b = b ** primary(); } return b; }
  function term() { let v = power(); while (peek() === "*" || peek() === "/" || peek() === "%") { const op = next(), r = power(); v = op === "*" ? v * r : op === "/" ? v / r : v % r; } return v; }
  function expr() { let v = term(); while (peek() === "+" || peek() === "-") { const op = next(), r = term(); v = op === "+" ? v + r : v - r; } return v; }
  return expr();
}
for (const e of ["1+2*3", "(1+2)*3", "2^3^2", "10/4", "-3+5", "7%3*2", "1.5*4-0.5", "((2))", "100/3"]) out(e, "=", calc(e));
// Event emitter + closures
class Emitter { #h = new Map(); on(n, f) { (this.#h.get(n) ?? this.#h.set(n, []).get(n)).push(f); return this; } emit(n, ...a) { (this.#h.get(n) || []).forEach(f => f(...a)); return this.#h.has(n); } }
const em = new Emitter(); const log = []; em.on("x", (a, b) => log.push(a + b)).on("x", a => log.push(a * 2)); em.emit("x", 3, 4); out(log, em.emit("y"));
// LRU cache
class LRU { constructor(n) { this.n = n; this.m = new Map(); } get(k) { if (!this.m.has(k)) return undefined; const v = this.m.get(k); this.m.delete(k); this.m.set(k, v); return v; } set(k, v) { this.m.delete(k); this.m.set(k, v); if (this.m.size > this.n) this.m.delete(this.m.keys().next().value); } }
const lru = new LRU(2); lru.set("a", 1); lru.set("b", 2); lru.get("a"); lru.set("c", 3); out([...lru.m.keys()], lru.get("b"));
// memoize + fib
const memo = f => { const c = {}; return n => c[n] ?? (c[n] = f(n)); }; const fib = memo(n => n < 2 ? n : fib(n - 1) + fib(n - 2)); out(fib(50), fib(78));
// sieve
const sieve = n => { const s = new Array(n + 1).fill(true); s[0] = s[1] = false; for (let i = 2; i * i <= n; i++) if (s[i]) for (let j = i * i; j <= n; j += i) s[j] = false; return s.reduce((a, v, i) => v ? (a.push(i), a) : a, []); }; out(sieve(60).join(","), sieve(100000).length);
// text stats
const text = "The quick brown fox jumps over the lazy dog. The dog barks; the fox runs away!";
const words = text.toLowerCase().match(/\b[a-z']+\b/g); const freq = words.reduce((m, w) => (m[w] = (m[w] || 0) + 1, m), {});
out(Object.entries(freq).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, 4), words.length, [...new Set(words)].length);
out(text.replace(/\b(\w)(\w*)/g, (_, a, b) => a.toUpperCase() + b), text.split(/[.;!]\s*/).filter(Boolean).length);
// matrix
const mm = (a, b) => a.map(r => b[0].map((_, j) => r.reduce((s, v, k) => s + v * b[k][j], 0))); out(mm([[1, 2], [3, 4]], [[5, 6], [7, 8]]), mm([[1, 0], [0, 1]], [[9, 8], [7, 6]]));
const T = m => m[0].map((_, i) => m.map(r => r[i])); out(T([[1, 2, 3], [4, 5, 6]]));
// sorting stability + comparator
const people = [{ n: "A", a: 30 }, { n: "B", a: 25 }, { n: "C", a: 30 }, { n: "D", a: 25 }, { n: "E", a: 40 }]; out(people.sort((x, y) => x.a - y.a).map(p => p.n).join(""), people.toSorted((x, y) => y.a - x.a).map(p => p.n).join(""));
out([10, 9, 1, 100, 25].sort(), [10, 9, 1, 100, 25].sort((a, b) => a - b), ["b", "a", "C", "B"].sort(), ["b", "a", "C", "B"].sort((a, b) => a.localeCompare(b)), ["é", "e", "z", "a"].sort((a, b) => a.localeCompare(b)));
// binary search, gcd, reverse unicode
const bs = (a, x) => { let lo = 0, hi = a.length - 1; while (lo <= hi) { const m = (lo + hi) >> 1; if (a[m] === x) return m; a[m] < x ? lo = m + 1 : hi = m - 1; } return -1; }; out(bs([1, 3, 5, 7, 9, 11], 7), bs([1, 3], 4));
const gcd = (a, b) => b ? gcd(b, a % b) : a; out(gcd(1071, 462), [..."a😀b"].reverse().join(""), "a😀b".length, [..."a😀b"].length);
// getters/setters/static/inheritance/toString
class Shape { static count = 0; #id; constructor(n) { this.name = n; this.#id = ++Shape.count; } get id() { return this.#id; } area() { return 0; } toString() { return `${this.name}#${this.#id}(${this.area().toFixed(2)})`; } static get total() { return Shape.count; } }
class Circle extends Shape { constructor(r) { super("circle"); this.r = r; } area() { return Math.PI * this.r ** 2; } get diameter() { return this.r * 2; } set diameter(d) { this.r = d / 2; } }
class Sq extends Shape { constructor(s) { super("sq"); this.s = s; } area() { return this.s * this.s; } }
const shapes = [new Circle(1), new Sq(3), new Circle(2.5)]; shapes[0].diameter = 10; out(shapes.map(String), Shape.total, shapes.map(s => s.id), shapes[0] instanceof Shape, Object.getPrototypeOf(Circle) === Shape, `${shapes[1]}`);
// async/await without timers
const sleep = (v) => new Promise(r => r(v)); const order = [];
async function task(n) { order.push("start" + n); const v = await sleep(n * 2); order.push("end" + n); return v; }
(async () => { const rs = await Promise.all([task(1), task(2), task(3)]); out(rs, order.join(","));
  const settled = await Promise.allSettled([Promise.resolve(1), Promise.reject(new Error("no")), sleep("z")]); out(settled.map(s => s.status + ":" + (s.value ?? s.reason.message)));
  try { await Promise.any([Promise.reject(1), Promise.reject(2)]); } catch (e) { out(e.constructor.name, e.errors); }
  out(await Promise.race([sleep("fast"), new Promise(() => { })]));
  for await (const x of (async function* () { yield 1; yield 2; })()) out("fa", x);
})();
// Date
const dt = new Date(Date.UTC(2024, 0, 31, 12, 30, 45)); out(dt.toISOString(), dt.getUTCDay(), new Date(dt.getTime() + 86400000 * 30).toISOString().slice(0, 10), new Date("2024-02-29T00:00:00Z").getTime(), Date.UTC(2000, 1, 29));
out(new Intl.NumberFormat("en-US").format(1234567.891), (1234.5).toLocaleString("en-US", { style: "currency", currency: "USD" }), (0.256).toLocaleString("en-US", { style: "percent" }), (1e6).toLocaleString("en-US"), (12).toString().padStart(5, "0"), (255).toString(16).toUpperCase(), (0.1 + 0.2).toFixed(2), parseInt("12px"), +"  42  ", Number(""), Number("12abc"));
// destructuring / spread / optional chaining
const cfg = { a: { b: { c: [1, 2, { d: "deep" }] } }, list: [1, 2, 3, 4, 5] }; const { a: { b: { c: [, , { d }] } }, list: [first, ...rest] } = cfg; out(d, first, rest, cfg?.x?.y?.z, cfg.a?.["b"]?.c?.[0], cfg.nope ?? "dflt", cfg.list?.at(-1));
const merged = { ...cfg.a, extra: 1, ...{ extra: 2 } }; out(Object.keys(merged), merged.extra, Math.max(...cfg.list), [...cfg.list, ...cfg.list].length);
// tagged templates, labels, switch, comma, exponent, bitwise
const tag = (s, ...v) => s.raw.map((x, i) => x + (v[i] !== undefined ? `[${v[i]}]` : "")).join(""); out(tag`a${1}b${"x"}c\n`, `multi
line`, String.raw`\n${1}`);
let acc = []; outer: for (let i = 0; i < 4; i++) { switch (i) { case 1: continue outer; case 3: break outer; default: acc.push(i); } } out(acc, (5, 6), 2 ** 10, 7 & 3, 7 | 8, 5 ^ 1, ~5, 1 << 31, -16 >> 2, -16 >>> 28, 5 / 2 | 0);
// JSON round-trip with reviver/replacer
const data = { n: 1, s: "x", arr: [1, { k: null }], d: new Date(0), u: undefined, f() { }, nested: { big: 1e21 } }; const js = JSON.stringify(data, null, 2); out(js, JSON.parse(js, (k, v) => typeof v === "number" ? v + 1 : v));
// getter on object literal / defineProperty / freeze
const o2 = { _v: 1, get v() { return this._v * 2; }, set v(x) { this._v = x; } }; o2.v = 21; Object.defineProperty(o2, "hidden", { value: 1, enumerable: false }); out(o2.v, Object.keys(o2), JSON.stringify(o2), Object.isFrozen(Object.freeze({ a: 1 })), Object.getOwnPropertyNames(o2));
// iterators / generators
function* take(it, n) { let i = 0; for (const x of it) { if (i++ >= n) return; yield x; } } function* nat() { let n = 0; while (true) yield n++; } out([...take(nat(), 5)], Array.from(take(nat(), 3), x => x * x), Object.fromEntries(new Map([["a", 1]])), [...new Set("hello")].join(""));
const range = { from: 1, to: 4, [Symbol.iterator]() { let c = this.from, t = this.to; return { next: () => c <= t ? { value: c++, done: false } : { value: undefined, done: true } }; } }; out([...range], Math.min(...range));
// string methods
out("  trim me ".trim() + "|", "a-b_c d".split(/[-_ ]/), "abc".at(-1), "Hello".replaceAll("l", "L"), "x".codePointAt(0), "abc".localeCompare("abd"), "ß".toUpperCase(), "İ".toLowerCase().length, "a,b,,c".split(","), "abc".substring(2, 0), "abc".substr(-2, 1), "%E4%B8%AD", decodeURIComponent("%E4%B8%AD"), encodeURIComponent("a b&c=d/é"), escape ? "esc" : "", btoa ? "b" : "");
