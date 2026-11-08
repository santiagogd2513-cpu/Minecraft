package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ventilador de techo: cuelga de la parte alta del bloque. Clic derecho = enciende (las aspas giran,
 * textura animada) y otro clic derecho = lo apaga. No tiene colisión para no golpearte la cabeza.
 */
public class CeilingFanBlock extends Block {
    public static final BooleanProperty ON = BooleanProperty.create("on");

    private static final VoxelShape SHAPE = Shapes.or(
            ModBlock.box(0.5, 11.0, 0.5, 15.5, 11.8, 15.5),   // aspas
            ModBlock.box(5.5, 9.5, 5.5, 10.5, 12.4, 10.5),    // motor
            ModBlock.box(7.0, 12.4, 7.0, 9.0, 16.0, 9.0));    // varilla

    public CeilingFanBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ON);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        boolean on = !state.getValue(ON);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(ON, on), 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, on ? 1.2f : 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
