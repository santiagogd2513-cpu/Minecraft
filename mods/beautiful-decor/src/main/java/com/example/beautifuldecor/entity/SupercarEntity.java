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
 * Superdeportivo Bugotti Chillón (V3.7.0, parodia de un hiperdeportivo famoso; diseño propio): cabe el conductor (asiento izquierdo) y un
 * acompañante. Velocidad máxima {@link #MAX_SPEED} = 12,5 bloques por tick = 250 bloques por segundo, el vehículo terrestre más rápido del mod.
 * Acelera fuerte (de 0 a tope en unos 5 segundos), tiene mucho agarre y su motor suena agudo. Sube escalones de un bloque.
 * Se maneja igual que los demás vehículos (ver {@link VehicleEntity}); la radio con la tecla X y los colores con la tecla R ya vienen de ahí.
 * <p>
 * Ojo: la caja de colisión de cualquier entidad no gira con ella, así que es un rectángulo más corto que el modelo;
 * el auto se ve de 5 bloques de largo pero choca como una caja de 2,1 x 1,3.
 */
public class SupercarEntity extends VehicleEntity {
    /** Velocidad máxima en bloques por tick (250 bloques por segundo). Cambiar solo este número cambia el tope. */
    public static final float MAX_SPEED = 12.5f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.50f,
            0.45f,          // aceleración
            5.5f,           // giro máximo por tick (grados)
            0.95f,          // agarre lateral (a tope gira 4,3 grados por tick)
            0.99f,          // rueda libre
            0.34f,          // radio de la rueda
            1.5f, -0.5f,    // la carrocería se balancea un poco hacia afuera en las curvas
            0.0f,           // sin pata de cabra
            0.09f, 0.45f,   // giro visual de las ruedas delanteras
            new double[]{0.52, 1.02},
            1.9f,
            140.0f,
            30.0f,
            0.70f, 2.0f);

    /** Lado de cada asiento (positivo = izquierda del vehículo) y cuánto hacia adelante respecto al centro. */
    private static final double SEAT_SIDE = 0.40625;
    private static final double SEAT_FORWARD = -0.3125;

    public SupercarEntity(EntityType<? extends SupercarEntity> type, Level level) {
        super(type, level);
    }

    /** V3.9.0: almacenamiento de 1 fila(s) de 9 casillas. */
    @Override
    public int storageRows() {
        return 1;
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
        return ModSounds.BUGOTTI_CHILLON_START.get();
    }

    @Override
    protected SoundEvent getHornSound() {
        return ModSounds.BUGOTTI_CHILLON_HORN.get();
    }

    @Override
    public SoundEvent getEngineLoop() {
        return ModSounds.BUGOTTI_CHILLON_ENGINE.get();
    }

    @Override
    public float[] getGearFractions() {
        return new float[]{0.0f, 0.10f, 0.20f, 0.32f, 0.46f, 0.62f, 0.80f, 1.0f, 1.01f};
    }

    /** La base del asiento está a 6 píxeles del suelo: el jugador va casi a ras, como en un auto bajo. */
    @Override
    public double getPassengersRidingOffset() {
        return -0.03;
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
        return VehiclePaint.Kind.SUPERCAR;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.BUGOTTI_CHILLON.get());
    }
}
