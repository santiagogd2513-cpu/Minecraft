package com.example.beautifuldecor.item;

import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;

/** V6.7.0: pistola eléctrica de la Polisía Nasional. Alcance de 12 bloques; el objetivo queda inmóvil (sin caminar ni saltar) unos 5 segundos. */
public class TaserItem extends Item {
    public static final double RANGE = 12.0;
    public static final int STUN_TICKS = 100;

    public TaserItem() {
        super(new Item.Properties().stacksTo(1).durability(64));
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
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(RANGE));
        AABB box = player.getBoundingBox().expandTowards(player.getViewVector(1.0f).scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, start, end, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        level.playSound(null, player.blockPosition(), ModSounds.TASER_ZAP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.getCooldowns().addCooldown(this, 60);
        Vec3 to = end;
        if (hit != null) {
            Entity e = hit.getEntity();
            to = hit.getLocation();
            if (e instanceof LivingEntity target) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 9, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, STUN_TICKS, 128, false, false, false));
                target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                target.hurtMarked = true;
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 24, 0.3, 0.5, 0.3, 0.2);
                }
            }
        }
        if (level instanceof ServerLevel sl) {
            int n = (int) Math.max(2, start.distanceTo(to) * 2);
            for (int i = 1; i <= n; i++) {
                Vec3 p = start.lerp(to, i / (double) n);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y - 0.15, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.consume(stack);
    }
}
