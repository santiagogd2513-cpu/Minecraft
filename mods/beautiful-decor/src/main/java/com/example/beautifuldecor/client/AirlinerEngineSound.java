package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Turbinas del avión: suenan mientras giran (las oyen todos los cercanos, de lejos); el tono sube con el régimen y el esfuerzo. */
public class AirlinerEngineSound extends AbstractTickableSoundInstance {
    private final AirlinerEntity airliner;
    private boolean finished;
    private int lastTickCount = -1;
    private int frozenTicks;
    private int emptyTicks;

    public AirlinerEngineSound(AirlinerEntity airliner) {
        super(ModSounds.AIRLINER_ENGINE.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.airliner = airliner;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1f;
        this.pitch = 0.6f;
        updatePosition();
    }

    @Override
    public void tick() {
        float rpm = this.airliner.getRpm();
        Minecraft minecraft = Minecraft.getInstance();
        // V3.9.0: si el avión ya no está en el mundo del cliente, o dejó de actualizarse (rpm congeladas), o lleva rato vacío, la turbina se calla
        boolean gone = this.airliner.isRemoved() || !this.airliner.isAlive() || minecraft.level == null
                || minecraft.level.getEntity(this.airliner.getId()) != this.airliner;
        if (this.airliner.tickCount == this.lastTickCount) {
            ++this.frozenTicks;
        } else {
            this.frozenTicks = 0;
            this.lastTickCount = this.airliner.tickCount;
        }
        this.emptyTicks = this.airliner.isVehicle() ? 0 : this.emptyTicks + 1;
        if (gone || rpm < 0.03f || this.frozenTicks > 40 || this.emptyTicks > 400) {
            this.finished = true;
            this.stop();
            return;
        }
        updatePosition();
        float effort = Mth.clamp(this.airliner.getVisualSpeed() / AirlinerEntity.MAX_SPEED
                + Math.abs(this.airliner.getVisualClimb()) / AirlinerEntity.CLIMB_SPEED * 0.2f, 0.0f, 1.0f);
        this.pitch = Mth.clamp(0.55f + 0.4f * rpm + 0.35f * effort, 0.5f, 2.0f);
        this.volume = Mth.clamp(rpm * (0.9f + 2.0f * effort), 0.0f, 3.0f);        // V3.9.0: más bajo en ralentí (parado con alguien sentado)
    }

    public boolean isFinished() {
        return this.finished;
    }

    private void updatePosition() {
        this.x = this.airliner.getX();
        this.y = this.airliner.getY() + 3.0;
        this.z = this.airliner.getZ();
    }
}
