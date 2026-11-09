package com.boveda.archivos;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * La Radiografía del almacenamiento: recorre todo un almacenamiento (interno, SD o USB) y cuenta qué
 * ocupa cuánto: fotos, videos, música, documentos, APK, comprimidos… Además junta los archivos más
 * grandes, las carpetas que más pesan, los archivos repetidos, las descargas viejas y las carpetas vacías.
 */
final class StorageScan {
    static final int IMAGES = 0, VIDEOS = 1, AUDIO = 2, DOCS = 3, APKS = 4, ARCHIVES = 5, OTHER = 6, TRASH = 7, VAULT = 8;
    static final int CATS = 9;
    static final long OLD_DOWNLOAD = 90L * 24 * 60 * 60 * 1000;
    private static final long DUP_MIN = 512 * 1024;

    interface Progress {
        void on(int files, long bytes, String where, boolean dupPhase);
    }

    static final class Result {
        String root, name;
        long total, free, scanned;
        final long[] cats = new long[CATS];
        final int[] catFiles = new int[CATS];
        int files, dirs;
        final List<File> biggest = new ArrayList<>();
        final List<long[]> folderSizes = new ArrayList<>();   // índice en folders, tamaño
        final List<File> folders = new ArrayList<>();
        final List<List<File>> duplicates = new ArrayList<>();
        long duplicateWaste;
        final List<File> oldDownloads = new ArrayList<>();
        long oldDownloadsSize;
        final List<File> emptyDirs = new ArrayList<>();
        boolean skippedAndroidData;

        long used() {
            return Math.max(0, total - free);
        }

        /** Lo que no se pudo recorrer: el sistema, las apps instaladas y sus datos. */
        long system() {
            return Math.max(0, used() - scanned);
        }
    }

    private StorageScan() {}

    static int category(String name) {
        if (Viewers.isImage(name)) return IMAGES;
        if (Viewers.isVideo(name)) return VIDEOS;
        if (Viewers.isAudio(name)) return AUDIO;
        String e = Viewers.ext(name);
        if (e.equals("apk") || e.equals("apks") || e.equals("xapk") || e.equals("apkm")) return APKS;
        if (Archive.supported(name) || e.equals("rar") || e.equals("iso") || e.equals("img")) return ARCHIVES;
        if (Viewers.isText(name) || Viewers.isPdf(name) || DOC_EXT.contains(e)) return DOCS;
        return OTHER;
    }

    private static final java.util.Set<String> DOC_EXT = new java.util.HashSet<>(java.util.Arrays.asList(
            "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub", "mobi", "azw3", "pages", "numbers", "key"));

    static Result scan(String root, String name, long total, long free, AtomicBoolean cancel, Progress p) {
        Result r = new Result();
        r.root = root;
        r.name = name;
        r.total = total;
        r.free = free;
        PriorityQueue<File> big = new PriorityQueue<>((a, b) -> Long.compare(a.length(), b.length()));
        Map<Long, List<File>> bySize = new HashMap<>();
        File trash = new File(root, Trash.NAME);
        File vault = new File(Vault.DIR);
        File downloads = new File(root, "Download");
        long now = System.currentTimeMillis();
        long[] lastReport = {0};

        File[] top = new File(root).listFiles();
        if (top == null) return r;
        for (File t : top) {
            if (cancel.get()) break;
            long before = r.scanned;
            int cat = t.equals(trash) ? TRASH : t.equals(vault) ? VAULT : -1;
            walk(t, cat, r, big, bySize, root, downloads, now, cancel, p, lastReport);
            if (t.isDirectory()) {
                r.folders.add(t);
                r.folderSizes.add(new long[]{r.folders.size() - 1, r.scanned - before});
            }
        }
        r.folderSizes.sort((a, b) -> Long.compare(b[1], a[1]));
        while (!big.isEmpty()) r.biggest.add(0, big.poll());
        r.oldDownloads.sort((a, b) -> Long.compare(b.length(), a.length()));
        if (!cancel.get()) findDuplicates(r, bySize, cancel, p);
        return r;
    }

