let seed = 12345; function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }
for (let blk = 0; blk < 20; blk++) {
  const o = [];
  for (let i = 0; i < 500; i++) { const x = i % 3 ? rnd() * 10 : 1 + rnd() * 0.2; const y = i % 2 ? Math.floor(rnd() * 12) - 3 : rnd() * 8 - 3; o.push(Math.pow(x, y).toPrecision(11)); }
  out(o.join(" "));
}
out(2 ** 10, 2 ** -2, 10 ** 21, 10 ** 22, 10 ** 23, 7 ** 77, (-3) ** 3, (-3) ** 2, 0 ** 0, 0 ** -1, (-0) ** -1, (-0) ** -2, 2 ** 1023, 2 ** 1024, 2 ** -1074, 2 ** -1075, 1.0000001 ** 1e7, (1.05 ** 10).toPrecision(12), 1.1 ** 10, 0.1 ** 3, 9 ** 0.5, 27 ** (1 / 3), 100 ** 1.5, 2 ** 0.5);
