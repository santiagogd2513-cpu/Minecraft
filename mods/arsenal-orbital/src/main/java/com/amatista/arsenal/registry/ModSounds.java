package com.amatista.arsenal.registry;

import com.amatista.arsenal.ArsenalOrbital;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sonidos propios del mod (sintetizados, ver tools/make_sounds.py). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ArsenalOrbital.MODID);

    public static final RegistryObject<SoundEvent> ALARM = reg("alarm");
    public static final RegistryObject<SoundEvent> BEAM_IMPACT = reg("beam_impact");
    public static final RegistryObject<SoundEvent> BELL_STRIKE = reg("bell_strike");
    public static final RegistryObject<SoundEvent> CAPACITOR_CHARGE = reg("capacitor_charge");
    public static final RegistryObject<SoundEvent> CATHEDRAL_BELL = reg("cathedral_bell");
    public static final RegistryObject<SoundEvent> CHARGE_WHINE = reg("charge_whine");
    public static final RegistryObject<SoundEvent> COLLAPSE = reg("collapse");
    public static final RegistryObject<SoundEvent> COSMIC_SWELL = reg("cosmic_swell");
    public static final RegistryObject<SoundEvent> COUNTDOWN_TICK = reg("countdown_tick");
    public static final RegistryObject<SoundEvent> CRYSTAL_CHIME = reg("crystal_chime");
    public static final RegistryObject<SoundEvent> CRYSTAL_RESONANCE = reg("crystal_resonance");
    public static final RegistryObject<SoundEvent> DEEP_THUNDER = reg("deep_thunder");
    public static final RegistryObject<SoundEvent> ENERGY_CRACK = reg("energy_crack");
    public static final RegistryObject<SoundEvent> EXPLOSION = reg("explosion");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> LASER_SHOT = reg("laser_shot");
    public static final RegistryObject<SoundEvent> LOCK_ON = reg("lock_on");
    public static final RegistryObject<SoundEvent> MACHINE_HUM = reg("machine_hum");
    public static final RegistryObject<SoundEvent> MECH_EXTEND = reg("mech_extend");
    public static final RegistryObject<SoundEvent> MECH_RETRACT = reg("mech_retract");
    public static final RegistryObject<SoundEvent> MISSILE_LAUNCH = reg("missile_launch");
    public static final RegistryObject<SoundEvent> PLANET_GROAN = reg("planet_groan");
    public static final RegistryObject<SoundEvent> PLASMA_CRACKLE = reg("plasma_crackle");
    public static final RegistryObject<SoundEvent> PLASMA_WHOOSH = reg("plasma_whoosh");
    public static final RegistryObject<SoundEvent> POWER_DOWN = reg("power_down");
    public static final RegistryObject<SoundEvent> POWER_UP = reg("power_up");
    public static final RegistryObject<SoundEvent> RUNE_GLIMMER = reg("rune_glimmer");
    public static final RegistryObject<SoundEvent> RUSH_WIND = reg("rush_wind");
    public static final RegistryObject<SoundEvent> SINGULARITY_BIRTH = reg("singularity_birth");
    public static final RegistryObject<SoundEvent> SKY_BURST = reg("sky_burst");
    public static final RegistryObject<SoundEvent> SONIC_BLAST = reg("sonic_blast");
    public static final RegistryObject<SoundEvent> STATION_ONLINE = reg("station_online");
    public static final RegistryObject<SoundEvent> THRUSTER_BURN = reg("thruster_burn");
    public static final RegistryObject<SoundEvent> THRUSTER_IDLE = reg("thruster_idle");
    public static final RegistryObject<SoundEvent> TITAN_ROAR = reg("titan_roar");
    public static final RegistryObject<SoundEvent> UI_CONFIRM = reg("ui_confirm");
    public static final RegistryObject<SoundEvent> VOID_DRONE = reg("void_drone");
    public static final RegistryObject<SoundEvent> WARP_JUMP = reg("warp_jump");
    public static final RegistryObject<SoundEvent> WORMHOLE_OPEN = reg("wormhole_open");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArsenalOrbital.MODID, name)));
    }

    private ModSounds() {}
}
