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

    public static void impact(Vec3 at, int kind) {
        float big = kind == 1 ? 1 : 0;
        Minecraft.getInstance().getSoundManager().play(at(ModSounds.FAR_BOOM.get(), at, 3f + big * 5, 0.8f - big * 0.25f + (float) Math.random() * 0.3f));
    }

    private static SimpleSoundInstance at(net.minecraft.sounds.SoundEvent e, Vec3 p, float volume, float pitch) {
        return new SimpleSoundInstance(e, SoundSource.HOSTILE, volume, pitch, SoundInstance.createUnseededRandom(), p.x, p.y, p.z);
    }

    /** El rugido del motor, pegado al misil. */
    private static final class Engine extends AbstractTickableSoundInstance {
        private final MissileEntity m;

        Engine(MissileEntity m, float volume, float pitch) {
            super(ModSounds.MISSILE_LOOP.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.m = m;
            looping = true;
            delay = 0;
            this.volume = volume;
            this.pitch = pitch;
            x = m.getX();
            y = m.getY();
            z = m.getZ();
        }

        @Override
        public void tick() {
            if (m.isRemoved()) {
                stop();
                return;
            }
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
