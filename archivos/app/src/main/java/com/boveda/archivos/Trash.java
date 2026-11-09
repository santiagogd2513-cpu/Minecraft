package com.boveda.archivos;

import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * La papelera: lo que se borra se mueve (sin copiar, al instante) a una carpeta oculta en el mismo
 * almacenamiento, «.Boveda-papelera», junto con una notita que dice de dónde venía y cuándo se borró.
 * Pasados 30 días se borra solo para siempre.
 */
final class Trash {
    static final String NAME = ".Boveda-papelera";
    static final long KEEP = 30L * 24 * 60 * 60 * 1000;

    static final class Item {
        final File file, meta;
        final String original;
        final long deleted, size;
        final boolean dir;

        Item(File file, File meta, String original, long deleted, long size, boolean dir) {
            this.file = file;
            this.meta = meta;
            this.original = original;
            this.deleted = deleted;
            this.size = size;
            this.dir = dir;
        }

        String name() {
            return Paths.name(original);
        }

        long daysLeft() {
            return Math.max(0, (deleted + KEEP - System.currentTimeMillis()) / (24L * 60 * 60 * 1000));
        }
    }

    private Trash() {}

    /** La papelera del almacenamiento donde está esta ruta; null si ahí no se puede (nube, FTP, Bóveda…). */
    private static File binFor(String path) {
        if (path == null || !path.startsWith("/storage/") || Paths.restricted(path)) return null;
        String root;
        if (Paths.under(path, Paths.ROOT)) root = Paths.ROOT;
        else {
            root = FsRouter.removableRoot(path);
            if (root == null || !FsRouter.directWrite(root)) return null;
        }
        if (path.equals(root) || Paths.under(path, Paths.join(root, NAME)) || Paths.under(path, Vault.DIR)) return null;
        return new File(root, NAME);
    }

    /** ¿Se puede mandar a la papelera? (archivos normales del celular, la SD o una USB con acceso directo) */
    static boolean supported(String path) {
        return binFor(path) != null && new File(path).exists();
    }

    /** Mueve a la papelera. */
    static void put(String path) throws IOException {
        File bin = binFor(path);
        if (bin == null) throw new IOException(L.t("Esto no puede ir a la papelera"));
        if (!bin.isDirectory() && !bin.mkdirs()) throw new IOException(L.t("No se pudo crear la papelera"));
        File nomedia = new File(bin, ".nomedia");
        if (!nomedia.exists()) {
            //noinspection ResultOfMethodCallIgnored
            nomedia.createNewFile();
        }
        File src = new File(path);
        String id = System.currentTimeMillis() + "-" + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xFFFFFF);
        File dst = new File(bin, id), meta = new File(bin, id + ".json");
        try {
            JSONObject j = new JSONObject();
            j.put("ruta", path);
            j.put("borrado", System.currentTimeMillis());
            j.put("carpeta", src.isDirectory());
            j.put("tamano", sizeOf(src));
            try (FileOutputStream o = new FileOutputStream(meta)) {
                o.write(j.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            meta.delete();
            throw new IOException(L.t("No se pudo mover a la papelera: ") + src.getName());
        }
        if (!src.renameTo(dst)) {
            //noinspection ResultOfMethodCallIgnored
            meta.delete();
            throw new IOException(L.t("No se pudo mover a la papelera: ") + src.getName());
        }
    }

    private static long sizeOf(File f) {
        if (!f.isDirectory()) return f.length();
        long t = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) t += sizeOf(k);
        return t;
    }

    /** Todas las papeleras: la del almacenamiento interno y las de las SD o USB conectadas. */
    private static List<File> bins(Context c) {
        List<File> out = new ArrayList<>();
        out.add(new File(Paths.ROOT, NAME));
        StorageManager sm = c.getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            File d = android.os.Build.VERSION.SDK_INT >= 30 ? v.getDirectory() : null;
            if (d == null || v.isPrimary()) continue;
            out.add(new File(d, NAME));
        }
        return out;
    }

    /** Lo que hay en la papelera, lo último borrado primero. */
    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        for (File bin : bins(c)) {
            File[] files = bin.listFiles();
            if (files == null) continue;
            for (File meta : files) {
                String n = meta.getName();
                if (!n.endsWith(".json")) continue;
                File f = new File(bin, n.substring(0, n.length() - 5));
                if (!f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    meta.delete();
                    continue;
                }
                try {
                    JSONObject j = new JSONObject(new String(java.nio.file.Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8));
                    out.add(new Item(f, meta, j.getString("ruta"), j.optLong("borrado", meta.lastModified()),
                            j.optLong("tamano", 0), f.isDirectory()));
                } catch (Exception ignored) {
                }
            }
        }
        out.sort((a, b) -> Long.compare(b.deleted, a.deleted));
        return out;
    }

    /** Lo devuelve a donde estaba (si ya hay algo con ese nombre, le pone «(1)»). Devuelve dónde quedó. */
    static String restore(Item it) throws IOException {
        File target = new File(it.original);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException(L.t("No se pudo restaurar: ") + it.name());
        }
        if (target.exists()) {
            String name = it.name();
            int dot = it.dir ? -1 : name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
            for (int n = 1; target.exists(); n++) target = new File(parent, base + " (" + n + ")" + ext);
        }
        if (!it.file.renameTo(target)) throw new IOException(L.t("No se pudo restaurar: ") + it.name());
        //noinspection ResultOfMethodCallIgnored
        it.meta.delete();
        return target.getPath();
    }

    static void deleteForever(Item it) throws IOException {
        wipe(it.file);
        //noinspection ResultOfMethodCallIgnored
        it.meta.delete();
    }

    private static void wipe(File f) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) wipe(k);
        }
        if (!f.delete() && f.exists()) throw new IOException(L.t("No se pudo borrar: ") + f.getName());
    }

    /** Borra para siempre lo que lleva más de 30 días en la papelera. */
    static void purge(Context c) {
        long now = System.currentTimeMillis();
        for (Item it : list(c)) {
            if (now - it.deleted < KEEP) continue;
            try {
                deleteForever(it);
            } catch (IOException ignored) {
            }
        }
    }
}
