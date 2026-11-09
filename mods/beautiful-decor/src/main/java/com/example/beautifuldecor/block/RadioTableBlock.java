package com.example.beautifuldecor.block;

import com.example.beautifuldecor.item.RadioSpeakerItem;
import com.example.beautifuldecor.radio.RadioHub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * V4.2.0: radio de mesa. Clic derecho con la mano vacía abre el mismo menú que la radio de los vehículos (la lista de emisoras tuyas
 * es la misma: lo que agregas en una está en la otra). Clic derecho con un bafle en la mano lo vincula a esta radio. Lo que suena (emisora,
 * encendido, volumen) vive en el servidor (RadioHub), no en el bloque; el estado ON solo cambia el dibujo y da un poco de luz.
 */
public class RadioTableBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    private static final VoxelShape SHAPE_NS = Shapes.box(1.0 / 16.0, 0.0, 4.0 / 16.0, 15.0 / 16.0, 9.0 / 16.0, 12.0 / 16.0);
    private static final VoxelShape SHAPE_EW = Shapes.box(4.0 / 16.0, 0.0, 1.0 / 16.0, 12.0 / 16.0, 9.0 / 16.0, 15.0 / 16.0);

    public RadioTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RadioSpeakerItem) {
            if (!level.isClientSide) {
                RadioSpeakerItem.setLink(held, pos, level.dimension().location());
                player.displayClientMessage(Component.translatable("message.beautifuldecor.radio.linked_item", pos.getX(), pos.getY(), pos.getZ()), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.ClientHooks.openRadioScreen(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Al quitarla, el servidor se olvida de su emisora y los bafles vinculados se callan. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            RadioHub.get(sl).remove(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
