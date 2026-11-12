package com.example.beautifuldecor.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.UseAnim;

/** V5.2.0: lata de Rojo Toro (marca parodia). Se bebe: velocidad II y salto mejorado por 60 segundos. */
public class RojoToroCanItem extends Item {
    public RojoToroCanItem() {
        super(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1f).alwaysEat()
                .effect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1), 1.0f)
                .effect(new MobEffectInstance(MobEffects.JUMP, 1200, 1), 1.0f).build()));
    }

    @Override
    public UseAnim getUseAnimation(net.minecraft.world.item.ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }
}
