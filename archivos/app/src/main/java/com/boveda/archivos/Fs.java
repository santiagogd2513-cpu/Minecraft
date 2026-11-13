package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Una forma de llegar a los archivos (normal, selector del sistema o Shizuku). Todo con rutas absolutas. */
interface Fs {
    /** Cuántas operaciones a la vez aguanta bien (para copiar muchos archivos chiquitos en paralelo). */
    default int maxParallel() {
        return 1;
    }

    List<Entry> list(String dir) throws IOException;

    /** null si no existe. */
    Entry stat(String path) throws IOException;

    InputStream in(String path) throws IOException;

    /** Crea el archivo (o lo vacía si existe) y lo abre para escribir. */
    OutputStream out(String path) throws IOException;

    ParcelFileDescriptor pfd(String path, String mode) throws IOException;

    void mkdir(String path) throws IOException;

    /** Borra un archivo o una carpeta con todo lo que tenga. */
    void delete(String path) throws IOException;

    /** Cambia el nombre o mueve dentro del mismo almacenamiento (sin copiar). Falla si no se puede. */
    void rename(String from, String to) throws IOException;

    void setModified(String path, long time);

    /** Identifica el "almacenamiento": mover entre rutas con la misma clave es solo renombrar. */
    String key(String path);

    final class NeedAccess extends IOException {
        final String path;

        NeedAccess(String path) {
            super(L.t("Se necesita acceso para entrar aquí"));
            this.path = path;
        }
    }
}
