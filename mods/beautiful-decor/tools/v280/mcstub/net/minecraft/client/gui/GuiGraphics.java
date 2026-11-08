package net.minecraft.client.gui;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
public class GuiGraphics {
    public void fill(int a, int b, int c, int d, int col) {}
    public int drawString(Font f, String s, int x, int y, int c, boolean sh) { return 0; }
    public int drawString(Font f, Component s, int x, int y, int c, boolean sh) { return 0; }
    public int drawString(Font f, String s, int x, int y, int c) { return 0; }
    public void enableScissor(int a, int b, int c, int d) {}
    public void disableScissor() {}
    public PoseStack pose() { return null; }
    public void renderTooltip(Font f, Component c, int x, int y) {}
}
