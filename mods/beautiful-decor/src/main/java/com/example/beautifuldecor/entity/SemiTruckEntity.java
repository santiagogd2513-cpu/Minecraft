package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * V7.9.0: tractomula KENWORT K880 (parodia de una marca famosa de tractocamiones). Cabina con camarote, dos asientos, 3 filas de almacenamiento
 * y quinta rueda detrás de la cabina: ahí se engancha un {@link TrailerEntity}. Se engancha solo al retroceder bajo el remolque o con el botón
 * del tablero (el naranja, a la izquierda de la radio), que también lo suelta. Con remolque pesa más y arranca y frena más despacio
 * (física de {@link VehiclePhysics} con la masa del remolque y su carga).
 */
public class SemiTruckEntity extends VehicleEntity {
    /** Botón del tablero: engancha o suelta el remolque. */
    public static final int ACTION_HITCH = 4;
    /** V7.10.0: botón azul del tablero: rampas de la niñera (cerrada, rampa baja, rampa alta). */
    public static final int ACTION_RAMP = 5;
    /** Cuánto está la quinta rueda detrás del origen (36 px del modelo). */
    public static final double HITCH_BACK = 2.25;
    public static final float MAX_SPEED = 0.72f;

    private static final EntityDataAccessor<Integer> TRAILER_ID =
            SynchedEntityData.defineId(SemiTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRAILER_KG =
            SynchedEntityData.defineId(SemiTruckEntity.class, EntityDataSerializers.INT);

    private static final VehicleHull.Spec HULL = new VehicleHull.Spec(2.8, 7.7, 0.0, 3.5);

    private static final Spec SPEC = new Spec(
            MAX_SPEED, 0.28f,
            0.016f,         // aceleración inicial: pesado
            2.8f,           // giro máximo por tick (grados): tractor largo
            0.024f,         // agarre lateral
            0.988f,         // rueda libre
            0.5f,           // radio de la rueda
            1.8f, -0.5f,    // la carrocería se balancea un poco hacia afuera en las curvas
            0.0f,           // sin pata de cabra
            0.08f, 0.45f,   // giro visual de las ruedas delanteras
            new double[]{0.52, 1.02},
            2.4f,
            140.0f,
            7.0f,
            0.66f, 1.2f);

    private static final VehiclePhysics.Params PHYSICS = new VehiclePhysics.Params(9000, 330_000, 1.3, 4.5, 3.0, 4.2, 0.012, 1.2, MAX_SPEED * 20.0, 0.28f * 20.0);

    /** Lado de cada asiento (positivo = izquierda) y cuánto hacia adelante respecto al origen. */
    private static final double SEAT_SIDE = 0.5;
    private static final double SEAT_FORWARD = 0.25;

    /** Botones del tablero (medidas del modelo, tools/v790/semi_truck.py): claxon (centro del volante), luces, radio y enganche. */
    private static final double[][] BUTTONS = {
            {0.5, 1.75, 0.75, 0.19, ACTION_HORN},
            {0.125, 1.81, 0.906, 0.10, ACTION_LIGHT},
            {-0.125, 1.81, 0.906, 0.10, ACTION_RADIO},
            {-0.5, 1.81, 0.906, 0.12, ACTION_HITCH},
            {-0.3125, 1.81, 0.906, 0.09, ACTION_RAMP}};

    public SemiTruckEntity(EntityType<? extends SemiTruckEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TRAILER_ID, -1);
        this.entityData.define(TRAILER_KG, 0);
    }

    // ------------------------------------------------------------------------------------------------ lo de siempre de un vehículo
    @Override
    protected VehicleHull.Spec hull() {
        return HULL;
    }

    @Override
    protected Spec spec() {
        return SPEC;
    }

    @Override
    protected VehiclePhysics.Params physics() {
        return PHYSICS;
    }

    @Override
    protected VehicleImpact.Profile impactProfile() {
        return VehicleImpact.TRUCK;
    }

    /** Masa de ahora: la del tractor, los que van a bordo y su almacenamiento, más el remolque enganchado con su carga. */
    @Override
    public double totalMassKg() {
        return super.totalMassKg() + this.entityData.get(TRAILER_KG);
    }

    @Override
    public int storageRows() {
        return 3;
    }

    @Override
    public boolean hasCabButtons() {
        return true;
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
        return ModSounds.DELIVERY_TRUCK_START.get();
    }

    @Override
    protected SoundEvent getHornSound() {
        return ModSounds.DELIVERY_TRUCK_HORN.get();
    }

    @Override
    protected float hornVolume() {
        return 2.4f;
    }

    @Override
    public SoundEvent getEngineLoop() {
        return ModSounds.DELIVERY_TRUCK_ENGINE.get();
    }

    @Override
    public float[] getGearFractions() {
        return new float[]{0.0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1.01f};
    }

