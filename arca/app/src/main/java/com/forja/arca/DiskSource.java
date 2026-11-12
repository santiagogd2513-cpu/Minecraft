package com.forja.arca;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Un disco USB (por sectores) visto como bytes, con un poco de memoria para no leer lo mismo mil veces. Solo lee. */
final class DiskSource implements ByteSource {
    private static final int CHUNK = 128 * 1024;
    private final BlockDevice dev;
    private final long size;
    private final int sector;
    private final int chunk;
    private final Map<Long, byte[]> cache = new LinkedHashMap<Long, byte[]>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, byte[]> e) {
            return size() > 48;   // unos 6 MB
        }
    };

    DiskSource(BlockDevice dev) {
        this.dev = dev;
        this.sector = dev.sectorSize();
        this.size = dev.sectors() * (long) sector;
        this.chunk = Math.max(CHUNK / sector, 1) * sector;
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public synchronized void read(long off, byte[] buf, int boff, int len) throws IOException {
        if (off < 0 || off + len > size) throw new IOException("Lectura fuera del disco");
        while (len > 0) {
            long idx = off / chunk;
            byte[] c = cache.get(idx);
            if (c == null) {
                long start = idx * chunk;
                int bytes = (int) Math.min(chunk, size - start);
                int sectors = (bytes + sector - 1) / sector;
                c = new byte[sectors * sector];
                dev.read(start / sector, c, 0, sectors);
                cache.put(idx, c);
            }
            int inChunk = (int) (off - idx * chunk);
            int n = Math.min(len, chunk - inChunk);
            System.arraycopy(c, inChunk, buf, boff, n);
            off += n;
            boff += n;
            len -= n;
        }
    }
}
