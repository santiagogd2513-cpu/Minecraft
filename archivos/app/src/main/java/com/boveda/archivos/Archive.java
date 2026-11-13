package com.boveda.archivos;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Lee archivos comprimidos: ZIP (y JAR, APK, XAPK…), 7Z, TAR (solo o con gz, bz2, xz, lz4, lzma, Z),
 * GZ, BZ2, XZ, LZMA, LZ4, Z sueltos, CPIO, AR/DEB, ARJ e ISO (discos).
 * Los RAR no: se echan a patadas.
 */
final class Archive {
    private Archive() {}

    /** Contraseñas que el usuario escribió en esta sesión (solo en memoria), por ruta del archivo. */
    private static final java.util.Map<String, char[]> PASSWORDS = new java.util.concurrent.ConcurrentHashMap<>();

    static void setPassword(String path, char[] pw) {
        if (pw == null) PASSWORDS.remove(path);
        else PASSWORDS.put(path, pw);
    }

    static char[] passwordOf(String path) {
        return PASSWORDS.get(path);
    }

    static boolean hasPassword(String path) {
        return PASSWORDS.containsKey(path);
    }

    /** El archivo necesita contraseña para leer su contenido. */
    static final class PasswordException extends IOException {
        PasswordException(String m) {
            super(m);
        }
    }

