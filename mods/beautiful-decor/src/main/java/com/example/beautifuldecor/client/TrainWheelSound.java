package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** V6.2.0: sonido en bucle de un coche (rodadura, chirrido de freno o freno de emergencia) que sigue al coche. */
public class TrainWheelSound extends AbstractTickableSoundInstance {
    public enum Mode { ROLL, BRAKE, EMERGENCY }

    private final TrainCarEntity car;
    private final Mode mode;
    private int idle;

    public TrainWheelSound(TrainCarEntity car, Mode mode) {
        super(eventFor(car, mode), SoundSource.NEUTRAL, RandomSource.create());
        this.car = car;
        this.mode = mode;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = 1.0f;
        follow();
    }

    private static SoundEvent eventFor(TrainCarEntity car, Mode mode) {
        boolean metro = car.variant().isMetro();
        if (mode == Mode.EMERGENCY) {
            return ModSounds.TRAIN_EMERGENCY.get();
        }
        if (mode == Mode.BRAKE) {
            return metro ? ModSounds.TRAIN_BRAKE_METRO.get() : ModSounds.TRAIN_BRAKE_SQUEAL.get();
        }
        if (metro) {
            return ModSounds.TRAIN_ROLL_METRO.get();
        }
        if (car.variant().isSteam()) {
            return ModSounds.TRAIN_ROLL_STEAM.get();
        }
        if (car.variant().isCoach()) {
            return ModSounds.TRAIN_ROLL_PASSENGER.get();
        }
        return ModSounds.TRAIN_ROLL_FREIGHT.get();
    }

    /** Velocidad de referencia (bloques/tick) a la que el sonido suena con tono 1. */
    private float ref() {
        TrainCarEntity.Variant v = this.car.variant();
        if (v.isMetro()) {
            return 0.9f;
        }
        if (v.isSteam()) {
            return 0.345f;
        }
        if (v.isCoach()) {
            return 0.83f;
        }
        return 0.69f;
    }

    @Override
    public void tick() {
        if (this.car.isRemoved()) {
            this.stop();
            return;
        }
        float speed = Math.abs(this.car.visualSpeed());
        int state = this.car.brakeState();
        float targetVol;
        boolean metro = this.car.variant().isMetro();
        switch (this.mode) {
            case ROLL -> {
                targetVol = speed < 0.02f ? 0.0f : Mth.clamp(0.25f + speed * 1.1f, 0.0f, 1.0f);
                this.pitch = Mth.clamp(speed / ref(), 0.5f, 2.0f);
            }
            case BRAKE -> {
                targetVol = (state == 1 && speed > 0.04f) ? Mth.clamp(speed * 1.6f, 0.15f, 1.0f) * (metro ? 0.25f : 1.0f) : 0.0f;
                this.pitch = Mth.clamp(0.8f + speed * 0.6f, 0.7f, 1.3f);
            }
            default -> {
                targetVol = (state == 2 && speed > 0.03f) ? (metro ? 0.5f : 1.0f) : 0.0f;
                this.pitch = metro ? 1.25f : Mth.clamp(0.85f + speed * 0.4f, 0.8f, 1.2f);
            }
        }
        this.volume += (targetVol - this.volume) * 0.25f;
        if (this.volume < 0.01f) {
            this.volume = 0.0001f;
            if (++this.idle > 40) {
                this.stop();
                this.car.soundDone(this.mode.ordinal());
                return;
            }
        } else {
            this.idle = 0;
        }
        follow();
    }

    private void follow() {
        this.x = this.car.getX();
        this.y = this.car.getY() + 0.5;
        this.z = this.car.getZ();
    }
}
