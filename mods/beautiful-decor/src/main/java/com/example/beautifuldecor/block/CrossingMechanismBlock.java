package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.CrossingMechanismBlockEntity;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.init.ModBlocks;
import com.example.beautifuldecor.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * V7.8.0: mecanismo de la barrera. Va sobre el sostenedor; la barrera se engancha a su lado ({@code facing}) y él la sube y la baja.
 */
public class CrossingMechanismBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0, 13.0 / 16.0, 14.0 / 16.0, 13.0 / 16.0);

    public CrossingMechanismBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrossingMechanismBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CROSSING_MECHANISM.get(), CrossingMechanismBlockEntity::tick);
    }

    /** ¿Hay una barrera completa enganchada en el lado {@code f} del mecanismo? */
    public static boolean hasArm(Level level, BlockPos pos, Direction f) {
        BlockState b = level.getBlockState(pos.relative(f, 1));
        return b.is(ModBlocks.CROSSING_BARRIER.get()) && b.getValue(CrossingBarrierBlock.FACING) == f;
    }

    /** Pone la posición del brazo (3 abajo ... 0 arriba) en las tres partes de la barrera. */
    public static void setArm(Level level, BlockPos pos, int arm) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof CrossingMechanismBlock)) {
            return;
        }
        Direction f = st.getValue(FACING);
        for (int i = 1; i <= 3; i++) {
            BlockPos p = pos.relative(f, i);
            BlockState b = level.getBlockState(p);
            if (b.is(ModBlocks.CROSSING_BARRIER.get()) && b.getValue(CrossingBarrierBlock.FACING) == f && b.getValue(CrossingBarrierBlock.ARM) != arm) {
                level.setBlock(p, b.setValue(CrossingBarrierBlock.ARM, arm), 3);
            }
        }
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.25f, 1.6f);
    }

    /** Al quitar el mecanismo, la barrera enganchada se suelta como objeto. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            Direction f = state.getValue(FACING);
            if (hasArm(level, pos, f)) {
                for (int i = 1; i <= 3; i++) {
                    BlockPos p = pos.relative(f, i);
                    BlockState b = level.getBlockState(p);
                    if (b.is(ModBlocks.CROSSING_BARRIER.get()) && b.getValue(CrossingBarrierBlock.FACING) == f) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                Block.popResource(level, pos, new ItemStack(ModItems.CROSSING_BARRIER.get()));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
