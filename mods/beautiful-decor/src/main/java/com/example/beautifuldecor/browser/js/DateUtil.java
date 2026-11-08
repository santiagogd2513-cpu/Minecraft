package com.example.beautifuldecor.browser.js;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.TimeZone;

/** Cálculos de calendario para Date. */
final class DateUtil {
    static final double MAX = 8.64e15;
    static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private static volatile ZoneId zone = ZoneId.systemDefault();

    private DateUtil() {
    }

    static ZoneId zone() {
        return zone;
    }

    static void setZone(ZoneId z) {
        zone = z;
    }

    /** Campos de un instante (en UTC o ya desplazado a hora local). */
    static final class Fields {
        long year;
        int month;
        int day;
        int hour;
        int minute;
        int second;
        int ms;
        int weekday;
    }

    static long daysFromCivil(long y0, int m, int d) {
        long y = y0 - (m <= 2 ? 1 : 0);
        long era = Math.floorDiv(y, 400L);
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    static Fields fields(double t) {
        Fields f = new Fields();
        long ms = (long) t;
        long days = Math.floorDiv(ms, 86400000L);
        long rem = ms - days * 86400000L;
        f.hour = (int) (rem / 3600000L);
        f.minute = (int) (rem / 60000L % 60);
        f.second = (int) (rem / 1000L % 60);
        f.ms = (int) (rem % 1000L);
        f.weekday = (int) Math.floorMod(days + 4, 7L);
        long z = days + 719468;
        long era = Math.floorDiv(z, 146097L);
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        f.day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int m = (int) (mp < 10 ? mp + 3 : mp - 9);
        f.year = y + (m <= 2 ? 1 : 0);
        f.month = m - 1;
        return f;
    }

    static boolean isLeap(long y) {
        return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0;
    }

    /** MakeDay + MakeTime + MakeDate con los valores desbordados del estándar. */
    static double make(double year, double month, double date, double h, double mi, double s, double ms) {
        if (Double.isNaN(year) || Double.isNaN(month) || Double.isNaN(date) || Double.isNaN(h) || Double.isNaN(mi)
                || Double.isNaN(s) || Double.isNaN(ms) || Double.isInfinite(year) || Double.isInfinite(month)
                || Double.isInfinite(date) || Double.isInfinite(h) || Double.isInfinite(mi) || Double.isInfinite(s)
                || Double.isInfinite(ms)) {
            return Double.NaN;
        }
        double y = trunc(year);
        double m = trunc(month);
        double ym = y + Math.floor(m / 12);
        if (Math.abs(ym) > 400000) {
            return Double.NaN;
        }
        int mn = (int) (m - Math.floor(m / 12) * 12);
        double day = daysFromCivil((long) ym, mn + 1, 1) + trunc(date) - 1;
        double time = trunc(h) * 3600000.0 + trunc(mi) * 60000.0 + trunc(s) * 1000.0 + trunc(ms);
        return day * 86400000.0 + time;
    }

    private static double trunc(double d) {
        return d < 0 ? Math.ceil(d) : Math.floor(d);
    }

    static double clip(double t) {
        if (Double.isNaN(t) || Math.abs(t) > MAX) {
            return Double.NaN;
        }
        return trunc(t) + 0.0;
    }

    static long offsetMs(ZoneId z, double utc) {
        if (Double.isNaN(utc)) {
            return 0;
        }
        return z.getRules().getOffset(java.time.Instant.ofEpochMilli((long) utc)).getTotalSeconds() * 1000L;
    }

    static double toLocal(double utc) {
        return utc + offsetMs(zone, utc);
    }

    /** De hora local (como si fuera UTC) a UTC real. */
    static double toUtc(double local) {
        return toUtc(zone, local);
    }

    static double toUtc(ZoneId z, double local) {
        if (Double.isNaN(local) || Math.abs(local) > MAX + 86400000.0 * 2) {
            return Double.NaN;
        }
        long ms = (long) local;
        long sec = Math.floorDiv(ms, 1000L);
        int nano = (int) Math.floorMod(ms, 1000L) * 1_000_000;
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(sec, nano, ZoneOffset.UTC);
        ZonedDateTime zdt = ZonedDateTime.ofLocal(ldt, z, null);
        return zdt.toInstant().toEpochMilli();
    }

    /** Nombre corto de zona horaria como lo da ICU en inglés de EE. UU. */
    static String zoneShort(ZoneId z, double utc) {
        String id = z.getId();
        if (id.equals("UTC") || id.equals("Z") || id.equals("GMT") || id.equals("Etc/UTC")) {
            return "UTC";
        }
        if (id.startsWith("America/") || id.startsWith("US/") || id.equals("Pacific/Honolulu")) {
            String n = zoneName(z, utc, false);
            switch (n) {
                case "EST":
                case "EDT":
                case "CST":
                case "CDT":
                case "MST":
                case "MDT":
                case "PST":
                case "PDT":
                case "AKST":
                case "AKDT":
                case "HST":
                    return n;
                default:
                    break;
            }
        }
        long mins = offsetMs(z, utc) / 60000;
        if (mins == 0) {
            return "GMT";
        }
        long am = Math.abs(mins);
        return "GMT" + (mins < 0 ? "-" : "+") + am / 60 + (am % 60 != 0 ? ":" + pad(am % 60, 2) : "");
    }

    static String zoneName(ZoneId z, double utc, boolean longName) {
        boolean dst = z.getRules().isDaylightSavings(java.time.Instant.ofEpochMilli((long) utc));
        TimeZone tz = TimeZone.getTimeZone(z);
        return tz.getDisplayName(dst, longName ? TimeZone.LONG : TimeZone.SHORT, Locale.US);
    }

    static String pad(long v, int n) {
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        if (v < 0) {
            sb.append('-');
        }
        for (int i = s.length(); i < n; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    static String yearStr(long y) {
        return y >= 0 ? pad(y, 4) : "-" + pad(-y, 6);
    }

    static String offsetStr(long offMs, boolean colon) {
        long mins = offMs / 60000;
        String sign = mins < 0 ? "-" : "+";
        mins = Math.abs(mins);
        return sign + pad(mins / 60, 2) + (colon ? ":" : "") + pad(mins % 60, 2);
    }

    static String iso(double t) {
        Fields f = fields(t);
        String y = f.year >= 0 && f.year <= 9999 ? pad(f.year, 4) : (f.year < 0 ? "-" : "+") + pad(Math.abs(f.year), 6);
        return y + "-" + pad(f.month + 1, 2) + "-" + pad(f.day, 2) + "T" + pad(f.hour, 2) + ":" + pad(f.minute, 2) + ":"
                + pad(f.second, 2) + "." + pad(f.ms, 3) + "Z";
    }

    static String dateString(double t) {
        Fields f = fields(toLocal(t));
        return DAYS[f.weekday] + " " + MONTHS[f.month] + " " + pad(f.day, 2) + " " + yearStr(f.year);
    }

    static String timeString(double t) {
        Fields f = fields(toLocal(t));
        long off = offsetMs(zone, t);
        return pad(f.hour, 2) + ":" + pad(f.minute, 2) + ":" + pad(f.second, 2) + " GMT" + offsetStr(off, false) + " ("
                + zoneName(zone, t, true) + ")";
    }

    static String utcString(double t) {
        Fields f = fields(t);
        return DAYS[f.weekday] + ", " + pad(f.day, 2) + " " + MONTHS[f.month] + " " + yearStr(f.year) + " " + pad(f.hour, 2) + ":"
                + pad(f.minute, 2) + ":" + pad(f.second, 2) + " GMT";
    }

    // ------------------------------------------------------------------ lectura de fechas en texto

    private static int num(String s, int from, int to) {
        return Integer.parseInt(s.substring(from, to));
    }

    static double parse(String text) {
        double iso = parseIso(text);
        if (!Double.isNaN(iso)) {
            return iso;
        }
        return parseLegacy(text.trim());
    }

    private static double parseIso(String s) {
        int n = s.length();
        int p = 0;
        long year;
        if (n >= 7 && (s.charAt(0) == '+' || s.charAt(0) == '-')) {
            for (int i = 1; i < 7; i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    return Double.NaN;
                }
            }
            year = Long.parseLong(s.substring(1, 7));
            if (s.charAt(0) == '-') {
                if (year == 0) {
                    return Double.NaN;
                }
                year = -year;
            }
            p = 7;
        } else {
            if (n < 4) {
                return Double.NaN;
            }
            for (int i = 0; i < 4; i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    return Double.NaN;
                }
            }
            year = Long.parseLong(s.substring(0, 4));
            p = 4;
        }
        int month = 1;
        int day = 1;
        boolean hasTime = false;
        int hh = 0;
        int mm = 0;
        int ss = 0;
        int ms = 0;
        if (p < n && s.charAt(p) == '-') {
            if (p + 3 > n || !isDigits(s, p + 1, p + 3)) {
                return Double.NaN;
            }
            month = num(s, p + 1, p + 3);
            p += 3;
            if (p < n && s.charAt(p) == '-') {
                if (p + 3 > n || !isDigits(s, p + 1, p + 3)) {
                    return Double.NaN;
                }
                day = num(s, p + 1, p + 3);
                p += 3;
            }
        }
        if (p < n && (s.charAt(p) == 'T' || s.charAt(p) == 't')) {
            p++;
            if (p + 5 > n || !isDigits(s, p, p + 2) || s.charAt(p + 2) != ':' || !isDigits(s, p + 3, p + 5)) {
                return Double.NaN;
            }
            hasTime = true;
            hh = num(s, p, p + 2);
            mm = num(s, p + 3, p + 5);
            p += 5;
            if (p < n && s.charAt(p) == ':') {
                if (p + 3 > n || !isDigits(s, p + 1, p + 3)) {
                    return Double.NaN;
                }
                ss = num(s, p + 1, p + 3);
                p += 3;
                if (p < n && (s.charAt(p) == '.' || s.charAt(p) == ',')) {
                    int q = p + 1;
                    while (q < n && Character.isDigit(s.charAt(q))) {
                        q++;
                    }
                    if (q == p + 1) {
                        return Double.NaN;
                    }
                    String frac = (s.substring(p + 1, q) + "000").substring(0, 3);
                    ms = Integer.parseInt(frac);
                    p = q;
                }
            }
        }
        boolean hasOffset = false;
        long offMin = 0;
        if (p < n && (s.charAt(p) == 'Z' || s.charAt(p) == 'z')) {
            hasOffset = true;
            p++;
        } else if (p < n && (s.charAt(p) == '+' || s.charAt(p) == '-') && hasTime) {
            int sign = s.charAt(p) == '-' ? -1 : 1;
            if (p + 3 > n || !isDigits(s, p + 1, p + 3)) {
                return Double.NaN;
            }
            int oh = num(s, p + 1, p + 3);
            int om = 0;
            p += 3;
            if (p < n && s.charAt(p) == ':') {
                p++;
            }
            if (p + 2 <= n && isDigits(s, p, p + 2)) {
                om = num(s, p, p + 2);
                p += 2;
            }
            hasOffset = true;
            offMin = sign * (oh * 60L + om);
        }
        if (p != n) {
            return Double.NaN;
        }
        if (month < 1 || month > 12 || day < 1 || day > 31 || hh > 24 || mm > 59 || ss > 59) {
            return Double.NaN;
        }
        if (hh == 24 && (mm != 0 || ss != 0 || ms != 0)) {
            return Double.NaN;
        }
        double t = make(year, month - 1, day, hh, mm, ss, ms);
        if (hasOffset) {
            return clip(t - offMin * 60000.0);
        }
        if (!hasTime) {
            return clip(t);
        }
        return clip(toUtc(t));
    }

    private static boolean isDigits(String s, int a, int b) {
        for (int i = a; i < b; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static int monthOf(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        if (l.length() < 3) {
            return -1;
        }
        String[] names = {"january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
            "november", "december"};
        for (int i = 0; i < 12; i++) {
            if (names[i].startsWith(l) || (l.length() >= 3 && l.startsWith(names[i].substring(0, 3)) && names[i].startsWith(l.substring(0, Math.min(l.length(), names[i].length()))))) {
                return i;
            }
        }
        if (l.equals("sept")) {
            return 8;
        }
        return -1;
    }

    private static boolean isDayName(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        if (l.length() < 3) {
            return false;
        }
        String[] names = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (String nm : names) {
            if (nm.startsWith(l)) {
                return true;
            }
        }
        return false;
    }

    private static long zoneAbbrev(String lw) {
        switch (lw) {
            case "z":
            case "gmt":
            case "utc":
            case "ut":
                return 0;
            case "est":
                return -300;
            case "edt":
                return -240;
            case "cst":
                return -360;
            case "cdt":
                return -300;
            case "mst":
                return -420;
            case "mdt":
                return -360;
            case "pst":
                return -480;
            case "pdt":
                return -420;
            default:
                return Long.MIN_VALUE;
        }
    }

    private static double parseLegacy(String s) {
        int n = s.length();
        int p = 0;
        int month = -1;
        int[] nums = new int[4];
        int[] digits = new int[4];
        int nn = 0;
        boolean hasTime = false;
        int hh = 0;
        int mi = 0;
        int sec = 0;
        int ms = 0;
        int ampm = 0;
        boolean hasZone = false;
        long zoneMin = 0;
        boolean afterTime = false;
        while (p < n) {
            char c = s.charAt(p);
            if (c == '(') {
                int depth = 0;
                while (p < n) {
                    if (s.charAt(p) == '(') {
                        depth++;
                    } else if (s.charAt(p) == ')') {
                        depth--;
                        if (depth == 0) {
                            p++;
                            break;
                        }
                    }
                    p++;
                }
                continue;
            }
            if (Character.isWhitespace(c) || c == ',' || c == '/' || c == '.') {
                p++;
                continue;
            }
            if (Character.isLetter(c)) {
                int q = p;
                while (q < n && (Character.isLetter(s.charAt(q)) || s.charAt(q) == '.')) {
                    q++;
                }
                String w = s.substring(p, q).replace(".", "");
                p = q;
                String lw = w.toLowerCase(Locale.ROOT);
                if (lw.equals("am") || lw.equals("pm")) {
                    ampm = lw.equals("am") ? 1 : 2;
                } else if (zoneAbbrev(lw) != Long.MIN_VALUE) {
                    hasZone = true;
                    zoneMin = zoneAbbrev(lw);
                    afterTime = true;
                } else if (monthOf(w) >= 0 && month < 0) {
                    month = monthOf(w);
                } else if (isDayName(w)) {
                    continue;
                } else {
                    return Double.NaN;
                }
                continue;
            }
            if ((c == '+' || c == '-') && (afterTime || hasTime)) {
                int q = p + 1;
                int d = q;
                while (q < n && Character.isDigit(s.charAt(q))) {
                    q++;
                }
                if (q == d) {
                    p++;
                    continue;
                }
                int sign = c == '-' ? -1 : 1;
                long v;
                if (q < n && s.charAt(q) == ':') {
                    int q2 = q + 1;
                    int d2 = q2;
                    while (q2 < n && Character.isDigit(s.charAt(q2))) {
                        q2++;
                    }
                    v = Long.parseLong(s.substring(d, q)) * 60 + (q2 > d2 ? Long.parseLong(s.substring(d2, q2)) : 0);
                    q = q2;
                } else if (q - d <= 2) {
                    v = Long.parseLong(s.substring(d, q)) * 60;
                } else {
                    long raw = Long.parseLong(s.substring(d, q));
                    v = raw / 100 * 60 + raw % 100;
                }
                hasZone = true;
                zoneMin = sign * v;
                p = q;
                continue;
            }
            if (c == '-') {
                p++;
                continue;
            }
            if (Character.isDigit(c)) {
                int q = p;
                while (q < n && Character.isDigit(s.charAt(q))) {
                    q++;
                }
                long v = Long.parseLong(s.substring(p, Math.min(q, p + 9)));
                if (q < n && s.charAt(q) == ':' && !hasTime) {
                    hasTime = true;
                    hh = (int) v;
                    p = q + 1;
                    int q2 = p;
                    while (q2 < n && Character.isDigit(s.charAt(q2))) {
                        q2++;
                    }
                    if (q2 == p) {
                        return Double.NaN;
                    }
                    mi = Integer.parseInt(s.substring(p, q2));
                    p = q2;
                    if (p < n && s.charAt(p) == ':') {
                        p++;
                        q2 = p;
                        while (q2 < n && Character.isDigit(s.charAt(q2))) {
                            q2++;
                        }
                        if (q2 == p) {
                            return Double.NaN;
                        }
                        sec = Integer.parseInt(s.substring(p, q2));
                        p = q2;
                        if (p < n && s.charAt(p) == '.') {
                            q2 = p + 1;
                            while (q2 < n && Character.isDigit(s.charAt(q2))) {
                                q2++;
                            }
                            if (q2 > p + 1) {
                                ms = Integer.parseInt((s.substring(p + 1, q2) + "000").substring(0, 3));
                            }
                            p = q2;
                        }
                    }
                    afterTime = true;
                    continue;
                }
                if (nn >= 4) {
                    return Double.NaN;
                }
                nums[nn] = (int) v;
                digits[nn] = q - p;
                nn++;
                p = q;
                continue;
            }
            return Double.NaN;
        }
        long year;
        int mon;
        int day;
        if (month >= 0) {
            mon = month;
            if (nn == 0) {
                return Double.NaN;
            }
            if (nn == 1) {
                if (nums[0] > 31 || digits[0] >= 3) {
                    year = nums[0];
                    day = 1;
                } else {
                    day = nums[0];
                    year = 2001;
                }
            } else if (nn == 2) {
                if (nums[0] > 31 || digits[0] >= 3) {
                    year = nums[0];
                    day = nums[1];
                } else {
                    day = nums[0];
                    year = nums[1];
                    if (digits[1] <= 2) {
                        year += year < 50 ? 2000 : 1900;
                    }
                }
            } else {
                return Double.NaN;
            }
        } else if (nn == 3) {
            if (nums[0] > 31 || digits[0] >= 3) {
                year = nums[0];
                mon = nums[1] - 1;
                day = nums[2];
            } else {
                mon = nums[0] - 1;
                day = nums[1];
                year = nums[2];
                if (digits[2] <= 2) {
                    year += year < 50 ? 2000 : 1900;
                }
            }
        } else if (nn == 2) {
            if (nums[0] > 31 || digits[0] >= 3) {
                year = nums[0];
                mon = nums[1] - 1;
                day = 1;
            } else {
                mon = nums[0] - 1;
                day = nums[1];
                year = 2001;
            }
        } else if (nn == 1) {
            int v = nums[0];
            day = 1;
            if (v >= 1 && v <= 12) {
                mon = v - 1;
                year = 2001;
            } else if (v >= 13 && v <= 31) {
                return Double.NaN;
            } else {
                mon = 0;
                year = v < 50 ? 2000 + v : (v < 100 ? 1900 + v : v);
            }
        } else {
            return Double.NaN;
        }
        if (mon < 0 || mon > 11 || day < 1 || day > 31) {
            return Double.NaN;
        }
        if (ampm != 0) {
            if (hh < 1 || hh > 12) {
                return Double.NaN;
            }
            hh = hh % 12 + (ampm == 2 ? 12 : 0);
        }
        if (hh > 24 || mi > 59 || sec > 59) {
            return Double.NaN;
        }
        double t = make(year, mon, day, hh, mi, sec, ms);
        if (hasZone) {
            return clip(t - zoneMin * 60000.0);
        }
        return clip(toUtc(t));
    }
}
