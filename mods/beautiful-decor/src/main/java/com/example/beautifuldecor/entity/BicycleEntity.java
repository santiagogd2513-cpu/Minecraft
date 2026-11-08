package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bicicleta (V2.1.0): velocidad máxima {@link #MAX_SPEED} = 2 bloques por tick = 40 bloques por segundo.
 * Sin motor ni arranque; H toca el timbre. Aguanta escalones de medio bloque (losas y escaleras).
 */
public class BicycleEntity extends VehicleEntity {
    /** Velocidad máxima en bloques por tick (40 bloques por segundo). */
    public static final float MAX_SPEED = 2.0f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.12f,
            0.06f,          // aceleración (pedaleando)
            8.0f,           // giro máximo por tick (grados)
            0.10f,          // agarre lateral
            0.985f,         // rueda libre al dejar de pedalear
            0.34f,
            22.0f, 3.2f,    // se inclina bastante en las curvas
            8.0f,           // apoyada en la pata
            0.09f, 0.6f,
            new double[]{0.52},
            1.0f,
            120.0f,
            9.0f,
            1.0f, 1.0f);

    public BicycleEntity(EntityType<? extends BicycleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Spec spec() {
        return SPEC;
    }

    @Override
    protected SoundEvent getStartSound() {
        return null;
    }

    @Override
    protected SoundEvent getHornSound() {
        return ModSounds.BICYCLE_BELL.get();
    }

    @Override
    protected float hornVolume() {
        return 1.0f;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.55;
    }

    @Override
    public VehiclePaint.Kind paintKind() {
        return VehiclePaint.Kind.BICYCLE;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.BICYCLE.get());
    }
}
