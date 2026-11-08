package net.minecraft.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
public class Minecraft {
    public Font font;
    public ClientLevel level;
    public LocalPlayer player;
    public Options options;
    public static Minecraft getInstance() { return null; }
    public TextureManager getTextureManager() { return null; }
}
