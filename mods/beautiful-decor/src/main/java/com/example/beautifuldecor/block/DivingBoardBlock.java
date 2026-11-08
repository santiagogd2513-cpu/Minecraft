package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

/** A compact pool diving board that gives the player a spring-assisted jump on right-click. */
public class DivingBoardBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Shapes.box(3.0 / 16.0, 11.0 / 16.0, 1.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0, 15.0 / 16.0),
            Shapes.box(4.0 / 16.0, 0.0, 11.0 / 16.0, 6.0 / 16.0, 11.0 / 16.0, 15.0 / 16.0),
            Shapes.box(10.0 / 16.0, 0.0, 11.0 / 16.0, 12.0 / 16.0, 11.0 / 16.0, 15.0 / 16.0),
            Shapes.box(4.0 / 16.0, 5.0 / 16.0, 11.0 / 16.0, 12.0 / 16.0, 7.0 / 16.0, 15.0 / 16.0));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Shapes.box(3.0 / 16.0, 11.0 / 16.0, 1.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0, 15.0 / 16.0),
            Shapes.box(4.0 / 16.0, 0.0, 1.0 / 16.0, 6.0 / 16.0, 11.0 / 16.0, 5.0 / 16.0),
            Shapes.box(10.0 / 16.0, 0.0, 1.0 / 16.0, 12.0 / 16.0, 11.0 / 16.0, 5.0 / 16.0),
            Shapes.box(4.0 / 16.0, 5.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0, 7.0 / 16.0, 5.0 / 16.0));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Shapes.box(1.0 / 16.0, 11.0 / 16.0, 3.0 / 16.0, 15.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0),
            Shapes.box(1.0 / 16.0, 0.0, 4.0 / 16.0, 5.0 / 16.0, 11.0 / 16.0, 6.0 / 16.0),
            Shapes.box(1.0 / 16.0, 0.0, 10.0 / 16.0, 5.0 / 16.0, 11.0 / 16.0, 12.0 / 16.0),
            Shapes.box(1.0 / 16.0, 5.0 / 16.0, 4.0 / 16.0, 5.0 / 16.0, 7.0 / 16.0, 12.0 / 16.0));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Shapes.box(1.0 / 16.0, 11.0 / 16.0, 3.0 / 16.0, 15.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0),
            Shapes.box(11.0 / 16.0, 0.0, 4.0 / 16.0, 15.0 / 16.0, 11.0 / 16.0, 6.0 / 16.0),
            Shapes.box(11.0 / 16.0, 0.0, 10.0 / 16.0, 15.0 / 16.0, 11.0 / 16.0, 12.0 / 16.0),
            Shapes.box(11.0 / 16.0, 5.0 / 16.0, 4.0 / 16.0, 15.0 / 16.0, 7.0 / 16.0, 12.0 / 16.0));

    public DivingBoardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
            default -> Shapes.block();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, 0.95, motion.z);
            player.hurtMarked = true;
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.diving_board.bounce"), true);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.15f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
