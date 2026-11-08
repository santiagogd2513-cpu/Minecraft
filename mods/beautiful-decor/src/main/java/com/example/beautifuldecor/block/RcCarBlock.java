package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Carro RC Lamborgonzo Huracanito "apagado" (V3.1.0): se coloca como bloque y se activa con su control remoto.
 * Mide casi un bloque de largo y 4,6 píxeles de alto, así que la caja de colisión gira con el frente.
 */
public class RcCarBlock extends ModBlock {
    private static final VoxelShape NORTH_SOUTH = box(4.2, 0.0, 0.0, 11.8, 4.6, 16.0);
    private static final VoxelShape EAST_WEST = box(0.0, 0.0, 4.2, 16.0, 4.6, 11.8);

    public RcCarBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
                .strength(1.0f)
                .sound(SoundType.METAL)
                .noOcclusion(),
                NORTH_SOUTH);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    /** Sin acción propia: así el control remoto (onItemUseFirst) es quien lo activa. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
