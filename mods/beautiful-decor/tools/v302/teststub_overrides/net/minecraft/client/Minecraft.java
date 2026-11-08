package net.minecraft.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
public class Minecraft {
    public Font font;
    public ClientLevel level;
    public LocalPlayer player;
    public Options options = new Options();
    private static final Minecraft I = new Minecraft();
    private final TextureManager tm = new TextureManager();
    public static Minecraft getInstance() { return I; }
    public TextureManager getTextureManager() { return tm; }
}
