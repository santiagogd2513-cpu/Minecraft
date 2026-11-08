const o = { b: 2, a: 1, 2: "two", 1: "one", [Symbol("s")]: 3 };
out(Object.keys(o), Object.values(o), Object.entries(o), JSON.stringify(o), Object.getOwnPropertyNames(o), Object.getOwnPropertySymbols(o).length, "a" in o, o.hasOwnProperty("a"), Object.hasOwn(o, "zz"));
for (const k in { x: 1, y: 2, __proto__: { z: 3 } }) out("forin", k);
const fr = Object.freeze({ a: 1, n: { b: 2 } }); fr.a = 9; fr.n.b = 3; delete fr.a; out(fr, Object.isFrozen(fr), Object.isSealed(fr), Object.isExtensible(fr));
"use strict";
(function () { "use strict"; try { fr.a = 2; } catch (e) { out(e.name, e.message); } try { delete fr.a; } catch (e) { out(e.name, e.message); } try { Object.defineProperty(fr, "q", { value: 1 }); } catch (e) { out(e.name, e.message); } })();
const d = {}; Object.defineProperty(d, "hidden", { value: 1, enumerable: false }); Object.defineProperty(d, "acc", { get() { return 42; }, enumerable: true, configurable: true });
out(Object.keys(d), d.hidden, d.acc, JSON.stringify(d), Object.getOwnPropertyDescriptor(d, "hidden"), Object.getOwnPropertyDescriptor(d, "acc").get !== undefined, Object.getOwnPropertyDescriptors({ a: 1 }));
out(Object.assign({}, { a: 1 }, null, { b: 2 }, "xy"), { ...{ a: 1 }, ...{ a: 2, b: 3 } }, { ...[1, 2] }, { ..."ab" }, { ...null });
out(Object.fromEntries(new Map([["a", 1]])), Object.fromEntries([["x", 1], ["y", 2]]), Object.entries({ a: 1 }).map(([k, v]) => k + v));
const proto = { greet() { return "hi " + this.name; } }; const child = Object.create(proto, { name: { value: "kid", enumerable: true } });
out(child.greet(), Object.getPrototypeOf(child) === proto, proto.isPrototypeOf(child), Object.keys(child), child instanceof Object, Object.create(null) instanceof Object);
out(Object.is(NaN, NaN), Object.is(0, -0), Object.is("a", "a"), Object.setPrototypeOf({ a: 1 }, null) instanceof Object);
out({}.toString(), Object.prototype.toString.call([]), Object.prototype.toString.call(null), Object.prototype.toString.call(new Date(0)), Object.prototype.toString.call(/x/), Object.prototype.toString.call(function () {}), Object.prototype.toString.call(1), Object.prototype.toString.call("s"), Object.prototype.toString.call(undefined), Object.prototype.toString.call(Symbol()), Object.prototype.toString.call(new Map()), Object.prototype.toString.call(Promise.resolve()), Object.prototype.toString.call(JSON), Object.prototype.toString.call(Math));
class Temp { constructor() { this.c = 20; } get f() { return this.c * 9 / 5 + 32; } set f(v) { this.c = (v - 32) * 5 / 9; } toString() { return "T" + this.c; } valueOf() { return this.c; } toJSON() { return { celsius: this.c }; } }
const t = new Temp(); t.f = 212; out(t.c, t.f, `${t}`, t + 1, t * 2, JSON.stringify(t), t > 50, Object.keys(t));
out(typeof Object.getOwnPropertyDescriptor(Temp.prototype, "f").get, Object.getOwnPropertyNames(Temp.prototype), Temp.prototype.constructor === Temp);
const sym = Symbol("desc"); out(sym.toString(), sym.description, Symbol.for("k") === Symbol.for("k"), Symbol.keyFor(Symbol.for("k")), typeof Symbol.iterator, Symbol("a") === Symbol("a"));
const wk = { [Symbol.toPrimitive](h) { return h === "number" ? 42 : h === "string" ? "str" : "def"; } }; out(+wk, `${wk}`, wk + "", wk * 1);
const circ = { name: "c" }; circ.self = circ; try { JSON.stringify(circ); } catch (e) { out(e.name, e.message.split("\n")[0]); }
out(Object.entries({ a: 1, b: 2 }).reduce((acc, [k, v]) => ({ ...acc, [v]: k }), {}), structuredClone({ a: [1, { b: 2 }], d: new Date(0), s: new Set([1]), m: new Map([[1, 2]]) }));
const getset = { _v: 1, get v() { return this._v; }, set v(x) { this._v = x * 2; } }; getset.v = 5; out(getset.v, Object.keys(getset), JSON.stringify(getset));
out(Object.keys("abc"), Object.values("ab"), Object.entries([7]), Object.getOwnPropertyNames("ab"), Object.getOwnPropertyNames([1]));
out(delete {}.x, (() => { const q = { a: 1 }; return delete q.a && !("a" in q); })(), typeof Object.entries, Object.keys(Object).length, Object.length, Function.length, Function.prototype.name === "");
const counter = (() => { let c = 0; return { inc: () => ++c, get value() { return c; } }; })(); counter.inc(); counter.inc(); out(counter.value);
label1: { out("in block"); break label1; }
var x1 = 1; { var x1 = 2; } out(x1); let y1 = 1; { let y1 = 2; } out(y1);
out(((a, b = 2, ...c) => 0).length, (function (a, b) {}).length, Math.max.length, [].push.length, "a".padStart.length);
out(isFinite("12"), Number.isFinite("12"), parseFloat("1.5e3x"), (123.456).toFixed(1), 0.1 + 0.2 === 0.3, Math.abs(0.3 - (0.1 + 0.2)) < Number.EPSILON);
