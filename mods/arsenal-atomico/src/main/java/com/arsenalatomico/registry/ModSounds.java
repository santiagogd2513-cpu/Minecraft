package com.arsenalatomico.registry;

import com.arsenalatomico.ArsenalAtomico;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sonidos propios del mod (despegue, motor, alarma, cuenta regresiva, hidráulica y explosión lejana). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ArsenalAtomico.MODID);

    public static final RegistryObject<SoundEvent> MISSILE_LAUNCH = register("missile_launch");
    public static final RegistryObject<SoundEvent> MISSILE_LOOP = register("missile_loop");
    public static final RegistryObject<SoundEvent> ALARM = register("alarm");
    public static final RegistryObject<SoundEvent> BEEP = register("beep");
    public static final RegistryObject<SoundEvent> HYDRAULIC = register("hydraulic");
    public static final RegistryObject<SoundEvent> FAR_BOOM = register("far_boom");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArsenalAtomico.MODID, id)));
    }
}
