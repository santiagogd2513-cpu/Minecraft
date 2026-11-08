package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.HelicopterEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Rotor y turbina de un helicóptero: suena mientras los rotores giran (lo oyen todos los cercanos); el tono sube con el régimen. */
public class HelicopterRotorSound extends AbstractTickableSoundInstance {
    private final HelicopterEntity helicopter;
    private boolean finished;

    public HelicopterRotorSound(HelicopterEntity helicopter) {
        super(ModSounds.HELICOPTER_ROTOR.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.helicopter = helicopter;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1f;
        this.pitch = 0.6f;
        updatePosition();
    }

    @Override
    public void tick() {
        float rpm = this.helicopter.getRpm();
        if (this.helicopter.isRemoved() || rpm < 0.03f) {
            this.finished = true;
            this.stop();
            return;
        }
        updatePosition();
        float effort = Mth.clamp(this.helicopter.getVisualSpeed() / HelicopterEntity.MAX_SPEED
                + Math.abs(this.helicopter.getVisualClimb()) / HelicopterEntity.CLIMB_SPEED * 0.5f, 0.0f, 1.0f);
        this.pitch = Mth.clamp(0.55f + 0.45f * rpm + 0.12f * effort, 0.5f, 2.0f);
        this.volume = Mth.clamp(rpm * (1.5f + 0.4f * effort), 0.0f, 2.0f);
    }

    public boolean isFinished() {
        return this.finished;
    }

    private void updatePosition() {
        this.x = this.helicopter.getX();
        this.y = this.helicopter.getY() + 1.5;
        this.z = this.helicopter.getZ();
    }
}
