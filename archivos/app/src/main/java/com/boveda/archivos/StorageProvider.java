package com.boveda.archivos;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bóveda dentro del selector de archivos del sistema. Cuando otra app (un emulador, un editor…)
 * pide una carpeta o un archivo, se elige «Bóveda» en el menú del selector y se le da acceso a lo
 * que se quiera, incluso a carpetas que el selector normal no deja elegir (la raíz, Descargas, y
 * Android/data si Bóveda tiene Shizuku o el permiso de esa carpeta).
 * El identificador de cada documento es su ruta absoluta.
 */
public class StorageProvider extends DocumentsProvider {
    private static final String[] ROOT_COLS = {Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_AVAILABLE_BYTES};
    private static final String[] DOC_COLS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS};

    static String authority(Context c) {
        return c.getPackageName() + ".documents";
    }

    static Uri documentUri(Context c, String path) {
        return DocumentsContract.buildDocumentUri(authority(c), path);
    }

    @Override
    public boolean onCreate() {
        try {
            ShizukuHelper.init(getContext());
        } catch (Throwable ignored) {
        }
        return true;
    }

    private Fs fs(String path) throws FileNotFoundException {
        // la Bóveda solo la abre Bóveda: ninguna otra app puede pedir sus archivos
        if ((VaultFs.isVault(path) || Paths.under(path, Vault.DIR)) && android.os.Binder.getCallingUid() != android.os.Process.myUid()) {
            throw new FileNotFoundException(path);
        }
        try {
            return FsRouter.get(getContext(), path);
        } catch (Fs.NeedAccess e) {
            throw new FileNotFoundException(L.t("Abre Bóveda y da acceso a ") + path);
        }
    }

    private static boolean allowed() {
        return android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : ROOT_COLS);
        if (!allowed()) return c;
        StorageManager sm = getContext().getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            File dir = android.os.Build.VERSION.SDK_INT >= 30 ? v.getDirectory() : null;
            if (dir == null) {
                if (v.isPrimary()) dir = Environment.getExternalStorageDirectory();
                else continue;
            }
            String path = dir.getAbsolutePath();
            MatrixCursor.RowBuilder r = c.newRow();
            r.add(Root.COLUMN_ROOT_ID, path);
            r.add(Root.COLUMN_DOCUMENT_ID, path);
            r.add(Root.COLUMN_TITLE, "Bóveda");
            r.add(Root.COLUMN_SUMMARY, v.isPrimary() ? L.t("Almacenamiento interno") : v.getDescription(getContext()));
            r.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_LOCAL_ONLY);
            r.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
            r.add(Root.COLUMN_AVAILABLE_BYTES, dir.getUsableSpace());
        }
        return c;
    }

    private void addRow(MatrixCursor c, Entry e) {
        MatrixCursor.RowBuilder r = c.newRow();
        r.add(Document.COLUMN_DOCUMENT_ID, e.path);
        r.add(Document.COLUMN_DISPLAY_NAME, e.name);
        r.add(Document.COLUMN_MIME_TYPE, e.dir ? Document.MIME_TYPE_DIR : FileTypes.mime(e.name));
        r.add(Document.COLUMN_SIZE, e.dir ? null : e.size);
        r.add(Document.COLUMN_LAST_MODIFIED, e.modified);
        int flags = Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME | Document.FLAG_SUPPORTS_MOVE
                | Document.FLAG_SUPPORTS_COPY | Document.FLAG_SUPPORTS_REMOVE;
        flags |= e.dir ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE;
        r.add(Document.COLUMN_FLAGS, flags);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLS);
        try {
            Entry e = fs(documentId).stat(documentId);
            if (e == null) throw new FileNotFoundException(documentId);
            addRow(c, e);
        } catch (IOException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String parentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLS);
        try {
            for (Entry e : fs(parentId).list(parentId)) {
                if (e.name.endsWith(".boveda-parte") || e.path.equals(Vault.DIR)) continue;
                addRow(c, e);
            }
        } catch (IOException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
        c.setNotificationUri(getContext().getContentResolver(), DocumentsContract.buildChildDocumentsUri(authority(getContext()), parentId));
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        try {
            return fs(documentId).pfd(documentId, mode);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public boolean isChildDocument(String parentId, String documentId) {
        return Paths.under(documentId, parentId);
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        try {
            Entry e = fs(documentId).stat(documentId);
            if (e == null) throw new FileNotFoundException(documentId);
            return e.dir ? Document.MIME_TYPE_DIR : FileTypes.mime(e.name);
        } catch (IOException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    @Override
    public String createDocument(String parentId, String mimeType, String displayName) throws FileNotFoundException {
        try {
            Fs f = fs(parentId);
            String path = Paths.join(parentId, displayName);
            for (int n = 1; f.stat(path) != null; n++) {
                int dot = displayName.lastIndexOf('.');
                String base = dot > 0 ? displayName.substring(0, dot) : displayName, ext = dot > 0 ? displayName.substring(dot) : "";
                path = Paths.join(parentId, base + " (" + n + ")" + ext);
            }
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                f.mkdir(path);
            } else {
                f.out(path).close();
            }
            notifyChange(parentId);
            return path;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        try {
            fs(documentId).delete(documentId);
            notifyChange(Paths.parent(documentId));
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public void removeDocument(String documentId, String parentDocumentId) throws FileNotFoundException {
        deleteDocument(documentId);
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        try {
            String to = Paths.join(Paths.parent(documentId), displayName);
            fs(documentId).rename(documentId, to);
            notifyChange(Paths.parent(documentId));
            return to;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public String moveDocument(String sourceId, String sourceParentId, String targetParentId) throws FileNotFoundException {
        String to = Paths.join(targetParentId, Paths.name(sourceId));
        try {
            Fs sf = fs(sourceId), df = fs(to);
            if (sf.key(sourceId).equals(df.key(to))) {
                sf.rename(sourceId, to);
            } else {
                copyRec(sf, sourceId, df, to);
                sf.delete(sourceId);
            }
            notifyChange(sourceParentId);
            notifyChange(targetParentId);
            return to;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public String copyDocument(String sourceId, String targetParentId) throws FileNotFoundException {
        String to = Paths.join(targetParentId, Paths.name(sourceId));
        try {
            copyRec(fs(sourceId), sourceId, fs(to), to);
            notifyChange(targetParentId);
            return to;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private static void copyRec(Fs sf, String src, Fs df, String dst) throws IOException {
        Entry e = sf.stat(src);
        if (e == null) throw new FileNotFoundException(src);
        if (e.dir) {
            df.mkdir(dst);
            for (Entry k : sf.list(src)) copyRec(sf, k.path, df, Paths.join(dst, k.name));
            return;
        }
        try (InputStream in = sf.in(src); OutputStream out = df.out(dst)) {
            byte[] buf = new byte[1 << 20];
            int r;
            while ((r = in.read(buf)) >= 0) out.write(buf, 0, r);
        }
    }

    private void notifyChange(String dir) {
        getContext().getContentResolver().notifyChange(DocumentsContract.buildChildDocumentsUri(authority(getContext()), dir), null);
    }
}
