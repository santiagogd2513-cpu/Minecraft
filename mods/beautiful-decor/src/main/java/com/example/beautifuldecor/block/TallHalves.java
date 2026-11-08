package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Utilidades para bloques de dos de alto (mitad de abajo y mitad de arriba), como las puertas del juego (V2.3.0):
 * el ventilador de pie y la puerta de ascensor. La mitad de abajo es la que suelta el ítem y la de arriba copia su estado.
 */
public final class TallHalves {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private TallHalves() {
    }

    /** ¿Hay espacio para las dos mitades? */
    public static boolean canPlace(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context);
    }

    /** ¿La dirección apunta hacia la otra mitad? (la de abajo mira hacia arriba, la de arriba hacia abajo) */
    public static boolean towardsPartner(BlockState state, Direction direction) {
        return direction.getAxis() == Direction.Axis.Y
                && (state.getValue(HALF) == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
    }

    /** ¿El vecino es la otra mitad del mismo bloque? */
    public static boolean isPartner(BlockState state, BlockState neighbor) {
        return neighbor.is(state.getBlock()) && neighbor.getValue(HALF) != state.getValue(HALF);
    }

    /** Pone la mitad de arriba copiando el estado de la de abajo. */
    public static void placeUpper(Level level, BlockPos lowerPos, BlockState lower) {
        level.setBlock(lowerPos.above(), lower.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    /** En creativo, romper la mitad de arriba quita la de abajo sin soltar el ítem (igual que las puertas). */
    public static void preventCreativeDrop(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide || !player.isCreative() || state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return;
        }
        BlockPos below = pos.below();
        BlockState lower = level.getBlockState(below);
        if (lower.is(state.getBlock()) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, below, Block.getId(lower));
        }
    }
}
