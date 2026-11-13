package com.boveda.archivos;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reducir peso: encuentra archivos idénticos en una carpeta o dentro de un archivo comprimido. */
final class Duplicates {
    private Duplicates() {}

    interface Progress {
        void say(String text);
    }

    /** Varias copias del mismo contenido. files[0] es la que se conserva. */
    static final class Group {
        final long size;
        final List<String> names = new ArrayList<>();   // ruta (en carpetas) o nombre dentro del archivo
        final List<Long> times = new ArrayList<>();

        Group(long size) {
            this.size = size;
        }

        long saving() {
            return size * (names.size() - 1);
        }
    }

    static long totalSaving(List<Group> g) {
        long t = 0;
        for (Group x : g) t += x.saving();
        return t;
    }

    // ------------------------------------------------------------------ carpetas

    static List<Group> scanFolder(Context c, String root, Progress p, AtomicBoolean cancel) throws IOException {
        Map<Long, List<Entry>> bySize = new HashMap<>();
        int[] n = {0};
        collect(c, root, bySize, n, p, cancel);
        List<Group> out = new ArrayList<>();
        int done = 0;
        for (Map.Entry<Long, List<Entry>> e : bySize.entrySet()) {
            if (cancel.get()) throw new IOException(L.t("Cancelado"));
            List<Entry> same = e.getValue();
            if (same.size() < 2) continue;
            p.say(L.t("Comparando… ") + (++done) + "/" + countCandidates(bySize));
            // huella completa de cada uno y, entre los que coinciden, comparación byte por byte
            Map<String, List<Entry>> byHash = new HashMap<>();
            for (Entry en : same) byHash.computeIfAbsent(hash(c, en.path), k -> new ArrayList<>()).add(en);
            for (List<Entry> hs : byHash.values()) {
                if (hs.size() < 2) continue;
                List<Entry> real = new ArrayList<>();
                real.add(hs.get(0));
                for (int i = 1; i < hs.size(); i++) if (sameBytes(c, hs.get(0).path, hs.get(i).path)) real.add(hs.get(i));
                if (real.size() < 2) continue;
                real.sort((a, b) -> Long.compare(a.modified, b.modified));
                Group g = new Group(e.getKey());
                for (Entry r : real) {
                    g.names.add(r.path);
                    g.times.add(r.modified);
                }
                out.add(g);
            }
        }
        out.sort((a, b) -> Long.compare(b.saving(), a.saving()));
        return out;
    }

    private static int countCandidates(Map<Long, List<Entry>> m) {
        int k = 0;
        for (List<Entry> l : m.values()) if (l.size() > 1) k++;
        return k;
    }

    private static void collect(Context c, String dir, Map<Long, List<Entry>> by, int[] n, Progress p, AtomicBoolean cancel) throws IOException {
        if (cancel.get()) throw new IOException(L.t("Cancelado"));
        for (Entry e : FsRouter.get(c, dir).list(dir)) {
            if (e.dir) {
                collect(c, e.path, by, n, p, cancel);
            } else if (e.size > 0) {
                by.computeIfAbsent(e.size, k -> new ArrayList<>()).add(e);
                if (++n[0] % 200 == 0) p.say(L.t("Mirando archivos… ") + n[0]);
            }
        }
    }

    private static String hash(Context c, String path) throws IOException {
        try (InputStream in = FsRouter.get(c, path).in(path)) {
            return sha(in);
        }
    }

