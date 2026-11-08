function ticks(name, fn) {
  const log = [];
  let p = Promise.resolve();
  for (let i = 1; i <= 14; i++) { const n = i; p = p.then(() => { log.push("t" + n); }); }
  fn(log);
  return p.then(() => p).then(() => out(name, log.join(" ")));
}
async function* ag() { yield 1; yield 2; }
async function* ag2() { const x = await Promise.resolve(5); yield x; return 9; }
(async () => {
  await ticks("forawait gen", log => { (async () => { for await (const x of ag()) log.push("v" + x); log.push("done"); })(); });
  await ticks("forawait array", log => { (async () => { for await (const x of [1, 2]) log.push("v" + x); log.push("done"); })(); });
  await ticks("gen next", log => { const g = ag(); g.next().then(r => log.push("n1")); g.next().then(r => log.push("n2")); g.next().then(r => log.push("n3")); });
  await ticks("gen2", log => { (async () => { for await (const x of ag2()) log.push("v" + x); log.push("done"); })(); });
  await ticks("await val", log => { (async () => { await 1; log.push("a1"); await 1; log.push("a2"); })(); });
  await ticks("await promise", log => { (async () => { await Promise.resolve(); log.push("a1"); await new Promise(r => r()); log.push("a2"); })(); });
  await ticks("async return promise", log => { (async () => Promise.resolve(1))().then(() => log.push("r")); });
  await ticks("async return val", log => { (async () => 1)().then(() => log.push("r")); });
  await ticks("then returning promise", log => { Promise.resolve().then(() => Promise.resolve(1)).then(() => log.push("r")); });
  await ticks("yield*", log => { function* a() { yield 1; return 5; } function* b() { const r = yield* a(); log.push("r" + r); } log.push([...b()].length); });
  await ticks("async gen return", log => { async function* g() { return 1; } g().next().then(() => log.push("ret")); });
  await ticks("asyncgen yield promise", log => { async function* g() { yield Promise.resolve(1); } g().next().then(() => log.push("yp")); });
})();
