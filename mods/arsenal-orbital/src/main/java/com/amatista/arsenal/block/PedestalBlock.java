package com.amatista.arsenal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Pedestal de vitrina. Clic derecho: si tiene un objeto, lo tomas; si está vacío, pones el que
 * tienes en la mano. En la bóveda del búnker guarda el Designador orbital.
 */
public class PedestalBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 4, 16),
            box(3, 4, 3, 13, 12, 13),
            box(1, 12, 1, 15, 16, 15));

    public PedestalBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PedestalBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!be.getItem().isEmpty()) {
            if (!level.isClientSide) {
                ItemStack got = be.take();
                if (!player.addItem(got)) player.drop(got, false);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide) {
                be.setItem(player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PedestalBlockEntity be && !be.getItem().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, be.getItem());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Chispas de colores que suben dentro de la vitrina. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity be) || !be.isRainbow()) return;
        for (int i = 0; i < 2; i++) {
            float[] c = PedestalBlockEntity.rainbow(level.getGameTime() + r.nextInt(60), 0);
            level.addParticle(new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 0.9f),
                    pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 1.0 + r.nextDouble() * 0.4,
                    pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.03, 0);
        }
    }
}
