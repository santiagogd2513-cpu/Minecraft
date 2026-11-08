package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A decorative console whose thin collision shape follows its horizontal facing. */
public class HorizontalConsoleBlock extends ModBlock {
    private final VoxelShape northSouthShape;
    private final VoxelShape eastWestShape;

    public HorizontalConsoleBlock(BlockBehaviour.Properties properties,
                                  VoxelShape northSouthShape,
                                  VoxelShape eastWestShape) {
        super(properties, northSouthShape);
        this.northSouthShape = northSouthShape;
        this.eastWestShape = eastWestShape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shapeFor(state);
    }

    private VoxelShape shapeFor(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? northSouthShape
                : eastWestShape;
    }
}
