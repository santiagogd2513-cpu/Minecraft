package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.DogBedBlockEntity;
import com.example.beautifuldecor.entity.SeatEntity;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Dog bed with stable wolf-sit (no SeatEntity for wolves - avoids floating limbo).
 * Players still use SeatEntity to sit.
 */
public class DogBedBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.box(0.5 / 16.0, 0.0, 0.5 / 16.0, 15.5 / 16.0, 7.0 / 16.0, 15.5 / 16.0);
    private static final double PLAYER_SIT_Y = 0.38;
    /** Height on top of cushion */
    private static final double WOLF_Y = 7.0 / 16.0;

    public DogBedBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DogBedBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DOG_BED.get(), DogBedBlock::serverTick);
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state, DogBedBlockEntity be) {
        Wolf wolf = be.findSittingWolf();
        if (wolf == null) {
            if (be.getSittingWolfId() != null) {
                be.setSittingWolfId(null);
            }
            return;
        }
        // Keep wolf on the bed surface every tick
        double x = pos.getX() + 0.5;
        double y = pos.getY() + WOLF_Y;
        double z = pos.getZ() + 0.5;
        if (wolf.distanceToSqr(x, y, z) > 0.05) {
            wolf.setPos(x, y, z);
        }
        wolf.setDeltaMovement(0, 0, 0);
        wolf.getNavigation().stop();
        if (!wolf.isOrderedToSit()) {
            wolf.setOrderedToSit(true);
        }
        if (!wolf.isNoGravity()) {
            wolf.setNoGravity(true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DogBedBlockEntity dogBed) {
                dogBed.releaseWolf();
            }
            // Also clear any leftover seat entities (players)
            if (!level.isClientSide) {
                AABB box = new AABB(pos).inflate(0.6, 1.0, 0.6);
                for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, box)) {
                    seat.releasePassengers();
                    seat.discard();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DogBedBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // Already has a sitting wolf -> wake it
        Wolf sitting = be.findSittingWolf();
        if (sitting != null) {
            be.releaseWolf();
            level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.5f, 1.2f);
            return InteractionResult.CONSUME;
        }

        // Find nearby owned wolf to put on bed
        AABB bedBox = new AABB(pos).inflate(0.75, 1.0, 0.75);
        List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class, bedBox,
                w -> w.isAlive() && w.isTame() && w.isOwnedBy(player) && !w.isPassenger());

        if (!wolves.isEmpty()) {
            Wolf wolf = wolves.get(0);
            // Dismount anything, clear old state
            if (wolf.isPassenger()) {
                wolf.stopRiding();
            }
            wolf.getNavigation().stop();
            wolf.setDeltaMovement(0, 0, 0);
            wolf.setPos(pos.getX() + 0.5, pos.getY() + WOLF_Y, pos.getZ() + 0.5);
            wolf.setOrderedToSit(true);
            wolf.setNoGravity(true);

            float yaw = state.getValue(FACING).toYRot();
            wolf.setYRot(yaw);
            wolf.setYBodyRot(yaw);
            wolf.setYHeadRot(yaw);

            be.setSittingWolfId(wolf.getUUID());
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
            return InteractionResult.CONSUME;
        }

        // Player sits (SeatEntity only for players)
        if (!player.isShiftKeyDown()) {
            SeatEntity seat = new SeatEntity(level, pos, PLAYER_SIT_Y);
            level.addFreshEntity(seat);
            player.startRiding(seat);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }
}
