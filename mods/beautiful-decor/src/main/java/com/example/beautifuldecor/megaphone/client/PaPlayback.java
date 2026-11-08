package com.example.beautifuldecor.megaphone.client;

import com.example.beautifuldecor.megaphone.ImaAdpcm;
import com.example.beautifuldecor.megaphone.PaDsp;
import com.example.beautifuldecor.network.SpeakerAudioPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * V4.0.0: reproduce la voz de la megafonía con OpenAL, en el mismo contexto de audio que usa Minecraft. Hay una fuente de sonido por cada
 * micrófono que está hablando; se pone en la bocina más cercana a ti y suena más o menos según la distancia.
 * Cada bloque llega ya decodificable por separado; se encolan en la fuente y se arranca cuando hay unos cuantos (colchón contra el lag).
 */
public final class PaPlayback {
    private static final int BUFFERS = 16;
    private static final int PREBUFFER = 3;                  // 120 ms de colchón antes de arrancar
    private static final int MAX_QUEUED = 10;                // 400 ms: más que eso se descarta para no acumular retraso
    private static final long IDLE_MS = 2500L;

    private static final class Stream {
        final long context;
        final int source;
        final int[] buffers;
        final ArrayDeque<Integer> free = new ArrayDeque<>();
        int queued;
        long lastAudioMs;

        Stream(long context, int source, int[] buffers) {
            this.context = context;
            this.source = source;
            this.buffers = buffers;
            for (int buffer : buffers) {
                this.free.add(buffer);
            }
        }
    }

    private static final Map<Long, Stream> STREAMS = new HashMap<>();
    private static ShortBuffer scratch = BufferUtils.createShortBuffer(PaDsp.FRAME * 2);

    private PaPlayback() {}

    /** Reproduce un bloque de voz. {@code listenerPos}: dónde estás (tu cabeza). */
    public static void play(SpeakerAudioPacket packet, Vec3 listenerPos, float volume) {
        if (volume <= 0.0f) {
            return;
        }
        long context = ALC10.alcGetCurrentContext();
        if (context == 0L) {
            return;                                           // el sonido del juego no está activo
        }
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (long encoded : packet.speakers) {
            BlockPos speaker = BlockPos.of(encoded);
            double distance = listenerPos.distanceTo(Vec3.atCenterOf(speaker));
            if (distance < best) {
                best = distance;
                nearest = speaker;
            }
        }
        if (nearest == null) {
            return;
        }
        float gain = PaDsp.distanceGain(best) * volume;
        if (gain <= 0.002f) {
            return;
        }
        short[] pcm = ImaAdpcm.decode(packet.data);
        if (pcm.length == 0) {
            return;
        }
        Stream stream = streamFor(packet.mic.asLong(), context);
        if (stream == null) {
            return;
        }
        recycle(stream);
        stream.lastAudioMs = System.currentTimeMillis();
        if (stream.queued >= MAX_QUEUED || stream.free.isEmpty()) {
            return;
        }
        if (scratch.capacity() < pcm.length) {
            scratch = BufferUtils.createShortBuffer(pcm.length);
        }
        scratch.clear();
        scratch.put(pcm);
        scratch.flip();
        int buffer = stream.free.pollFirst();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, scratch, PaDsp.RATE);
        AL10.alSourceQueueBuffers(stream.source, buffer);
        stream.queued++;

        Vec3 at = Vec3.atCenterOf(nearest);
        AL10.alSource3f(stream.source, AL10.AL_POSITION, (float) at.x, (float) at.y, (float) at.z);
        AL10.alSourcef(stream.source, AL10.AL_GAIN, Math.min(1.0f, gain));
        if (AL10.alGetSourcei(stream.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && stream.queued >= PREBUFFER) {
            AL10.alSourcePlay(stream.source);
        }
    }

    /** El volumen de los bloques que tienes puesto en las opciones de sonido (el volumen general ya lo aplica el propio juego). */
    public static float blocksVolume() {
        return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.BLOCKS);
    }

    private static Stream streamFor(long key, long context) {
        Stream stream = STREAMS.get(key);
        if (stream != null && stream.context != context) {
            STREAMS.remove(key);                              // el sonido del juego se reinició: esas fuentes ya no existen
            stream = null;
        }
        if (stream != null) {
            return stream;
        }
        AL10.alGetError();
        int source = AL10.alGenSources();
        if (source == 0 || AL10.alGetError() != AL10.AL_NO_ERROR) {
            return null;
        }
        int[] buffers = new int[BUFFERS];
        for (int i = 0; i < BUFFERS; i++) {
            buffers[i] = AL10.alGenBuffers();
            if (buffers[i] == 0 || AL10.alGetError() != AL10.AL_NO_ERROR) {
                for (int j = 0; j < i; j++) {
                    AL10.alDeleteBuffers(buffers[j]);
                }
                AL10.alDeleteSources(source);
                return null;
            }
        }
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);   // la distancia la calculamos nosotros
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
        stream = new Stream(context, source, buffers);
        STREAMS.put(key, stream);
        return stream;
    }

    /** Devuelve al montón los buffers que ya terminaron de sonar. */
    private static void recycle(Stream stream) {
        int processed = AL10.alGetSourcei(stream.source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(stream.source);
            stream.free.addLast(buffer);
            stream.queued = Math.max(0, stream.queued - 1);
        }
    }

    private static void release(Stream stream) {
        if (stream.context == ALC10.alcGetCurrentContext()) {
            AL10.alSourceStop(stream.source);
            AL10.alSourcei(stream.source, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(stream.source);
            for (int buffer : stream.buffers) {
                AL10.alDeleteBuffers(buffer);
            }
        }
    }

    /** Cada tick: limpia las fuentes que llevan rato sin voz. */
    public static void tick() {
        if (STREAMS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long context = ALC10.alcGetCurrentContext();
        for (Iterator<Stream> it = STREAMS.values().iterator(); it.hasNext(); ) {
            Stream stream = it.next();
            if (stream.context != context) {
                it.remove();
                continue;
            }
            recycle(stream);
            if (now - stream.lastAudioMs > IDLE_MS && AL10.alGetSourcei(stream.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                release(stream);
                it.remove();
            }
        }
    }

    /** Al salir del mundo: se detiene y se libera todo. */
    public static void shutdown() {
        for (Stream stream : STREAMS.values()) {
            release(stream);
        }
        STREAMS.clear();
    }
}
