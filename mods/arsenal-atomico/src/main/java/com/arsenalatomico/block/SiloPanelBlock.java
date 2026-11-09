package com.arsenalatomico.block;

import com.arsenalatomico.strike.SolNegro;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * El panel de un silo de la Base Sol Negro (chiquito o mediano): clic derecho abre la pantalla de
 * coordenadas, donde se escribe exactamente dónde va a caer el misil.
 */
public class SiloPanelBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public SiloPanelBlock(Properties p) {
        super(p);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) SolNegro.panel(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
