const nums = [0, -0, 1, -1, 0.5, 1.5, 2.5, 123.456, 1e21, 1e-7, 123456789.123456789, 5e-324, 1.7976931348623157e308, NaN, Infinity, -Infinity, 0.1 + 0.2, 100, 1234567.891, 0.000123, 255, 1 / 3];
for (const n of nums) {
  out(String(n), n.toFixed(2), n.toPrecision(4), n.toExponential(3), n.toString(2).slice(0, 30), n.toString(16), n.toString(36));
}
out((25).toString(2), (255).toString(16), (-255).toString(16), (0.5).toString(2), (3.14159).toString(8), (1e21).toString(7).slice(0, 20));
out((1.005).toFixed(2), (1.45).toFixed(1), (8.345).toFixed(2), (0).toFixed(0), (-1.5).toFixed(0), (1e21).toFixed(2), (123.456).toFixed(0), (0.000001).toFixed(7));
out((1234.5678).toPrecision(2), (0.00001234).toPrecision(2), (1e21).toPrecision(3), (123456).toPrecision(2), (99.99).toPrecision(3));
out((12345.6789).toExponential(), (0).toExponential(2), (1e-10).toExponential(1));
out(Number("  12  "), Number(""), Number("1e3"), Number("0b101"), Number("0o17"), Number("12abc"), Number(null), Number(undefined), Number([]), Number([5]), Number({}), Number(true), Number("Infinity"), Number("-0"));
out(parseInt("08"), parseInt("0x1F"), parseInt("101", 2), parseInt("z", 36), parseInt(""), parseInt("  -42abc"), parseInt(0.0000005), parseInt("123", 1), parseFloat("3.14abc"), parseFloat(".5"), parseFloat("-.5e2"), parseFloat("e5"), parseFloat("Infinityx"));
out(Number.isInteger(5.0), Number.isInteger(5.5), Number.isSafeInteger(2 ** 53), Number.EPSILON > 0, Number.MAX_SAFE_INTEGER, Number.MIN_VALUE, Number.isNaN("x"), isNaN("x"));
out(Math.floor(-0.5), Math.ceil(-0.5), Math.round(0.5), Math.round(-0.5), Math.round(1.4999999999999999), Math.abs(-3), Math.sqrt(2), Math.cbrt(27), Math.pow(2, 0.5), Math.log(Math.E), Math.log10(1000), Math.log2(8), Math.exp(1));
out(Math.sin(1), Math.cos(1), Math.tan(1), Math.atan2(1, 1), Math.asin(0.5), Math.sinh(1), Math.cosh(1), Math.tanh(1), Math.fround(5.5), Math.fround(5.05), Math.clz32(1), Math.imul(3, 4), Math.expm1(1), Math.log1p(1));
out(Math.max(), Math.min(), Math.max(NaN, 1), Math.min(1, "2"), Math.max(...[1, 5, 3]), Math.hypot(), Math.sign(-0), Math.trunc(0.9));
out(5 & 3, 5 | 3, 5 ^ 3, ~5, 1 << 31, 1 << 32, -1 >>> 0, -1 >> 1, 2 ** 32 | 0, 2 ** 31 | 0, 1.9 | 0, -1.9 | 0, NaN | 0);
out(0.1 + 0.7, 1.1 * 1.1, 4.35 * 100, 1.15.toFixed(1), 9007199254740993, 2 ** 53 + 2, 1e300 * 1e10, -1e300 * 1e10);
out((1234567.891).toLocaleString(), (0.256).toLocaleString("en-US", { style: "percent" }), (1234.5).toLocaleString("en-US", { style: "currency", currency: "USD" }), (1e6).toLocaleString("en-US"), (12).toLocaleString("en-US", { minimumFractionDigits: 2 }));
out(new Intl.NumberFormat("en-US").format(9876543.21), new Intl.NumberFormat("de-DE").format(9876543.21), new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(2.5));
out(Number.prototype.toString.call(5), (5).constructor.name, typeof Number("5"), typeof new Number(5), new Number(5) + 1, Object(1) instanceof Number);
out([10, 1, 5, 100].sort(), ["b", "a", "C"].sort(), [3, 2, 1].sort((a, b) => a - b), [0.1, 0.01].map(x => x * 3));
out(1e21 + 1, 123456789012345678901234567890, 0.1.toFixed(20), (1e-7).toFixed(10), 5e-7.toString(), 1e100.toString(), (2 ** 64).toString(), (-1e-7).toString());
