package com.mojang.blaze3d.platform;
public class NativeImage implements AutoCloseable {
    public enum Format { RGBA }
    public final int w, h;
    public final int[] px;
    public NativeImage(Format format, int w, int h, boolean zero) { this.w = w; this.h = h; this.px = new int[w * h]; }
    public void setPixelRGBA(int x, int y, int abgr) {
        if (x < 0 || y < 0 || x >= w || y >= h) throw new IllegalArgumentException("fuera: " + x + "," + y);
        px[y * w + x] = abgr;
    }
    @Override public void close() { }
}
