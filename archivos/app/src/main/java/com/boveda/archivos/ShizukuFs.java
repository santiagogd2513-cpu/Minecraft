package com.boveda.archivos;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Archivos a través de Shizuku (permisos de "shell"). */
final class ShizukuFs implements Fs {
    private final IFileService s;

    ShizukuFs(IFileService s) {
        this.s = s;
    }

    private static void addAll(List<Entry> out, String dir, JSONArray a) throws org.json.JSONException {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String n = o.getString("n");
            out.add(new Entry(Paths.join(dir, n), n, o.getBoolean("d"), o.getLong("s"), o.getLong("m")));
        }
    }

    private static IOException io(RemoteException e) {
        return new IOException("Shizuku se desconectó", e);
    }

    @Override
    public List<Entry> list(String dir) throws IOException {
        try {
            // de a 400 elementos: una carpeta con miles de archivos no cabe en un solo mensaje entre procesos
            final int page = 400;
            List<Entry> out = new ArrayList<>();
            for (int start = 0; ; start += page) {
                String json;
                try {
                    json = s.listPage(dir, start, page);
                } catch (RuntimeException | RemoteException old) {
                    if (start > 0) throw old;
                    json = s.list(dir);   // servicio de una versión anterior
                    if (json == null) throw new IOException("No se puede leer esta carpeta");
                    addAll(out, dir, new JSONArray(json));
                    return out;
                }
                if (json == null) throw new IOException("No se puede leer esta carpeta");
                JSONArray a = new JSONArray(json);
                addAll(out, dir, a);
                if (a.length() < page) return out;
            }
        } catch (RemoteException e) {
            throw io(e);
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Entry stat(String path) throws IOException {
        try {
            String json = s.stat(path);
            if (json == null) return null;
            JSONObject o = new JSONObject(json);
            return new Entry(path, o.getString("n"), o.getBoolean("d"), o.getLong("s"), o.getLong("m"));
        } catch (RemoteException e) {
            throw io(e);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        try {
            ParcelFileDescriptor p = s.open(path, mode);
            if (p == null) throw new FileNotFoundException(path);
            return p;
        } catch (RemoteException e) {
            throw io(e);
        }
    }

    @Override
    public InputStream in(String path) throws IOException {
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd(path, "r"));
    }

    @Override
    public OutputStream out(String path) throws IOException {
        return new ParcelFileDescriptor.AutoCloseOutputStream(pfd(path, "rwt"));
    }

    @Override
    public void mkdir(String path) throws IOException {
        try {
            if (!s.mkdirs(path)) throw new IOException("No se pudo crear la carpeta");
        } catch (RemoteException e) {
            throw io(e);
        }
    }

    @Override
    public void delete(String path) throws IOException {
        try {
            if (!s.delete(path)) throw new IOException("No se pudo borrar " + Paths.name(path));
        } catch (RemoteException e) {
            throw io(e);
        }
    }

    @Override
    public void rename(String from, String to) throws IOException {
        try {
            if (!s.rename(from, to)) throw new IOException("No se pudo renombrar");
        } catch (RemoteException e) {
            throw io(e);
        }
    }

    @Override
    public void setModified(String path, long time) {
        try {
            s.setModified(path, time);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public String key(String path) {
        return LocalFs.I.key(path);
    }
}
