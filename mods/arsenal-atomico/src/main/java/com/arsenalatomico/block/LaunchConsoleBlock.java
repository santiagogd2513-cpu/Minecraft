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
 * La consola de lanzamiento del centro de mando de la Base Sol Negro: un tablero bajito con el botón
 * rojo bajo su tapa y la llave. Clic derecho la abre (solo si hay un lanzamiento pendiente).
 * No se puede romper: la base es del ejército.
 */
public class LaunchConsoleBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public LaunchConsoleBlock(Properties p) {
        super(p);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) SolNegro.console(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
