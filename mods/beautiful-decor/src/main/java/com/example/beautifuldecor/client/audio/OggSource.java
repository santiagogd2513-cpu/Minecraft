package com.example.beautifuldecor.client.audio;

import com.example.beautifuldecor.browser.audio.PcmSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Decodifica un OGG Vorbis completo en memoria con el STBVorbis que ya trae Minecraft (LWJGL). */
public final class OggSource implements PcmSource {
    private static final int FRAMES = 4096;

    private final ByteBuffer data;
    private final ShortBuffer pcm;
    private final int channels;
    private final int sampleRate;
    private long handle;

    public OggSource(byte[] bytes) throws IOException {
        this.data = MemoryUtil.memAlloc(bytes.length);
        this.data.put(bytes).flip();
        int ch;
        int rate;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            this.handle = STBVorbis.stb_vorbis_open_memory(this.data, error, null);
            if (this.handle == 0L) {
                MemoryUtil.memFree(this.data);
                throw new IOException("OGG no válido (" + error.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(this.handle, info);
            ch = Math.max(1, Math.min(2, info.channels()));
            rate = info.sample_rate();
        }
        this.channels = ch;
        this.sampleRate = rate;
        this.pcm = MemoryUtil.memAllocShort(FRAMES * this.channels);
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
        if (this.handle == 0L) {
            return -1;
        }
        int frameBytes = 2 * this.channels;
        int maxFrames = buf.length / frameBytes;
        int total = 0;
        while (total < maxFrames) {
            int want = Math.min(FRAMES, maxFrames - total);
            this.pcm.clear();
            this.pcm.limit(want * this.channels);
            int got = STBVorbis.stb_vorbis_get_samples_short_interleaved(this.handle, this.channels, this.pcm);
            if (got <= 0) {
                break;
            }
            int shorts = got * this.channels;
            int base = total * frameBytes;
            for (int i = 0; i < shorts; i++) {
                short v = this.pcm.get(i);
                buf[base + 2 * i] = (byte) v;
                buf[base + 2 * i + 1] = (byte) (v >> 8);
            }
            total += got;
        }
        return total == 0 ? -1 : total * frameBytes;
    }

    @Override
    public void close() {
        if (this.handle != 0L) {
            STBVorbis.stb_vorbis_close(this.handle);
            this.handle = 0L;
            MemoryUtil.memFree(this.data);
            MemoryUtil.memFree(this.pcm);
        }
    }
}
