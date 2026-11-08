package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decorative, directional black Wii Family Edition in its horizontal layout.
 * Right-click starts a short blue disc-slot animation and temporary light pulse.
 */
public class WiiConsoleBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 2);

    private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
            Shapes.box(1.0 / 16.0, 1.0 / 16.0, 3.0 / 16.0, 15.0 / 16.0, 5.25 / 16.0, 13.0 / 16.0),
            Shapes.box(2.0 / 16.0, 0.0, 3.5 / 16.0, 4.0 / 16.0, 1.0 / 16.0, 5.0 / 16.0),
            Shapes.box(12.0 / 16.0, 0.0, 3.5 / 16.0, 14.0 / 16.0, 1.0 / 16.0, 5.0 / 16.0),
            Shapes.box(2.0 / 16.0, 0.0, 11.0 / 16.0, 4.0 / 16.0, 1.0 / 16.0, 12.5 / 16.0),
            Shapes.box(12.0 / 16.0, 0.0, 11.0 / 16.0, 14.0 / 16.0, 1.0 / 16.0, 12.5 / 16.0));
    private static final VoxelShape EAST_WEST_SHAPE = Shapes.or(
            Shapes.box(3.0 / 16.0, 1.0 / 16.0, 1.0 / 16.0, 13.0 / 16.0, 5.25 / 16.0, 15.0 / 16.0),
            Shapes.box(3.5 / 16.0, 0.0, 2.0 / 16.0, 5.0 / 16.0, 1.0 / 16.0, 4.0 / 16.0),
            Shapes.box(3.5 / 16.0, 0.0, 12.0 / 16.0, 5.0 / 16.0, 1.0 / 16.0, 14.0 / 16.0),
            Shapes.box(11.0 / 16.0, 0.0, 2.0 / 16.0, 12.5 / 16.0, 1.0 / 16.0, 4.0 / 16.0),
            Shapes.box(11.0 / 16.0, 0.0, 12.0 / 16.0, 12.5 / 16.0, 1.0 / 16.0, 14.0 / 16.0));

    public WiiConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PHASE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PHASE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? NORTH_SOUTH_SHAPE
                : EAST_WEST_SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PHASE, 2), 3);
            level.scheduleTick(pos, this, 12);
            // Uses Minecraft's built-in item-frame click; no custom audio files are added.
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.55f, 0.85f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int phase = state.getValue(PHASE);
        if (phase == 2) {
            level.setBlock(pos, state.setValue(PHASE, 1), 3);
            level.scheduleTick(pos, this, 10);
        } else if (phase == 1) {
            level.setBlock(pos, state.setValue(PHASE, 0), 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.25f, 1.35f);
        }
    }
}
