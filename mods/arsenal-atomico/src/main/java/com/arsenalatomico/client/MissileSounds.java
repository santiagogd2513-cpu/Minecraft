package com.arsenalatomico.client;

import com.arsenalatomico.entity.MissileEntity;
import com.arsenalatomico.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Lo que se oye de cada misil: el despegue, el motor que lo sigue en todo el vuelo y el impacto lejano. */
public final class MissileSounds {
    private MissileSounds() {}

    public static void start(MissileEntity m) {
        var sm = Minecraft.getInstance().getSoundManager();
        if (m.kind() == 3) {
            // Protocolo Hongo: el rugido del encendido dentro del silo y el motor que lo sigue
            if (m.age() < 10) {
                sm.play(at(ModSounds.MISSILE_LAUNCH.get(), m.position(), 16f, 0.48f));
                sm.play(at(ModSounds.FAR_BOOM.get(), m.position(), 8f, 0.8f));
                sm.play(at(SoundEvents.GENERIC_EXPLODE, m.position(), 6f, 0.45f));
            }
            sm.play(new Engine(m, 9f, 0.5f));
            return;
        }
        if (m.kind() == 2) {
            // el Cóndor: rugido enorme dentro del silo
            if (m.age() < 10) {
                sm.play(at(ModSounds.MISSILE_LAUNCH.get(), m.position(), 16f, 0.42f));
                sm.play(at(ModSounds.FAR_BOOM.get(), m.position(), 8f, 0.85f));
                sm.play(at(SoundEvents.GENERIC_EXPLODE, m.position(), 6f, 0.5f));
            }
            sm.play(new Engine(m, 9f, 0.42f, 0, com.arsenalatomico.CondorPlan.SEP1));
            sm.play(new Engine(m, 6f, 0.7f, com.arsenalatomico.CondorPlan.SEP1, com.arsenalatomico.CondorPlan.SEP2));
            sm.play(new Engine(m, 5f, 1.7f, com.arsenalatomico.CondorPlan.SEP2, Integer.MAX_VALUE));
            return;
        }
        if (m.kind() == 1) {
            // intercontinental: primero el golpe de la salida en frío (gas, o vapor bajo el agua)
            if (m.age() < 10) {
                boolean water = !m.level().getFluidState(m.blockPosition()).isEmpty();
                sm.play(at(water ? SoundEvents.GENERIC_SPLASH : SoundEvents.GENERIC_EXPLODE, m.position(), 6f, water ? 0.5f : 1.4f));
                sm.play(at(SoundEvents.FIRE_EXTINGUISH, m.position(), 4f, 0.5f));
            }
            return;
        }
        if (m.age() < 10) sm.play(at(ModSounds.MISSILE_LAUNCH.get(), m.position(), 4f, 0.85f + (float) Math.random() * 0.3f));
        sm.play(new Engine(m, 2.5f, 0.85f + (float) Math.random() * 0.3f));
    }

    /** Se enciende el motor del intercontinental: rugido grave y fuerte, que lo sigue. */
    public static void ignite(MissileEntity m) {
        var sm = Minecraft.getInstance().getSoundManager();
        sm.play(at(ModSounds.MISSILE_LAUNCH.get(), m.position(), 10f, 0.55f));
        sm.play(at(ModSounds.FAR_BOOM.get(), m.position(), 4f, 1.3f));
        sm.play(new Engine(m, 6f, 0.55f));
    }

    /** El Cóndor suelta la primera etapa (1) o la ojiva (2): estallido y la pieza que cae. */
    public static void separate(MissileEntity m, int stage) {
        Minecraft.getInstance().getSoundManager().play(at(SoundEvents.GENERIC_EXPLODE, m.position(), 8f, stage == 1 ? 1.4f : 1.8f));
        CondorFx.separate(m, stage);
    }

    public static void impact(Vec3 at, int kind) {
        if (kind == 3) {
            NukeFx.spawn(at);
            return;
        }
        ImpactFx.spawn(at, kind == 2 ? ImpactFx.Kind.CONDOR : kind == 1 ? ImpactFx.Kind.ICBM : ImpactFx.Kind.SALVO);
        float big = kind >= 1 ? 1 : 0;
        Minecraft.getInstance().getSoundManager().play(at(ModSounds.FAR_BOOM.get(), at, 3f + big * 5, 0.8f - big * 0.25f + (float) Math.random() * 0.3f));
    }

    private static SimpleSoundInstance at(net.minecraft.sounds.SoundEvent e, Vec3 p, float volume, float pitch) {
        return new SimpleSoundInstance(e, SoundSource.HOSTILE, volume, pitch, SoundInstance.createUnseededRandom(), p.x, p.y, p.z);
    }

    /** El rugido del motor, pegado al misil. */
    private static final class Engine extends AbstractTickableSoundInstance {
        private final MissileEntity m;

        private final int from, until;
        private float baseVolume;

        Engine(MissileEntity m, float volume, float pitch) {
            this(m, volume, pitch, 0, Integer.MAX_VALUE);
        }

        /** Motor que suena solo entre dos edades del misil (cada etapa del Cóndor suena distinto). */
        Engine(MissileEntity m, float volume, float pitch, int from, int until) {
            super(ModSounds.MISSILE_LOOP.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.m = m;
            this.from = from;
            this.until = until;
            looping = true;
            delay = 0;
            this.volume = from > 0 ? 0f : volume;
            this.baseVolume = volume;
            this.pitch = pitch;
            x = m.getX();
            y = m.getY();
            z = m.getZ();
        }

        @Override
        public void tick() {
            if (m.isRemoved() || m.age() >= until) {
                stop();
                return;
            }
            float target = m.age() >= from ? baseVolume : 0f;
            volume += (target - volume) * 0.25f;
            x = m.getX();
            y = m.getY();
            z = m.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
