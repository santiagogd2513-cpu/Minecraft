package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.TelevisionBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * Televisor grande hecho de varios bloques (ancho x 2 de alto). Se coloca igual que uno normal:
 * el bloque donde haces clic es la esquina inferior IZQUIERDA (vista desde el frente) y el resto
 * se extiende hacia tu derecha y hacia arriba. Solo esa esquina guarda el video (block entity).
 * PX = columna (0 = izquierda), PY = fila (0 = abajo).
 */
public class BigTelevisionBlock extends TelevisionBlock {
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, 3);
    public static final IntegerProperty PY = IntegerProperty.create("py", 0, 1);

    private static final VoxelShape[] LOWER = ShapeRotator.all(Shapes.or(
            ModBlock.box(0, 0, 5, 16, 4, 10), ModBlock.box(0, 4, 7, 16, 16, 8.4)));
    private static final VoxelShape[] UPPER = ShapeRotator.all(ModBlock.box(0, 0, 7, 16, 16, 8.4));

    private final int width;
    private final int height;

    public BigTelevisionBlock(BlockBehaviour.Properties properties, int width, int height) {
        super(properties, Shapes.block());
        this.width = width;
        this.height = height;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PX, PY);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape[] set = state.getValue(PY) == 0 ? LOWER : UPPER;
        return set[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction right = facing.getCounterClockWise();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                BlockPos p = origin.relative(right, px).above(py);
                if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(ON, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction right = state.getValue(FACING).getCounterClockWise();
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                if (px == 0 && py == 0) {
                    continue;
                }
                level.setBlock(pos.relative(right, px).above(py),
                        state.setValue(PX, px).setValue(PY, py), 3);
            }
        }
    }

    /** Cuántos bloques de ancho y de alto mide el televisor. */
    public int widthBlocks() {
        return this.width;
    }

    public int heightBlocks() {
        return this.height;
    }

    /** Esquina inferior izquierda (la que tiene el block entity) a partir de cualquier parte. */
    public BlockPos originOf(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), state.getValue(PX)).below(state.getValue(PY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PX) == 0 && state.getValue(PY) == 0) {
            return new TelevisionBlockEntity(pos, state);
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            final BlockPos origin = originOf(pos, state);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.example.beautifuldecor.client.ClientHooks.openTelevisionScreen(origin));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Enciende o apaga la pantalla en todas las partes del televisor. */
    public void setOnAllParts(Level level, BlockPos originPos, BlockState originState, boolean on) {
        Direction right = originState.getValue(FACING).getCounterClockWise();
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                BlockPos p = originPos.relative(right, px).above(py);
                BlockState s = level.getBlockState(p);
                if (s.is(this)) {
                    level.setBlock(p, s.setValue(ON, on), 3);
                }
            }
        }
    }

    /** Si rompes una parte que no es la esquina, el televisor igual suelta su ítem (una sola vez). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && (state.getValue(PX) != 0 || state.getValue(PY) != 0)) {
            BlockPos origin = originOf(pos, state);
            BlockState originState = level.getBlockState(origin);
            if (originState.is(this)) {
                Block.dropResources(originState, level, origin, level.getBlockEntity(origin), player,
                        player.getMainHandItem());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos origin = originOf(pos, state);
            Direction right = state.getValue(FACING).getCounterClockWise();
            for (int px = 0; px < width; px++) {
                for (int py = 0; py < height; py++) {
                    BlockPos p = origin.relative(right, px).above(py);
                    if (p.equals(pos)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.is(this) && s.getValue(FACING) == state.getValue(FACING)
                            && s.getValue(PX) == px && s.getValue(PY) == py) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
