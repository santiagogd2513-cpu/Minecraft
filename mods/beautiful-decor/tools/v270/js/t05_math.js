out(Math.PI, Math.E, Math.LN2, Math.LN10, Math.LOG2E, Math.LOG10E, Math.SQRT2, Math.SQRT1_2);
const fns = ["sin","cos","tan","asin","acos","atan","sinh","cosh","tanh","asinh","acosh","atanh","exp","expm1","log","log1p","log2","log10","cbrt","sqrt"];
const xs = [0, 0.1, 0.5, 1, 1.5, 2, 3, 7, 10, 22.5, 100, 1000, 0.001, -0.5, -2, 1e-9, 1e10, 123.456, 0.9999, 5.5];
for (const f of fns) { const r = []; for (const x of xs) r.push(Math[f](x)); out(f, r.join(" ")); }
let seed = 12345; function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }
for (const f of fns) { const r = []; for (let i = 0; i < 60; i++) { const x = (rnd() * 20 - 5) * (i % 3 ? 1 : 100); r.push(Math[f](x)); } out(f, "rnd", r.join(" ")); }
const pw = []; for (let i = 0; i < 80; i++) { pw.push(Math.pow(rnd() * 10, rnd() * 8 - 3).toPrecision(11)); } out(pw.join(" "));
const at = []; for (let i = 0; i < 40; i++) { at.push(Math.atan2(rnd() * 4 - 2, rnd() * 4 - 2)); } out(at.join(" "));
const hy = []; for (let i = 0; i < 40; i++) { hy.push(Math.hypot(rnd() * 100, rnd() * 100, rnd() * 3)); } out(hy.join(" "));
out(Math.pow(2, 10), Math.pow(2, -1), Math.pow(-8, 1 / 3), Math.pow(0, 0), Math.pow(NaN, 0), Math.pow(1, Infinity), 2 ** 0.5, 10 ** -2, 10 ** 15, 10 ** 21, 3 ** 40);
out(Math.round(2.4), Math.round(2.5), Math.round(2.6), Math.round(-2.4), Math.round(-2.5), Math.round(-2.6), Math.round(0.49999999999999994), Math.round(4503599627370495.5), Math.round(-0.2));
out(Math.min(0, -0), Math.max(-0, 0), Math.atan2(0, -0), Math.atan2(-0, -0), 1 / Math.min(0, -0));
