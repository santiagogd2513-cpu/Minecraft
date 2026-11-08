package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.ElevatorControllerBlockEntity;
import com.example.beautifuldecor.elevator.ElevatorShaft;
import com.example.beautifuldecor.item.ElevatorLinkBlockItem;
import com.example.beautifuldecor.network.ElevatorControllerPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

import javax.annotation.Nullable;

/**
 * Controlador de ascensor (V2.2.0). Se coloca FUERA de la cabina, pegado a su fondo y en el centro. La cara con el panel
 * (FACING) apunta hacia donde está la cabina.
 * <ul>
 *   <li>Clic derecho en un LADO del controlador: ese lado pasa a ser el lado de la cabina, se marca la zona del ascensor
 *       (contorno celeste, piso en amarillo) y se abre el menú: ancho, fondo, alto, velocidad, nombre del piso y dirección.</li>
 *   <li>Con la pantalla o una puerta de ascensor en la mano: las vincula a este controlador (a este piso).</li>
 *   <li>Agacharse + clic con las manos vacías: solo muestra la zona, sin abrir el menú.</li>
 *   <li>Varios controladores en la misma columna (mismo x y z, a distintas alturas) forman un solo ascensor con un piso cada uno.</li>
 * </ul>
 */
public class ElevatorControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ElevatorControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** La cabina queda del lado desde el que lo colocas (si lo pegas a una pared, mira hacia afuera de ella). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
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
        return new ElevatorControllerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel) {
            ElevatorShaft.joinColumn(serverLevel, pos);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ElevatorLinkBlockItem) {
            if (!level.isClientSide) {
                ElevatorLinkBlockItem.setLink(held, level, pos);
                player.displayClientMessage(Component.translatable("message.beautifuldecor.elevator.linked_ok",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ElevatorControllerBlockEntity be) {
            Direction side = hit.getDirection();
            boolean menu = !player.isShiftKeyDown();
            if (side.getAxis().isHorizontal() && side != state.getValue(FACING)
                    && ElevatorShaft.travellingCabin(level, pos) == null) {
                ElevatorShaft.applySettings(serverLevel, pos, side, be.getWidth(), be.getDepth(), be.getHeight(),
                        be.getOffsetX(), be.getOffsetY(), be.getOffsetZ(), be.getSpeed(), be.getFloorName(), null);
            }
            Direction facing = level.getBlockState(pos).getValue(FACING);
            ModMessages.sendToPlayer(serverPlayer, new ElevatorControllerPacket(pos, facing, be.getWidth(), be.getDepth(),
                    be.getHeight(), be.getOffsetX(), be.getOffsetY(), be.getOffsetZ(), be.getSpeed(), be.getFloorName(), menu));
        }
        return InteractionResult.CONSUME;
    }
}
