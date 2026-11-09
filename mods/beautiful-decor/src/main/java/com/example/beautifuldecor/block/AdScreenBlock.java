package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.AdScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * V4.1.0: pantalla de anuncios (cuadrada, de 4x4, 8x8 o 12x12 bloques) hecha de muchos bloques delgados pegados a la pared.
 * Se coloca como el televisor grande: el bloque donde haces clic es la esquina inferior IZQUIERDA (vista de frente) y el resto
 * se extiende hacia tu derecha y hacia arriba; hace falta que todo ese espacio esté libre. Solo esa esquina guarda datos
 * (block entity). PX = columna (0 = izquierda), PY = fila (0 = abajo).
 * <p>
 * Clic derecho: enciende o apaga la pantalla. Encendida, pasa los anuncios de todas las marcas del mod, con imagen y sonido.
 */
public class AdScreenBlock extends BaseEntityBlock {
    public static final int MAX_SIZE = 12;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, MAX_SIZE - 1);
    public static final IntegerProperty PY = IntegerProperty.create("py", 0, MAX_SIZE - 1);

    /** Panel de 3/16 pegado a la pared (para facing=north, la pared queda al sur). */
    private static final VoxelShape[] SHAPES = ShapeRotator.all(ModBlock.box(0, 0, 13, 16, 16, 16));

    private final int size;

    public AdScreenBlock(BlockBehaviour.Properties properties, int size) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PX, 0).setValue(PY, 0));
    }

    /** Cuántos bloques mide de lado la pantalla. */
    public int sizeBlocks() {
        return this.size;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PX, PY);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction right = facing.getCounterClockWise();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                BlockPos p = origin.relative(right, px).above(py);
                if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction right = state.getValue(FACING).getCounterClockWise();
        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                if (px == 0 && py == 0) {
                    continue;
                }
                level.setBlock(pos.relative(right, px).above(py), state.setValue(PX, px).setValue(PY, py), 3);
            }
        }
    }

    /** Esquina inferior izquierda (la que tiene el block entity) a partir de cualquier parte. */
    public BlockPos originOf(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), state.getValue(PX)).below(state.getValue(PY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PX) == 0 && state.getValue(PY) == 0) {
            return new AdScreenBlockEntity(pos, state);
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(originOf(pos, state));
            if (be instanceof AdScreenBlockEntity screen) {
                boolean on = screen.toggle();
                player.displayClientMessage(Component.translatable(on ? "message.beautifuldecor.ad_screen.on"
                        : "message.beautifuldecor.ad_screen.off"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Si rompes una parte que no es la esquina, la pantalla igual suelta su ítem (una sola vez). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && (state.getValue(PX) != 0 || state.getValue(PY) != 0)) {
            BlockPos origin = originOf(pos, state);
            BlockState originState = level.getBlockState(origin);
            if (originState.is(this)) {
                Block.dropResources(originState, level, origin, level.getBlockEntity(origin), player, player.getMainHandItem());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos origin = originOf(pos, state);
            Direction right = state.getValue(FACING).getCounterClockWise();
            for (int px = 0; px < size; px++) {
                for (int py = 0; py < size; py++) {
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
