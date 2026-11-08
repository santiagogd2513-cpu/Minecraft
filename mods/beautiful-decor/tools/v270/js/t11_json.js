out(JSON.stringify({ a: 1, b: "x", c: [1, 2, { d: null }], e: undefined, f() {}, g: Symbol("s"), h: NaN, i: Infinity, j: -0, k: new Date(0), l: /x/, m: new Map([[1, 2]]), n: new Set([1]), o: [undefined, function () {}, Symbol()] }));
out(JSON.stringify("he said \"hi\"\n\t\u0001   é 😀 \ud800"), JSON.stringify(1), JSON.stringify(null), JSON.stringify(undefined), JSON.stringify(() => 1), JSON.stringify(Symbol()), JSON.stringify([]), JSON.stringify({}), JSON.stringify(new Number(5)), JSON.stringify(new String("s")), JSON.stringify(Object(false)));
out(JSON.stringify({ a: [1, 2], b: { c: 3 } }, null, 2));
out(JSON.stringify({ a: [], b: {}, c: [[]], d: [{}] }, null, "--"));
out(JSON.stringify({ a: 1, b: 2, c: 3 }, ["a", "c"]), JSON.stringify({ a: 1, b: { a: 2, c: 3 } }, ["a"]), JSON.stringify({ a: 1, b: 2 }, (k, v) => typeof v === "number" ? v * 10 : v), JSON.stringify([1, 2], (k, v) => k === "0" ? undefined : v));
out(JSON.stringify({ a: 1 }, null, 20).length, JSON.stringify({ a: 1 }, null, "abcdefghijklmnop"), JSON.stringify([1], null, 0), JSON.stringify([1], null, -1), JSON.stringify({ toJSON() { return { x: 1 }; } }), JSON.stringify({ a: { toJSON(k) { return "key:" + k; } } }));
out(JSON.parse("1"), JSON.parse('"s"'), JSON.parse("null"), JSON.parse("true"), JSON.parse("[1,2,3]"), JSON.parse(' {"a" : [1 , {"b":2}] } '), JSON.parse("-1.5e2"), JSON.parse('"\\u00e9\\n\\/"'), JSON.parse("0.1"), JSON.parse("1E3"), JSON.parse('{"__proto__": 1}'));
out(JSON.parse('{"a":1,"b":[1,2]}', (k, v) => Array.isArray(v) ? v.length : v), JSON.parse("[1,2,3]", function (k, v) { return k === "" ? v : v * 2; }), JSON.parse('{"a":{"b":1}}', (k, v) => k === "b" ? undefined : v));
for (const bad of ["", "{", "[1,]", '{"a":1,}', "{a:1}", "'x'", "01", "1.", ".5", "+1", "NaN", "undefined", '"\n"', '{"a":1}x', "[1 2]", '"\\x41"', "tru", "nul"]) { try { JSON.parse(bad); out("parsed?", bad); } catch (e) { out(e.name, JSON.stringify(bad)); } }
out(JSON.stringify(JSON.parse('{"b":1,"a":2,"1":3,"0":4}')), Object.keys(JSON.parse('{"b":1,"a":2,"1":3,"0":4}')));
const nested = JSON.parse(JSON.stringify({ x: [1, { y: [2, [3]] }] })); out(nested.x[1].y[1][0]);
out(JSON.stringify({ date: new Date(Date.UTC(2020, 1, 29, 12, 30)) }), JSON.stringify([new Date(NaN)]), JSON.stringify({ [Symbol("k")]: 1, k: 2 }), JSON.stringify(Object.create({ inherited: 1 })), JSON.stringify("\u007f\u0080"), JSON.stringify({ "a b": 1, "": 2, 3: 4 }));
out(JSON[Symbol.toStringTag], typeof JSON.parse, JSON.stringify.length, JSON.parse.length);
const big = []; for (let i = 0; i < 200; i++) big.push({ id: i, tags: ["a", "b"], nested: { v: i / 3 } }); const txt = JSON.stringify(big); out(txt.length, JSON.parse(txt)[199].nested.v, JSON.stringify(big, null, 1).split("\n").length);
