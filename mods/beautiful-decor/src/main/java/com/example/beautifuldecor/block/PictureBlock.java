package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * Cuadro de pared (V3.0.0): 16 x 12 px, 2 px de grosor, pegado a la pared de atrás.
 * <p>
 * Se coloca sobre la cara lateral de un bloque (clic en la pared) y mira hacia afuera de esa pared. Si se hace clic
 * en el suelo o el techo, mira hacia el jugador, pero solo se pone si hay pared firme justo detrás. Si la pared
 * desaparece, el cuadro se cae como ítem. Las texturas y los modelos los genera tools/v300/paintings.py.
 */
public class PictureBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // El cuadro ocupa de y = 2 a y = 14 (12 px) y los 2 px de la pared de atrás.
    private static final VoxelShape NORTH_SHAPE = Shapes.box(0.0, 2.0 / 16.0, 14.0 / 16.0, 1.0, 14.0 / 16.0, 1.0);
    private static final VoxelShape SOUTH_SHAPE = Shapes.box(0.0, 2.0 / 16.0, 0.0, 1.0, 14.0 / 16.0, 2.0 / 16.0);
    private static final VoxelShape EAST_SHAPE = Shapes.box(0.0, 2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 1.0);
    private static final VoxelShape WEST_SHAPE = Shapes.box(14.0 / 16.0, 2.0 / 16.0, 0.0, 1.0, 14.0 / 16.0, 1.0);

    public PictureBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
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
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> Shapes.empty();
        };
    }

    /** Sin colisión: se puede pasar por delante de un cuadro sin tropezar (como un cartel). */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
