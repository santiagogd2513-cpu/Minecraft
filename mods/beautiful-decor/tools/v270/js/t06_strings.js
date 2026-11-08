const s = "Hello, Wörld! 😀 ñandú";
out(s.length, s.toUpperCase(), s.toLowerCase(), [...s].length, s.codePointAt(14), s.charCodeAt(14), s.at(-1), s.charAt(100) === "");
out(s.indexOf("o"), s.indexOf("o", 5), s.lastIndexOf("o"), s.lastIndexOf("o", 5), s.indexOf(""), s.indexOf("", 100), s.lastIndexOf(""), s.includes("Wö"), s.startsWith("Hell"), s.startsWith("ell", 1), s.endsWith("dú"), s.endsWith("Hello", 5));
out(s.slice(2, 5), s.slice(-4), s.slice(5, 2), s.substring(5, 2), s.substring(-3, 3), s.substr(-4, 2), s.substr(2), s.slice(), s.slice(NaN, 3));
out("a,b,,c".split(","), "a,b,,c".split(",", 2), "abc".split(""), "".split(","), "".split(""), "abc".split(), "a1b22c".split(/\d+/), "a1b22c".split(/(\d+)/), "abc".split("b", 0), " a  b ".split(" "), "test".split("t"));
out("  pad ".trim() + "|", "  pad ".trimStart() + "|", "  pad ".trimEnd() + "|", " ﻿x\n".trim() === "x", "5".padStart(3, "0"), "5".padEnd(3, "ab"), "abc".padStart(2), "abc".padStart(6, ""), "x".repeat(0) === "");
out("abc".localeCompare("abd"), "a".localeCompare("B"), "a".localeCompare("A"), "ä".localeCompare("z", "de"), ["b", "a", "C", "ä"].sort((a, b) => a.localeCompare(b)), "résumé".normalize("NFD").length, "résumé".normalize("NFD").normalize("NFC").length);
out("a-b_c d".replace(/[-_ ]/g, "+"), "aaa".replace("a", "b"), "aaa".replaceAll("a", "b"), "abc".replace("b", "$&$&"), "abc".replace("b", "[$`|$']"), "abc".replace(/(b)/, "<$1>"), "abc".replace(/(?<x>b)/, "<$<x>>"), "abc".replace("b", () => "$&"), "x".replace(/x/, "$$"), "abc".replace(/b/, "$0"));
out("John Smith".replace(/(\w+)\s(\w+)/, "$2, $1"), "camelCaseString".replace(/([A-Z])/g, m => "_" + m.toLowerCase()), "a.b.c".replaceAll(".", "/"), "abc".replace(/(?:)/g, "-"), "x1y2".replace(/\d/g, (d, off, str) => `[${d}@${off}]`));
out(String(123), String(null), String(undefined), String(true), String([1, [2, 3]]), String({}), String(Symbol("x")), `${[1, 2]}`, "" + { toString() { return "custom"; } }, "a" + 1 + 2, 1 + 2 + "a");
out(String.fromCharCode(72, 105, 0x1F600 & 0xFFFF), String.fromCodePoint(0x1F600, 65), String.raw`a\nb${1}c`, "\u{1F600}".length, "\x41B\103", "tab\there", 'q"uote', "it's");
out("abc" < "abd", "a" < "B", "Z" < "a", "10" < "9", "abc" == new String("abc"), "abc" === new String("abc"), typeof new String("a"), new String("ab").length, Object("x") instanceof String);
out("abc"[1], "abc"[5], "abc".length, Object.keys("ab"), "ab".hasOwnProperty(0), 1 in Object("ab"));
out([..."a😀b"], "a😀b".split(""), Array.from("a😀b"), "😀".codePointAt(0), "😀".codePointAt(1), "😀".charCodeAt(0));
out(escape("a b+c/é€"), unescape("%u20AC%E9"), encodeURI("http://x.com/a b?q=é&r=1#h"), encodeURIComponent("é€😀"), decodeURI("%41%2F%20"), decodeURIComponent("%F0%9F%98%80"));
try { decodeURIComponent("%E0%A4%A"); } catch (e) { out(e.name, e.message); }
try { encodeURIComponent("\uD800"); } catch (e) { out(e.name, e.message); }
out("abc".concat(1, null), "a-b".split("-").map(x => x.toUpperCase()).join(""), "Title Case Me".split(" ").map(w => w[0]).join(""), "%s".length);
out("ß".toUpperCase(), "İ".toLowerCase().length, "ǆ".toUpperCase(), "Σ".toLowerCase(), "ΑΣ".toLowerCase(), "I".toLowerCase());
out("abc".search(/c/), "abc".search("z"), "abcabc".match(/b/g), "abc".match(/z/), "abc".match(/(?<l>b)/).index, [..."a1b2".matchAll(/\d/g)].map(m => m[0] + m.index), "x".match(/x/g).length);
out("The Quick".match(/[A-Z]/g), "no match".match(/\d/g), "12-34".match(/(\d+)-(\d+)/).slice(0), "aBc".match(/b/i)[0]);
out(isNaN("abc"), "3" * "4", "3" + 4, +"", +" ", +"1,2", +"0x10", +"1e2", -"5", +[], +{}, +null, +undefined, +true, +"Infinity");
