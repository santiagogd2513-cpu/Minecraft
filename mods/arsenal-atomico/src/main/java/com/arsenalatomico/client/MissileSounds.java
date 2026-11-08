package com.arsenalatomico.client;

import com.arsenalatomico.entity.MissileEntity;
import com.arsenalatomico.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Lo que se oye de cada misil: el despegue, el motor que lo sigue en todo el vuelo y el impacto lejano. */
public final class MissileSounds {
    private MissileSounds() {}

    public static void start(MissileEntity m) {
        var sm = Minecraft.getInstance().getSoundManager();
        if (m.age() < 10) {
            sm.play(new SimpleSoundInstance(ModSounds.MISSILE_LAUNCH.get(), SoundSource.HOSTILE, 4f,
                    0.85f + m.getRandom().nextFloat() * 0.3f, SoundInstance.createUnseededRandom(), m.getX(), m.getY(), m.getZ()));
        }
        sm.play(new Engine(m));
    }

    public static void impact(Vec3 at) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.FAR_BOOM.get(), SoundSource.HOSTILE, 3f,
                0.8f + (float) Math.random() * 0.35f, SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
    }

    /** El rugido del motor, pegado al misil. */
    private static final class Engine extends AbstractTickableSoundInstance {
        private final MissileEntity m;

        Engine(MissileEntity m) {
            super(ModSounds.MISSILE_LOOP.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.m = m;
            looping = true;
            delay = 0;
            volume = 2.5f;
            pitch = 0.85f + m.getRandom().nextFloat() * 0.3f;
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
