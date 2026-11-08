package net.minecraft.client.gui;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
public class Font {
    public enum DisplayMode { NORMAL, SEE_THROUGH, POLYGON_OFFSET }
    public int width(String text) { return 0; }
    public int drawInBatch(String text, float x, float y, int color, boolean shadow, Matrix4f pose, MultiBufferSource buffer,
                           DisplayMode mode, int background, int light) { return 0; }
}
