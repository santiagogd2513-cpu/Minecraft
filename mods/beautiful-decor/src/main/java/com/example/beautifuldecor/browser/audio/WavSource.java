package com.example.beautifuldecor.browser.audio;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Lector de archivos WAV (PCM de 8, 16, 24 o 32 bits, o float de 32 bits; mono o estéreo) que entrega PCM de 16 bits. */
public final class WavSource implements PcmSource {
    private final InputStream in;
    private int channels;
    private int sampleRate;
    private int bits;
    private boolean floating;
    private long remaining = -1;
    private byte[] raw = new byte[0];

    public WavSource(InputStream stream) throws IOException {
        this.in = stream;
        DataInputStream data = new DataInputStream(stream);
        byte[] id = new byte[4];
        data.readFully(id);
        if (!"RIFF".equals(new String(id, "US-ASCII"))) {
            throw new IOException("No es un WAV (falta RIFF)");
        }
        data.readInt();
        data.readFully(id);
        if (!"WAVE".equals(new String(id, "US-ASCII"))) {
            throw new IOException("No es un WAV (falta WAVE)");
        }
        boolean haveFormat = false;
        while (true) {
            try {
                data.readFully(id);
            } catch (EOFException e) {
                throw new IOException("El WAV no tiene datos", e);
            }
            long size = Integer.reverseBytes(data.readInt()) & 0xFFFFFFFFL;
            String name = new String(id, "US-ASCII");
            if (name.equals("fmt ")) {
                byte[] fmt = new byte[(int) Math.min(size, 64)];
                data.readFully(fmt);
                skip(data, size - fmt.length + (size & 1));
                int tag = le16(fmt, 0);
                this.channels = le16(fmt, 2);
                this.sampleRate = le16(fmt, 4) | (le16(fmt, 6) << 16);
                this.bits = le16(fmt, 14);
                if (tag == 0xFFFE && fmt.length >= 26) {
                    tag = le16(fmt, 24);
                }
                this.floating = tag == 3;
                if (tag != 1 && tag != 3) {
                    throw new IOException("WAV comprimido (solo se admite PCM)");
                }
                if (this.channels < 1 || this.channels > 2) {
                    throw new IOException("WAV con " + this.channels + " canales (solo mono o estéreo)");
                }
                if (this.bits != 8 && this.bits != 16 && this.bits != 24 && this.bits != 32) {
                    throw new IOException("WAV de " + this.bits + " bits no admitido");
                }
                haveFormat = true;
            } else if (name.equals("data")) {
                if (!haveFormat) {
                    throw new IOException("WAV con los datos antes del formato");
                }
                this.remaining = size == 0 || size == 0xFFFFFFFFL ? Long.MAX_VALUE : size;
                return;
            } else {
                skip(data, size + (size & 1));
            }
        }
    }

    private static int le16(byte[] b, int i) {
        return (b[i] & 255) | ((b[i + 1] & 255) << 8);
    }

    private static void skip(DataInputStream in, long n) throws IOException {
        while (n > 0) {
            long k = in.skip(n);
            if (k <= 0) {
                if (in.read() < 0) {
                    return;
                }
                k = 1;
            }
            n -= k;
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

    @Override
    public int read(byte[] buf) throws IOException {
        if (this.remaining <= 0) {
            return -1;
        }
        int bytesPer = this.bits / 8;
        int samples = buf.length / 2;
        int want = (int) Math.min((long) samples * bytesPer, this.remaining);
        if (this.raw.length < want) {
            this.raw = new byte[want];
        }
        int got = 0;
        while (got < want) {
            int n = this.in.read(this.raw, got, want - got);
            if (n < 0) {
                break;
            }
            got += n;
        }
        got -= got % bytesPer;
        if (got <= 0) {
            this.remaining = 0;
            return -1;
        }
        if (this.remaining != Long.MAX_VALUE) {
            this.remaining -= got;
        }
        int count = got / bytesPer;
        for (int i = 0; i < count; i++) {
            int base = i * bytesPer;
            int value;
            switch (this.bits) {
                case 8 -> value = ((this.raw[base] & 255) - 128) << 8;
                case 16 -> value = (short) le16(this.raw, base);
                case 24 -> value = (short) le16(this.raw, base + 1);
                default -> {
                    if (this.floating) {
                        float f = Float.intBitsToFloat((this.raw[base] & 255) | ((this.raw[base + 1] & 255) << 8)
                                | ((this.raw[base + 2] & 255) << 16) | ((this.raw[base + 3] & 255) << 24));
                        value = Math.round(Math.max(-1f, Math.min(1f, f)) * 32767f);
                    } else {
                        value = (short) le16(this.raw, base + 2);
                    }
                }
            }
            buf[2 * i] = (byte) value;
            buf[2 * i + 1] = (byte) (value >> 8);
        }
        return count * 2;
    }

    @Override
    public void close() throws IOException {
        this.in.close();
    }
}
