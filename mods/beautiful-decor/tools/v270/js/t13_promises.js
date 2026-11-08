const log = []; const L = (x) => log.push(x);
Promise.resolve(1).then(v => { L("a" + v); return Promise.resolve(2); }).then(v => L("b" + v));
Promise.resolve().then(() => L("c1")).then(() => L("c2")).then(() => L("c3")).then(() => L("c4"));
queueMicrotask(() => L("qm"));
(async () => { L("as1"); await null; L("as2"); await new Promise(r => r()); L("as3"); })();
new Promise((res, rej) => { L("exec"); res("r"); rej("ignored"); }).then(v => L("then " + v), e => L("rej " + e));
Promise.reject(new Error("boom")).catch(e => L("caught " + e.message)).finally(() => L("fin"));
Promise.all([1, Promise.resolve(2), new Promise(r => r(3))]).then(v => L("all " + v));
Promise.allSettled([Promise.reject("x"), 1]).then(v => L("settled " + JSON.stringify(v)));
Promise.race([new Promise(() => {}), Promise.resolve("fast")]).then(v => L("race " + v));
Promise.any([Promise.reject(1), Promise.resolve(2)]).then(v => L("any " + v));
Promise.any([Promise.reject(1), Promise.reject(2)]).catch(e => L("anyfail " + e.constructor.name + " " + e.errors + " " + e.message));
Promise.all([]).then(v => L("empty " + JSON.stringify(v)));
Promise.resolve(Promise.resolve("flat")).then(v => L(v));
const thenable = { then(res) { L("thenable called"); res("tv"); } }; Promise.resolve(thenable).then(v => L("tv " + v));
(async () => { try { await Promise.reject(new Error("aw")); } catch (e) { L("awcatch " + e.message); } finally { L("awfin"); } })();
(async () => { const r = await Promise.all([1, 2].map(async x => x * 2)); L("mapasync " + r); })();
async function thrower() { throw new TypeError("async throw"); } thrower().catch(e => L(e.name));
Promise.resolve(1).finally(() => 99).then(v => L("finally keeps " + v));
Promise.resolve(1).finally(() => { throw new Error("fin throw"); }).catch(e => L(e.message));
Promise.resolve().then(() => { throw "str"; }).catch(e => L("thrown " + e));
const p = Promise.resolve(); L(p === Promise.resolve(p)); L(p instanceof Promise); L(typeof p.then);
Promise.withResolvers && (() => { const w = Promise.withResolvers(); w.promise.then(v => L("wr " + v)); w.resolve("ok"); })();

L("sync end");

Promise.resolve().then(() => Promise.resolve().then(() => Promise.resolve().then(() => Promise.resolve().then(() => { out(log.join("\n")); }))));
