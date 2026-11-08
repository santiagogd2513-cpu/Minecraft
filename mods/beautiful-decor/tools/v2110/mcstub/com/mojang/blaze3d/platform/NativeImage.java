package com.mojang.blaze3d.platform;
public class NativeImage implements AutoCloseable {
    public enum Format { RGBA }
    public NativeImage(Format format, int w, int h, boolean zero) { }
    public void setPixelRGBA(int x, int y, int abgr) { }
    @Override public void close() { }
}
