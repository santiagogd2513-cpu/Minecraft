package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Dron "apagado": se coloca como bloque y se activa con el control remoto. */
public class DroneBlock extends ModBlock {
    public DroneBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(1.0f)
                .sound(SoundType.METAL)
                .noOcclusion(),
                box(2, 0, 2, 14, 6, 14));
    }

    /** Sin acción propia: así el control remoto (onItemUseFirst) es quien activa el dron. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
