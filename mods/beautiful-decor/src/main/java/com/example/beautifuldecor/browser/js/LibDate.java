package com.example.beautifuldecor.browser.js;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

/** Date y el formato local de fechas. */
final class LibDate {
    private LibDate() {
    }

    private static JsDate thisDate(Object t, String m) {
        if (t instanceof JsDate d) {
            return d;
        }
        throw Js.typeError("this is not a Date object.");
    }

    private static double argNum(Object[] a, int i) {
        return Js.toNumber(Js.arg(a, i));
    }

    private static double fromComponents(Object[] a, boolean utc) {
        double y = argNum(a, 0);
        double mo = a.length > 1 ? argNum(a, 1) : 0;
        double d = a.length > 2 ? argNum(a, 2) : 1;
        double h = a.length > 3 ? argNum(a, 3) : 0;
        double mi = a.length > 4 ? argNum(a, 4) : 0;
        double s = a.length > 5 ? argNum(a, 5) : 0;
        double ms = a.length > 6 ? argNum(a, 6) : 0;
        if (!Double.isNaN(y)) {
            double yi = y < 0 ? Math.ceil(y) : Math.floor(y);
            if (yi >= 0 && yi <= 99) {
                y = 1900 + yi;
            }
        }
        double t = DateUtil.make(y, mo, d, h, mi, s, ms);
        return DateUtil.clip(utc ? t : DateUtil.toUtc(t));
    }

    private static String toStr(double t) {
        if (Double.isNaN(t)) {
            return "Invalid Date";
        }
        return DateUtil.dateString(t) + " " + DateUtil.timeString(t);
    }

