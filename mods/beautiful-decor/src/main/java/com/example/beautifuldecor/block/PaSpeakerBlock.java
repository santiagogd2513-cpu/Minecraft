package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.MicrophoneBlockEntity;
import com.example.beautifuldecor.block.entity.PaSpeakerBlockEntity;
import com.example.beautifuldecor.item.PaSpeakerItem;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * V4.0.0: bocina de megafonía. Si el ítem venía vinculado a un micrófono (clic derecho con ella sobre el micrófono), al ponerla queda lista:
 * se enlaza sola. También se puede vincular desde el menú del micrófono («Vincular bocinas cercanas»).
 * Clic derecho con la mano vacía: dice a qué micrófono está vinculada.
 */
public class PaSpeakerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE_NS = Shapes.box(2.6 / 16.0, 0.0, 3.6 / 16.0, 13.4 / 16.0, 1.0, 12.4 / 16.0);
    private static final VoxelShape SHAPE_EW = Shapes.box(3.6 / 16.0, 0.0, 2.6 / 16.0, 12.4 / 16.0, 1.0, 13.4 / 16.0);

    public PaSpeakerBlock(BlockBehaviour.Properties properties) {
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PaSpeakerBlockEntity(pos, state);
    }

    /** Al ponerla: si el ítem traía un micrófono vinculado, se enlaza (queda lista). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Player player = placer instanceof Player p ? p : null;
        BlockPos mic = PaSpeakerItem.getLink(stack);
        if (mic == null) {
            tell(player, Component.translatable("message.beautifuldecor.pa.speaker_unlinked"));
            return;
        }
        if (!level.isLoaded(mic) || !(level.getBlockEntity(mic) instanceof MicrophoneBlockEntity micBe)) {
            tell(player, Component.translatable("message.beautifuldecor.pa.mic_missing"));
            return;
        }
        if (!micBe.addSpeaker(pos)) {
            tell(player, Component.translatable("message.beautifuldecor.pa.mic_full", MicrophoneBlockEntity.MAX_SPEAKERS));
            return;
        }
        if (level.getBlockEntity(pos) instanceof PaSpeakerBlockEntity be) {
            be.setMicrophone(mic);
        }
        tell(player, Component.translatable("message.beautifuldecor.pa.speaker_linked", micBe.getSpeakerCount(), MicrophoneBlockEntity.MAX_SPEAKERS));
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof PaSpeakerItem) {
            return InteractionResult.PASS;           // con otra bocina en la mano: se pone la bocina de al lado, como siempre
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PaSpeakerBlockEntity be) {
            BlockPos mic = be.getMicrophone();
            if (mic != null) {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.pa.speaker_status", mic.getX(), mic.getY(), mic.getZ()), true);
            } else {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.pa.speaker_unlinked"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Al quitarla, se borra de la lista del micrófono. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PaSpeakerBlockEntity be) {
            BlockPos mic = be.getMicrophone();
            if (mic != null && level.isLoaded(mic) && level.getBlockEntity(mic) instanceof MicrophoneBlockEntity micBe) {
                micBe.removeSpeaker(pos);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
