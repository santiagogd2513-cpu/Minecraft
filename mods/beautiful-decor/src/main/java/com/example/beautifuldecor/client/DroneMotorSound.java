package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.DroneEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class DroneMotorSound extends AbstractTickableSoundInstance {
    private final DroneEntity drone;
    private boolean finished;

    public DroneMotorSound(DroneEntity drone) {
        super(ModSounds.DRONE_MOTOR.get(), SoundSource.PLAYERS, RandomSource.create());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.68f;
        this.pitch = 1.0f;
        updatePosition();
    }

    @Override
    public void tick() {
        if (this.drone.isRemoved() || !this.drone.isPiloted()) {
            this.finished = true;
            this.stop();
            return;
        }
        updatePosition();
    }

    public boolean isFinished() {
        return this.finished;
    }

    private void updatePosition() {
        this.x = this.drone.getX();
        this.y = this.drone.getY();
        this.z = this.drone.getZ();
    }
}
