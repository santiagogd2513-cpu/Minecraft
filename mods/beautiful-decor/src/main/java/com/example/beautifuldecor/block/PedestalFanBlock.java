package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Ventilador de pie (V2.3.0): base en el piso, un tubo largo y la cabeza con jaula y aspas arriba. Mide dos bloques de alto
 * (mitad de abajo y mitad de arriba). Clic derecho en cualquiera de las dos mitades lo enciende o apaga; encendido las aspas
 * giran (textura animada). La mitad de arriba copia el estado de la de abajo y las dos se rompen juntas.
 */
public class PedestalFanBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");

    // Formas para "facing=north". Abajo: base + tubo. Arriba: tubo + cabeza.
    private static final VoxelShape[] LOWER = ShapeRotator.all(Shapes.or(
            ModBlock.box(3, 0, 3, 13, 1.8, 13), ModBlock.box(7, 0, 7, 9, 16, 9)));
    private static final VoxelShape[] UPPER = ShapeRotator.all(Shapes.or(
            ModBlock.box(7, 0, 7, 9, 6.2, 9), ModBlock.box(2, 2.5, 6.2, 14, 14.5, 10)));

    public PedestalFanBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false)
                .setValue(TallHalves.HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TallHalves.HALF, ON);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!TallHalves.canPlace(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        TallHalves.placeUpper(level, pos, state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (TallHalves.towardsPartner(state, direction)) {
            if (TallHalves.isPartner(state, neighborState)) {
                return state.setValue(FACING, neighborState.getValue(FACING)).setValue(ON, neighborState.getValue(ON));
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
        int index = state.getValue(FACING).get2DDataValue();
        return state.getValue(TallHalves.HALF) == DoubleBlockHalf.LOWER ? LOWER[index] : UPPER[index];
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        boolean on = !state.getValue(ON);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(ON, on), 3);       // la otra mitad copia el estado sola (updateShape)
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, on ? 1.2f : 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
