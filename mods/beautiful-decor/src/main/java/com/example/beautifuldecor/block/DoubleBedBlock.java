package com.example.beautifuldecor.block;

import com.example.beautifuldecor.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Cama matrimonial de 2 x 2 bloques. Se coloca igual que una cama normal: el bloque donde haces clic
 * son los PIES (esquina izquierda, mirando hacia donde miras) y la cama se extiende hacia delante
 * (cabecera) y hacia tu derecha.
 * FACING = hacia dónde está la cabecera. PX: 0 = izquierda, 1 = derecha. PZ: 0 = pies, 1 = cabecera.
 * Solo la parte (0,0) suelta el ítem. Clic derecho (sin agacharte) = sentarte en la cama.
 */
public class DoubleBedBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, 1);
    public static final IntegerProperty PZ = IntegerProperty.create("pz", 0, 1);

    // Formas pensadas para facing = north (la cabecera queda al norte, z = 0).
    private static final VoxelShape[] FOOT_SELECT = ShapeRotator.all(ModBlock.box(0, 0, 0, 16, 10, 16));
    private static final VoxelShape[] HEAD_SELECT = ShapeRotator.all(Shapes.or(
            ModBlock.box(0, 0, 0, 16, 10, 16), ModBlock.box(0, 10, 0, 16, 16, 2)));
    // La colisión es 1 px más baja (9/16 < 0.6) para poder subirse caminando sin saltar.
    private static final VoxelShape[] FOOT_COLLIDE = ShapeRotator.all(ModBlock.box(0, 0, 0, 16, 9, 16));
    private static final VoxelShape[] HEAD_COLLIDE = ShapeRotator.all(Shapes.or(
            ModBlock.box(0, 0, 0, 16, 9, 16), ModBlock.box(0, 9, 0, 16, 15, 2)));

    public DoubleBedBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PX, 0).setValue(PZ, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PX, PZ);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[] set = state.getValue(PZ) == 0 ? FOOT_SELECT : HEAD_SELECT;
        return set[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[] set = state.getValue(PZ) == 0 ? FOOT_COLLIDE : HEAD_COLLIDE;
        return set[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Posición de la parte (px, pz) a partir de la esquina de los pies (parte 0,0). */
    private static BlockPos partPos(BlockPos origin, Direction facing, int px, int pz) {
        return origin.relative(facing.getClockWise(), px).relative(facing, pz);
    }

    /** Esquina de los pies (la parte que suelta el ítem) a partir de cualquier parte. */
    public BlockPos originOf(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getCounterClockWise(), state.getValue(PX))
                .relative(facing.getOpposite(), state.getValue(PZ));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int px = 0; px < 2; px++) {
            for (int pz = 0; pz < 2; pz++) {
                BlockPos p = partPos(origin, facing, px, pz);
                if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PX, 0).setValue(PZ, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int px = 0; px < 2; px++) {
            for (int pz = 0; pz < 2; pz++) {
                if (px == 0 && pz == 0) {
                    continue;
                }
                level.setBlock(partPos(pos, facing, px, pz),
                        state.setValue(PX, px).setValue(PZ, pz), 3);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty()) {
                SeatEntity seat = new SeatEntity(level, pos, 0.5);
                level.addFreshEntity(seat);
                player.startRiding(seat);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Si rompes cualquier parte en supervivencia, la cama suelta su ítem una sola vez. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && (state.getValue(PX) != 0 || state.getValue(PZ) != 0)) {
            BlockPos origin = originOf(pos, state);
            BlockState originState = level.getBlockState(origin);
            if (originState.is(this)) {
                Block.dropResources(originState, level, origin, null, player, player.getMainHandItem());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Al quitar una parte se quitan las otras tres (sin soltar ítems extra). */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            Direction facing = state.getValue(FACING);
            BlockPos origin = originOf(pos, state);
            for (int px = 0; px < 2; px++) {
                for (int pz = 0; pz < 2; pz++) {
                    BlockPos p = partPos(origin, facing, px, pz);
                    if (p.equals(pos)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.is(this) && s.getValue(FACING) == facing
                            && s.getValue(PX) == px && s.getValue(PZ) == pz) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
