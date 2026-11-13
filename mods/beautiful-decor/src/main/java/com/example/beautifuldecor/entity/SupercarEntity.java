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
 * acompañante. Velocidad máxima {@link #MAX_SPEED} = 6 bloques por tick = 120 bloques por segundo = 432 km/h, el vehículo terrestre más rápido del mod.
 * Acelera fuerte (de 0 a tope en unos 5 segundos), tiene mucho agarre y su motor suena agudo. Sube escalones de un bloque.
 * Se maneja igual que los demás vehículos (ver {@link VehicleEntity}); la radio con la tecla X y los colores con la tecla R ya vienen de ahí.
 * <p>
 * Ojo: la caja de colisión de cualquier entidad no gira con ella, así que es un rectángulo más corto que el modelo;
 * el auto se ve de 5 bloques de largo pero choca como una caja de 2,1 x 1,3.
 */
public class SupercarEntity extends VehicleEntity {
    /** V6.13.0: caja de colisión igual al modelo (ancho, largo, desfase hacia delante, alto; tools/v6130/hulls.py). */
    private static final VehicleHull.Spec HULL = new VehicleHull.Spec(2.25, 4.94, -0.03, 1.56);

    @Override
    protected VehicleHull.Spec hull() {
        return HULL;
    }

    /** Velocidad máxima en bloques por tick (V4.4.0: 120 bloques por segundo = 432 km/h). Cambiar solo este número cambia el tope. */
    public static final float MAX_SPEED = 6.0f;

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.50f,
            0.22f,          // aceleración (V4.4.0: escalada con la velocidad máxima)
            5.5f,           // giro máximo por tick (grados)
            0.46f,          // agarre lateral (V4.4.0: escalado con la velocidad máxima)
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

    /** V4.6.0: física (VehiclePhysics): masa en vacío con el conductor, potencia, aceleración de arranque, frenos, arrastre, rodadura, freno motor. */
    private static final VehiclePhysics.Params PHYSICS = new VehiclePhysics.Params(1900, 850_000, 9.0, 11.0, 4.0, 0.7, 0.012, 1.0, MAX_SPEED * 20.0, 0.50f * 20.0);

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

    /** V6.5.0: botones del tablero (medidas del modelo, tools/v370/supercar.py): centro del volante = claxon, luces y radio en el tablero. */
    private static final double[][] BUTTONS = {
            {0.406, 0.72, 0.19, 0.15, ACTION_HORN},
            {0.125, 0.56, 0.22, 0.09, ACTION_LIGHT},
            {-0.125, 0.56, 0.22, 0.09, ACTION_RADIO},
            {-0.3125, 0.56, 0.22, 0.09, ACTION_SIREN}};

    /** V6.14.0: skins: 0 = original, 1 = carreras, 2 = policía (con sirena; botón rojo del tablero), 3 = callejero, 4 = taxi. */
    @Override
    public int skinCount() {
        return 5;
    }

    @Override
    public int policeSkin() {
        return 2;
    }

    @Override
    public String skinKey(int skin) {
        return "skin.beautifuldecor.bugotti_chillon." + skin;
    }

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
