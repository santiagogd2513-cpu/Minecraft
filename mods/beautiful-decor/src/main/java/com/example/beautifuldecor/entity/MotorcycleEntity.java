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
 *   <li>Velocidad máxima: {@link #MAX_SPEED} = 1,736 bloques por tick = 34,7 bloques por segundo = 125 km/h
 *       (en la 2.0.0 era 190; la 2.1.0 la limitó a 120).</li>
 *   <li>Salta sola los escalones de un bloque; las curvas se abren cuanto más rápido vas.</li>
 * </ul>
 */
public class MotorcycleEntity extends VehicleEntity {
    /** V6.13.0: caja de colisión igual al modelo (ancho, largo, desfase hacia delante, alto; tools/v6130/hulls.py). */
    private static final VehicleHull.Spec HULL = new VehicleHull.Spec(0.93, 2.08, 0.07, 1.47);

    @Override
    protected VehicleHull.Spec hull() {
        return HULL;
    }

    /** Velocidad máxima en bloques por tick (V4.4.0: 125 km/h = 34,7 bloques por segundo). Cambiar solo este número cambia el tope. */
    public static final float MAX_SPEED = 1.736f;
    public static final float REVERSE_SPEED = 0.35f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, REVERSE_SPEED,
            0.045f,         // aceleración (V4.4.0: escalada con la velocidad máxima)
            6.0f,           // giro máximo por tick (grados)
            0.064f,         // agarre lateral (V4.4.0: escalado con la velocidad máxima)
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

    /** V4.6.0: física (VehiclePhysics): masa en vacío con el conductor, potencia, aceleración de arranque, frenos, arrastre, rodadura, freno motor. */
    private static final VehiclePhysics.Params PHYSICS = new VehiclePhysics.Params(330, 55_000, 7.0, 8.0, 3.0, 0.45, 0.015, 0.8, MAX_SPEED * 20.0, REVERSE_SPEED * 20.0);

    @Override
    protected VehiclePhysics.Params physics() {
        return PHYSICS;
    }

    @Override
    protected Spec spec() {
        return SPEC;
    }

    @Override
    protected SoundEvent getStartSound() {
        return ModSounds.MOTORCYCLE_START.get();
    }

    /** V6.7.0: skins: 0 = original, 1 = policía (barra de luces y sirena), 2 = repartidor (caja de reparto). */
    @Override
    public int skinCount() {
        return 3;
    }

    @Override
    public int policeSkin() {
        return 1;
    }

    @Override
    public String skinKey(int skin) {
        return "skin.beautifuldecor.motorcycle." + skin;
    }

    @Override
    public boolean hasCabButtons() {
        return true;
    }

    /** V6.7.0: botones (medidas del modelo, tools/v200/moto_model.py): luces en el manubrio izquierdo, claxon en el derecho, radio en el tablero y sirena sobre el tanque. */
    private static final double[][] BUTTONS = {
            {0.1875, 1.02, 0.38, 0.09, ACTION_LIGHT},
            {-0.1875, 1.02, 0.38, 0.09, ACTION_HORN},
            {0.0, 1.19, 0.36, 0.08, ACTION_RADIO},
            {0.0, 1.125, 0.03, 0.09, ACTION_SIREN}};

    @Override
    protected double[][] cabButtons() {
        return BUTTONS;
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
