package com.nocturna.browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Una descarga, con sus pedazos (cada pedazo se baja por su propia conexión). */
final class DownloadItem {
    static final int QUEUED = 0, RUNNING = 1, PAUSED = 2, DONE = 3, FAILED = 4;

    static final class Segment {
        final long start;
        volatile long end;
        volatile long pos;
        volatile boolean active;

        Segment(long start, long end, long pos) {
            this.start = start;
            this.end = end;
            this.pos = pos;
        }

        long remaining() {
            return Math.max(0, end - pos + 1);
        }
    }

    long id;
    String url;
    String finalUrl;
    /** Si las peticiones llevan galletas y página de origen (se apaga si el servidor no las acepta). */
    transient boolean extras = true;
    String name;
    String mime;
    String userAgent;
    String referer;
    String uri;
    long total = -1;
    boolean ranges;
    volatile int status = QUEUED;
    volatile boolean stop;
    String error;
    long created;
    long finished;
    final AtomicLong done = new AtomicLong();
    final AtomicInteger connections = new AtomicInteger();
    final List<Segment> segments = new ArrayList<>();
    // velocidad (calculada por el motor cada medio segundo)
    double speed;
    long lastBytes;
    double peakSpeed;

    synchronized List<long[]> snapshot() {
        List<long[]> out = new ArrayList<>();
        for (Segment s : segments) out.add(new long[]{s.start, s.end, s.pos, s.active ? 1 : 0});
        return out;
    }

    float progress() {
        if (total <= 0) return status == DONE ? 1f : 0f;
        return Math.min(1f, done.get() / (float) total);
    }

    String extension() {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id).put("url", url).put("name", name).put("mime", mime).put("ua", userAgent)
                .put("ref", referer).put("uri", uri).put("total", total).put("ranges", ranges)
                .put("status", status == RUNNING || status == QUEUED ? PAUSED : status)
                .put("error", error).put("created", created).put("finished", finished).put("done", done.get());
        JSONArray segs = new JSONArray();
        synchronized (this) {
            for (Segment s : segments) segs.put(new JSONArray().put(s.start).put(s.end).put(s.pos));
        }
        o.put("segs", segs);
        return o;
    }

    static DownloadItem fromJson(JSONObject o) throws Exception {
        DownloadItem d = new DownloadItem();
        d.id = o.getLong("id");
        d.url = o.optString("url");
        d.name = o.optString("name");
        d.mime = o.optString("mime", "application/octet-stream");
        d.userAgent = o.optString("ua", null);
        d.referer = o.optString("ref", null);
        d.uri = o.has("uri") && !o.isNull("uri") ? o.optString("uri") : null;
        d.total = o.optLong("total", -1);
        d.ranges = o.optBoolean("ranges");
        d.status = o.optInt("status", PAUSED);
        d.error = o.has("error") && !o.isNull("error") ? o.optString("error") : null;
        d.created = o.optLong("created");
        d.finished = o.optLong("finished");
        d.done.set(o.optLong("done"));
        JSONArray segs = o.optJSONArray("segs");
        if (segs != null) {
            for (int i = 0; i < segs.length(); i++) {
                JSONArray s = segs.getJSONArray(i);
                d.segments.add(new Segment(s.getLong(0), s.getLong(1), s.getLong(2)));
            }
        }
        return d;
    }
}
