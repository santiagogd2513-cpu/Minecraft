// ámbitos y elevación
out(typeof hoisted, typeof fnDecl, fnDecl()); var hoisted = 1; function fnDecl(){ return "fd"; }
try { tdz; let tdz = 1; } catch (e) { out(e.name, e.message); }
try { const c = 1; c = 2; } catch (e) { out(e.name, e.message); }
try { undefinedVar; } catch (e) { out(e.name, e.message); }
try { null.x; } catch (e) { out(e.name, e.message); }
try { undefined.foo.bar; } catch (e) { out(e.name, e.message); }
try { let o = {}; o.f(); } catch (e) { out(e.name, e.message); }
try { let o = {a:{}}; o.a.b.c(); } catch (e) { out(e.name, e.message); }
try { (void 0)(); } catch (e) { out(e.name, e.message); }
try { new (class A { constructor(){ this.x; } })(); out("ok"); } catch (e) { out(e.name, e.message); }
try { class B extends Object { constructor(){ this.x = 1; } } new B(); } catch (e) { out(e.name, e.message); }
try { new 5; } catch (e) { out(e.name, e.message); }
try { ({}).x.y; } catch (e) { out(e.name, e.message); }
try { [].reduce((a,b)=>a); } catch (e) { out(e.name, e.message); }
try { new Array(-1); } catch (e) { out(e.name, e.message); }
try { "abc".repeat(-1); } catch (e) { out(e.name, e.message); }
try { Symbol() + ""; } catch (e) { out(e.name, e.message); }
try { null[Symbol.iterator]; } catch (e) { out(e.name, e.message); }
try { const [a] = null; } catch (e) { out(e.name, e.message); }
try { const {a} = undefined; } catch (e) { out(e.name, e.message); }
try { for (const x of 5) {} } catch (e) { out(e.name, e.message); }
try { JSON.parse("{bad"); } catch (e) { out(e.name); }
try { x = y + 1; } catch (e) { out(e.name, e.message); }
try { (function f(){ f(); })(); } catch (e) { out(e.name, e.message); }
// cierres en bucles
var fs = []; for (var i = 0; i < 3; i++) fs.push(() => i); out(fs.map(f => f()));
var gs = []; for (let i = 0; i < 3; i++) gs.push(() => i); out(gs.map(f => f()));
// this
const o = { n: 1, f() { return this.n; }, g: () => typeof this, h() { return (() => this.n)(); } };
out(o.f(), o.g(), o.h()); const ff = o.f; try { out(ff()); } catch (e) { out("err", e.name); }
out(o.f.call({n: 9}), o.f.apply({n: 8}, []), o.f.bind({n: 7})());
function Args() { return arguments.length + ":" + Array.prototype.slice.call(arguments).join(","); } out(Args(1,2,3));
function dflt(a, b = a + 1, ...r) { return [a, b, r]; } out(dflt(1), dflt(1, 5, 6, 7), dflt.length);
const { p = 10, q: { r2 = 20 } = {} } = { p: undefined }; out(p, r2);
const [x1, , x3 = 3, ...xs] = [1, 2, undefined, 4, 5]; out(x1, x3, xs);
out(o?.a?.b, o.zz?.(), o?.["n"], null ?? "d", 0 ?? "d", 0 || "d", "" && "x");
let la = null; la ??= 5; let lb = 1; lb &&= 7; let lc = 0; lc ||= 9; out(la, lb, lc);
out(2 ** 3 ** 2, (-2) ** 2, 7 % -3, -7 % 3, 5 / 0, -5 / 0, 0 / 0, 1 / -0);
out(typeof undeclared, typeof null, typeof (() => 1));
out("a" in {a: 1}, 1 in [5, 6], delete o.n, o.n, void 0, (1, 2, 3));
out([] + [], [] + {}, 1 + "2", "3" - 1, true + 1, null + 1, undefined + 1, "5" * "2", [2] * [3]);
out(1 == "1", 0 == "", null == undefined, null == 0, NaN == NaN, [1] == 1, "b" > "a", "10" < "9", 10 < 9, "10" < 9);
out(0.1 * 3, 1e21, 1e-7, 123456789012345680000, 0.000001, -0, [-0], 100 / 3);
switch (3) { case 1: out("one"); case 3: out("three"); case 4: out("four"); break; default: out("def"); }
function tf() { try { return "try"; } finally { out("fin"); } } out(tf());
function tf2() { try { throw 1; } catch (e) { return "c" + e; } finally { out("fin2"); } } out(tf2());
function tf3() { for (var i = 0; i < 3; i++) { try { continue; } finally { out("f" + i); } } } tf3();
// clases
class Animal { constructor(n) { this.name = n; } speak() { return this.name + " hace ruido"; } get upper() { return this.name.toUpperCase(); } static create(n) { return new this(n); } }
class Dog extends Animal { speak() { return super.speak() + " guau"; } static create(n) { const d = super.create(n); d.dog = true; return d; } }
const d = Dog.create("Rex"); out(d.speak(), d.upper, d.dog, d instanceof Animal, Object.getPrototypeOf(d) === Dog.prototype, Dog.name, d.constructor.name);
class S { static #c = 0; static inc() { return ++S.#c; } static { S.init = "ok"; } #priv() { return 1; } callPriv() { return this.#priv(); } static has(o) { return #priv in o; } }
out(S.inc(), S.inc(), S.init, new S().callPriv(), S.has(new S()), S.has({}));
function NT() { return new.target === undefined ? "call" : "new"; } out(NT(), new NT() instanceof NT);
const iter = { *[Symbol.iterator]() { yield 1; yield 2; } }; out([...iter], Array.from(iter), Math.max(...iter));
function tag(s, ...v) { return s.raw.join("|") + v.join(","); } out(tag`a${1}b${2}c\n`);
const obj2 = { get a() { return 1; }, set a(v) { out("set", v); }, ["comp" + 1]: 2, __proto__: { inh: 1 } }; obj2.a = 5; out(obj2.a, obj2.comp1, obj2.inh, Object.keys(obj2));
out([..."ab"].map((c, i) => c + i), Object.fromEntries([["a", 1]]), [1, 2, 3].findLast(x => x < 3));
var getterCount = 0; const lazy = { get v() { getterCount++; return 1; } }; lazy.v; lazy.v; out(getterCount);
out(((a, b) => a + b).length, (function () {}).name, (() => {}).name, (class {}).name, ({ m() {} }).m.name);
out(String(function foo(a, b) { return a + b; }), String(class Q { m() {} }), String((a) => a * 2));
var self = this; out(typeof globalThis, typeof self);
out(eval("1+2"), eval("var ev = 5; ev * 2"), new Function("a", "b", "return a*b")(3, 4));
(function () { "use strict"; try { undefinedStrict = 1; } catch (e) { out(e.name, e.message); } })();
(function () { out(this === undefined ? "undef" : typeof this); })();
(function () { "use strict"; out(this === undefined ? "undef" : typeof this); })();
