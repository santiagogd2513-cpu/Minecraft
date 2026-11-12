package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Camioneta Furd F-150 azul oscuro (V2.1.0): cabe el conductor (asiento izquierdo) y un acompañante.
 * Velocidad máxima {@link #MAX_SPEED} = 1,111 bloques por tick = 22,2 bloques por segundo = 80 km/h. Sube escalones de un bloque.
 * <p>
 * Ojo: la caja de colisión de cualquier entidad no gira con ella, así que es un rectángulo más corto que el modelo;
 * la camioneta se ve de 4,5 bloques de largo pero choca como una caja de 2,2 x 2,2.
 */
public class PickupEntity extends VehicleEntity {
    /** Velocidad máxima en bloques por tick (V4.4.0: 80 km/h = 22,2 bloques por segundo). */
    public static final float MAX_SPEED = 1.111f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.40f,
            0.03f,          // aceleración (V4.4.0: escalada con la velocidad máxima)
            4.5f,           // giro máximo por tick (grados)
            0.034f,         // agarre lateral (V4.4.0: escalado con la velocidad máxima)
            0.99f,          // rueda libre
            0.45f,          // radio de la rueda
            3.0f, -0.9f,    // la carrocería se balancea un poco hacia afuera en las curvas
            0.0f,           // sin pata de cabra
            0.09f, 0.55f,   // giro visual de las ruedas delanteras
            new double[]{0.52, 1.02},
            1.9f,
            140.0f,
            14.0f,
            0.8f, 1.5f);

    /** Lado de cada asiento (positivo = izquierda del vehículo) y cuánto hacia adelante respecto al centro. */
    private static final double SEAT_SIDE = 0.42;
    private static final double SEAT_FORWARD = 0.3;

    public PickupEntity(EntityType<? extends PickupEntity> type, Level level) {
        super(type, level);
    }

    /** V3.9.0: almacenamiento de 3 fila(s) de 9 casillas. */
    @Override
    public int storageRows() {
        return 3;
    }

    /** V4.6.0: física (VehiclePhysics): masa en vacío con el conductor, potencia, aceleración de arranque, frenos, arrastre, rodadura, freno motor. */
    private static final VehiclePhysics.Params PHYSICS = new VehiclePhysics.Params(2500, 190_000, 3.5, 7.5, 3.5, 1.3, 0.015, 0.9, MAX_SPEED * 20.0, 0.40f * 20.0);

    @Override
    protected VehiclePhysics.Params physics() {
        return PHYSICS;
    }

    @Override
    protected Spec spec() {
        return SPEC;
    }

    @Override
    protected int seatCount() {
        return 2;
    }

    @Override
    protected SoundEvent getStartSound() {
        return ModSounds.PICKUP_START.get();
    }

    @Override
    protected SoundEvent getHornSound() {
        return ModSounds.PICKUP_HORN.get();
    }

    @Override
    public SoundEvent getEngineLoop() {
        return ModSounds.PICKUP_ENGINE.get();
    }

    @Override
    public float[] getGearFractions() {
        return new float[]{0.0f, 0.18f, 0.36f, 0.56f, 0.78f, 1.0f, 1.01f};
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.37;
    }

    /** Dos asientos: conductor a la izquierda y acompañante a la derecha. */
    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int index = this.getPassengers().indexOf(passenger);
        double side = index == 0 ? SEAT_SIDE : -SEAT_SIDE;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double ox = Mth.cos(yawRad) * side - Mth.sin(yawRad) * SEAT_FORWARD;
        double oz = Mth.sin(yawRad) * side + Mth.cos(yawRad) * SEAT_FORWARD;
        double y = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
        callback.accept(passenger, this.getX() + ox, y, this.getZ() + oz);
    }

    @Override
    public VehiclePaint.Kind paintKind() {
        return VehiclePaint.Kind.PICKUP;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.FORD_F150.get());
    }
}
