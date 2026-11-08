package com.ventoy.nooficial;

import java.io.IOException;

/** Un disco visto como sectores de 512 bytes (la USB por OTG, o un archivo en las pruebas). */
public interface BlockDevice {
    int SECTOR = 512;

    /** Cantidad de sectores de 512 bytes. */
    long sectors();

    /** Lee count sectores desde lba hacia buf[off…]. */
    void read(long lba, byte[] buf, int off, int count) throws IOException;

    /** Escribe count sectores de buf[off…] en lba. */
    void write(long lba, byte[] buf, int off, int count) throws IOException;

    /** Asegura que todo lo escrito quedó en el disco. */
    void flush() throws IOException;

    /** Mayor cantidad de sectores por lectura/escritura que conviene usar. */
    default int maxTransfer() {
        return 256;
    }
}
