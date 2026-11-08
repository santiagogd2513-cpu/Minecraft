function* fib() { let [a, b] = [0, 1]; while (true) { yield a; [a, b] = [b, a + b]; } }
const it = fib(); out(Array.from({ length: 10 }, () => it.next().value));
function* g1() { const x = yield 1; out("got", x); try { yield 2; } finally { out("cleanup"); } return 3; }
const i1 = g1(); out(i1.next("ignored"), i1.next("hello"), i1.return(9), i1.next());
function* g2() { try { yield 1; yield 2; } catch (e) { out("caught", e); yield "after"; } }
const i2 = g2(); out(i2.next(), i2.throw("err"), i2.next());
function* g3() { yield* [1, 2]; const r = yield* g4(); yield r; } function* g4() { yield "a"; return "ret"; } out([...g3()]);
const obj = { *[Symbol.iterator]() { yield 1; yield 2; yield 3; } }; const [first, ...others] = obj; out(first, others, Math.max(...obj), new Set(obj).size, Array.from(obj, x => x * 2));
class Coll { constructor() { this.items = ["x", "y"]; } *[Symbol.iterator]() { for (const i of this.items) yield i.toUpperCase(); } static *range(a, b) { for (let i = a; i < b; i++) yield i; } }
out([...new Coll()], [...Coll.range(0, 4)]);
const gi = (function* () { yield 1; })(); out(typeof gi.next, gi[Symbol.iterator]() === gi, Object.prototype.toString.call(gi), gi.toString());
for (const [k, v] of Object.entries({ a: 1, b: 2 })) out(k, v);
for (const [i, ch] of [..."ab"].entries()) out(i, ch);
for (const x of new Set([1, 2, 2])) out("set", x);
for (const [k, v] of new Map([["m", 1]])) out("map", k, v);
const closed = []; const iter = { [Symbol.iterator]() { let n = 0; return { next() { return { value: n++, done: n > 5 }; }, return() { closed.push("closed"); return {}; } }; } };
for (const x of iter) { if (x === 2) break; } out(closed); const [q1, q2] = iter; out(q1, q2, closed);
function* inf() { let i = 0; while (true) yield i++; } const taken = []; for (const v of inf()) { if (v > 3) break; taken.push(v); } out(taken);
out([...function* () { yield 1; yield 2; }()].map(x => x * 2));
const lazy = { *map(it, f) { for (const x of it) yield f(x); }, *filter(it, f) { for (const x of it) if (f(x)) yield x; }, *take(it, n) { let i = 0; for (const x of it) { if (i++ >= n) return; yield x; } } };
out([...lazy.take(lazy.filter(lazy.map(inf(), x => x * x), x => x % 2), 4)]);
async function af() { return 1; } out(af() instanceof Promise, Object.prototype.toString.call(af), af.constructor.name);
const sleep = (ms, v) => new Promise(r => Promise.resolve().then(() => r(v)));
(async () => { const r = []; for (const x of [1, 2, 3]) r.push(await sleep(0, x * 2)); out(r); const res = await Promise.all([sleep(0, "a"), sleep(0, "b")]); out(res); })();
async function* ag() { yield 1; yield 2; } (async () => { const out2 = []; for await (const x of ag()) out2.push(x); out("asyncgen", out2); for await (const y of [Promise.resolve("p1"), "p2"]) out(y); })();
