package net.minecraft.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
public class Minecraft {
    public java.io.File gameDirectory;
    public KeyboardHandler keyboardHandler;
    public Lvl level;
    public Plr player;
    public void setScreen(Screen s) { }
    public static class Lvl extends Level { public BlockState getBlockState(BlockPos p) { return null; } }
    public static class Plr { public double distanceToSqr(Vec3 v) { return 0; } }
}
