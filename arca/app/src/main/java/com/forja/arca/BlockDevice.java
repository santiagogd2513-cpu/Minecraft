package com.forja.arca;

import java.io.IOException;

/** Un disco visto como sectores (512 o 4096 bytes): la USB por OTG, o un archivo en las pruebas. */
public interface BlockDevice {
    /** Bytes por sector lógico (512 o 4096). */
    int sectorSize();

    /** Cantidad de sectores. */
    long sectors();

    void read(long lba, byte[] buf, int off, int count) throws IOException;

    void write(long lba, byte[] buf, int off, int count) throws IOException;

    void flush() throws IOException;

    /** Mayor cantidad de bytes por lectura/escritura que conviene usar. */
    default int maxTransferBytes() {
        return 128 * 1024;
    }
}
