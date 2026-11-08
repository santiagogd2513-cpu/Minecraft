package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Moto de carreras (V2.0.0). Se maneja con la base común {@link VehicleEntity}.
 * <ul>
 *   <li>Velocidad máxima: {@link #MAX_SPEED} = 6 bloques por tick = 120 bloques por segundo
 *       (en la 2.0.0 era 190; la 2.1.0 la limitó a 120).</li>
 *   <li>Salta sola los escalones de un bloque; las curvas se abren cuanto más rápido vas.</li>
 * </ul>
 */
public class MotorcycleEntity extends VehicleEntity {
    /** Velocidad máxima en bloques por tick (120 bloques por segundo). Cambiar solo este número cambia el tope. */
    public static final float MAX_SPEED = 6.0f;
    public static final float REVERSE_SPEED = 0.35f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, REVERSE_SPEED,
            0.14f,          // aceleración
            6.0f,           // giro máximo por tick (grados)
            0.22f,          // agarre lateral
            0.988f,         // fricción al soltar el gas
            0.34f,          // radio de la rueda
            17.0f, 2.6f,    // inclinación máxima y cuánto se inclina
            9.0f,           // inclinación apoyada en la pata de cabra
            0.07f, 0.6f,    // giro visual del manubrio
            new double[]{0.52, 1.02},
            1.25f,          // a qué distancia aparece al bajarte
            105.0f,         // límite de giro de la cabeza
            26.0f,          // campo de visión extra a toda velocidad
            0.72f, 1.8f);   // tono del motor (parado / a tope)

    public MotorcycleEntity(EntityType<? extends MotorcycleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Spec spec() {
        return SPEC;
    }

    @Override
    protected SoundEvent getStartSound() {
        return ModSounds.MOTORCYCLE_START.get();
    }

    @Override
    protected SoundEvent getHornSound() {
        return ModSounds.MOTORCYCLE_HORN.get();
    }

    @Override
    public SoundEvent getEngineLoop() {
        return ModSounds.MOTORCYCLE_ENGINE.get();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.47;
    }

    @Override
    public VehiclePaint.Kind paintKind() {
        return VehiclePaint.Kind.MOTORCYCLE;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.MOTORCYCLE.get());
    }
}
