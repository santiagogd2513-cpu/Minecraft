package com.forja.arca;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Leer una carpeta que el usuario eligió con el selector de Android (por ejemplo, una memoria USB que Android ya montó). */
final class Saf {
    static final class Doc {
        String id, name;
        boolean dir;
        long size;
    }

    private Saf() {}

    static List<Doc> children(Context c, Uri tree, String parentId) {
        List<Doc> out = new ArrayList<>();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor cur = c.getContentResolver().query(kids, cols, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                Doc d = new Doc();
                d.id = cur.getString(0);
                d.name = cur.getString(1);
                d.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(cur.getString(2));
                d.size = cur.isNull(3) ? 0 : cur.getLong(3);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Un archivo del selector, como fuente de bytes de solo lectura. El cierre libera el archivo. */
    static final class FileSource implements ByteSource, Closeable {
        private final ParcelFileDescriptor pfd;
        private final FileInputStream in;
        private final FileChannel ch;
        private final long size;

        FileSource(ContentResolver cr, Uri doc) throws IOException {
            pfd = cr.openFileDescriptor(doc, "r");
            if (pfd == null) throw new IOException("Android no dejó abrir el archivo");
            in = new FileInputStream(pfd.getFileDescriptor());
            ch = in.getChannel();
            size = ch.size();
        }

        @Override
        public long length() {
            return size;
        }

        @Override
        public synchronized void read(long off, byte[] buf, int boff, int len) throws IOException {
            if (off < 0 || off + len > size) throw new IOException("Lectura fuera del archivo");
            ByteBuffer b = ByteBuffer.wrap(buf, boff, len);
            long pos = off;
            while (b.hasRemaining()) {
                int n = ch.read(b, pos);
                if (n <= 0) throw new IOException("La memoria dejó de responder");
                pos += n;
            }
        }

        @Override
        public void close() {
            try {
                in.close();
                pfd.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Los archivos Data0000, Data0001… de la carpeta «Xbox360» (o, si se eligió esa misma carpeta, de ahí). En orden. */
    static List<FileSource> dataFiles(Context c, Uri tree) throws IOException {
        String root = DocumentsContract.getTreeDocumentId(tree);
        List<Doc> top = children(c, tree, root);
        List<Doc> files = new ArrayList<>();
        for (Doc d : top) if (!d.dir && isData(d.name)) files.add(d);
        if (files.isEmpty()) {
            for (Doc d : top) {
                if (d.dir && d.name.equalsIgnoreCase("Xbox360")) {
                    for (Doc k : children(c, tree, d.id)) if (!k.dir && isData(k.name)) files.add(k);
                }
            }
        }
        files.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        List<FileSource> out = new ArrayList<>();
        try {
            for (Doc d : files) out.add(new FileSource(c.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(tree, d.id)));
        } catch (IOException e) {
            for (FileSource s : out) s.close();
            throw e;
        }
        return out;
    }

    private static boolean isData(String n) {
        return n != null && n.toLowerCase(Locale.ROOT).matches("data\\d{4}");
    }
}
