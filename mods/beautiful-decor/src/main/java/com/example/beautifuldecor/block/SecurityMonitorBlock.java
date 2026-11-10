package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.SecurityMonitorBlockEntity;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * V4.3.0: monitor de cámaras de seguridad (2 bloques de ancho por 1 de alto) y su versión de pared (la pantalla, plana, que se pega a una
 * pared). Se colocan como el televisor grande: el bloque donde haces clic es la mitad IZQUIERDA (vista de frente) y la otra mitad va hacia
 * tu derecha; hace falta ese espacio libre. Clic derecho: menú con la lista de cámaras para elegir cuál muestra (o apagarlo).
 * PX = 0 (izquierda, con el block entity) o 1 (derecha).
 */
public class SecurityMonitorBlock extends BaseEntityBlock {
    public static final int WIDTH = 2;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, WIDTH - 1);

    /** Monitor de mesa (facing=norte): pie y cuello en el centro y el panel arriba. */
    private static final VoxelShape[] DESK_LEFT = ShapeRotator.all(Shapes.or(ModBlock.box(0, 2, 6, 16, 16, 10), ModBlock.box(0, 0, 4, 6, 2, 12), ModBlock.box(0, 2, 6.5, 2, 4, 9.5)));
    private static final VoxelShape[] DESK_RIGHT = ShapeRotator.all(Shapes.or(ModBlock.box(0, 2, 6, 16, 16, 10), ModBlock.box(10, 0, 4, 16, 2, 12), ModBlock.box(14, 2, 6.5, 16, 4, 9.5)));
    /** Pantalla de pared (facing=norte, pared al sur): panel de 2/16 pegado a la pared. */
    private static final VoxelShape[] WALL = ShapeRotator.all(ModBlock.box(0, 0, 14, 16, 16, 16));

    private final boolean wall;

    public SecurityMonitorBlock(BlockBehaviour.Properties properties, boolean wall) {
        super(properties);
        this.wall = wall;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PX, 0));
    }

    /** true si es la pantalla de pared. */
    public boolean isWall() {
        return this.wall;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PX);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int d = state.getValue(FACING).get2DDataValue();
        if (this.wall) {
            return WALL[d];
        }
        return state.getValue(PX) == 0 ? DESK_LEFT[d] : DESK_RIGHT[d];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing;
        Direction clicked = context.getClickedFace();
        if (this.wall && clicked.getAxis().isHorizontal()) {
            facing = clicked;                                   // la pantalla mira hacia fuera de la pared en que haces clic
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        BlockPos origin = context.getClickedPos();
        Direction right = facing.getCounterClockWise();
        Level level = context.getLevel();
        for (int px = 0; px < WIDTH; px++) {
            BlockPos p = origin.relative(right, px);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction right = state.getValue(FACING).getCounterClockWise();
        level.setBlock(pos.relative(right), state.setValue(PX, 1), 3);
    }

    /** La mitad izquierda (la que tiene el block entity) a partir de cualquier parte. */
    public BlockPos originOf(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), state.getValue(PX));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PX) == 0 ? new SecurityMonitorBlockEntity(pos, state) : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            BlockPos origin = originOf(pos, state);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.ClientHooks.openCameraListScreen(origin));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Si rompes una mitad, el monitor igual suelta su ítem (una sola vez). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(PX) != 0) {
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
            for (int px = 0; px < WIDTH; px++) {
                BlockPos p = origin.relative(right, px);
                if (p.equals(pos)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(FACING) == state.getValue(FACING) && s.getValue(PX) == px) {
                    level.removeBlock(p, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
