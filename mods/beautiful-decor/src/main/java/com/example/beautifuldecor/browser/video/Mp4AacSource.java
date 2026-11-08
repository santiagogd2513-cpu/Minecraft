package com.example.beautifuldecor.browser.video;

import com.example.beautifuldecor.browser.audio.AacDecoder;
import com.example.beautifuldecor.browser.audio.PcmSource;
import java.io.IOException;
import java.nio.file.Path;

/**
 * El sonido de un MP4: decodifica la pista AAC-LC como {@link PcmSource} (PCM de 16 bits, mono o estéreo).
 * Si el archivo no trae sonido, o es de un tipo que no se puede decodificar (MP3 dentro de MP4, AC-3, Opus...), entrega silencio
 * mono con la duración del video: así el reproductor de audio sigue sirviendo de reloj, de pausa y de barra de tiempo.
 */
public final class Mp4AacSource implements PcmSource {
    private static final int MAX_BAD_UNITS = 100;
    private static final int SILENT_RATE = 8000;

    /** Silencio con una duración fija. */
    private static final class Silence implements PcmSource {
        private long bytesLeft;

        Silence(long durationUs) {
            long frames = Math.max(SILENT_RATE, durationUs * SILENT_RATE / 1_000_000L);
            this.bytesLeft = frames * 2;
        }

        @Override
        public int channels() {
            return 1;
        }

        @Override
        public int sampleRate() {
            return SILENT_RATE;
        }

        @Override
        public int read(byte[] buf) {
            if (this.bytesLeft <= 0) {
                return -1;
            }
            int n = (int) Math.min(buf.length, this.bytesLeft);
            n &= ~1;
            if (n <= 0) {
                return -1;
            }
            java.util.Arrays.fill(buf, 0, n, (byte) 0);
            this.bytesLeft -= n;
            return n;
        }

        @Override
        public void close() {
            this.bytesLeft = 0;
        }
    }

    private final Mp4 mp4;
    private final Mp4.Track track;
    private final AacDecoder decoder;
    private final int channels;
    private int next;
    private byte[] pending = new byte[0];
    private int pendingPos;
    private long skipBytes = -1;
    private int bad;

    private Mp4AacSource(Mp4 mp4, Mp4.Track track, AacDecoder decoder) {
        this.mp4 = mp4;
        this.track = track;
        this.decoder = decoder;
        this.channels = decoder.channels();
    }

    /** Cierto si los primeros bytes (al menos 8) parecen un archivo MP4 o MOV: una caja con un nombre de los habituales. */
    public static boolean looksLikeMp4(byte[] head) {
        if (head == null || head.length < 8) {
            return false;
        }
        String type = new String(head, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        return switch (type) {
            case "ftyp", "moov", "mdat", "free", "skip", "wide", "styp", "moof", "pnot" -> true;
            default -> false;
        };
    }

    /** Abre el sonido del archivo; si no hay sonido que se pueda decodificar, devuelve silencio. */
    public static PcmSource open(Path file) throws IOException {
        Mp4 m = Mp4.open(file);
        boolean ok = false;
        try {
            m.cutToFile();                                  // si el archivo está cortado, el sonido termina donde termina la imagen
            Mp4.Track a = m.audio;
            if (a == null || a.audioConfig == null || a.objectType != 0x40 || a.sampleCount() == 0) {
                long dur = m.durationUs();
                return new Silence(dur);
            }
            AacDecoder d;
            try {
                d = new AacDecoder(a.audioConfig);
            } catch (RuntimeException e) {
                return new Silence(m.durationUs());
            }
            Mp4AacSource s = new Mp4AacSource(m, a, d);
            ok = true;
            return s;
        } finally {
            if (!ok) {
                m.close();
            }
        }
    }

    @Override
    public int channels() {
        return this.channels;
    }

    @Override
    public int sampleRate() {
        return this.decoder.sampleRate();
    }

    @Override
    public int read(byte[] buf) throws IOException {
        while (this.pendingPos >= this.pending.length) {
            if (this.next >= this.track.sampleCount()) {
                return -1;
            }
            fill();
        }
        int n = Math.min(buf.length, this.pending.length - this.pendingPos);
        System.arraycopy(this.pending, this.pendingPos, buf, 0, n);
        this.pendingPos += n;
        return n;
    }

    private void fill() throws IOException {
        int i = this.next++;
        if (this.skipBytes < 0) {
            // el audio con retraso del codificador (priming) empieza en un tiempo negativo: eso no se toca
            long pts = this.track.ptsUs(0);
            this.skipBytes = pts < 0 ? ((-pts * this.decoder.sampleRate() + 500_000L) / 1_000_000L) * 2L * this.channels : 0;
        }
        byte[] pcm;
        try {
            short[] s = this.decoder.decode(this.mp4.readSample(this.track, i));
            pcm = new byte[s.length * 2];
            for (int k = 0; k < s.length; k++) {
                pcm[2 * k] = (byte) s[k];
                pcm[2 * k + 1] = (byte) (s[k] >> 8);
            }
            this.bad = 0;
        } catch (RuntimeException e) {
            if (++this.bad > MAX_BAD_UNITS) {
                throw new IOException("audio AAC dañado", e);
            }
            pcm = new byte[1024 * 2 * this.channels];
        }
        int skip = (int) Math.min(pcm.length, this.skipBytes);
        this.skipBytes -= skip;
        this.pending = pcm;
        this.pendingPos = skip;
    }

    @Override
    public void close() throws IOException {
        this.mp4.close();
    }
}
