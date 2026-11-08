package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.ElevatorControllerBlockEntity;
import com.example.beautifuldecor.block.entity.ElevatorDisplayBlockEntity;
import com.example.beautifuldecor.elevator.ElevatorShaft;
import com.example.beautifuldecor.item.ElevatorLinkBlockItem;
import com.example.beautifuldecor.network.ElevatorDisplayPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de ascensor (V2.2.0): panel delgado que se pega a cualquier cara de un bloque (pared, piso o techo).
 * Se vincula a un controlador (clic derecho en el controlador con la pantalla en la mano) y, al hacerle clic, abre la
 * lista de todos los pisos del ascensor; al elegir uno la cabina va a ese piso.
 */
public class ElevatorDisplayBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape NORTH = ModBlock.box(2, 2, 13, 14, 14, 16);
    private static final VoxelShape SOUTH = ModBlock.box(2, 2, 0, 14, 14, 3);
    private static final VoxelShape WEST = ModBlock.box(13, 2, 2, 16, 14, 14);
    private static final VoxelShape EAST = ModBlock.box(0, 2, 2, 3, 14, 14);
    private static final VoxelShape UP = ModBlock.box(2, 0, 2, 14, 3, 14);
    private static final VoxelShape DOWN = ModBlock.box(2, 13, 2, 14, 16, 14);

    public ElevatorDisplayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            case UP -> UP;
            case DOWN -> DOWN;
            default -> NORTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElevatorDisplayBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ElevatorDisplayBlockEntity be) {
            be.setController(ElevatorLinkBlockItem.getLink(stack));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof ElevatorDisplayBlockEntity be)) {
            return InteractionResult.CONSUME;
        }
        BlockPos controller = be.getController();
        if (controller == null) {
            player.displayClientMessage(Component.translatable("message.beautifuldecor.elevator.unlinked"), true);
            return InteractionResult.CONSUME;
        }
        if (!level.isLoaded(controller) || !(level.getBlockEntity(controller) instanceof ElevatorControllerBlockEntity)) {
            player.displayClientMessage(Component.translatable("message.beautifuldecor.elevator.missing"), true);
            return InteractionResult.CONSUME;
        }
        // V3.4.0: los pisos de su columna (de arriba hacia abajo) y las paradas de lado conectadas, ya en el orden de la pantalla
        List<ElevatorShaft.Stop> stops = ElevatorShaft.stops(level, controller);
        List<ElevatorDisplayPacket.FloorEntry> entries = new ArrayList<>();
        int own = -1;
        for (int i = 0; i < stops.size(); i++) {
            ElevatorShaft.Stop stop = stops.get(i);
            entries.add(new ElevatorDisplayPacket.FloorEntry(stop.pos(), stop.name(), stop.cabinHere(), stop.axis()));
            if (stop.pos().equals(controller)) {
                own = i;
            }
        }
        boolean moving = ElevatorShaft.travellingCabin(level, controller) != null;
        ModMessages.sendToPlayer(serverPlayer, new ElevatorDisplayPacket(pos, entries, own, moving));
        return InteractionResult.CONSUME;
    }
}
