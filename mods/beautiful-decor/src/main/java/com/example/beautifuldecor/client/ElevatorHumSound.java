package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.ElevatorCabinEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Zumbido del motor del ascensor mientras la cabina viaja: más agudo y fuerte a más velocidad. */
public class ElevatorHumSound extends AbstractTickableSoundInstance {
    private final ElevatorCabinEntity cabin;

    public ElevatorHumSound(ElevatorCabinEntity cabin) {
        super(ModSounds.ELEVATOR_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
        this.cabin = cabin;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.5f;
        this.pitch = 0.85f;
        updatePosition();
    }

    @Override
    public void tick() {
        if (this.cabin.isRemoved() || this.cabin.isLanded()) {
            this.stop();
            return;
        }
        updatePosition();
        float fraction = this.cabin.getSpeedFraction();
        this.pitch = 0.8f + 0.35f * fraction;
        this.volume = 0.35f + 0.65f * fraction;
    }

    private void updatePosition() {
        this.x = this.cabin.getX();
        this.y = this.cabin.getY() + 1.5;
        this.z = this.cabin.getZ();
    }
}