    static void install(Realm r) {
        JsObject dp = r.dateProto;
        NativeFunction dc = Lib.ctor(r, "Date", 7, dp, (t, a) -> toStr(Math.floor((double) System.currentTimeMillis())), (a, nt) -> {
            JsObject proto = Lib.protoFrom(nt, dp);
            if (a.length == 0) {
                return new JsDate(proto, (double) System.currentTimeMillis());
            }
            if (a.length == 1) {
                Object v = a[0];
                if (v instanceof JsDate d) {
                    return new JsDate(proto, d.time);
                }
                Object p = Js.toPrimitive(v, "default");
                if (p instanceof String s) {
                    return new JsDate(proto, DateUtil.parse(s));
                }
                return new JsDate(proto, DateUtil.clip(Js.toNumber(p)));
            }
            return new JsDate(proto, fromComponents(a, false));
        });
        Lib.method(r, dc, "now", 0, (t, a) -> Js.num((double) System.currentTimeMillis()));
        Lib.method(r, dc, "parse", 1, (t, a) -> Js.num(DateUtil.parse(Js.toStr(Js.arg(a, 0)))));
        Lib.method(r, dc, "UTC", 7, (t, a) -> Js.num(fromComponents(a, true)));

        Lib.method(r, dp, "getTime", 0, (t, a) -> Js.num(thisDate(t, "getTime").time));
        Lib.method(r, dp, "valueOf", 0, (t, a) -> Js.num(thisDate(t, "valueOf").time));
        Lib.method(r, dp, "getTimezoneOffset", 0, (t, a) -> {
            double tm = thisDate(t, "getTimezoneOffset").time;
            return Double.isNaN(tm) ? Js.NAN : Js.num(-(long) (DateUtil.offsetMs(DateUtil.zone(), tm) / 60000.0) + 0.0);
        });
        String[] names = {"FullYear", "Month", "Date", "Hours", "Minutes", "Seconds", "Milliseconds", "Day"};
        for (int i = 0; i < names.length; i++) {
            int field = i;
            for (int u = 0; u < 2; u++) {
                boolean utc = u == 1;
                String nm = "get" + (utc ? "UTC" : "") + names[i];
                Lib.method(r, dp, nm, 0, (t, a) -> {
                    double tm = thisDate(t, nm).time;
                    if (Double.isNaN(tm)) {
                        return Js.NAN;
                    }
                    DateUtil.Fields f = DateUtil.fields(utc ? tm : DateUtil.toLocal(tm));
                    switch (field) {
                        case 0:
                            return Js.num(f.year);
                        case 1:
                            return Js.num(f.month);
                        case 2:
                            return Js.num(f.day);
                        case 3:
                            return Js.num(f.hour);
                        case 4:
                            return Js.num(f.minute);
                        case 5:
                            return Js.num(f.second);
                        case 6:
                            return Js.num(f.ms);
                        default:
                            return Js.num(f.weekday);
                    }
                });
            }
        }
        Lib.method(r, dp, "getYear", 0, (t, a) -> {
            double tm = thisDate(t, "getYear").time;
            return Double.isNaN(tm) ? Js.NAN : Js.num(DateUtil.fields(DateUtil.toLocal(tm)).year - 1900);
        });
        Lib.method(r, dp, "setYear", 1, (t, a) -> {
            JsDate d = thisDate(t, "setYear");
            double y = argNum(a, 0);
            if (Double.isNaN(y)) {
                d.time = Double.NaN;
                return Js.NAN;
            }
            double yi = y < 0 ? Math.ceil(y) : Math.floor(y);
            double full = yi >= 0 && yi <= 99 ? yi + 1900 : yi;
            double tm = Double.isNaN(d.time) ? 0 : d.time;
            DateUtil.Fields f = DateUtil.fields(DateUtil.toLocal(tm));
            return Js.num(applySet(d, f, false, 0, new double[] {full}, false));
        });
        Lib.method(r, dp, "setTime", 1, (t, a) -> {
            JsDate d = thisDate(t, "setTime");
            d.time = DateUtil.clip(argNum(a, 0));
            return Js.num(d.time);
        });
        // setters: nombre, campo inicial, máximo de argumentos
        Object[][] setters = {{"FullYear", 0, 3}, {"Month", 1, 2}, {"Date", 2, 1}, {"Hours", 3, 4}, {"Minutes", 4, 3},
            {"Seconds", 5, 2}, {"Milliseconds", 6, 1}};
        for (Object[] st : setters) {
            int first = (Integer) st[1];
            int max = (Integer) st[2];
            for (int u = 0; u < 2; u++) {
                boolean utc = u == 1;
                String nm = "set" + (utc ? "UTC" : "") + st[0];
                Lib.method(r, dp, nm, max, (t, a) -> {
                    JsDate d = thisDate(t, nm);
                    double[] vals = new double[Math.min(max, Math.max(a.length, 1))];
                    for (int i = 0; i < vals.length; i++) {
                        vals[i] = argNum(a, i);
                    }
                    double tm = d.time;
                    if (Double.isNaN(tm)) {
                        if (first != 0) {
                            return Js.NAN;
                        }
                        tm = 0;
                        DateUtil.Fields f = DateUtil.fields(0);
                        return Js.num(applySet(d, f, utc, first, vals, true));
                    }
                    DateUtil.Fields f = DateUtil.fields(utc ? tm : DateUtil.toLocal(tm));
                    return Js.num(applySet(d, f, utc, first, vals, false));
                });
            }
        }
        Lib.method(r, dp, "toISOString", 0, (t, a) -> {
            double tm = thisDate(t, "toISOString").time;
            if (Double.isNaN(tm)) {
                throw Js.rangeError("Invalid time value");
            }
            return DateUtil.iso(tm);
        });
        Lib.method(r, dp, "toJSON", 1, (t, a) -> {
            Object tv = Js.toPrimitive(t, "number");
            if (tv instanceof Number n && (Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue()))) {
                return Js.NULL;
            }
            return Lib.callable(Js.getMember(r, t, "toISOString"), "toISOString").call(t, Js.EMPTY);
        });
        Lib.method(r, dp, "toString", 0, (t, a) -> toStr(thisDate(t, "toString").time));
        Lib.method(r, dp, "toDateString", 0, (t, a) -> {
            double tm = thisDate(t, "toDateString").time;
            return Double.isNaN(tm) ? "Invalid Date" : DateUtil.dateString(tm);
        });
        Lib.method(r, dp, "toTimeString", 0, (t, a) -> {
            double tm = thisDate(t, "toTimeString").time;
            return Double.isNaN(tm) ? "Invalid Date" : DateUtil.timeString(tm);
        });
        NativeFunction utcStr = new NativeFunction(r, "toUTCString", 0, (t, a) -> {
            double tm = thisDate(t, "toUTCString").time;
            return Double.isNaN(tm) ? "Invalid Date" : DateUtil.utcString(tm);
        });
        dp.setHidden("toUTCString", utcStr);
        dp.setHidden("toGMTString", utcStr);
        Lib.method(r, dp, "toLocaleString", 0, (t, a) -> localeFormat(thisDate(t, "toLocaleString").time, Js.arg(a, 0), Js.arg(a, 1), 3));
        Lib.method(r, dp, "toLocaleDateString", 0, (t, a) -> localeFormat(thisDate(t, "toLocaleDateString").time, Js.arg(a, 0), Js.arg(a, 1), 1));
        Lib.method(r, dp, "toLocaleTimeString", 0, (t, a) -> localeFormat(thisDate(t, "toLocaleTimeString").time, Js.arg(a, 0), Js.arg(a, 1), 2));
        dp.defineOwn(JsSymbol.TO_PRIMITIVE, new Prop(new NativeFunction(r, "[Symbol.toPrimitive]", 1, (t, a) -> {
            if (!(t instanceof JsObject o)) {
                throw Js.typeError("Date.prototype[Symbol.toPrimitive] called on non-object");
            }
            Object hint = Js.arg(a, 0);
            boolean str;
            if ("string".equals(hint) || "default".equals(hint)) {
                str = true;
            } else if ("number".equals(hint)) {
                str = false;
            } else {
                throw Js.typeError("Invalid hint: " + Js.display(hint));
            }
            String[] order = str ? new String[] {"toString", "valueOf"} : new String[] {"valueOf", "toString"};
            for (String m : order) {
                Object f = o.get(m);
                if (f instanceof JsFunction fn) {
                    Object res = fn.call(o, Js.EMPTY);
                    if (!(res instanceof JsObject)) {
                        return res;
                    }
                }
            }
            throw Js.typeError("Cannot convert object to primitive value");
        }), Prop.C));
    }

    private static double applySet(JsDate d, DateUtil.Fields f, boolean utc, int first, double[] vals, boolean fromNaN) {
        double[] v = {f.year, f.month, f.day, f.hour, f.minute, f.second, f.ms};
        for (int i = 0; i < vals.length; i++) {
            v[first + i] = vals[i];
        }
        double t = DateUtil.make(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
        double res = DateUtil.clip(utc ? t : DateUtil.toUtc(t));
        d.time = res;
        return res;
    }

    // ------------------------------------------------------------------ formato local

    private static String opt(JsObject o, String key) {
        if (o == null) {
            return null;
        }
        Object v = o.get(key);
        return v == Js.UNDEFINED ? null : Js.toStr(v);
    }

    private static String pad2(long v) {
        return v < 10 ? "0" + v : Long.toString(v);
    }

    static String localeTag(Object locales) {
        if (locales instanceof String s) {
            return s;
        }
        if (locales instanceof JsArray a && !a.list.isEmpty() && a.getIndex(0) instanceof String s) {
            return s;
        }
        return "en-US";
    }

    static String localeFormat(double t, Object locales, Object options, int defaults) {
        if (Double.isNaN(t)) {
            return "Invalid Date";
        }
        String tag = localeTag(locales);
        JsObject o = options instanceof JsObject jo ? jo : null;
        String weekday = opt(o, "weekday");
        String year = opt(o, "year");
        String month = opt(o, "month");
        String day = opt(o, "day");
        String hour = opt(o, "hour");
        String minute = opt(o, "minute");
        String second = opt(o, "second");
        String tzName = opt(o, "timeZoneName");
        String dateStyle = opt(o, "dateStyle");
        String timeStyle = opt(o, "timeStyle");
        String h12 = opt(o, "hour12");
        String hourCycle = opt(o, "hourCycle");
        String fsd = opt(o, "fractionalSecondDigits");
        String tzOpt = opt(o, "timeZone");
        boolean atJoin = false;
        if (dateStyle != null || timeStyle != null) {
            if (dateStyle != null) {
                switch (dateStyle) {
                    case "full":
                        weekday = "long";
                        month = "long";
                        day = "numeric";
                        year = "numeric";
                        atJoin = true;
                        break;
                    case "long":
                        month = "long";
                        day = "numeric";
                        year = "numeric";
                        atJoin = true;
                        break;
                    case "medium":
                        month = "short";
                        day = "numeric";
                        year = "numeric";
                        break;
                    default:
                        month = "numeric";
                        day = "numeric";
                        year = "2-digit";
                }
            }
            if (timeStyle != null) {
                hour = "numeric";
                minute = "2-digit";
                if (!timeStyle.equals("short")) {
                    second = "2-digit";
                }
                if (timeStyle.equals("full")) {
                    tzName = "long";
                } else if (timeStyle.equals("long")) {
                    tzName = "short";
                }
            }
        } else {
            boolean anyDate = weekday != null || year != null || month != null || day != null;
            boolean anyTime = hour != null || minute != null || second != null || fsd != null;
            if (!anyDate && !anyTime) {
                if ((defaults & 1) != 0) {
                    year = "numeric";
                    month = "numeric";
                    day = "numeric";
                }
                if ((defaults & 2) != 0) {
                    hour = "numeric";
                    minute = "2-digit";
                    second = "2-digit";
                }
            } else if (defaults == 1 && !anyDate) {
                year = "numeric";
                month = "numeric";
                day = "numeric";
            } else if (defaults == 2 && !anyTime) {
                hour = "numeric";
                minute = "2-digit";
                second = "2-digit";
            }
            if ("long".equals(month)) {
                atJoin = true;
            }
        }
        ZoneId zone = DateUtil.zone();
        if (tzOpt != null) {
            try {
                zone = ZoneId.of(tzOpt.equalsIgnoreCase("utc") ? "UTC" : tzOpt);
            } catch (DateTimeException e) {
                throw Js.rangeError("Invalid time zone specified: " + tzOpt);
            }
        }
        long off = DateUtil.offsetMs(zone, t);
        DateUtil.Fields f = DateUtil.fields(t + off);
        String lang = tag.indexOf('-') > 0 ? tag.substring(0, tag.indexOf('-')) : tag;
        boolean us = lang.equals("en") && !tag.equals("en-GB") && !tag.equals("en-AU") && !tag.equals("en-NZ")
                && !tag.equals("en-IN") && !tag.equals("en-IE") && !tag.equals("en-ZA");
        Locale loc = Locale.forLanguageTag(tag);

        String datePart = formatDate(f, loc, lang, us, weekday, year, month, day);
        String timePart = formatTime(f, us, lang, hour, minute, second, fsd, h12, hourCycle);
        if (tzName != null) {
            String z;
            if (tzName.equals("long")) {
                z = zone.getId().equals("UTC") || zone.getId().equals("Z") ? "Coordinated Universal Time" : DateUtil.zoneName(zone, t, true);
            } else {
                z = DateUtil.zoneShort(zone, t);
            }
            if (timePart.isEmpty()) {
                datePart = datePart.isEmpty() ? z : datePart + ", " + z;
            } else {
                timePart = timePart + " " + z;
            }
        }
        if (datePart.isEmpty()) {
            return timePart;
        }
        if (timePart.isEmpty()) {
            return datePart;
        }
        return datePart + (atJoin && us ? " at " : ", ") + timePart;
    }

    private static String monthName(int m0, Loc loc, String style) {
        if (loc.us) {
            String[] full = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
                "November", "December"};
            if (style.equals("long")) {
                return full[m0];
            }
            if (style.equals("narrow")) {
                return full[m0].substring(0, 1);
            }
            return full[m0].substring(0, 3);
        }
        TextStyle ts = style.equals("long") ? TextStyle.FULL : (style.equals("narrow") ? TextStyle.NARROW : TextStyle.SHORT);
        return Month.of(m0 + 1).getDisplayName(ts, loc.locale);
    }

    private static String dayName(int wd, Loc loc, String style) {
        if (loc.us) {
            String[] full = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            if (style.equals("long")) {
                return full[wd];
            }
            if (style.equals("narrow")) {
                return full[wd].substring(0, 1);
            }
            return full[wd].substring(0, 3);
        }
        TextStyle ts = style.equals("long") ? TextStyle.FULL : (style.equals("narrow") ? TextStyle.NARROW : TextStyle.SHORT);
        DayOfWeek dow = DayOfWeek.of(wd == 0 ? 7 : wd);
        return dow.getDisplayName(ts, loc.locale);
    }

    private static final class Loc {
        final boolean us;
        final Locale locale;
        final String lang;

        Loc(boolean us, Locale locale, String lang) {
            this.us = us;
            this.locale = locale;
            this.lang = lang;
        }
    }

    private static String formatDate(DateUtil.Fields f, Locale locale, String lang, boolean us, String weekday, String year,
            String month, String day) {
        if (weekday == null && year == null && month == null && day == null) {
            return "";
        }
        Loc loc = new Loc(us, locale, lang);
        String y = null;
        if (year != null) {
            y = year.equals("2-digit") ? pad2(Math.floorMod(f.year, 100L)) : Long.toString(f.year);
        }
        String d = null;
        if (day != null) {
            d = day.equals("2-digit") ? pad2(f.day) : Integer.toString(f.day);
        }
        String w = weekday == null ? null : dayName(f.weekday, loc, weekday);
        boolean textMonth = month != null && (month.equals("long") || month.equals("short") || month.equals("narrow"));
        String core;
        if (textMonth) {
            String mn = monthName(f.month, loc, month);
            if (us) {
                core = mn + (d != null ? " " + d : "");
                if (y != null) {
                    core = d != null ? core + ", " + y : core + " " + y;
                }
            } else {
                boolean de = lang.equals("es") || lang.equals("pt");
                StringBuilder sb = new StringBuilder();
                if (d != null) {
                    sb.append(d).append(lang.equals("de") ? ". " : " ");
                    if (de) {
                        sb.append("de ");
                    }
                }
                sb.append(mn);
                if (y != null) {
                    sb.append(de ? " de " : " ").append(y);
                }
                core = sb.toString();
            }
        } else {
            String m = null;
            if (month != null) {
                m = month.equals("2-digit") ? pad2(f.month + 1) : Integer.toString(f.month + 1);
            }
            String order = "DMY";
            String sep = "/";
            boolean pad = false;
            if (us) {
                order = "MDY";
            } else if (lang.equals("de") || lang.equals("ru") || lang.equals("pl") || lang.equals("tr") || lang.equals("fi")
                    || lang.equals("nb") || lang.equals("cs")) {
                sep = ".";
            } else if (lang.equals("ja") || lang.equals("zh")) {
                order = "YMD";
            } else if (lang.equals("sv") || lang.equals("lt")) {
                order = "YMD";
                sep = "-";
                pad = true;
            } else if (lang.equals("nl")) {
                sep = "-";
            } else if (lang.equals("fr") || lang.equals("pt") || lang.equals("en")) {
                pad = true;
            }
            if (pad) {
                if (m != null && month.equals("numeric")) {
                    m = pad2(f.month + 1);
                }
                if (d != null && day.equals("numeric")) {
                    d = pad2(f.day);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (char c : order.toCharArray()) {
                String part = c == 'D' ? d : (c == 'M' ? m : y);
                if (part != null) {
                    if (sb.length() > 0) {
                        sb.append(sep);
                    }
                    sb.append(part);
                }
            }
            core = sb.toString();
        }
        if (w == null) {
            return core;
        }
        if (core.isEmpty()) {
            return w;
        }
        boolean comma = us || lang.equals("es") || lang.equals("pt") || lang.equals("de") || !textMonth;
        return w + (comma ? ", " : " ") + core;
    }

    private static String formatTime(DateUtil.Fields f, boolean us, String lang, String hour, String minute, String second, String fsd,
            String h12, String hourCycle) {
        if (hour == null && minute == null && second == null && fsd == null) {
            return "";
        }
        boolean twelve = us;
        if (h12 != null) {
            twelve = h12.equals("true");
        } else if (hourCycle != null) {
            twelve = hourCycle.equals("h12") || hourCycle.equals("h11");
        }
        StringBuilder sb = new StringBuilder();
        if (hour != null) {
            int h = f.hour;
            if (twelve) {
                h = h % 12 == 0 ? 12 : h % 12;
            }
            boolean two = hour.equals("2-digit") || (!twelve && minute != null && !lang.equals("es") && !lang.equals("it"));
            sb.append(two ? pad2(h) : Integer.toString(h));
        }
        if (minute != null) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(hour != null || minute.equals("2-digit") && second != null ? pad2(f.minute) : (minute.equals("2-digit") ? pad2(f.minute) : Integer.toString(f.minute)));
        }
        if (second != null) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(minute != null || second.equals("2-digit") ? pad2(f.second) : Integer.toString(f.second));
        }
        if (fsd != null) {
            int digits = Math.max(1, Math.min(3, (int) Js.toInteger(Js.num(Double.parseDouble(fsd)))));
            String ms = DateUtil.pad(f.ms, 3).substring(0, digits);
            sb.append(sb.length() > 0 ? "." : "").append(ms);
        }
        if (hour != null && twelve) {
            sb.append(f.hour < 12 ? " AM" : " PM");
        }
        return sb.toString();
    }
}
