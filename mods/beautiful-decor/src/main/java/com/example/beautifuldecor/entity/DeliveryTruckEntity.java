package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import com.example.beautifuldecor.lottery.PrizeTruckDriver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Camión de reparto AMAZOOM (V3.0.0, parodia de una marca de envíos famosa): furgón blanco con caja de carga, cabina con
 * dos asientos (conductor a la izquierda y acompañante). Más pesado y lento que la camioneta: velocidad máxima
 * {@link #MAX_SPEED} = 0,764 bloques por tick = 15,3 bloques por segundo = 55 km/h, gira más cerrado y suena a motor diésel.
 * Se maneja igual que los demás vehículos (ver {@link VehicleEntity}).
 * <p>
 * Ojo: la caja de colisión de cualquier entidad no gira con ella, así que es un rectángulo más corto que el modelo;
 * el camión se ve de casi 6 bloques de largo pero choca como una caja de 2,4 x 2,4.
 */
public class DeliveryTruckEntity extends VehicleEntity {
    /** V6.13.0: caja de colisión igual al modelo (ancho, largo, desfase hacia delante, alto; tools/v6130/hulls.py). */
    private static final VehicleHull.Spec HULL = new VehicleHull.Spec(2.12, 6.38, -1.25, 2.75);

    @Override
    protected VehicleHull.Spec hull() {
        return HULL;
    }

    /** Velocidad máxima en bloques por tick (V4.4.0: 55 km/h = 15,3 bloques por segundo). */
    public static final float MAX_SPEED = 0.764f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.30f,
            0.02f,          // aceleración: pesado, tarda en arrancar (V4.4.0: escalada)
            3.4f,           // giro máximo por tick (grados)
            0.027f,         // agarre lateral (V4.4.0: escalado)
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

    // ---- V6.15.0: camión de premios de la lotería (maneja solo; ver PrizeTruckDriver)
    public int prizeTier;
    public int prizeStage;
    public int prizeTicks;
    public UUID prizeOwner;
    public Vec3 prizeTarget = Vec3.ZERO;
    public Vec3 prizeSpawn = Vec3.ZERO;

    public void startPrize(int tier, UUID owner, Vec3 target, Vec3 spawn) {
        this.prizeTier = tier;
        this.prizeOwner = owner;
        this.prizeTarget = target;
        this.prizeSpawn = spawn;
        this.prizeStage = PrizeTruckDriver.ARRIVING;
        this.prizeTicks = 0;
    }

    @Override
    public void tick() {
        if (this.prizeTier > 0 && !this.level().isClientSide) {
            PrizeTruckDriver.drive(this);
            if (this.isRemoved()) {
                return;
            }
        }
        super.tick();
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return this.prizeTier == 0 && super.canAddPassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return this.prizeTier > 0 ? InteractionResult.PASS : super.interact(player, hand);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return this.prizeTier > 0 ? false : super.hurt(source, amount);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getInt("PrizeTier") > 0) {
            this.discard();                                       // un camión de premios que quedó guardado: ya no tiene a quién entregar
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PrizeTier", this.prizeTier);
    }

    /** V3.9.0: almacenamiento de 6 fila(s) de 9 casillas. */
    @Override
    public int storageRows() {
        return 6;
    }

    /** V4.6.0: física (VehiclePhysics): masa en vacío con el conductor, potencia, aceleración de arranque, frenos, arrastre, rodadura, freno motor. */
    private static final VehiclePhysics.Params PHYSICS = new VehiclePhysics.Params(7500, 150_000, 1.6, 5.0, 3.0, 3.6, 0.012, 1.2, MAX_SPEED * 20.0, 0.30f * 20.0);

    @Override
    protected VehiclePhysics.Params physics() {
        return PHYSICS;
    }

    @Override
    protected Spec spec() {
        return SPEC;
    }

    @Override
    public boolean hasCabButtons() {
        return true;
    }

    /** V6.3.0: botones del tablero (medidas del modelo, tools/v300/delivery_truck.py): centro del volante = claxon, luces y radio en el tablero. */
    private static final double[][] BUTTONS = {
            {0.4375, 1.28, 0.91, 0.19, ACTION_HORN},
            {0.125, 1.31, 1.03, 0.10, ACTION_LIGHT},
            {-0.125, 1.31, 1.03, 0.10, ACTION_RADIO}};

    @Override
    protected double[][] cabButtons() {
        return BUTTONS;
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
