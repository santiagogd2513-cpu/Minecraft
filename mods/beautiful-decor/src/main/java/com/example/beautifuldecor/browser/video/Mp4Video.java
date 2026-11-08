package com.example.beautifuldecor.browser.video;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * El video H.264 de un MP4 como una sucesión de cuadros en orden de presentación (con la cola de reordenamiento que piden los
 * cuadros B). No toca nada de Minecraft; el reproductor llama {@link #next} en su propio hilo.
 * Límites: video progresivo de 8 bits 4:2:0 (perfiles Baseline, Main y High), hasta {@link #MAX_PIXELS} píxeles por cuadro.
 */
public final class Mp4Video implements Closeable {
    /** El archivo se puede abrir pero este video no se puede reproducir (códec, tamaño o función no soportados). */
    public static final class Unsupported extends IOException {
        private static final long serialVersionUID = 1L;

        public Unsupported(String message) {
            super(message);
        }
    }

    /** El archivo está cortado y no queda ni un cuadro completo para mostrar. */
    public static final class Truncated extends IOException {
        private static final long serialVersionUID = 1L;
        public final long have;
        public final long need;

        public Truncated(long have, long need) {
            super("archivo cortado: mide " + have + " bytes y el video llega hasta el " + need);
            this.have = have;
            this.need = need;
        }
    }

    /** Un cuadro listo: píxeles ABGR (como los guarda NativeImage) o null si se omitió por llegar tarde. */
    public static final class Frame {
        public final long ptsUs;
        public final int[] abgr;

        Frame(long ptsUs, int[] abgr) {
            this.ptsUs = ptsUs;
            this.abgr = abgr;
        }
    }

    public static final int MAX_PIXELS = 1280 * 720;

    private static final class Entry implements Comparable<Entry> {
        final H264Pic pic;
        final long seq;

        Entry(H264Pic pic, long seq) {
            this.pic = pic;
            this.seq = seq;
        }

        @Override
        public int compareTo(Entry o) {
            int c = Long.compare(this.pic.ptsUs, o.pic.ptsUs);
            return c != 0 ? c : Long.compare(this.seq, o.seq);
        }
    }

    private final Mp4 mp4;
    private final Mp4.Track track;
    private final H264Decoder decoder = new H264Decoder();
    private final PriorityQueue<Entry> heap = new PriorityQueue<>();
    private final int width;
    private final int height;
    private final int depth;
    private int nextSample;
    private long seq;
    private boolean ended;

    private Mp4Video(Mp4 mp4, Mp4.Track track, int width, int height, int depth) {
        this.mp4 = mp4;
        this.track = track;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public static Mp4Video open(Path file) throws IOException {
        Mp4 m = Mp4.open(file);
        boolean ok = false;
        try {
            boolean cut = m.cutToFile();                    // un archivo cortado se reproduce hasta donde llega
            Mp4.Track v = m.video;
            if (cut && v != null && v.sampleCount() == 0) {
                throw new Truncated(m.fileLength(), m.requiredBytes());
            }
            if (v == null) {
                throw new Unsupported(m.unsupportedVideo != null ? m.unsupportedVideo : "");
            }
            if (v.sampleCount() == 0 || v.sps.isEmpty() || v.pps.isEmpty()) {
                throw new Unsupported("");
            }
            H264Decoder probe = new H264Decoder();
            int[] size;
            try {
                probe.setParameterSets(v.sps, v.pps, v.nalLengthSize);
                size = probe.visibleSize();
            } catch (H264Decoder.Unsupported u) {
                throw new Unsupported(u.getMessage());
            } catch (RuntimeException e) {
                throw new Unsupported("");
            }
            if (size == null || size[0] <= 0 || size[1] <= 0) {
                throw new Unsupported("");
            }
            if ((long) size[0] * size[1] > MAX_PIXELS) {
                throw new Unsupported("size " + size[0] + "x" + size[1]);
            }
            Mp4Video r = new Mp4Video(m, v, size[0], size[1], reorderDepth(v));
            try {
                r.decoder.setParameterSets(v.sps, v.pps, v.nalLengthSize);
            } catch (H264Decoder.Unsupported u) {
                throw new Unsupported(u.getMessage());
            }
            ok = true;
            return r;
        } finally {
            if (!ok) {
                m.close();
            }
        }
    }

    /** Cuántos cuadros hay que guardar para sacar siempre el que toca: lo máximo que un cuadro se muestra antes de lo que se decodifica. */
    private static int reorderDepth(Mp4.Track v) {
        int n = v.sampleCount();
        long[] pts = new long[n];
        boolean sorted = true;
        for (int i = 0; i < n; i++) {
            pts[i] = v.ptsUs(i);
            if (i > 0 && pts[i] < pts[i - 1]) {
                sorted = false;
            }
        }
        if (sorted) {
            return 0;
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int c = Long.compare(pts[a], pts[b]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        int depth = 0;
        for (int d = 0; d < n; d++) {
            depth = Math.max(depth, order[d] - d);
        }
        return Math.min(depth, 16);
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public long durationUs() {
        return this.mp4.durationUs();
    }

    /** Tamaño del archivo en bytes. */
    public long fileLength() {
        return this.mp4.fileLength();
    }

    /** Hasta qué byte necesita llegar el archivo para tener todo el video y el sonido (si es más que {@link #fileLength()}, está cortado). */
    public long requiredBytes() {
        return this.mp4.requiredBytes();
    }

    /** Cierto si el archivo está cortado: se reproduce solo hasta donde llega ({@link #durationUs()} ya es la parte que sí hay). */
    public boolean isCut() {
        return this.mp4.isCut();
    }

    /** Cuántos cuadros trae el video (aproximado: uno por muestra). */
    public int frameCount() {
        return this.track.sampleCount();
    }

    /**
     * El siguiente cuadro en orden de presentación, o null cuando se acabó. Los cuadros con tiempo menor que skipBeforeUs se
     * decodifican igual (los siguientes dependen de ellos) pero no se convierten: {@link Frame#abgr} es null.
     * spare puede ser un arreglo de ancho*alto enteros para reutilizar.
     */
    public Frame next(long skipBeforeUs, int[] spare) throws IOException {
        try {
            while (true) {
                if (this.heap.size() > this.depth || (this.ended && !this.heap.isEmpty())) {
                    return convert(this.heap.poll().pic, skipBeforeUs, spare);
                }
                if (this.ended) {
                    return null;
                }
                if (this.nextSample >= this.track.sampleCount()) {
                    H264Pic last = this.decoder.flush();
                    if (last != null) {
                        this.decoder.release(last);
                    }
                    this.ended = true;
                    continue;
                }
                int i = this.nextSample++;
                H264Pic p = this.decoder.decode(this.mp4.readSample(this.track, i));
                if (p != null) {
                    p.ptsUs = this.track.ptsUs(i);
                    this.heap.add(new Entry(p, this.seq++));
                }
            }
        } catch (H264Decoder.Unsupported u) {
            throw new Unsupported(u.getMessage());
        }
    }

    private Frame convert(H264Pic p, long skipBeforeUs, int[] spare) {
        long pts = p.ptsUs;
        int[] out = null;
        if (pts >= skipBeforeUs) {
            int need = this.width * this.height;
            out = spare != null && spare.length >= need ? spare : new int[need];
            p.toAbgr(out);
        }
        this.decoder.release(p);
        return new Frame(pts, out);
    }

    @Override
    public void close() throws IOException {
        this.heap.clear();
        this.mp4.close();
    }
}
