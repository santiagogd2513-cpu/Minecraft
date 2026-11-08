package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Corre como "shell" (a través de Shizuku), con acceso a Android/data y Android/obb. */
public class FileService extends IFileService.Stub {
    public FileService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String list(String path) {
        File[] files = new File(path).listFiles();
        if (files == null) return null;
        JSONArray a = new JSONArray();
        try {
            for (File f : files) {
                boolean d = f.isDirectory();
                a.put(new JSONObject().put("n", f.getName()).put("d", d).put("s", d ? 0 : f.length()).put("m", f.lastModified()));
            }
        } catch (Exception ignored) {
        }
        return a.toString();
    }

    @Override
    public String listPage(String path, int start, int count) {
        File[] files = new File(path).listFiles();
        if (files == null) return null;
        java.util.Arrays.sort(files, (x, y) -> x.getName().compareTo(y.getName()));
        JSONArray a = new JSONArray();
        try {
            for (int i = start; i < files.length && i < start + count; i++) {
                File f = files[i];
                boolean d = f.isDirectory();
                a.put(new JSONObject().put("n", f.getName()).put("d", d).put("s", d ? 0 : f.length()).put("m", f.lastModified()));
            }
        } catch (Exception ignored) {
        }
        return a.toString();
    }

    @Override
    public String stat(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        try {
            boolean d = f.isDirectory();
            return new JSONObject().put("n", f.getName()).put("d", d).put("s", d ? 0 : f.length()).put("m", f.lastModified()).toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean mkdirs(String path) {
        File f = new File(path);
        return f.isDirectory() || f.mkdirs();
    }

    @Override
    public boolean delete(String path) {
        return deleteRec(new File(path));
    }

    private static boolean deleteRec(File f) {
        if (f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) if (!deleteRec(k)) return false;
        }
        return !f.exists() || f.delete();
    }

    @Override
    public boolean rename(String from, String to) {
        return new File(from).renameTo(new File(to));
    }

    @Override
    public ParcelFileDescriptor open(String path, String mode) {
        try {
            return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.parseMode(mode));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean setModified(String path, long time) {
        return new File(path).setLastModified(time);
    }
}
