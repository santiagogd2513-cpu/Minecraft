package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.VehicleEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Motor de un vehículo: suena mientras alguien lo monta; el tono sube con la velocidad y "cambia de marcha". */
public class VehicleEngineSound extends AbstractTickableSoundInstance {
    private final VehicleEntity vehicle;
    /** Velocidad (bloques por tick) a la que termina cada marcha. */
    private final float[] gears;
    private final float pitchLow;
    private final float pitchHigh;
    private boolean finished;
    private float smoothPitch;
    private static final int SHIFT_TICKS = 7;
    private int lastGear = -1;
    private int shiftTicks;
    private float blip;

    public VehicleEngineSound(VehicleEntity vehicle, SoundEvent loop) {
        super(loop, SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        float[] fractions = vehicle.getGearFractions();
        this.gears = new float[fractions.length];
        for (int i = 0; i < fractions.length; i++) {
            this.gears[i] = fractions[i] * vehicle.getMaxSpeed();
        }
        this.gears[this.gears.length - 1] += 0.01f;
        this.pitchLow = vehicle.getSpec().pitchLow();
        this.pitchHigh = vehicle.getSpec().pitchHigh();
        this.smoothPitch = this.pitchLow;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.7f;
        this.pitch = this.pitchLow;
        updatePosition();
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved() || !this.vehicle.isVehicle()) {
            this.finished = true;
            this.stop();
            return;
        }
        updatePosition();

        float speed = Math.abs(this.vehicle.getVisualSpeed());
        int gear = 0;
        while (gear < this.gears.length - 2 && speed >= this.gears[gear + 1]) {
            gear++;
        }
        float t = Mth.clamp((speed - this.gears[gear]) / (this.gears[gear + 1] - this.gears[gear]), 0.0f, 1.0f);

        // V4.4.0: marchas más realistas. En 1ª las revoluciones suben desde ralentí; en las demás arrancan a ~40 % (no vuelven a ralentí
        // al cambiar). Al subir de marcha se corta el gas un instante (las revoluciones caen y el motor baja de volumen); al bajar de marcha
        // (frenando) hay un "golpe" de revoluciones.
        float rev = gear == 0 ? t : 0.40f + 0.60f * t;
        if (this.lastGear >= 0 && gear > this.lastGear) {
            this.shiftTicks = SHIFT_TICKS;
            this.blip = 0.0f;
        } else if (this.lastGear >= 0 && gear < this.lastGear) {
            this.blip = 0.22f;
            this.shiftTicks = 0;
        }
        this.lastGear = gear;
        float cut = 0.0f;
        if (this.shiftTicks > 0) {
            --this.shiftTicks;
            cut = 0.28f * Mth.sin((float) Math.PI * (this.shiftTicks / (float) SHIFT_TICKS));
        }
        this.blip *= 0.85f;
        rev = Mth.clamp(rev - cut + this.blip, 0.0f, 1.0f);
        float target = this.pitchLow + (this.pitchHigh - this.pitchLow) * (float) Math.pow(rev, 0.85);
        // las revoluciones suben con algo de inercia y bajan más rápido
        float follow = target > this.smoothPitch ? 0.22f : 0.40f;
        this.smoothPitch += (target - this.smoothPitch) * follow;
        this.pitch = Mth.clamp(this.smoothPitch, 0.5f, 2.0f);
        this.volume = (0.6f + 0.4f * Mth.clamp(speed / this.vehicle.getMaxSpeed(), 0.0f, 1.0f)) * (1.0f - 0.35f * (cut / 0.28f));
    }

    public boolean isFinished() {
        return this.finished;
    }

    private void updatePosition() {
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY();
        this.z = this.vehicle.getZ();
    }
}
