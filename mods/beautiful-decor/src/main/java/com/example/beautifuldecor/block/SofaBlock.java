package com.example.beautifuldecor.block;

import com.example.beautifuldecor.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A colored sofa whose side armrests disappear where another matching sofa joins it.
 *
 * <p>Sofas join in a straight line when they face the same way, and they also join in an "L" when
 * one sofa sits directly in front of another one and faces perpendicular to it (like inner stairs).
 * The sofa at the elbow becomes a corner piece: it gets a second backrest on the side where the
 * perpendicular sofa has its back, and the perpendicular sofa loses the armrest on the side that
 * touches the elbow.
 *
 * <p>Every part of the state only depends on the {@code FACING} of directly adjacent sofas, so the
 * normal {@link #updateShape} neighbour updates are enough to keep both sofas in sync.
 */
public class SofaBlock extends Block {
    public static final BooleanProperty LEFT_CONNECTED = BooleanProperty.create("left_connected");
    public static final BooleanProperty RIGHT_CONNECTED = BooleanProperty.create("right_connected");
    /** NONE = straight sofa; LEFT / RIGHT = elbow with an extra backrest on that side. */
    public static final EnumProperty<Corner> CORNER = EnumProperty.create("corner", Corner.class);

    public enum Corner implements StringRepresentable {
        NONE("none"),
        LEFT("left"),
        RIGHT("right");

        private final String name;

        Corner(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.75, 1.0);

    public SofaBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ModBlock.FACING, Direction.NORTH)
                .setValue(LEFT_CONNECTED, false)
                .setValue(RIGHT_CONNECTED, false)
                .setValue(CORNER, Corner.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModBlock.FACING, LEFT_CONNECTED, RIGHT_CONNECTED, CORNER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(ModBlock.FACING, context.getHorizontalDirection().getOpposite());
        return updateConnections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return updateConnections(state, level, pos);
    }

    private BlockState updateConnections(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(ModBlock.FACING);
        Direction leftDir = facing.getCounterClockWise();
        Direction rightDir = facing.getClockWise();

        // Elbow: a matching sofa in front of this one that faces to our left or right. Its back is on
        // the opposite side, so that side becomes a second backrest (a wall) for this sofa.
        Corner corner = Corner.NONE;
        Direction frontFacing = matchingFacing(level.getBlockState(pos.relative(facing)));
        if (frontFacing == leftDir) {
            corner = Corner.RIGHT;
        } else if (frontFacing == rightDir) {
            corner = Corner.LEFT;
        }

        boolean left = isStraightNeighbor(level.getBlockState(pos.relative(leftDir)), facing)
                || isElbowNeighbor(level.getBlockState(pos.relative(leftDir)), leftDir);
        boolean right = isStraightNeighbor(level.getBlockState(pos.relative(rightDir)), facing)
                || isElbowNeighbor(level.getBlockState(pos.relative(rightDir)), rightDir);

        // The side that became a wall can't also be joined to another sofa.
        if (corner == Corner.LEFT) {
            left = false;
        } else if (corner == Corner.RIGHT) {
            right = false;
        }
        return state.setValue(LEFT_CONNECTED, left).setValue(RIGHT_CONNECTED, right).setValue(CORNER, corner);
    }

    /** Facing of the neighbour if it is the same sofa block, otherwise {@code null}. */
    private Direction matchingFacing(BlockState neighbor) {
        if (neighbor.getBlock() == this && neighbor.hasProperty(ModBlock.FACING)) {
            return neighbor.getValue(ModBlock.FACING);
        }
        return null;
    }

    /** Straight run: the same sofa next to us, facing the same way. */
    private boolean isStraightNeighbor(BlockState neighbor, Direction facing) {
        return matchingFacing(neighbor) == facing;
    }

    /**
     * L-shaped join seen from the straight piece: the sofa at {@code sideDir} faces back towards us,
     * so we are the sofa in front of its elbow.
     */
    private boolean isElbowNeighbor(BlockState neighbor, Direction sideDir) {
        return matchingFacing(neighbor) == sideDir.getOpposite();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            SeatEntity seat = new SeatEntity(level, pos, 0.4);
            level.addFreshEntity(seat);
            player.startRiding(seat);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
