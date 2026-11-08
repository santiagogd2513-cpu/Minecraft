package com.example.beautifuldecor.block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
public class TelevisionBlock extends Block {
    public static final DirectionProperty FACING = new DirectionProperty();
    public static final BooleanProperty ON = new BooleanProperty();
}
