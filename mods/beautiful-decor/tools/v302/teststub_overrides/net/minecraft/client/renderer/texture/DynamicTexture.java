package net.minecraft.client.renderer.texture;
import com.mojang.blaze3d.platform.NativeImage;
public class DynamicTexture extends AbstractTexture {
    private final NativeImage img;
    public static volatile int uploads;
    public static volatile int[] last;
    public static volatile int lastW, lastH;
    public DynamicTexture(NativeImage image) { this.img = image; }
    public NativeImage getPixels() { return img; }
    public void upload() { uploads++; last = img.px.clone(); lastW = img.w; lastH = img.h; }
}
