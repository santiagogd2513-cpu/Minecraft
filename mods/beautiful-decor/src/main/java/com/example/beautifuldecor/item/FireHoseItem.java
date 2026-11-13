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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * V6.10.0: manguera de Aguacero Bomberos. Mantén el clic derecho: lanza un chorro de agua de hasta 10 bloques que apaga el fuego a su paso
 * y a quien esté ardiendo, y empuja un poco a las criaturas. Gasta 1 de durabilidad cada medio segundo (300 en total).
 */
public class FireHoseItem extends Item {
    public static final double RANGE = 10.0;

    public FireHoseItem() {
        super(new Item.Properties().stacksTo(1).durability(300));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide) {
            return;
        }
        Vec3 start = user.getEyePosition().add(0.0, -0.2, 0.0);
        Vec3 look = user.getViewVector(1.0f);
        Vec3 end = start;
        for (double d = 0.5; d <= RANGE; d += 0.5) {
            Vec3 p = start.add(look.scale(d));
            BlockPos bp = BlockPos.containing(p);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                break;
            }
            end = p;
            FireExtinguisherItem.extinguishArea(level, p, 0, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(0.8), t -> t != user)) {
                e.clearFire();
                e.push(look.x * 0.05, 0.01, look.z * 0.05);
                e.hurtMarked = true;
            }
        }
        FireExtinguisherItem.extinguishArea(level, end, 1, 1);
        if (level instanceof ServerLevel sl) {
            for (int i = 1; i <= 8; i++) {
                Vec3 p = start.add(look.scale(i * RANGE / 8.0 * (0.6 + 0.4 * level.random.nextDouble())));
                sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.02);
            }
            sl.sendParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, 2, 0.2, 0.2, 0.2, 0.01);
        }
        if (user.tickCount % 4 == 0) {
            level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.3f, 1.5f);
        }
        if (user.tickCount % 10 == 0) {
            stack.hurtAndBreak(1, user, p -> p.broadcastBreakEvent(user.getUsedItemHand()));
        }
    }
}