    /** ¿Algo de lo que hay dentro está cifrado? */
    static boolean needsPassword(Context c, String path) {
        Type t = type(Paths.name(path));
        try {
            if (t == Type.ZIP) {
                try (Channel ch = new Channel(c, path); ZipFile zf = openZip(ch)) {
                    Enumeration<ZipArchiveEntry> en = zf.getEntries();
                    while (en.hasMoreElements()) {
                        ZipArchiveEntry e = en.nextElement();
                        if (!e.isDirectory() && e.getGeneralPurposeBit().usesEncryption()) return true;
                    }
                }
            } else if (t == Type.SEVEN_Z) {
                try (Channel ch = new Channel(c, path); SevenZFile sz = new SevenZFile(ch.ch)) {
                    for (SevenZArchiveEntry e : sz.getEntries()) {
                        for (org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration m : e.getContentMethods()) {
                            if (m.getMethod() == org.apache.commons.compress.archivers.sevenz.SevenZMethod.AES256SHA256) return true;
                        }
                    }
                } catch (org.apache.commons.compress.PasswordRequiredException ex) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /** Prueba una contraseña leyendo el comienzo del primer archivo cifrado. */
    static boolean checkPassword(Context c, String path, char[] pw) {
        char[] old = PASSWORDS.get(path);
        PASSWORDS.put(path, pw);
        try {
            walk(c, path, (it, data) -> {
                if (it.dir || data == null) return true;
                byte[] buf = new byte[1 << 16];
                long left = 4L << 20;
                int r;
                while (left > 0 && (r = data.read(buf, 0, (int) Math.min(buf.length, left))) >= 0) left -= r;
                return false; // con el primero basta
            });
            return true;
        } catch (IOException e) {
            if (old == null) PASSWORDS.remove(path);
            else PASSWORDS.put(path, old);
            return false;
        }
    }

    /** Un archivo en el almacenamiento propio del celular (copiándolo a la caché si hace falta). */
    private static java.io.File localCopy(Context c, String path, boolean[] temp) throws IOException {
        java.io.File f = new java.io.File(path);
        if (f.isFile() && f.canRead()) return f;
        java.io.File t = java.io.File.createTempFile("boveda-zip", ".tmp", c.getCacheDir());
        temp[0] = true;
        try (InputStream in = FsRouter.get(c, path).in(path); java.io.OutputStream o = new java.io.FileOutputStream(t)) {
            byte[] buf = new byte[1 << 20];
            int r;
            while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
        }
        return t;
    }

    /** ZIP con contraseña (AES o clásica): lo lee zip4j. */
    private static void walkZipPassword(Context c, String path, char[] pw, Visitor v) throws IOException {
        boolean[] temp = {false};
        java.io.File f = localCopy(c, path, temp);
        try {
            net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(f, pw);
            for (net.lingala.zip4j.model.FileHeader h : z.getFileHeaders()) {
                Item it = new Item(clean(h.getFileName()), h.isDirectory(), Math.max(0, h.getUncompressedSize()), h.getLastModifiedTimeEpoch());
                if (h.isDirectory()) {
                    if (!v.entry(it, null)) return;
                    continue;
                }
                try (InputStream in = z.getInputStream(h)) {
                    if (!v.entry(it, in)) return;
                }
            }
        } catch (net.lingala.zip4j.exception.ZipException e) {
            if (e.getType() == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) throw new PasswordException(L.t("Contraseña incorrecta"));
            throw new IOException(e.getMessage(), e);
        } finally {
            if (temp[0]) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    /** Algo que hay dentro del archivo comprimido. */
    static final class Item {
        final String name;   // ruta completa dentro del archivo, sin "/" al final
        final boolean dir;
        final long size;
        final long time;

        Item(String name, boolean dir, long size, long time) {
            this.name = name;
            this.dir = dir;
            this.size = size;
            this.time = time;
        }
    }

    interface Visitor {
        /** data es null para carpetas. Devuelve false para parar. */
        boolean entry(Item it, InputStream data) throws IOException;
    }

    enum Type { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, TAR_LZ4, TAR_LZMA, TAR_Z, GZ, BZ2, XZ, LZ4, LZMA, Z, CPIO, AR, ARJ, ISO }

    static boolean isRar(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".rar") || n.matches(".*\\.r\\d\\d$") || n.endsWith(".cbr");
    }

    static Type type(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) return Type.TAR_GZ;
        if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz")) return Type.TAR_BZ2;
        if (n.endsWith(".tar.xz") || n.endsWith(".txz")) return Type.TAR_XZ;
        if (n.endsWith(".tar.lz4")) return Type.TAR_LZ4;
        if (n.endsWith(".tar.lzma") || n.endsWith(".tlz")) return Type.TAR_LZMA;
        if (n.endsWith(".tar.z")) return Type.TAR_Z;
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1);
        switch (ext) {
            case "zip", "jar", "apk", "apks", "xapk", "apkm", "aar", "war", "ear", "cbz", "epub", "zipx", "mcpack", "mcworld", "mrpack":
                return Type.ZIP;
            case "7z", "cb7": return Type.SEVEN_Z;
            case "tar", "cbt": return Type.TAR;
            case "gz", "gzip": return Type.GZ;
            case "bz2": return Type.BZ2;
            case "xz": return Type.XZ;
            case "lz4": return Type.LZ4;
            case "lzma": return Type.LZMA;
            case "z": return Type.Z;
            case "cpio": return Type.CPIO;
            case "ar", "deb", "a": return Type.AR;
            case "arj": return Type.ARJ;
            case "iso": return Type.ISO;
            default: return null;
        }
    }

    static boolean supported(String name) {
        return !isRar(name) && type(name) != null;
    }

    /** Se abre al tocarlo (los APK se instalan; su contenido se ve con «Ver contenido»). */
    static boolean opensOnTap(String name) {
        Type t = type(name);
        if (t == null || isRar(name)) return false;
        String n = name.toLowerCase();
        return !(n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm") || n.endsWith(".epub")
                || n.endsWith(".a") || n.endsWith(".mcpack") || n.endsWith(".mcworld"));
    }

    /** Nombre sin la extensión de compresión: "juego.tar.gz" → "juego". */
    static String baseName(String name) {
        String n = name;
        String low = n.toLowerCase();
        for (String ext : new String[]{".tar.gz", ".tar.bz2", ".tar.xz", ".tar.lz4", ".tar.lzma", ".tar.z"}) {
            if (low.endsWith(ext)) return n.substring(0, n.length() - ext.length());
        }
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // ------------------------------------------------------------------ abrir el archivo

    /** Canal de acceso aleatorio (ZIP, 7Z e ISO lo necesitan), funcione por donde funcione la ruta. */
    static final class Channel implements Closeable {
        final FileChannel ch;
        private final ParcelFileDescriptor pfd;
        private final FileInputStream fis;

        Channel(Context c, String path) throws IOException {
            pfd = FsRouter.get(c, path).pfd(path, "r");
            fis = new FileInputStream(pfd.getFileDescriptor());
            ch = fis.getChannel();
        }

        @Override
        public void close() throws IOException {
            try {
                fis.close();
            } finally {
                pfd.close();
            }
        }
    }

    private static InputStream decompress(Type t, InputStream in) throws IOException {
        InputStream b = new BufferedInputStream(in, 1 << 20);
        return switch (t) {
            case TAR_GZ, GZ -> new GzipCompressorInputStream(b, true);
            case TAR_BZ2, BZ2 -> new BZip2CompressorInputStream(b, true);
            case TAR_XZ, XZ -> new XZCompressorInputStream(b, true);
            case TAR_LZ4, LZ4 -> new FramedLZ4CompressorInputStream(b, true);
            case TAR_LZMA, LZMA -> new LZMACompressorInputStream(b);
            case TAR_Z, Z -> new ZCompressorInputStream(b);
            default -> b;
        };
    }

    /** Lista todo lo que hay dentro. */
    static List<Item> list(Context c, String path) throws IOException {
        List<Item> out = new ArrayList<>();
        Type t = type(Paths.name(path));
        if (t == Type.ZIP) {
            try (Channel ch = new Channel(c, path); ZipFile zf = openZip(ch)) {
                Enumeration<ZipArchiveEntry> en = zf.getEntries();
                while (en.hasMoreElements()) {
                    ZipArchiveEntry e = en.nextElement();
                    out.add(new Item(clean(e.getName()), e.isDirectory(), Math.max(0, e.getSize()), e.getTime()));
                }
            }
            return out;
        }
        if (t == Type.SEVEN_Z) {
            try (Channel ch = new Channel(c, path); SevenZFile sz = openSevenZ(ch, path)) {
                for (SevenZArchiveEntry e : sz.getEntries()) {
                    out.add(new Item(clean(e.getName()), e.isDirectory(), e.getSize(), e.getHasLastModifiedDate()
                            ? e.getLastModifiedDate().getTime() : 0));
                }
            }
            return out;
        }
        if (t == Type.ISO) {
            try (Channel ch = new Channel(c, path)) {
                return new IsoReader(ch.ch).list();
            }
        }
        // los demás se leen de corrido (sin leer el contenido de cada archivo)
        walk(c, path, (it, data) -> {
            out.add(it);
            return true;
        }, false);
        return out;
    }

    /** Recorre todo en orden, con el contenido de cada archivo. */
    static void walk(Context c, String path, Visitor v) throws IOException {
        walk(c, path, v, true);
    }

    private static void walk(Context c, String path, Visitor v, boolean withData) throws IOException {
        String name = Paths.name(path);
        Type t = type(name);
        if (t == null || isRar(name)) throw new IOException(L.t("Formato no soportado"));
        switch (t) {
            case ZIP -> {
                char[] pw = PASSWORDS.get(path);
                if (pw != null && withData) {
                    walkZipPassword(c, path, pw, v);
                    return;
                }
                try (Channel ch = new Channel(c, path); ZipFile zf = openZip(ch)) {
                    Enumeration<ZipArchiveEntry> en = zf.getEntriesInPhysicalOrder();
                    while (en.hasMoreElements()) {
                        ZipArchiveEntry e = en.nextElement();
                        Item it = new Item(clean(e.getName()), e.isDirectory(), Math.max(0, e.getSize()), e.getTime());
                        if (it.dir || !withData) {
                            if (!v.entry(it, null)) return;
                            continue;
                        }
                        if (e.getGeneralPurposeBit().usesEncryption()) throw new PasswordException(L.t("Este archivo tiene contraseña"));
                        if (!zf.canReadEntryData(e)) throw new IOException(L.t("No se puede leer «") + it.name + L.t("» (método no soportado)"));
                        try (InputStream in = zf.getInputStream(e)) {
                            if (!v.entry(it, in)) return;
                        }
                    }
                }
            }
            case SEVEN_Z -> {
                try (Channel ch = new Channel(c, path); SevenZFile sz = openSevenZ(ch, path)) {
                    SevenZArchiveEntry e;
                    while ((e = sz.getNextEntry()) != null) {
                        Item it = new Item(clean(e.getName()), e.isDirectory(), e.getSize(),
                                e.getHasLastModifiedDate() ? e.getLastModifiedDate().getTime() : 0);
                        if (it.dir || !withData || !e.hasStream()) {
                            if (!v.entry(it, it.dir ? null : InputStream.nullInputStream())) return;
                            continue;
                        }
                        final SevenZFile f = sz;
                        InputStream in = new InputStream() {
                            @Override
                            public int read() throws IOException {
                                return f.read();
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                return f.read(b, off, len);
                            }
                        };
                        if (!v.entry(it, in)) return;
                    }
                }
            }
            case ISO -> {
                try (Channel ch = new Channel(c, path)) {
                    IsoReader iso = new IsoReader(ch.ch);
                    for (IsoReader.Node n : iso.nodes()) {
                        if (n.dir || !withData) {
                            if (!v.entry(n.item, null)) return;
                            continue;
                        }
                        try (InputStream in = iso.open(n)) {
                            if (!v.entry(n.item, in)) return;
                        }
                    }
                }
            }
            case TAR, TAR_GZ, TAR_BZ2, TAR_XZ, TAR_LZ4, TAR_LZMA, TAR_Z -> {
                try (InputStream raw = FsRouter.get(c, path).in(path);
                     TarArchiveInputStream tar = new TarArchiveInputStream(decompress(t, raw))) {
                    TarArchiveEntry e;
                    while ((e = tar.getNextEntry()) != null) {
                        if (e.isSymbolicLink() || e.isLink()) continue;
                        Item it = new Item(clean(e.getName()), e.isDirectory(), e.getSize(), e.getModTime().getTime());
                        if (!v.entry(it, it.dir || !withData ? null : shield(tar))) return;
                    }
                }
            }
            case GZ, BZ2, XZ, LZ4, LZMA, Z -> {
                // un solo archivo comprimido
                try (InputStream raw = FsRouter.get(c, path).in(path)) {
                    Entry e = FsRouter.get(c, path).stat(path);
                    Item it = new Item(baseName(name), false, -1, e == null ? 0 : e.modified);
                    if (!withData) {
                        v.entry(it, null);
                        return;
                    }
                    try (InputStream in = decompress(t, raw)) {
                        v.entry(it, in);
                    }
                }
            }
            case CPIO, AR, ARJ -> {
                String fmt = t == Type.CPIO ? ArchiveStreamFactory.CPIO : t == Type.AR ? ArchiveStreamFactory.AR : ArchiveStreamFactory.ARJ;
                try (InputStream raw = FsRouter.get(c, path).in(path)) {
                    ArchiveInputStream<?> ais;
                    try {
                        ais = new ArchiveStreamFactory().createArchiveInputStream(fmt, new BufferedInputStream(raw, 1 << 20));
                    } catch (org.apache.commons.compress.archivers.ArchiveException ex) {
                        throw new IOException(L.t("Archivo dañado o no soportado"), ex);
                    }
                    try (ArchiveInputStream<?> a = ais) {
                        ArchiveEntry e;
                        while ((e = a.getNextEntry()) != null) {
                            Item it = new Item(clean(e.getName()), e.isDirectory(), e.getSize(),
                                    e.getLastModifiedDate() == null ? 0 : e.getLastModifiedDate().getTime());
                            if (!a.canReadEntryData(e)) continue;
                            if (!v.entry(it, it.dir || !withData ? null : shield(a))) return;
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ZipFile openZip(Channel ch) throws IOException {
        return new ZipFile(ch.ch);
    }

    @SuppressWarnings("deprecation")
    private static SevenZFile openSevenZ(Channel ch, String path) throws IOException {
        char[] pw = PASSWORDS.get(path);
        try {
            return pw == null ? new SevenZFile(ch.ch) : new SevenZFile(ch.ch, pw);
        } catch (org.apache.commons.compress.PasswordRequiredException e) {
            throw new PasswordException(L.t("Este archivo tiene contraseña"));
        }
    }

    /** Evita que quien lee una entrada cierre el archivo entero. */
    private static InputStream shield(InputStream in) {
        return new FilterInputStream(in) {
            @Override
            public void close() {
            }
        };
    }

    /** Normaliza el nombre: sin "./", sin "/" al principio ni al final, con "/" como separador. */
    static String clean(String n) {
        String s = n.replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Un nombre es seguro si no puede salir de la carpeta destino. */
    static boolean safe(String name) {
        if (name.isEmpty()) return false;
        for (String part : name.split("/")) if (part.equals("..")) return false;
        return true;
    }
}
