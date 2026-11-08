package org.lwjgl.system;
import java.nio.ByteBuffer;
public class MemoryUtil {
    public static ByteBuffer memAlloc(int size) { return ByteBuffer.allocateDirect(size); }
    public static void memFree(java.nio.Buffer b) { }
}