    private static void walk(File start, int forcedCat, Result r, PriorityQueue<File> big, Map<Long, List<File>> bySize,
                             String root, File downloads, long now, AtomicBoolean cancel, Progress p, long[] lastReport) {
        ArrayDeque<File> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty() && !cancel.get()) {
            File f = stack.pop();
            String path = f.getPath();
            if (Paths.restricted(path)) {
                r.skippedAndroidData = true;
                continue;
            }
            if (f.isDirectory()) {
                if (java.nio.file.Files.isSymbolicLink(f.toPath())) continue;
                r.dirs++;
                File[] kids = f.listFiles();
                if (kids == null) continue;
                if (kids.length == 0 && forcedCat < 0 && !f.getName().startsWith(".")) r.emptyDirs.add(f);
                for (File k : kids) stack.push(k);
                long t = System.currentTimeMillis();
                if (t - lastReport[0] > 250) {
                    lastReport[0] = t;
                    p.on(r.files, r.scanned, path.replace(root, ""), false);
                }
                continue;
            }
            long len = f.length();
            r.files++;
            r.scanned += len;
            int cat = forcedCat >= 0 ? forcedCat : category(f.getName());
            r.cats[cat] += len;
            r.catFiles[cat]++;
            if (forcedCat >= 0) continue;   // lo de la papelera y la Bóveda no entra en las listas
            if (len > 0) {
                big.add(f);
                if (big.size() > 40) big.poll();
            }
            if (len >= DUP_MIN) bySize.computeIfAbsent(len, k -> new ArrayList<>()).add(f);
            if (Paths.under(path, downloads.getPath()) && now - f.lastModified() > OLD_DOWNLOAD) {
                r.oldDownloads.add(f);
                r.oldDownloadsSize += len;
            }
        }
    }

    /** Archivos idénticos: primero por tamaño, luego por el principio del archivo y al final por todo su contenido. */
    private static void findDuplicates(Result r, Map<Long, List<File>> bySize, AtomicBoolean cancel, Progress p) {
        List<Map.Entry<Long, List<File>>> cands = new ArrayList<>();
        for (Map.Entry<Long, List<File>> e : bySize.entrySet()) if (e.getValue().size() > 1) cands.add(e);
        cands.sort((a, b) -> Long.compare(b.getKey(), a.getKey()));
        int done = 0;
        for (Map.Entry<Long, List<File>> e : cands) {
            if (cancel.get()) return;
            p.on(done++, 0, cands.size() + "", true);
            Map<String, List<File>> quick = new HashMap<>();
            for (File f : e.getValue()) {
                String h = hash(f, 64 * 1024);
                if (h != null) quick.computeIfAbsent(h, k -> new ArrayList<>()).add(f);
            }
            for (List<File> q : quick.values()) {
                if (q.size() < 2) continue;
                Map<String, List<File>> full = new HashMap<>();
                for (File f : q) {
                    if (cancel.get()) return;
                    String h = hash(f, Long.MAX_VALUE);
                    if (h != null) full.computeIfAbsent(h, k -> new ArrayList<>()).add(f);
                }
                for (List<File> g : full.values()) {
                    if (g.size() < 2) continue;
                    // se queda el más viejo (normalmente el original); los demás son las copias
                    g.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                    r.duplicates.add(g);
                    r.duplicateWaste += e.getKey() * (g.size() - 1);
                }
            }
        }
        r.duplicates.sort((a, b) -> Long.compare(b.get(0).length() * (b.size() - 1), a.get(0).length() * (a.size() - 1)));
    }

    private static String hash(File f, long limit) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 20];
            long left = limit;
            int n;
            while (left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0) {
                md.update(buf, 0, n);
                left -= n;
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }
}
