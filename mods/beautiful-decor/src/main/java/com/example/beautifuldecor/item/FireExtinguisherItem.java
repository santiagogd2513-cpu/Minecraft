package com.example.beautifuldecor.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * V6.10.0: extintor de Aguacero Bomberos. Clic derecho: apaga el fuego en una zona de 5x3x5 bloques delante de ti y a las criaturas
 * (o jugadores) que estén ardiendo ahí; agachado, te apaga a ti. Tiene 40 usos y una pequeña espera entre uno y otro.
 */
public class FireExtinguisherItem extends Item {
    public FireExtinguisherItem() {
        super(new Item.Properties().stacksTo(1).durability(40));
    }

    /** Apaga el fuego (bloques de fuego y fogatas) en una caja alrededor de un punto; devuelve cuántos apagó. */
    public static int extinguishArea(Level level, Vec3 center, int radius, int up) {
        int count = 0;
        BlockPos c = BlockPos.containing(center);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -up, -radius), c.offset(radius, up, radius))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof BaseFireBlock) {
                level.removeBlock(p, false);
                count++;
            } else if (s.hasProperty(BlockStateProperties.LIT) && s.is(net.minecraft.tags.BlockTags.CAMPFIRES) && s.getValue(BlockStateProperties.LIT)) {
                level.setBlock(p, s.setValue(BlockStateProperties.LIT, Boolean.FALSE), 3);
                count++;
            }
        }
        return count;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        Vec3 look = player.getViewVector(1.0f);
        Vec3 center = player.getEyePosition().add(look.scale(3.0));
        extinguishArea(level, center, 2, 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(2.5, 1.5, 2.5))) {
            e.clearFire();
        }
        if (player.isShiftKeyDown()) {
            player.clearFire();
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 40, 1.2, 0.6, 1.2, 0.05);
        }
        player.getCooldowns().addCooldown(this, 20);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.consume(stack);
    }
}
