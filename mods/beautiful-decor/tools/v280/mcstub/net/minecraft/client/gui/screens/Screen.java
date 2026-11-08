package net.minecraft.client.gui.screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
public class Screen {
    public int width, height; public Font font; protected Minecraft minecraft;
    protected Screen(Component c) {}
    protected void init() {} public void tick() {} public void removed() {} public void onClose() {} public boolean isPauseScreen() { return true; }
    public void render(GuiGraphics g, int a, int b, float c) {}
    public void setFocused(GuiEventListener l) {}
    public <T extends GuiEventListener> T addRenderableWidget(T t) { return t; }
    public boolean mouseClicked(double a, double b, int c) { return false; }
    public boolean mouseReleased(double a, double b, int c) { return false; }
    public boolean mouseDragged(double a, double b, int c, double d, double e) { return false; }
    public boolean mouseScrolled(double a, double b, double c) { return false; }
    public boolean keyPressed(int a, int b, int c) { return false; }
    public boolean keyReleased(int a, int b, int c) { return false; }
    public boolean charTyped(char c, int m) { return false; }
    public static boolean hasShiftDown() { return false; } public static boolean hasControlDown() { return false; } public static boolean hasAltDown() { return false; }
}
