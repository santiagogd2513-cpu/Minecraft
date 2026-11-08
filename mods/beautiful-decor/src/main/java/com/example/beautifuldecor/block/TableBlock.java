package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mesa clásica de cuatro patas. Las mesas se unen entre sí (como las vallas): el tablero se
 * extiende y las patas solo aparecen en las esquinas que no tienen otra mesa al lado.
 */
public class TableBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape TOP = ModBlock.box(0, 13, 0, 16, 16, 16);
    private static final VoxelShape LEG_NW = ModBlock.box(1, 0, 1, 3.5, 13, 3.5);
    private static final VoxelShape LEG_NE = ModBlock.box(12.5, 0, 1, 15, 13, 3.5);
    private static final VoxelShape LEG_SW = ModBlock.box(1, 0, 12.5, 3.5, 13, 15);
    private static final VoxelShape LEG_SE = ModBlock.box(12.5, 0, 12.5, 15, 13, 15);

    /** Índice: bit0 = norte, bit1 = este, bit2 = sur, bit3 = oeste. */
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        for (int i = 0; i < 16; i++) {
            boolean n = (i & 1) != 0, e = (i & 2) != 0, s = (i & 4) != 0, w = (i & 8) != 0;
            VoxelShape shape = TOP;
            if (!n && !w) shape = Shapes.or(shape, LEG_NW);
            if (!n && !e) shape = Shapes.or(shape, LEG_NE);
            if (!s && !w) shape = Shapes.or(shape, LEG_SW);
            if (!s && !e) shape = Shapes.or(shape, LEG_SE);
            SHAPES[i] = shape;
        }
    }

    public TableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    private static boolean connectsTo(BlockState neighbor) {
        return neighbor.getBlock() instanceof TableBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(property(direction), connectsTo(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(property(direction), connectsTo(neighborState));
        }
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0)
                | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
        return SHAPES[index];
    }
}
