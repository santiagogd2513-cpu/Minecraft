package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lámpara Bugerati que se enciende y se apaga con clic derecho (y con los interruptores de pared, igual que
 * las demás luces). La forma se define mirando al norte (la pared queda hacia +Z) y gira con el bloque.
 */
public class BugeratiLampBlock extends ToggleBlock {
    private final VoxelShape[] shapes;

    public BugeratiLampBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
        super(properties, northShape, true);
        this.shapes = ShapeRotator.all(northShape);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }
}
