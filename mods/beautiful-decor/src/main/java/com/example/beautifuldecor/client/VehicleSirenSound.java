package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.SkinnableVehicle;
import net.minecraft.world.entity.Entity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** V6.7.0: sirena de un vehículo de policía: suena mientras esté encendida. */
public class VehicleSirenSound extends AbstractTickableSoundInstance {
    private final Entity vehicle;
    private final SkinnableVehicle skinnable;
    private boolean finished;

    public VehicleSirenSound(Entity vehicle, SkinnableVehicle skinnable) {
        super(ModSounds.VEHICLE_SIREN.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        this.skinnable = skinnable;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;
        follow();
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved() || !this.skinnable.isSirenOn()) {
            this.finished = true;
            this.stop();
            return;
        }
        follow();
    }

    public boolean isFinished() {
        return this.finished;
    }

    private void follow() {
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY();
        this.z = this.vehicle.getZ();
    }
}
