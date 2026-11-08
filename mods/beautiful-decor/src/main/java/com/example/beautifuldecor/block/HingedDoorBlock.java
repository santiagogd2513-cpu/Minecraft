package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Puerta decorativa de una hoja que se puede juntar con otra al lado para formar una puerta doble,
 * como las de Minecraft:
 * <ul>
 *   <li>Al colocar una puerta junto a otra, la bisagra de cada una queda en el borde exterior del par
 *       (el modelo de la bisagra izquierda es el espejo del original).</li>
 *   <li>Al abrir o cerrar una de las dos, la otra del par la acompaña.</li>
 * </ul>
 * {@code hinge=right} (valor por defecto) es el aspecto que ya tenían estas puertas, así que las puertas
 * ya colocadas en un mundo no cambian. "Izquierda" y "derecha" se miran desde el frente de la puerta.
 */
public class HingedDoorBlock extends ToggleBlock {
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;

    private final boolean metal;

    public HingedDoorBlock(BlockBehaviour.Properties properties, boolean metal) {
        super(properties);
        this.metal = metal;
        registerDefaultState(defaultBlockState().setValue(HINGE, DoorHingeSide.RIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HINGE);
    }

    /** Lado izquierdo visto desde el frente de la puerta (el frente mira hacia {@code facing}). */
    private static Direction viewerLeft(Direction facing) {
        return facing.getClockWise();
    }

    private static Direction viewerRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Dirección en la que está el borde libre (el opuesto a la bisagra). */
    private static Direction freeEdge(Direction facing, DoorHingeSide hinge) {
        return hinge == DoorHingeSide.RIGHT ? viewerLeft(facing) : viewerRight(facing);
    }

    private boolean isDoorAt(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(FACING) == facing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Direction facing = state.getValue(FACING);
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        DoorHingeSide hinge = DoorHingeSide.RIGHT;
        if (isDoorAt(level, pos.relative(viewerLeft(facing)), facing)) {
            hinge = DoorHingeSide.RIGHT;   // hay una puerta a mi izquierda: yo soy la de la derecha
        } else if (isDoorAt(level, pos.relative(viewerRight(facing)), facing)) {
            hinge = DoorHingeSide.LEFT;    // hay una puerta a mi derecha: yo soy la de la izquierda
        }
        return state.setValue(HINGE, hinge);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        // Si la puerta vecina estaba sola, voltea su bisagra para que el par quede en espejo.
        Direction facing = state.getValue(FACING);
        DoorHingeSide hinge = state.getValue(HINGE);
        Direction toPartner = freeEdge(facing, hinge);
        BlockPos partnerPos = pos.relative(toPartner);
        if (!isDoorAt(level, partnerPos, facing)) {
            return;
        }
        BlockState partner = level.getBlockState(partnerPos);
        DoorHingeSide wanted = hinge == DoorHingeSide.RIGHT ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
        boolean partnerAlreadyPaired = isDoorAt(level, partnerPos.relative(toPartner), facing);
        if (!partnerAlreadyPaired && partner.getValue(HINGE) != wanted) {
            level.setBlock(partnerPos, partner.setValue(HINGE, wanted), 3);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), 3);
            playToggleSound(level, pos, open);

            // La otra hoja del par (la que está en mi borde libre y tiene la bisagra contraria) me acompaña.
            Direction facing = state.getValue(FACING);
            DoorHingeSide hinge = state.getValue(HINGE);
            BlockPos partnerPos = pos.relative(freeEdge(facing, hinge));
            if (isDoorAt(level, partnerPos, facing)) {
                BlockState partner = level.getBlockState(partnerPos);
                if (partner.getValue(HINGE) != hinge && partner.getValue(OPEN) != open) {
                    level.setBlock(partnerPos, partner.setValue(OPEN, open), 3);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void playToggleSound(Level level, BlockPos pos, boolean open) {
        SoundEvent sound = metal
                ? (open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE)
                : (open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.8f, 1.0f);
    }
}
