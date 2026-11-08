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
 * Camión de reparto AMAZOOM (V3.0.0, parodia de una marca de envíos famosa): furgón blanco con caja de carga, cabina con
 * dos asientos (conductor a la izquierda y acompañante). Más pesado y lento que la camioneta: velocidad máxima
 * {@link #MAX_SPEED} = 2,4 bloques por tick = 48 bloques por segundo, gira más cerrado y suena a motor diésel.
 * Se maneja igual que los demás vehículos (ver {@link VehicleEntity}).
 * <p>
 * Ojo: la caja de colisión de cualquier entidad no gira con ella, así que es un rectángulo más corto que el modelo;
 * el camión se ve de casi 6 bloques de largo pero choca como una caja de 2,4 x 2,4.
 */
public class DeliveryTruckEntity extends VehicleEntity {
    /** Velocidad máxima en bloques por tick (48 bloques por segundo). */
    public static final float MAX_SPEED = 2.4f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.30f,
            0.05f,          // aceleración: pesado, tarda en arrancar
            3.4f,           // giro máximo por tick (grados)
            0.085f,         // agarre lateral
            0.988f,         // rueda libre
            0.47f,          // radio de la rueda
            2.2f, -0.7f,    // la carrocería se balancea un poco hacia afuera en las curvas
            0.0f,           // sin pata de cabra
            0.09f, 0.50f,   // giro visual de las ruedas delanteras
            new double[]{0.52, 1.02},
            2.2f,
            140.0f,
            9.0f,
            0.72f, 1.28f);

    /** Lado de cada asiento (positivo = izquierda del vehículo) y cuánto hacia adelante respecto al centro. */
    private static final double SEAT_SIDE = 0.4375;
    private static final double SEAT_FORWARD = 0.5625;

    public DeliveryTruckEntity(EntityType<? extends DeliveryTruckEntity> type, Level level) {
        super(type, level);
    }

    /** V3.9.0: almacenamiento de 6 fila(s) de 9 casillas. */
    @Override
    public int storageRows() {
        return 6;
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
        return ModSounds.DELIVERY_TRUCK_START.get();
    }

    @Override
    protected SoundEvent getHornSound() {
        return ModSounds.DELIVERY_TRUCK_HORN.get();
    }

    @Override
    protected float hornVolume() {
        return 2.0f;
    }

    @Override
    public SoundEvent getEngineLoop() {
        return ModSounds.DELIVERY_TRUCK_ENGINE.get();
    }

    @Override
    public float[] getGearFractions() {
        return new float[]{0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f, 1.01f};
    }

    /** La base del asiento está a 14 píxeles del suelo (la de la camioneta, a 12): la cabina es más alta. */
    @Override
    public double getPassengersRidingOffset() {
        return 0.5;
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
        return VehiclePaint.Kind.DELIVERY_TRUCK;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.DELIVERY_TRUCK.get());
    }
}
