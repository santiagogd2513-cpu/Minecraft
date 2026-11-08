package org.lwjgl.openal;
import java.nio.ByteBuffer;
public class AL10 {
    public static final int AL_BUFFERS_PROCESSED = 0, AL_BUFFERS_QUEUED = 1, AL_FALSE = 0, AL_NO_ERROR = 0, AL_FORMAT_MONO16 = 2, AL_FORMAT_STEREO16 = 3,
        AL_GAIN = 4, AL_LOOPING = 5, AL_PLAYING = 6, AL_POSITION = 7, AL_SOURCE_RELATIVE = 8, AL_SOURCE_STATE = 9, AL_TRUE = 1;
    public static void alBufferData(int buffer, int format, ByteBuffer data, int freq) { }
    public static void alDeleteBuffers(int buffer) { }
    public static void alDeleteSources(int source) { }
    public static int alGenBuffers() { return 0; }
    public static int alGetError() { return 0; }
    public static int alGenSources() { return 0; }
    public static float alGetSourcef(int source, int param) { return 0f; }
    public static int alGetSourcei(int source, int param) { return 0; }
    public static void alSource3f(int source, int param, float a, float b, float c) { }
    public static void alSourcePause(int source) { }
    public static void alSourcePlay(int source) { }
    public static void alSourceQueueBuffers(int source, int buffer) { }
    public static void alSourceStop(int source) { }
    public static int alSourceUnqueueBuffers(int source) { return 0; }
    public static void alSourcef(int source, int param, float value) { }
    public static void alSourcei(int source, int param, int value) { }
}