    /** La base del asiento está a 17 píxeles del suelo. */
    @Override
    public double getPassengersRidingOffset() {
        return 0.6875;
    }

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
        return VehiclePaint.Kind.SEMI_TRUCK;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.SEMI_TRUCK.get());
    }

    // ------------------------------------------------------------------------------------------------ el remolque
    /** Punto de la quinta rueda en el mundo (a nivel del suelo del tractor). */
    public Vec3 hitchPoint() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(this.getX() + Mth.sin(yaw) * HITCH_BACK, this.getY(), this.getZ() - Mth.cos(yaw) * HITCH_BACK);
    }

    public int getTrailerId() {
        return this.entityData.get(TRAILER_ID);
    }

    @Nullable
    public TrailerEntity getTrailer() {
        int id = this.getTrailerId();
        if (id < 0) {
            return null;
        }
        Entity e = this.level().getEntity(id);
        return e instanceof TrailerEntity t && !t.isRemoved() ? t : null;
    }

    public void setTrailerId(int id) {
        this.entityData.set(TRAILER_ID, id);
        if (id < 0) {
            this.entityData.set(TRAILER_KG, 0);
        }
    }

    /** El remolque avisa cada tanto cuánto pesa con su carga. */
    public void setTrailerKg(int kg) {
        if (this.entityData.get(TRAILER_KG) != kg) {
            this.entityData.set(TRAILER_KG, kg);
        }
    }

    @Override
    public void tick() {
        super.tick();
        int id = this.getTrailerId();
        if (id >= 0) {
            Entity e = this.level().getEntity(id);
            if (e instanceof TrailerEntity trailer && !trailer.isRemoved()) {
                trailer.follow(this);
            } else if (!this.level().isClientSide) {
                this.setTrailerId(-1);                                  // el remolque ya no existe
            }
        } else if (!this.level().isClientSide && this.tickCount % 4 == 0 && this.getControllingPassenger() != null) {
            this.tryAutoCouple();
        }
    }

    /** Retrocediendo bajo un remolque suelto (perno a menos de ~1 bloque de la quinta rueda y bien alineado) se engancha solo. */
    private void tryAutoCouple() {
        if (this.getTrailer() != null || Math.abs(this.getVisualSpeed()) > 0.35f) {
            return;
        }
        TrailerEntity best = this.findFreeTrailer(1.0, 40.0);
        if (best != null) {
            this.couple(best);
        }
    }

    @Nullable
    private TrailerEntity findFreeTrailer(double radius, double maxYawDiff) {
        Vec3 h = this.hitchPoint();
        AABB box = new AABB(h, h).inflate(radius, 2.0, radius);
        List<TrailerEntity> list = this.level().getEntitiesOfClass(TrailerEntity.class, box);
        TrailerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (TrailerEntity t : list) {
            if (t.isCoupled() || t.isRemoved()) {
                continue;
            }
            double dx = t.getX() - h.x;
            double dz = t.getZ() - h.z;
            double d = dx * dx + dz * dz;
            double yawDiff = Math.abs(Mth.wrapDegrees(t.getYRot() - this.getYRot()));
            if (d <= radius * radius && yawDiff <= maxYawDiff && d < bestD) {
                best = t;
                bestD = d;
            }
        }
        return best;
    }

    private void couple(TrailerEntity trailer) {
        this.setTrailerId(trailer.getId());
        trailer.setTractor(this);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.7f, 0.6f);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.NEUTRAL, 0.8f, 0.7f);
    }

    public void uncouple() {
        TrailerEntity trailer = this.getTrailer();
        if (trailer != null) {
            trailer.release();
        }
        this.setTrailerId(-1);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 0.8f, 0.7f);
    }

    /** Botón del tablero: suelta el remolque o engancha el que esté cerca (a menos de unos 3 bloques y no muy cruzado). */
    @Override
    protected void cabAction(ServerPlayer player, int action) {
        if (action == ACTION_RAMP) {
            TrailerEntity t = this.getTrailer();
            if (t == null) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.ramp_no_trailer"), true);
            } else {
                t.cycleRamp(player);
            }
            return;
        }
        if (action != ACTION_HITCH) {
            return;
        }
        if (this.getTrailerId() >= 0) {
            this.uncouple();
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_released"), true);
            return;
        }
        TrailerEntity near = this.findFreeTrailer(3.5, 75.0);
        if (near != null) {
            this.couple(near);
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_hitched"), true);
        } else {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_none"), true);
        }
    }

    /** Al recoger o destruir el tractor, el remolque se queda suelto donde está. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && this.getTrailerId() >= 0) {
            TrailerEntity trailer = this.getTrailer();
            if (trailer != null) {
                trailer.release();
            }
        }
        super.remove(reason);
    }
}
