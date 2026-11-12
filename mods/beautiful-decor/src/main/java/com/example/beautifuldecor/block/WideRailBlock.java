package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.WideRailBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * V6.1.0: pedazo de curva ancha (un arco de círculo de radio mayor que el de la curva de Minecraft). No tiene modelo: lo dibuja
 * {@code WideRailRenderer} siguiendo el arco. Se crea con clic en la cara de arriba de una curva con un riel de la misma vía en la mano.
 */
public class WideRailBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);
    private final int family;

    public WideRailBlock(Properties properties, int family) {
        super(properties);
        this.family = family;
    }

    /** Familia de vía: 0 = carga, 1 = pasajeros, 2 = metro. */
    public int family() {
        return this.family;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WideRailBlockEntity(pos, state);
    }
}
