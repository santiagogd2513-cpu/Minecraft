package com.example.beautifuldecor.browser.audio;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Decodificador de MP3 (capas I, II y III) con JLayer, que viene dentro del mod en el paquete
 * com.example.beautifuldecor.lib.jl.decoder (ver tools/v240/relocate_jlayer.py). Se llama por reflexión para que el
 * resto del código no dependa de esas clases al compilar.
 */
public final class Mp3Source implements PcmSource {
    private static final String PKG = "com.example.beautifuldecor.lib.jl.decoder.";
    private static final Class<?> BITSTREAM;
    private static final Class<?> DECODER;
    private static final Class<?> HEADER;
    private static final Class<?> SAMPLES;
    private static final Method READ_FRAME;
    private static final Method CLOSE_FRAME;
    private static final Method CLOSE_STREAM;
    private static final Method DECODE_FRAME;
    private static final Method GET_BUFFER;
    private static final Method GET_LENGTH;
    private static final Method GET_CHANNELS;
    private static final Method GET_FREQ;
    private static final Throwable LOAD_ERROR;

    static {
        Throwable error = null;
        Class<?> bs = null, dec = null, hd = null, sb = null;
        Method rf = null, cf = null, cs = null, df = null, gb = null, gl = null, gc = null, gf = null;
        try {
            bs = Class.forName(PKG + "Bitstream");
            dec = Class.forName(PKG + "Decoder");
            hd = Class.forName(PKG + "Header");
            sb = Class.forName(PKG + "SampleBuffer");
            Class<?> obuffer = Class.forName(PKG + "Obuffer");
            rf = bs.getMethod("readFrame");
            cf = bs.getMethod("closeFrame");
            cs = bs.getMethod("close");
            df = dec.getMethod("decodeFrame", hd, bs);
            gb = sb.getMethod("getBuffer");
            gl = sb.getMethod("getBufferLength");
            gc = sb.getMethod("getChannelCount");
            gf = sb.getMethod("getSampleFrequency");
            if (!obuffer.isAssignableFrom(sb)) {
                throw new IllegalStateException("SampleBuffer no es un Obuffer");
            }
        } catch (Throwable t) {
            error = t;
        }
        BITSTREAM = bs;
        DECODER = dec;
        HEADER = hd;
        SAMPLES = sb;
        READ_FRAME = rf;
        CLOSE_FRAME = cf;
        CLOSE_STREAM = cs;
        DECODE_FRAME = df;
        GET_BUFFER = gb;
        GET_LENGTH = gl;
        GET_CHANNELS = gc;
        GET_FREQ = gf;
        LOAD_ERROR = error;
    }

    private final Object bitstream;
    private final Object decoder;
    private byte[] pending = new byte[0];
    private int pendingPos;
    private int channels = 2;
    private int sampleRate = 44100;
    private boolean finished;
    private int badFrames;

    public static boolean available() {
        return LOAD_ERROR == null;
    }

    public Mp3Source(InputStream in) throws IOException {
        if (LOAD_ERROR != null) {
            throw new IOException("El decodificador de MP3 no está disponible: " + LOAD_ERROR);
        }
        try {
            this.bitstream = BITSTREAM.getConstructor(InputStream.class).newInstance(in);
            this.decoder = DECODER.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IOException("No se pudo iniciar el decodificador de MP3", e);
        }
        if (!fill()) {
            throw new IOException("No parece un MP3 válido");
        }
    }

    @Override
    public int channels() {
        return this.channels;
    }

    @Override
    public int sampleRate() {
        return this.sampleRate;
    }

    /** Decodifica la siguiente trama en pending. Devuelve false al llegar al final del archivo. */
    private boolean fill() throws IOException {
        while (!this.finished) {
            try {
                Object header = READ_FRAME.invoke(this.bitstream);
                if (header == null) {
                    this.finished = true;
                    return false;
                }
                Object samples;
                try {
                    samples = DECODE_FRAME.invoke(this.decoder, header, this.bitstream);
                } finally {
                    CLOSE_FRAME.invoke(this.bitstream);
                }
                short[] buffer = (short[]) GET_BUFFER.invoke(samples);
                int length = (Integer) GET_LENGTH.invoke(samples);
                this.channels = Math.max(1, Math.min(2, (Integer) GET_CHANNELS.invoke(samples)));
                this.sampleRate = (Integer) GET_FREQ.invoke(samples);
                if (length <= 0) {
                    continue;
                }
                byte[] bytes = new byte[length * 2];
                for (int i = 0; i < length; i++) {
                    bytes[2 * i] = (byte) buffer[i];
                    bytes[2 * i + 1] = (byte) (buffer[i] >> 8);
                }
                this.pending = bytes;
                this.pendingPos = 0;
                this.badFrames = 0;
                return true;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                // una trama dañada: se salta (si todo el resto del archivo está mal, se termina)
                if (++this.badFrames > 64) {
                    this.finished = true;
                    return false;
                }
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }
        return false;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            if (this.pendingPos >= this.pending.length && !fill()) {
                break;
            }
            int n = Math.min(buf.length - total, this.pending.length - this.pendingPos);
            System.arraycopy(this.pending, this.pendingPos, buf, total, n);
            this.pendingPos += n;
            total += n;
        }
        return total == 0 ? -1 : total;
    }

    @Override
    public void close() throws IOException {
        this.finished = true;
        try {
            CLOSE_STREAM.invoke(this.bitstream);
        } catch (ReflectiveOperationException ignored) {
            // nada que hacer
        }
    }
}
