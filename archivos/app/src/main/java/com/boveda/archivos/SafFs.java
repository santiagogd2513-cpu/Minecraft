package com.boveda.archivos;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Archivos dentro de una carpeta que el usuario autorizó con el selector del sistema
 * (así se entra a Android/data sin root, como en MT Manager).
 */
final class SafFs implements Fs {
    static final String AUTHORITY = "com.android.externalstorage.documents";

    private final ContentResolver cr;
    private final Uri tree;
    /** Ruta de la carpeta autorizada, p. ej. /storage/emulated/0/Android/data */
    final String base;
    private final String baseDocId;

    SafFs(ContentResolver cr, Uri tree, String base, String baseDocId) {
        this.cr = cr;
        this.tree = tree;
        this.base = base;
        this.baseDocId = baseDocId;
    }

    private String docId(String path) {
        if (path.equals(base)) return baseDocId;
        String rel = path.substring(base.length() + 1);
        return baseDocId.endsWith(":") ? baseDocId + rel : baseDocId + "/" + rel;
    }

    Uri uri(String path) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, docId(path));
    }

    @Override
    public List<Entry> list(String dir) throws IOException {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId(dir));
        List<Entry> out = new ArrayList<>();
        try (Cursor c = cr.query(kids, new String[]{Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            if (c == null) throw new IOException(L.t("No se puede leer esta carpeta"));
            while (c.moveToNext()) {
                String name = c.getString(0);
                boolean d = Document.MIME_TYPE_DIR.equals(c.getString(1));
                out.add(new Entry(Paths.join(dir, name), name, d, d ? 0 : c.getLong(2), c.getLong(3)));
            }
        } catch (SecurityException e) {
            throw new NeedAccess(dir);
        }
        return out;
    }

    @Override
    public Entry stat(String path) throws IOException {
        try (Cursor c = cr.query(uri(path), new String[]{Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            boolean d = Document.MIME_TYPE_DIR.equals(c.getString(1));
            return new Entry(path, Paths.name(path), d, d ? 0 : c.getLong(2), c.getLong(3));
        } catch (IllegalArgumentException | SecurityException e) {
            return null;
        } catch (Exception e) {
            // el proveedor lanza una excepción si el archivo no existe
            return null;
        }
    }

    @Override
    public InputStream in(String path) throws IOException {
        InputStream in = cr.openInputStream(uri(path));
        if (in == null) throw new FileNotFoundException(path);
        return in;
    }

    @Override
    public OutputStream out(String path) throws IOException {
        Uri u = stat(path) != null ? uri(path) : create(path, "application/octet-stream");
        OutputStream o = cr.openOutputStream(u, "wt");
        if (o == null) throw new IOException(L.t("No se pudo escribir ") + Paths.name(path));
        return o;
    }

    private Uri create(String path, String mime) throws IOException {
        Uri parent = uri(Paths.parent(path));
        Uri made = DocumentsContract.createDocument(cr, parent, mime, Paths.name(path));
        if (made == null) throw new IOException(L.t("No se pudo crear ") + Paths.name(path));
        return made;
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        Uri u = stat(path) == null && mode.contains("w") ? create(path, "application/octet-stream") : uri(path);
        ParcelFileDescriptor p = cr.openFileDescriptor(u, mode);
        if (p == null) throw new FileNotFoundException(path);
        return p;
    }

    @Override
    public void mkdir(String path) throws IOException {
        if (stat(path) != null) return;
        create(path, Document.MIME_TYPE_DIR);
    }

    @Override
    public void delete(String path) throws IOException {
        if (!DocumentsContract.deleteDocument(cr, uri(path))) throw new IOException(L.t("No se pudo borrar ") + Paths.name(path));
    }

    @Override
    public void rename(String from, String to) throws IOException {
        String fromParent = Paths.parent(from), toParent = Paths.parent(to);
        Uri u = uri(from);
        if (!fromParent.equals(toParent)) {
            Uri moved = DocumentsContract.moveDocument(cr, u, uri(fromParent), uri(toParent));
            if (moved == null) throw new IOException(L.t("No se pudo mover"));
            u = moved;
        }
        if (!Paths.name(from).equals(Paths.name(to))) {
            Uri r = DocumentsContract.renameDocument(cr, u, Paths.name(to));
            if (r == null) throw new IOException(L.t("No se pudo renombrar"));
        }
    }

    @Override
    public void setModified(String path, long time) {
        // el selector del sistema no permite cambiar la fecha
    }

    @Override
    public String key(String path) {
        return "saf:" + tree;
    }
}
