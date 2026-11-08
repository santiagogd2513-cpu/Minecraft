package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.ElevatorDoorBlockEntity;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.item.ElevatorLinkBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Puerta de ascensor (V2.2.0): una puerta corrediza de un bloque (se ponen varias lado a lado y una encima de otra para
 * cubrir la entrada). Se vincula a un controlador con la mano, igual que la pantalla; cuando la cabina llega a ese piso
 * se abre sola y cuando se va se cierra. Abierta, las hojas se esconden a los lados y se puede pasar.
 * <p>
 * V2.3.0: mide dos bloques de alto, como una puerta normal del juego (mitad de abajo y mitad de arriba). Solo la mitad de
 * abajo tiene el vínculo y consulta al controlador; la de arriba copia su estado y las dos se rompen juntas.
 * <p>
 * V2.2.1: si se pone junto a otra puerta del mismo piso (mismo controlador) y la misma orientación, las dos forman una
 * puerta doble: la de la izquierda dibuja una sola hoja grande, la de la derecha la otra, y al abrirse cada hoja se
 * esconde hacia su lado. Si una de las dos se rompe, la otra vuelve a ser una puerta sola.
 */
public class ElevatorDoorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** SINGLE = puerta sola (dos hojas); LEFT / RIGHT = mitad izquierda o derecha de una puerta doble, mirando la puerta de frente. */
    public static final EnumProperty<DoorPart> PART = EnumProperty.create("part", DoorPart.class);

    /** Mitad de la puerta (vista desde el frente, que es hacia donde mira el bloque). */
    public enum DoorPart implements StringRepresentable {
        SINGLE("single"), LEFT("left"), RIGHT("right");

        private final String name;

        DoorPart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public DoorPart opposite() {
            return this == LEFT ? RIGHT : this == RIGHT ? LEFT : SINGLE;
        }
    }

    // Formas para "facing=north"; el frente mira al norte, así que la izquierda de quien mira es el este (x grande).
    private static final VoxelShape[] CLOSED = ShapeRotator.all(ModBlock.box(0, 0, 6, 16, 16, 10));
    private static final VoxelShape[] OPENED_SINGLE = ShapeRotator.all(
            Shapes.or(ModBlock.box(0, 0, 6, 3, 16, 10), ModBlock.box(13, 0, 6, 16, 16, 10)));
    private static final VoxelShape[] OPENED_LEFT = ShapeRotator.all(ModBlock.box(13, 0, 6, 16, 16, 10));
    private static final VoxelShape[] OPENED_RIGHT = ShapeRotator.all(ModBlock.box(0, 0, 6, 3, 16, 10));

    public ElevatorDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(PART, DoorPart.SINGLE).setValue(TallHalves.HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, PART, TallHalves.HALF);
    }

    private static VoxelShape shapeFor(BlockState state) {
        int index = state.getValue(FACING).get2DDataValue();
        if (!state.getValue(OPEN)) {
            return CLOSED[index];
        }
        return switch (state.getValue(PART)) {
            case LEFT -> OPENED_LEFT[index];
            case RIGHT -> OPENED_RIGHT[index];
            default -> OPENED_SINGLE[index];
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!TallHalves.canPlace(context)) {
            return null;
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        BlockPos link = ElevatorLinkBlockItem.getLink(context.getItemInHand());
        DoorPart part = DoorPart.SINGLE;
        // Mirando la puerta de frente, su izquierda es facing.getClockWise() y su derecha facing.getCounterClockWise().
        if (canPair(context.getLevel(), pos.relative(facing.getClockWise()), facing, link)) {
            part = DoorPart.RIGHT;      // la pareja queda a mi izquierda: yo soy la hoja derecha
        } else if (canPair(context.getLevel(), pos.relative(facing.getCounterClockWise()), facing, link)) {
            part = DoorPart.LEFT;       // la pareja queda a mi derecha: yo soy la hoja izquierda
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part);
    }

    /** ¿Hay en {@code pos} una puerta de ascensor sola, con la misma orientación y el mismo controlador, que pueda ser mi pareja? */
    private static boolean canPair(BlockGetter level, BlockPos pos, Direction facing, @Nullable BlockPos link) {
        BlockState other = level.getBlockState(pos);
        if (!(other.getBlock() instanceof ElevatorDoorBlock) || other.getValue(FACING) != facing
                || other.getValue(PART) != DoorPart.SINGLE || other.getValue(TallHalves.HALF) != DoubleBlockHalf.LOWER) {
            return false;
        }
        BlockPos otherLink = level.getBlockEntity(pos) instanceof ElevatorDoorBlockEntity door ? door.getController() : null;
        return Objects.equals(link, otherLink);
    }

    /** Posición de la otra mitad de una puerta doble. */
    private static BlockPos partnerPos(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return pos.relative(state.getValue(PART) == DoorPart.RIGHT ? facing.getClockWise() : facing.getCounterClockWise());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (level.isClientSide || oldState.is(state.getBlock()) || state.getValue(PART) == DoorPart.SINGLE
                || state.getValue(TallHalves.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        // La puerta recién puesta ya sabe que es una mitad: avisa a su pareja para que sea la otra.
        BlockPos partner = partnerPos(state, pos);
        BlockState other = level.getBlockState(partner);
        if (other.is(this) && other.getValue(FACING) == state.getValue(FACING) && other.getValue(PART) == DoorPart.SINGLE
                && other.getValue(TallHalves.HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(partner, other.setValue(PART, state.getValue(PART).opposite()), 3);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && state.getValue(PART) != DoorPart.SINGLE
                && state.getValue(TallHalves.HALF) == DoubleBlockHalf.LOWER) {
            // Si se rompe una mitad, la otra vuelve a ser una puerta sola.
            BlockPos partner = partnerPos(state, pos);
            BlockState other = level.getBlockState(partner);
            if (other.is(this) && other.getValue(FACING) == state.getValue(FACING)
                    && other.getValue(PART) == state.getValue(PART).opposite()
                    && other.getValue(TallHalves.HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(partner, other.setValue(PART, DoorPart.SINGLE), 3);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.rotate(mirror.getRotation(state.getValue(FACING)));
        // Al espejar, la izquierda pasa a ser la derecha.
        return mirror == Mirror.NONE ? mirrored : mirrored.setValue(PART, mirrored.getValue(PART).opposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Solo la mitad de abajo recuerda el controlador; la de arriba copia el estado de la de abajo.
        return state.getValue(TallHalves.HALF) == DoubleBlockHalf.LOWER ? new ElevatorDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(TallHalves.HALF) != DoubleBlockHalf.LOWER ? null
                : createTickerHelper(type, ModBlockEntities.ELEVATOR_DOOR.get(), ElevatorDoorBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof ElevatorDoorBlockEntity be) {
            be.setController(ElevatorLinkBlockItem.getLink(stack));
        }
        TallHalves.placeUpper(level, pos, state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (TallHalves.towardsPartner(state, direction)) {
            if (TallHalves.isPartner(state, neighborState)) {
                // La otra mitad manda: se copia su orientación, si está abierta y si es mitad de puerta doble.
                return state.setValue(FACING, neighborState.getValue(FACING)).setValue(OPEN, neighborState.getValue(OPEN))
                        .setValue(PART, neighborState.getValue(PART));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        TallHalves.preventCreativeDrop(level, pos, state, player);
        super.playerWillDestroy(level, pos, state, player);
    }
}
