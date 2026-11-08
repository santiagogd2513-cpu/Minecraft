package org.lwjgl.openal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
/** Un OpenAL de mentira con tiempo real: una fuente, buffers en cola que "suenan" a la velocidad de su frecuencia. */
public class AL10 {
    public static final int AL_BUFFERS_PROCESSED = 0x1016, AL_BUFFERS_QUEUED = 0x1015, AL_FALSE = 0, AL_NO_ERROR = 0, AL_FORMAT_MONO16 = 0x1101,
        AL_FORMAT_STEREO16 = 0x1103, AL_GAIN = 0x100A, AL_LOOPING = 0x1007, AL_PLAYING = 0x1012, AL_POSITION = 0x1004,
        AL_SOURCE_RELATIVE = 0x202, AL_SOURCE_STATE = 0x1010, AL_TRUE = 1, AL_STOPPED = 0x1014, AL_PAUSED = 0x1013;
    static final class Buf { int size; int rate; int ch; }
    static final List<Buf> bufs = new ArrayList<>();
    static final List<Integer> queue = new ArrayList<>();
    static boolean playing;
    static boolean paused;
    static double consumed;        // segundos reproducidos dentro de la cola actual
    static long lastNs;
    static int errors;
    static synchronized void advance() {
        long now = System.nanoTime();
        if (playing && !paused) {
            double dt = (now - lastNs) / 1e9;
            double total = 0;
            for (int b : queue) total += dur(bufs.get(b));
            consumed = Math.min(total, consumed + dt);
            if (consumed >= total && !queue.isEmpty()) { playing = false; }
        }
        lastNs = now;
    }
    static double dur(Buf b) { return b.size / (2.0 * b.ch) / b.rate; }
    public static synchronized int alGetError() { return 0; }
    public static synchronized void alBufferData(int buffer, int format, ByteBuffer data, int freq) {
        Buf b = bufs.get(buffer - 1);
        b.size = data.remaining(); b.rate = freq; b.ch = format == AL_FORMAT_MONO16 ? 1 : 2;
        if (b.size % (2 * b.ch) != 0) errors++;
    }
    public static synchronized void alDeleteBuffers(int buffer) { }
    public static synchronized void alDeleteSources(int source) { }
    public static synchronized int alGenBuffers() { bufs.add(new Buf()); return bufs.size(); }
    public static synchronized int alGenSources() { queue.clear(); playing = false; paused = false; consumed = 0; lastNs = System.nanoTime(); return 1; }
    public static synchronized float alGetSourcef(int source, int param) { advance(); return (float) consumed; }
    public static synchronized int alGetSourcei(int source, int param) {
        advance();
        if (param == AL_BUFFERS_QUEUED) return queue.size();
        if (param == AL_BUFFERS_PROCESSED) {
            double acc = 0; int n = 0;
            for (int b : queue) { acc += dur(bufs.get(b)); if (acc <= consumed + 1e-9) n++; else break; }
            return n;
        }
        if (param == AL_SOURCE_STATE) return playing ? (paused ? AL_PAUSED : AL_PLAYING) : AL_STOPPED;
        return 0;
    }
    public static void alSource3f(int source, int param, float a, float b, float c) { }
    public static synchronized void alSourcePause(int source) { advance(); paused = true; }
    public static synchronized void alSourcePlay(int source) { advance(); playing = true; paused = false; }
    public static synchronized void alSourceQueueBuffers(int source, int buffer) { queue.add(buffer - 1); }
    public static synchronized void alSourceStop(int source) { playing = false; }
    public static synchronized int alSourceUnqueueBuffers(int source) { advance(); int b = queue.remove(0); consumed = Math.max(0, consumed - dur(bufs.get(b))); return b + 1; }
    public static void alSourcef(int source, int param, float value) { }
    public static void alSourcei(int source, int param, int value) { }
}