    static String sha(InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[1 << 16];
            int r;
            while ((r = in.read(b)) >= 0) md.update(b, 0, r);
            StringBuilder s = new StringBuilder();
            for (byte x : md.digest()) s.append(String.format("%02x", x));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static boolean sameBytes(Context c, String a, String b) throws IOException {
        try (InputStream x = new java.io.BufferedInputStream(FsRouter.get(c, a).in(a), 1 << 16);
             InputStream y = new java.io.BufferedInputStream(FsRouter.get(c, b).in(b), 1 << 16)) {
            byte[] p = new byte[1 << 16], q = new byte[1 << 16];
            while (true) {
                int r = x.readNBytes(p, 0, p.length), s = y.readNBytes(q, 0, q.length);
                if (r != s) return false;
                if (r == 0) return true;
                if (!java.util.Arrays.equals(p, 0, r, q, 0, r)) return false;
            }
        }
    }

    /** Manda a la papelera todas las copias menos la primera de cada grupo. Devuelve cuántos archivos movió. */
    static int trashExtras(List<Group> groups, int[] kept) throws IOException {
        int moved = 0;
        for (Group g : groups) {
            for (int i = 1; i < g.names.size(); i++) {
                String f = g.names.get(i);
                if (Trash.supported(f)) {
                    Trash.put(f);
                    moved++;
                } else {
                    kept[0]++;
                }
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------ dentro de un comprimido

    static List<Group> scanArchive(Context c, String archive, Progress p, AtomicBoolean cancel) throws IOException {
        Map<String, Group> by = new HashMap<>();
        int[] n = {0};
        Archive.walk(c, archive, (it, data) -> {
            if (cancel.get()) throw new IOException(L.t("Cancelado"));
            if (it.dir || data == null || it.size == 0) return true;
            p.say(L.t("Leyendo… ") + (++n[0]) + " · " + it.name);
            String key = sha(data) + ":" + it.size;
            Group g = by.computeIfAbsent(key, k -> new Group(it.size));
            g.names.add(it.name);
            g.times.add(it.time);
            return true;
        });
        List<Group> out = new ArrayList<>();
        for (Group g : by.values()) if (g.names.size() > 1) out.add(g);
        out.sort((a, b) -> Long.compare(b.saving(), a.saving()));
        return out;
    }

    /** Escribe un .zip nuevo, junto al original y con otro nombre, sin las copias repetidas. */
    static String rewriteZip(Context c, String archive, List<Group> groups, Progress p) throws IOException {
        Set<String> drop = new HashSet<>();
        for (Group g : groups) for (int i = 1; i < g.names.size(); i++) drop.add(g.names.get(i));
        Fs fs = FsRouter.get(c, archive);
        String name = Paths.name(archive);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String dest = Paths.join(Paths.parent(archive), base + " (sin duplicados).zip");
        for (int i = 2; fs.stat(dest) != null; i++) dest = Paths.join(Paths.parent(archive), base + " (sin duplicados " + i + ").zip");
        String tmp = Paths.join(Paths.parent(archive), "." + Paths.name(dest) + ".boveda-parte");
        try (OutputStream raw = fs.out(tmp);
             java.util.zip.ZipOutputStream zo = new java.util.zip.ZipOutputStream(new java.io.BufferedOutputStream(raw, 1 << 20))) {
            int[] n = {0};
            Archive.walk(c, archive, (it, data) -> {
                if (drop.contains(it.name)) return true;
                p.say(L.t("Escribiendo… ") + (++n[0]) + " · " + it.name);
                java.util.zip.ZipEntry ze = new java.util.zip.ZipEntry(it.dir ? it.name + "/" : it.name);
                if (it.time > 0) ze.setTime(it.time);
                zo.putNextEntry(ze);
                if (data != null) {
                    byte[] buf = new byte[1 << 16];
                    int r;
                    while ((r = data.read(buf)) >= 0) zo.write(buf, 0, r);
                }
                zo.closeEntry();
                return true;
            });
        } catch (IOException | RuntimeException e) {
            try {
                fs.delete(tmp);
            } catch (IOException ignored) {
            }
            throw e;
        }
        fs.rename(tmp, dest);
        return dest;
    }

    static boolean canRewrite(String archive) {
        return Archive.type(Paths.name(archive)) == Archive.Type.ZIP;
    }
}
