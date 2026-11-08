package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
public class BlockState {
    public Block getBlock() { return null; }
    public <T extends Comparable<T>> T getValue(Property<T> property) { return null; }
}
