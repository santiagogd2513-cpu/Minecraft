package com.example.beautifuldecor.megaphone.client;

import com.example.beautifuldecor.megaphone.PaDsp;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * V4.0.0: captura del micrófono con la extensión de captura de OpenAL (la misma librería de audio que usa Minecraft, así que funciona igual
 * en PC, Mac, Linux y en los launchers de Android que incluyen OpenAL).
 */
final class OpenAlMicBackend implements MicBackend {
    private static final int MAX_READ = PaDsp.FRAME * 4;

    private long device;
    private ByteBuffer bytes;
    private ShortBuffer shorts;

    @Override
    public String name() {
        return "OpenAL";
    }

    @Override
    public void open() {
        long dev = ALC11.alcCaptureOpenDevice((ByteBuffer) null, PaDsp.RATE, AL10.AL_FORMAT_MONO16, PaDsp.RATE);
        if (dev == 0L) {
            throw new IllegalStateException("OpenAL no encontró un micrófono (error " + ALC10.alcGetError(0L) + ")");
        }
        this.device = dev;
        this.bytes = BufferUtils.createByteBuffer(MAX_READ * 2).order(ByteOrder.nativeOrder());
        this.shorts = this.bytes.asShortBuffer();
        ALC11.alcCaptureStart(dev);
    }

    @Override
    public int read(short[] out, int max) {
        if (this.device == 0L) {
            return 0;
        }
        int available = ALC10.alcGetInteger(this.device, ALC11.ALC_CAPTURE_SAMPLES);
        if (available <= 0) {
            return 0;
        }
        int n = Math.min(available, Math.min(max, MAX_READ));
        this.bytes.clear();
        ALC11.alcCaptureSamples(this.device, this.bytes, n);
        this.shorts.clear();
        this.shorts.get(out, 0, n);
        return n;
    }

    @Override
    public void close() {
        long dev = this.device;
        this.device = 0L;
        if (dev != 0L) {
            try {
                ALC11.alcCaptureStop(dev);
            } finally {
                ALC11.alcCaptureCloseDevice(dev);
            }
        }
    }
}
