const tests = [
  [/a+b*/, "caaabbbd"], [/^abc$/, "abc"], [/^abc$/m, "x\nabc\ny"], [/./s, "\n"], [/./, "\n"], [/\bfoo\b/, "a foo b"], [/\Bfoo/, "afoo"],
  [/(\d{3})-(\d{4})/, "call 555-1234 now"], [/(a)|(b)/, "b"], [/(?:a|b)+/, "abab"], [/a{2,3}/, "aaaa"], [/a{2,}/, "a aa"], [/a{,2}/, "a{,2}"], [/x*/, "aaa"],
  [/(?=a)a/, "a"], [/(?!a)\w/, "ab"], [/(?<=\$)\d+/, "cost $42"], [/(?<!\$)\b\d+/, "cost $42 or 17"], [/(a+)+b/, "aaab"],
  [/[a-c]+/i, "xABCx"], [/[^a-c]+/, "abcxyzabc"], [/[\d-x]+/, "1-x2"], [/[\]]/, "]"], [/[\[\]]/, "["], [/[\s\S]+/, "a\nb"], [/\w+@\w+\.\w+/, "mail me@x.com ok"],
  [/(?<year>\d{4})-(?<month>\d{2})/, "on 2024-03-05"], [/(\w)\1/, "hello"], [/(?<c>\w)\k<c>/, "hello"], [/A\x42\103/, "ABC"], [/\//, "a/b"], [/[\/]/, "a/b"],
  [/\p{L}+/u, "héllo wörld"], [/\p{Lu}/u, "abcD"], [/[\u{1F600}-\u{1F64F}]/u, "x😀y"], [/^.$/u, "😀"], [/\d+(?:\.\d+)?/, "pi 3.14159"],
  [/^(?:(\d+)|(\w+))$/, "abc"], [/\s+/, "a \t\n b"], [/[\p{Emoji}]/u, "a😀"],
];
for (const [re, str] of tests) { const m = re.exec(str); out(String(re), m ? JSON.stringify([...m]) + " @" + m.index + (m.groups ? JSON.stringify(m.groups) : "") : "null"); }
const g = /a(\d)/g; let m; const res = []; while ((m = g.exec("a1 a2 a3")) !== null) res.push(m[1] + ":" + g.lastIndex); out(res);
const y = /a/y; y.lastIndex = 1; out(y.test("ba"), y.lastIndex, y.test("ba"), y.lastIndex);
const gl = /x/g; out(gl.test("x"), gl.lastIndex, gl.test("x"), gl.lastIndex);
out(/a/gi.flags, /a/.source, new RegExp("a/b").source, String(new RegExp("")), new RegExp("\\d+", "g").toString(), /[/]/.source, RegExp("a", "i").ignoreCase, /a/g.global, /a/m.multiline, /a/s.dotAll, /a/u.unicode, /a/y.sticky);
out(new RegExp(/ab+c/, "i").flags);
try { new RegExp("["); } catch (e) { out(e.name); }
try { new RegExp("a", "gg"); } catch (e) { out(e.name); }
out("aBc".replace(/b/i, x => x.toUpperCase() + "!"), "a1b2".replace(/(?<d>\d)/g, "[$<d>]"), "2024-03-05".replace(/(\d+)-(\d+)-(\d+)/, "$3/$2/$1"), "aaa".replace(/a/y, "b"), "abc".replace(/(?<=a)b/, "X"));
out("one two  three".split(/\s+/), "a,b;c".split(/[,;]/), "abc".split(/(?:)/), "a1b".split(/(\d)/), "2020-01-02".split("-").map(Number));
out("x".replace(/x/, "$'"), "abc".replace(/b/, "$`"), /(?:)/.test(""), /^$/.test(""), /a|/.exec("b")[0] === "");
out("The year 1999 and 2024".match(/\d{4}/g), "tel: +1 (555) 123-4567".replace(/\D/g, ""), "CSV,line,with,\"quoted,comma\"".match(/("[^"]*"|[^,]+)/g));
out([..."a-b-c".matchAll(/-/g)].length, "aaa".lastIndexOf("a"), /[a-z]+/.exec("123 abc")?.[0], "ab".match(/(?<first>a)(?<second>b)/).groups.second);
out(/^[\w.+-]+@[a-z\d-]+(\.[a-z\d-]+)*\.[a-z]+$/i.test("john.doe+tag@example.co.uk"), /^\d{3}-\d{2}-\d{4}$/.test("123-45-6789"), /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec("#FFaa00").slice(1).map(h => parseInt(h, 16)));
