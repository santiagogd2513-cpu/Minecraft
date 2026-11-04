package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Archivos normales (con el permiso de acceso a todos los archivos). */
final class LocalFs implements Fs {
    static final LocalFs I = new LocalFs();

    @Override
    public List<Entry> list(String dir) throws IOException {
        File[] files = new File(dir).listFiles();
        if (files == null) {
            if (Paths.restricted(dir)) throw new NeedAccess(dir);
            throw new IOException("No se puede leer esta carpeta");
        }
        List<Entry> out = new ArrayList<>(files.length);
        for (File f : files) out.add(entry(f));
        return out;
    }

    static Entry entry(File f) {
        boolean dir = f.isDirectory();
        return new Entry(f.getAbsolutePath(), f.getName(), dir, dir ? 0 : f.length(), f.lastModified());
    }

    @Override
    public Entry stat(String path) {
        File f = new File(path);
        return f.exists() ? entry(f) : null;
    }

    @Override
    public InputStream in(String path) throws IOException {
        return new FileInputStream(path);
    }

    @Override
    public OutputStream out(String path) throws IOException {
        return new FileOutputStream(path);
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public void mkdir(String path) throws IOException {
        File f = new File(path);
        if (!f.isDirectory() && !f.mkdirs()) throw new IOException("No se pudo crear la carpeta");
    }

    @Override
    public void delete(String path) throws IOException {
        deleteRec(new File(path));
    }

    private static void deleteRec(File f) throws IOException {
        if (f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        if (f.exists() && !f.delete()) throw new IOException("No se pudo borrar " + f.getName());
    }

    @Override
    public void rename(String from, String to) throws IOException {
        if (!new File(from).renameTo(new File(to))) throw new IOException("No se pudo renombrar");
    }

    @Override
    public void setModified(String path, long time) {
        //noinspection ResultOfMethodCallIgnored
        new File(path).setLastModified(time);
    }

    @Override
    public String key(String path) {
        // cada volumen (interno, cada tarjeta SD) es un almacenamiento distinto
        if (path.startsWith("/storage/")) {
            String rest = path.substring(9);
            int i = rest.indexOf('/');
            String vol = i < 0 ? rest : rest.substring(0, i);
            if (vol.equals("emulated")) return "local:/storage/emulated";
            return "local:/storage/" + vol;
        }
        return "local:/";
    }
}
