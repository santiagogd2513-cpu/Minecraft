package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.RcCarEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Motorcito eléctrico del carro RC (V3.1.0): suena mientras alguien lo maneja y el tono sube con la velocidad. */
public class RcCarMotorSound extends AbstractTickableSoundInstance {
    private final RcCarEntity car;
    private boolean finished;
    private float smoothPitch = 0.7f;

    public RcCarMotorSound(RcCarEntity car) {
        super(ModSounds.RC_CAR_MOTOR.get(), SoundSource.PLAYERS, RandomSource.create());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35f;
        this.pitch = this.smoothPitch;
        updatePosition();
    }

    @Override
    public void tick() {
        if (this.car.isRemoved() || !this.car.isPiloted()) {
            this.finished = true;
            this.stop();
            return;
        }
        updatePosition();
        float fraction = Mth.clamp(Math.abs(this.car.getVisualSpeed()) / RcCarEntity.TOP_SPEED, 0.0f, 1.0f);
        float target = 0.7f + 1.1f * fraction;
        this.smoothPitch += (target - this.smoothPitch) * 0.3f;
        this.pitch = Mth.clamp(this.smoothPitch, 0.5f, 2.0f);
        this.volume = 0.3f + 0.45f * fraction;
    }

    public boolean isFinished() {
        return this.finished;
    }

    private void updatePosition() {
        this.x = this.car.getX();
        this.y = this.car.getY();
        this.z = this.car.getZ();
    }
}
