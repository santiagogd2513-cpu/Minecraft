var a = 1 + 2 * 3;
out("a", a);
let s = "hola";
const f = (x, y = 2) => x * y;
out(f(3), f(3, 4), s + " mundo", typeof s);
function fib(n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
out(fib(15));
class A { constructor(x) { this.x = x; } get dbl() { return this.x * 2; } static make() { return new A(5); } }
class B extends A { constructor() { super(7); this.y = 1; } sum() { return this.x + this.y; } }
var b = new B();
out(b.sum(), b.dbl, A.make().x, b instanceof A);
var o = { p: 1, q: [1, 2, 3], r: { s: "t" } };
var { p, q: [q0, ...qr], r: { s: ss } } = o;
out(p, q0, qr, ss);
for (let i = 0; i < 3; i++) { out(i); }
label: for (var i = 0; i < 3; i++) { for (var j = 0; j < 3; j++) { if (j == 1) continue label; if (i == 2) break label; out(i, j); } }
try { null.x } catch (e) { out(e.message) }
function* g() { yield 1; yield 2; return 3; }
var it = g(); out(it.next(), it.next(), it.next(), it.next());
out([...g()], `t${1 + 1}x`);
