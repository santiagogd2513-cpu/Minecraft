package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Functional concrete ramp. Stepped collision so players walk up smoothly.
 * Low end faces the player when placed (you walk toward the high end).
 */
public class ConcreteRampBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 8 steps from z=0 (low) to z=16 (high) when facing north... 
    // Actually: when facing = direction of the HIGH side (like stairs).
    // getOpposite of look direction = front of block faces player = LOW side toward player.
    private static final VoxelShape SHAPE_NORTH = makeRampShape(Direction.NORTH);
    private static final VoxelShape SHAPE_SOUTH = makeRampShape(Direction.SOUTH);
    private static final VoxelShape SHAPE_EAST  = makeRampShape(Direction.EAST);
    private static final VoxelShape SHAPE_WEST  = makeRampShape(Direction.WEST);

    public ConcreteRampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape makeRampShape(Direction facing) {
        // 8 horizontal slices, height increases toward facing direction
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < 8; i++) {
            double h = (i + 1) / 8.0; // 2/16 .. 16/16
            double a = i / 8.0;
            double b = (i + 1) / 8.0;
            VoxelShape step;
            switch (facing) {
                case NORTH: // high toward -Z (north)
                    step = Shapes.box(0, 0, 1.0 - b, 1, h, 1.0 - a);
                    break;
                case SOUTH: // high toward +Z
                    step = Shapes.box(0, 0, a, 1, h, b);
                    break;
                case WEST:  // high toward -X
                    step = Shapes.box(1.0 - b, 0, 0, 1.0 - a, h, 1);
                    break;
                case EAST:  // high toward +X
                default:
                    step = Shapes.box(a, 0, 0, b, h, 1);
                    break;
            }
            shape = Shapes.or(shape, step);
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // High side points in the direction the player is looking (like vanilla stairs)
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    private static VoxelShape shapeFor(Direction facing) {
        switch (facing) {
            case SOUTH: return SHAPE_SOUTH;
            case EAST:  return SHAPE_EAST;
            case WEST:  return SHAPE_WEST;
            case NORTH:
            default:    return SHAPE_NORTH;
        }
    }
}
