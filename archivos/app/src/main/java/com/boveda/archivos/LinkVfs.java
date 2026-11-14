package com.boveda.archivos;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que un celular deja ver del otro: un árbol virtual con «carpetas compartidas» que él mismo eligió. Nada fuera de ellas
 * se puede ver ni tocar, aunque el otro celular lo pida con rutas raras.
 */
final class LinkVfs {
    static final class Item {
        final String name;
        final boolean dir;
        final long size, modified;

        Item(String name, boolean dir, long size, long modified) {
            this.name = name;
            this.dir = dir;
            this.size = size;
            this.modified = modified;
        }
    }

    /** Una fuente de archivos para el árbol: carpeta real, lista de apps, contactos… */
    interface Source {
        String name();

        boolean writable();

        List<Item> list(String rel) throws IOException;

        Item stat(String rel) throws IOException;

        InputStream open(String rel, long offset) throws IOException;

        /** Crea un archivo nuevo (con otro nombre si ya existe) y devuelve a dónde escribir. */
        Sink create(String rel, long size, long modified) throws IOException;

        void mkdir(String rel) throws IOException;

        void delete(String rel) throws IOException;
    }

    interface Sink {
        OutputStream stream();

        /** Cierra y devuelve el nombre final. */
        String finish() throws IOException;

        void abort();
    }

    private final List<Source> sources = new ArrayList<>();

    void add(Source s) {
        sources.add(s);
    }

    List<Source> sources() {
        return sources;
    }

    /** Separa «/Descargas/fotos/a.jpg» en la fuente «Descargas» y el resto «fotos/a.jpg»; null si no hay tal fuente. */
    Source source(String path) {
        String p = norm(path);
        if (p.isEmpty()) return null;
        int i = p.indexOf('/');
        String first = i < 0 ? p : p.substring(0, i);
        for (Source s : sources) if (s.name().equals(first)) return s;
        return null;
    }

    static String rest(String path) {
        String p = norm(path);
        int i = p.indexOf('/');
        return i < 0 ? "" : p.substring(i + 1);
    }

    /** Quita los «..», «.» y las barras repetidas; si intenta salirse hacia arriba, falla. */
    static String norm(String path) {
        if (path == null) return "";
        List<String> out = new ArrayList<>();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) throw new SecurityException("ruta no permitida");
            if (part.indexOf('\0') >= 0) throw new SecurityException("ruta no permitida");
            out.add(part);
        }
        return String.join("/", out);
    }

    // ---------------------------------------------------------------- una carpeta real

    /** Comparte una carpeta del almacenamiento. Todo se resuelve dentro de ella: nunca fuera. */
    static final class FolderSource implements Source {
        private final String name;
        private final File root;
        private final boolean writable;

        FolderSource(String name, File root, boolean writable) {
            this.name = name;
            this.root = root;
            this.writable = writable;
        }

        public String name() {
            return name;
        }

        public boolean writable() {
            return writable;
        }

        private File resolve(String rel) throws IOException {
            String r = norm(rel);
            File f = r.isEmpty() ? root : new File(root, r);
            // el camino real (con enlaces simbólicos resueltos) tiene que seguir dentro de la carpeta
            String rootPath = root.getCanonicalPath();
            String path = f.getCanonicalPath();
            if (!path.equals(rootPath) && !path.startsWith(rootPath + File.separator)) throw new SecurityException("ruta fuera de la carpeta compartida");
            return f;
        }

        public List<Item> list(String rel) throws IOException {
            File d = resolve(rel);
            File[] fs = d.listFiles();
            if (fs == null) throw new IOException("no se puede abrir la carpeta");
            List<Item> out = new ArrayList<>();
            for (File f : fs) out.add(new Item(f.getName(), f.isDirectory(), f.isDirectory() ? 0 : f.length(), f.lastModified()));
            return out;
        }

        public Item stat(String rel) throws IOException {
            File f = resolve(rel);
            if (!f.exists()) return null;
            return new Item(f.getName(), f.isDirectory(), f.isDirectory() ? 0 : f.length(), f.lastModified());
        }

        public InputStream open(String rel, long offset) throws IOException {
            File f = resolve(rel);
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            long skipped = 0;
            while (skipped < offset) {
                long s = in.skip(offset - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            return in;
        }

        public Sink create(String rel, long size, long modified) throws IOException {
            if (!writable) throw new SecurityException("esta carpeta es solo de lectura");
            File target = resolve(rel);
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("no se pudo crear la carpeta");
            String n = target.getName();
            int dot = n.lastIndexOf('.');
            File dest = target;
            for (int i = 1; dest.exists(); i++) dest = new File(parent, (dot > 0 ? n.substring(0, dot) : n) + " (" + i + ")" + (dot > 0 ? n.substring(dot) : ""));
            final File fin = dest;
            final File tmp = new File(parent, "." + fin.getName() + ".enviando");
            final java.io.FileOutputStream os = new java.io.FileOutputStream(tmp);
            return new Sink() {
                public OutputStream stream() {
                    return os;
                }

                public String finish() throws IOException {
                    os.close();
                    if (!tmp.renameTo(fin)) throw new IOException("no se pudo guardar el archivo");
                    if (modified > 0) //noinspection ResultOfMethodCallIgnored
                        fin.setLastModified(modified);
                    return fin.getName();
                }

                public void abort() {
                    try {
                        os.close();
                    } catch (IOException ignored) {
                    }
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            };
        }

        public void mkdir(String rel) throws IOException {
            if (!writable) throw new SecurityException("esta carpeta es solo de lectura");
            File f = resolve(rel);
            if (!f.isDirectory() && !f.mkdirs()) throw new IOException("no se pudo crear la carpeta");
        }

        public void delete(String rel) throws IOException {
            throw new SecurityException("por seguridad, el otro celular no puede borrar");
        }
    }
}
