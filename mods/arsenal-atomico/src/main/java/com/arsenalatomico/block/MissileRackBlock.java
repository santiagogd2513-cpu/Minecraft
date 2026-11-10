package com.arsenalatomico.block;

import com.arsenalatomico.strike.Depot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Un estante del depósito de suministros: clic derecho saca un misil mientras queden. */
public class MissileRackBlock extends Block {
    private final boolean medium;

    public MissileRackBlock(Properties p, boolean medium) {
        super(p);
        this.medium = medium;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) Depot.take(sp, medium);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
