package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Soporte de piano: mismo tamaño que el piano original (2 de ancho x 1 de alto x 1 de fondo) y se coloca
 * igual (el bloque donde haces clic es la mitad origen y la otra queda a tu derecha). No suena: el piano
 * se pone encima, en el bloque de arriba.
 */
public class PianoStandBlock extends PianoBlock {
    private static final VoxelShape[] STAND_SHAPES = ShapeRotator.all(ModBlock.box(0, 0, 2, 16, 16, 16));

    public PianoStandBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return STAND_SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
