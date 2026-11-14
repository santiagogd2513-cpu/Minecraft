package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** V7.8.0: sostenedor del cruce, el poste base (se apilan los que se quieran). */
public class CrossingPostBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(6.0 / 16.0, 0.0, 6.0 / 16.0, 10.0 / 16.0, 1.0, 10.0 / 16.0),
            Shapes.box(4.0 / 16.0, 0.0, 4.0 / 16.0, 12.0 / 16.0, 2.0 / 16.0, 12.0 / 16.0));

    public CrossingPostBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
