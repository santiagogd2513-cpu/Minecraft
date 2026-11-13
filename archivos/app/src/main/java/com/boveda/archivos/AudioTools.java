package com.boveda.archivos;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Convierte audio (y saca el audio de un video) usando los códecs del propio Android: no hay que añadir librerías.
 * Lee lo que el celular sepa decodificar (MP3, AAC/M4A, OGG, FLAC, WAV, Opus, AMR…) y escribe M4A (AAC), WAV y
 * OGG (Opus). MP3 y OGG Vorbis no se pueden crear porque Android no trae codificador para ellos.
 */
final class AudioTools {
    private AudioTools() {}

    enum Out {
        M4A(".m4a", "M4A (AAC)"), WAV(".wav", "WAV"), OPUS(".ogg", "OGG (Opus)");

        final String ext, label;

        Out(String e, String l) {
            ext = e;
            label = l;
        }

        boolean lossy() {
            return this != WAV;
        }
    }

    interface Progress {
        void onProgress(float fraction);

        boolean cancelled();
    }

    /** Información de lo que hay dentro de un archivo. */
    static final class Info {
        String mime = "";
        int sampleRate, channels;
        long durationUs;
        boolean hasAudio;
    }

    static Info probe(File f) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            Info i = new Info();
            for (int t = 0; t < ex.getTrackCount(); t++) {
                MediaFormat fm = ex.getTrackFormat(t);
                String m = fm.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) {
                    i.hasAudio = true;
                    i.mime = m;
                    i.sampleRate = fm.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fm.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 0;
                    i.channels = fm.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fm.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 0;
                    i.durationUs = fm.containsKey(MediaFormat.KEY_DURATION) ? fm.getLong(MediaFormat.KEY_DURATION) : 0;
                    break;
                }
            }
            return i;
        } catch (IOException | RuntimeException e) {
            throw new IOException(L.t("No pude leer este archivo (¿está dañado o es un formato que Android no abre?)"), e);
        } finally {
            ex.release();
        }
    }

    // ------------------------------------------------------------------ convertir

    /**
     * Convierte la primera pista de audio de {@code in} a {@code out}. Si {@code copyAac} y la pista ya es AAC y el
     * destino es M4A, se copia sin recomprimir (rápido y sin pérdida).
     */
    static void convert(File in, File out, Out fmt, int bitrateKbps, boolean copyAac, Progress prog) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec dec = null, enc = null;
        MediaMuxer mux = null;
        RandomAccessFile wav = null;
        boolean muxStarted = false;
        try {
            ex.setDataSource(in.getAbsolutePath());
            int track = -1;
            MediaFormat inFmt = null;
            for (int t = 0; t < ex.getTrackCount(); t++) {
                MediaFormat f = ex.getTrackFormat(t);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) {
                    track = t;
                    inFmt = f;
                    break;
                }
            }
            if (track < 0) throw new IOException(L.t("Este archivo no tiene audio"));
            ex.selectTrack(track);
            String mime = inFmt.getString(MediaFormat.KEY_MIME);
            long duration = inFmt.containsKey(MediaFormat.KEY_DURATION) ? inFmt.getLong(MediaFormat.KEY_DURATION) : 0;

            // copiar tal cual: AAC dentro de un video o de un m4a, a m4a
            if (copyAac && fmt == Out.M4A && "audio/mp4a-latm".equals(mime)) {
                mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int mt = mux.addTrack(inFmt);
                mux.start();
                muxStarted = true;
                ByteBuffer buf = ByteBuffer.allocate(1 << 20);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                while (true) {
                    if (prog.cancelled()) throw new IOException(L.t("Cancelado"));
                    buf.clear();
                    int n = ex.readSampleData(buf, 0);
                    if (n < 0) break;
                    bi.offset = 0;
                    bi.size = n;
                    bi.presentationTimeUs = ex.getSampleTime();
                    bi.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    mux.writeSampleData(mt, buf, bi);
                    if (duration > 0) prog.onProgress(Math.min(1f, bi.presentationTimeUs / (float) duration));
                    ex.advance();
                }
                return;
            }

            dec = MediaCodec.createDecoderByType(mime);
            dec.configure(inFmt, null, null, 0);
            dec.start();

            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo ebi = new MediaCodec.BufferInfo();
            boolean extractorDone = false, decoderDone = false, encInputEos = false, encoderDone = false;
            int sampleRate = inFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = inFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            boolean formatKnown = false;
            ArrayDeque<byte[]> pcm = new ArrayDeque<>();
            int pcmHead = 0;               // cuánto del primer pedazo ya se usó
            long pcmBytes = 0;
            long samplesOut = 0;           // muestras por canal entregadas al codificador
            long wavData = 0;
            int muxTrack = -1;
            Resampler rs = null;

            while (!(fmt == Out.WAV ? decoderDone && pcm.isEmpty() : encoderDone)) {
                if (prog.cancelled()) throw new IOException(L.t("Cancelado"));
                boolean progressed = false;

                // 1. pasarle datos al decodificador
                if (!extractorDone) {
                    int ii = dec.dequeueInputBuffer(2000);
                    if (ii >= 0) {
                        ByteBuffer b = dec.getInputBuffer(ii);
                        int n = ex.readSampleData(b, 0);
                        if (n < 0) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            extractorDone = true;
                        } else {
                            dec.queueInputBuffer(ii, 0, n, ex.getSampleTime(), 0);
                            if (duration > 0) prog.onProgress(Math.min(0.99f, ex.getSampleTime() / (float) duration));
                            ex.advance();
                        }
                        progressed = true;
                    }
                }

                // 2. sacar el sonido ya decodificado
                if (!decoderDone && pcmBytes < (1 << 21)) {
                    int oi = dec.dequeueOutputBuffer(bi, 2000);
                    if (oi >= 0) {
                        if (bi.size > 0) {
                            ByteBuffer ob = dec.getOutputBuffer(oi);
                            byte[] chunk = new byte[bi.size];
                            ob.position(bi.offset);
                            ob.get(chunk, 0, bi.size);
                            pcm.add(chunk);
                            pcmBytes += chunk.length;
                        }
                        dec.releaseOutputBuffer(oi, false);
                        if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) decoderDone = true;
                        progressed = true;
                    } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat of = dec.getOutputFormat();
                        if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) != android.media.AudioFormat.ENCODING_PCM_16BIT) {
                            throw new IOException(L.t("Este audio usa un formato interno que esta herramienta no sabe pasar"));
                        }
                    }
                }
                if (channels > 2) throw new IOException(L.t("Este audio tiene más de 2 canales; por ahora solo se convierten mono y estéreo"));

                // 3. crear la salida la primera vez que se sabe cómo suena
                if (!formatKnown && (pcmBytes > 0 || decoderDone)) {
                    formatKnown = true;
                    if (fmt == Out.WAV) {
                        wav = new RandomAccessFile(out, "rw");
                        wav.setLength(0);
                        wav.write(wavHeader(sampleRate, channels, 0));
                    } else {
                        int outRate = sampleRate;
                        if (fmt == Out.OPUS && outRate != 48000) {
                            rs = new Resampler(channels, sampleRate, 48000);
                            outRate = 48000;
                        }
                        String em = fmt == Out.M4A ? MediaFormat.MIMETYPE_AUDIO_AAC : MediaFormat.MIMETYPE_AUDIO_OPUS;
                        MediaFormat ef = MediaFormat.createAudioFormat(em, outRate, channels);
                        ef.setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000);
                        if (fmt == Out.M4A) {
                            ef.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                            ef.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 << 16);
                        }
                        try {
                            enc = MediaCodec.createEncoderByType(em);
                            enc.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                            enc.start();
                        } catch (Exception e) {
                            throw new IOException(L.t("Este celular no puede crear ") + fmt.label, e);
                        }
                        mux = new MediaMuxer(out.getAbsolutePath(), fmt == Out.M4A ? MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 : MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
                    }
                }

                // 4. entregar el sonido: a un archivo WAV o al codificador
                if (formatKnown && fmt == Out.WAV) {
                    while (!pcm.isEmpty()) {
                        byte[] c = pcm.poll();
                        int len = c.length - pcmHead;
                        wav.write(c, pcmHead, len);
                        wavData += len;
                        pcmBytes -= len;
                        pcmHead = 0;
                        progressed = true;
                    }
                } else if (formatKnown && enc != null && !encInputEos) {
                    int ei = enc.dequeueInputBuffer(2000);
                    if (ei >= 0) {
                        ByteBuffer eb = enc.getInputBuffer(ei);
                        eb.clear();
                        int want = Math.min(eb.remaining(), 1 << 16);
                        byte[] tmp = new byte[want];
                        int got = 0;
                        while (got < want && !pcm.isEmpty()) {
                            byte[] c = pcm.peek();
                            int take = Math.min(want - got, c.length - pcmHead);
                            System.arraycopy(c, pcmHead, tmp, got, take);
                            got += take;
                            pcmHead += take;
                            pcmBytes -= take;
                            if (pcmHead >= c.length) {
                                pcm.poll();
                                pcmHead = 0;
                            }
                        }
                        got -= got % (2 * channels);      // muestras enteras
                        // (lo que sobre de una muestra a medias se pierde: como mucho unos bytes)
                        byte[] data = got == tmp.length ? tmp : java.util.Arrays.copyOf(tmp, got);
                        if (rs != null && got > 0) data = rs.process(data);
                        if (data.length == 0 && decoderDone && pcm.isEmpty()) {
                            enc.queueInputBuffer(ei, 0, 0, samplesOut * 1_000_000L / (rs != null ? 48000 : sampleRate), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            encInputEos = true;
                        } else if (data.length > 0) {
                            eb.put(data, 0, Math.min(data.length, eb.remaining()));
                            long pts = samplesOut * 1_000_000L / (rs != null ? 48000 : sampleRate);
                            enc.queueInputBuffer(ei, 0, data.length, pts, 0);
                            samplesOut += data.length / (2L * channels);
                        } else {
                            enc.queueInputBuffer(ei, 0, 0, 0, 0);
                        }
                        progressed = true;
                    }
                }

                // 5. sacar lo codificado y guardarlo en el archivo
                if (enc != null) {
                    int eo = enc.dequeueOutputBuffer(ebi, 2000);
                    if (eo == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxTrack = mux.addTrack(enc.getOutputFormat());
                        mux.start();
                        muxStarted = true;
                        progressed = true;
                    } else if (eo >= 0) {
                        ByteBuffer ob = enc.getOutputBuffer(eo);
                        if ((ebi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) ebi.size = 0;
                        if (ebi.size > 0 && muxStarted) {
                            ob.position(ebi.offset);
                            ob.limit(ebi.offset + ebi.size);
                            mux.writeSampleData(muxTrack, ob, ebi);
                        }
                        enc.releaseOutputBuffer(eo, false);
                        if ((ebi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encoderDone = true;
                        progressed = true;
                    }
                }
                if (!progressed && decoderDone && fmt == Out.WAV && pcm.isEmpty()) break;
            }

            if (fmt == Out.WAV && wav != null) {
                wav.seek(0);
                wav.write(wavHeader(sampleRate, channels, wavData));
            }
            prog.onProgress(1f);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw e;
        } catch (RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw new IOException(L.t("No se pudo convertir: ") + e.getMessage(), e);
        } finally {
            try {
                if (dec != null) {
                    dec.stop();
                    dec.release();
                }
            } catch (Exception ignored) {
            }
            try {
                if (enc != null) {
                    enc.stop();
                    enc.release();
                }
            } catch (Exception ignored) {
            }
            try {
                if (mux != null) {
                    if (muxStarted) mux.stop();
                    mux.release();
                }
            } catch (Exception ignored) {
            }
            try {
                if (wav != null) wav.close();
            } catch (IOException ignored) {
            }
            ex.release();
        }
    }

    // ------------------------------------------------------------------ piezas (sin Android, se prueban aparte)

    /** Cabecera de un archivo WAV de 16 bits. dataBytes = cuánto sonido viene después. */
    static byte[] wavHeader(int rate, int channels, long dataBytes) {
        ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'});
        b.putInt((int) (36 + dataBytes));
        b.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) channels);
        b.putInt(rate);
        b.putInt(rate * channels * 2);
        b.putShort((short) (channels * 2));
        b.putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'});
        b.putInt((int) dataBytes);
        return b.array();
    }

    /** Cambia la frecuencia de muestreo (16 bits, intercalado) por interpolación lineal, por tandas. */
    static final class Resampler {
        private final int channels;
        private final double step;           // cuántas muestras de entrada avanza por cada una de salida
        private double pos;                  // posición dentro de la tanda actual (en muestras de entrada)
        private short[] last;                // la última muestra de la tanda anterior, por canal
        private boolean hasLast;

        Resampler(int channels, int inRate, int outRate) {
            this.channels = channels;
            this.step = inRate / (double) outRate;
            this.last = new short[channels];
        }

        byte[] process(byte[] in) {
            int frames = in.length / (2 * channels);
            if (frames == 0) return new byte[0];
            short[] s = new short[frames * channels];
            ByteBuffer.wrap(in, 0, frames * 2 * channels).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
            int maxOut = (int) ((frames + 1) / step) + 2;
            short[] out = new short[maxOut * channels];
            int n = 0;
            // pos cuenta desde la última muestra de la tanda anterior: el índice 0 es esa, el 1 es la primera de ésta
            if (!hasLast) {
                for (int c = 0; c < channels; c++) last[c] = s[c];
                hasLast = true;
                pos = 1;      // la primera salida es la primera muestra de entrada (índice 1 de esta tanda)
            }
            while (true) {
                int i0 = (int) Math.floor(pos);
                if (i0 + 1 > frames) break;      // faltan muestras: se sigue en la próxima tanda
                double frac = pos - i0;
                for (int c = 0; c < channels; c++) {
                    short a = i0 == 0 ? last[c] : s[(i0 - 1) * channels + c];
                    short b = s[i0 * channels + c];
                    if (n * channels + c < out.length) out[n * channels + c] = (short) Math.round(a + (b - a) * frac);
                }
                n++;
                pos += step;
            }
            for (int c = 0; c < channels; c++) last[c] = s[(frames - 1) * channels + c];
            pos -= frames;
            byte[] r = new byte[n * channels * 2];
            ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(out, 0, n * channels);
            return r;
        }
    }
}
