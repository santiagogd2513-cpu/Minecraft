package com.example.beautifuldecor.block;

import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
 * Piano de 2 bloques de ancho x 1 de fondo y 10/16 de alto (más bajo, pensado para ir sobre el soporte). El bloque donde haces clic es la mitad
 * izquierda (vista desde delante) y la otra mitad se coloca a tu derecha.
 * FACING = hacia dónde mira el frente (teclado). PX: 0 = mitad izquierda (origen), 1 = derecha.
 * NOTE (0..6) vive en la mitad origen: cada clic derecho (en cualquier mitad) toca la nota siguiente
 * do, re, mi, fa, sol, la, si y vuelve a empezar en do, sin fin.
 */
public class PianoBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, 1);
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 6);

    private static final VoxelShape[] SHAPES = ShapeRotator.all(ModBlock.box(0, 0, 2, 16, 10, 16));

    public PianoBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PX, 0).setValue(NOTE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PX, NOTE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Posición de la mitad derecha (vista desde delante) a partir de la mitad origen. */
    private static BlockPos rightPos(BlockPos origin, Direction facing) {
        return origin.relative(facing.getCounterClockWise());
    }

    /** Mitad origen (la que guarda la nota y suelta el ítem) a partir de cualquiera de las dos. */
    private static BlockPos originOf(BlockPos pos, BlockState state) {
        return state.getValue(PX) == 0 ? pos : pos.relative(state.getValue(FACING).getClockWise());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos right = rightPos(context.getClickedPos(), facing);
        if (level.isOutsideBuildHeight(right) || !level.getBlockState(right).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PX, 0).setValue(NOTE, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (!level.isClientSide) {
            level.setBlock(rightPos(pos, state.getValue(FACING)), state.setValue(PX, 1).setValue(NOTE, 0), 3);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos origin = originOf(pos, state);
            BlockState originState = level.getBlockState(origin);
            if (!originState.is(this)) {
                return InteractionResult.PASS;
            }
            int note = originState.getValue(NOTE);
            level.playSound(null, pos, ModSounds.PIANO_NOTES.get(note).get(), SoundSource.BLOCKS, 2.0f, 1.0f);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2,
                        pos.getZ() + 0.5, 0, note / 6.0, 0.0, 0.0, 1.0);
            }
            // flags 2 | 16: avisa a los clientes sin provocar actualizaciones de bloques vecinos
            level.setBlock(origin, originState.setValue(NOTE, (note + 1) % 7), 2 | 16);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Si rompes la mitad derecha en supervivencia, el piano suelta su ítem una sola vez. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(PX) == 1) {
            BlockPos origin = originOf(pos, state);
            BlockState originState = level.getBlockState(origin);
            if (originState.is(this)) {
                Block.dropResources(originState, level, origin, null, player, player.getMainHandItem());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Al quitar una mitad se quita la otra (sin soltar ítems extra). */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            Direction facing = state.getValue(FACING);
            BlockPos origin = originOf(pos, state);
            BlockPos other = state.getValue(PX) == 0 ? rightPos(origin, facing) : origin;
            BlockState s = level.getBlockState(other);
            if (s.is(this) && s.getValue(FACING) == facing && s.getValue(PX) != state.getValue(PX)) {
                level.removeBlock(other, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
