package com.arsenalatomico.entity;

import com.arsenalatomico.registry.ModEntities;
import com.arsenalatomico.registry.ModItems;
import com.arsenalatomico.strike.SolNegro;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * El camión militar ruso (verde oliva, con lona atrás y la banderita en la puerta).
 * <ul>
 *   <li>Suelto (del inventario creativo): el jugador se sube con clic derecho y lo maneja con las
 *   teclas de caminar, como un bote pero en tierra. Trepa escalones de un bloque.</li>
 *   <li>En la misión del Protocolo Sol Negro: lo maneja el conductor misterioso (nunca se le ve la
 *   cara) y el jugador va de copiloto; el servidor lo lleva por una lista de puntos.</li>
 * </ul>
 */
public class TruckEntity extends Entity {
    private static final EntityDataAccessor<Boolean> MISSION = SynchedEntityData.defineId(TruckEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(TruckEntity.class, EntityDataSerializers.FLOAT);
    /** La carga: misiles chiquitos (máximo 3) y medianos (máximo 2). */
    private static final EntityDataAccessor<Integer> CARGO_S = SynchedEntityData.defineId(TruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARGO_M = SynchedEntityData.defineId(TruckEntity.class, EntityDataSerializers.INT);
    public static final int MAX_SMALL = 3, MAX_MEDIUM = 2;

    /** Velocidad máxima manejando (bloques por tick) y la del conductor misterioso. */
    public static final double MAX_SPEED = 0.55, AUTO_SPEED = 0.42;

    /** Puntos por donde lo lleva el conductor (solo en el servidor). */
    private final List<Vec3> route = new ArrayList<>();
    private double speed;
    private int hits, stuckTicks;
    private float wheel;

    // movimiento suave en el cliente
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw;

    public TruckEntity(EntityType<? extends TruckEntity> type, Level level) {
        super(type, level);
        setMaxUpStep(1.1f);
    }

    public static TruckEntity create(Level level, Vec3 at, float yaw, boolean mission) {
        TruckEntity t = new TruckEntity(ModEntities.TRUCK.get(), level);
        t.setPos(at);
        t.setYRot(yaw);
        t.yRotO = yaw;
        t.entityData.set(MISSION, mission);
        return t;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MISSION, false);
        entityData.define(SPEED, 0f);
        entityData.define(CARGO_S, 0);
        entityData.define(CARGO_M, 0);
    }

    /** ¿Es el camión de la misión (lo maneja el conductor misterioso)? */
    public boolean mission() {
        return entityData.get(MISSION);
    }

    public void setMission(boolean m) {
        entityData.set(MISSION, m);
        if (!m) route.clear();
    }

    public int cargoSmall() {
        return entityData.get(CARGO_S);
    }

    public int cargoMedium() {
        return entityData.get(CARGO_M);
    }

    /** Velocidad para las ruedas y el motor (cliente y servidor). */
    public float speed() {
        return entityData.get(SPEED);
    }

    /** Giro de las ruedas (para dibujarlas). */
    public float wheel() {
        return wheel;
    }

    // ------------------------------------------------------------------ conductor misterioso

    /** Manda al conductor por estos puntos (en orden). */
    public void driveTo(List<Vec3> points) {
        route.clear();
        route.addAll(points);
        stuckTicks = 0;
    }

    public boolean arrived() {
        return route.isEmpty();
    }

    /** Ticks seguidos que lleva atascado contra algo. */
    public int stuck() {
        return stuckTicks;
    }

    public void stop() {
        route.clear();
        speed = 0;
    }

    private void autopilot() {
        if (route.isEmpty()) {
            speed *= 0.8;
        } else {
            Vec3 p = route.get(0);
            double dx = p.x - getX(), dz = p.z - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < (route.size() == 1 ? 1.2 : 3.5)) {
                route.remove(0);
                return;
            }
            float want = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
            float turn = Mth.wrapDegrees(want - getYRot());
            setYRot(getYRot() + Mth.clamp(turn, -5f, 5f));
            // frena en las curvas cerradas y al llegar al último punto
            double target = AUTO_SPEED * (Math.abs(turn) > 50 ? 0.4 : 1) * (route.size() == 1 ? Math.min(1, d / 10 + 0.25) : 1);
            speed += Mth.clamp(target - speed, -0.04, 0.02);
        }
        stuckTicks = horizontalCollision && speed > 0.05 ? stuckTicks + 1 : 0;
    }

    // ------------------------------------------------------------------ manejo

    @Override
    public void tick() {
        super.tick();
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
            Entity driver = getControllingPassenger();
            if (driver instanceof LivingEntity d) {
                // adelante y atrás aceleran; los lados giran (más en marcha, nada parado)
                float fwd = d.zza, side = d.xxa;
                if (fwd > 0) speed = Math.min(MAX_SPEED, speed + 0.018);
                else if (fwd < 0) speed = Math.max(-MAX_SPEED * 0.4, speed - (speed > 0 ? 0.04 : 0.012));
                else speed *= 0.96;
                double grip = Mth.clamp(Math.abs(speed) / 0.15, 0, 1);
                setYRot(getYRot() - side * 4.2f * (float) grip * (speed < 0 ? -1 : 1));
            } else if (!level().isClientSide) {
                autopilot();
            } else {
                speed *= 0.9;
            }
            Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
            double vy = onGround() ? -0.04 : getDeltaMovement().y - 0.08;
            setDeltaMovement(fwd.x * speed, Math.max(vy, -2), fwd.z * speed);
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision && !level().isClientSide && route.isEmpty()) speed *= 0.5;
            if (!level().isClientSide) entityData.set(SPEED, (float) speed);
        } else if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * k, getY() + (lerpY - getY()) * k, getZ() + (lerpZ - getZ()) * k);
            setYRot(getYRot() + Mth.wrapDegrees(lerpYaw - getYRot()) * (float) k);
            lerpSteps--;
        }
        float v = level().isClientSide && !isControlledByLocalInstance() ? speed() : (float) speed;
        wheel += v * 1.8f;
        // motor: un ronquido grave cada tanto, más seguido cuanto más rápido
        if (level().isClientSide && (Math.abs(v) > 0.02 || !getPassengers().isEmpty()) && tickCount % Math.max(4, 12 - (int) (Math.abs(v) * 16)) == 0) {
            level().playLocalSound(getX(), getY() + 1, getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL,
                    0.12f + Math.abs(v) * 0.5f, 0.45f + Math.abs(v) * 0.6f, false);
        }
    }

    @Override
    public boolean isControlledByLocalInstance() {
        // el camión de la misión lo mueve el servidor aunque el jugador vaya adentro
        if (mission()) return !level().isClientSide;
        return super.isControlledByLocalInstance();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (mission()) return null;
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (teleport || steps <= 0) {
            setPos(x, y, z);
            setYRot(yaw);
            lerpSteps = 0;
            return;
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpSteps = Math.max(steps, 3);
    }

    // ------------------------------------------------------------------ pasajeros

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.25;
    }

    /** El jugador va a la izquierda (manejando) o a la derecha (de copiloto en la misión). */
    @Override
    protected void positionRider(Entity e, MoveFunction move) {
        if (!hasPassenger(e)) return;
        double side = mission() ? -0.62 : 0.62, ahead = 1.75;
        Vec3 off = new Vec3(side, 0, ahead).yRot(-getYRot() * Mth.DEG_TO_RAD);
        move.accept(e, getX() + off.x, getY() + getPassengersRidingOffset() + e.getMyRidingOffset(), getZ() + off.z);
        if (getControllingPassenger() == e) {
            e.setYBodyRot(getYRot());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        // se baja por su puerta, a un lado de la cabina
        double side = mission() ? -2.4 : 2.4;
        Vec3 off = new Vec3(side, 0, 1.6).yRot(-getYRot() * Mth.DEG_TO_RAD);
        return new Vec3(getX() + off.x, getY() + 0.2, getZ() + off.z);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!mission() && held.getItem() instanceof com.arsenalatomico.item.MissileItem mi) {
            // cargar un misil en la caja
            if (!level().isClientSide) {
                EntityDataAccessor<Integer> slot = mi.medium ? CARGO_M : CARGO_S;
                int max = mi.medium ? MAX_MEDIUM : MAX_SMALL;
                if (entityData.get(slot) >= max) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(mi.medium
                            ? "message.arsenalatomico.truck.full_medium" : "message.arsenalatomico.truck.full_small", max), true);
                } else {
                    entityData.set(slot, entityData.get(slot) + 1);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1f, 0.6f);
                    cargoMessage(player);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!mission() && player.isSecondaryUseActive() && held.isEmpty() && cargoSmall() + cargoMedium() > 0) {
            // sacar un misil (primero los medianos)
            if (!level().isClientSide) {
                boolean medium = cargoMedium() > 0;
                EntityDataAccessor<Integer> slot = medium ? CARGO_M : CARGO_S;
                entityData.set(slot, entityData.get(slot) - 1);
                player.setItemInHand(hand, new ItemStack(medium ? ModItems.MISSILE_MEDIUM.get() : ModItems.MISSILE_SMALL.get()));
                level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1f, 0.8f);
                cargoMessage(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (mission()) {
            if (!level().isClientSide) SolNegro.board(player, this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) player.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void cargoMessage(Player player) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.arsenalatomico.truck.cargo",
                cargoSmall(), MAX_SMALL, cargoMedium(), MAX_MEDIUM), true);
    }

    // ------------------------------------------------------------------ golpes y colisión

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || mission() || isInvulnerableTo(source)) return false;
        if (!(source.getEntity() instanceof Player p)) return false;
        hits++;
        if (p.getAbilities().instabuild || hits >= 4) {
            if (!p.getAbilities().instabuild) spawnAtLocation(new ItemStack(ModItems.MILITARY_TRUCK.get()));
            for (int i = 0; i < cargoSmall(); i++) spawnAtLocation(new ItemStack(ModItems.MISSILE_SMALL.get()));
            for (int i = 0; i < cargoMedium(); i++) spawnAtLocation(new ItemStack(ModItems.MISSILE_MEDIUM.get()));
            ejectPassengers();
            discard();
        } else {
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.4f, 1.6f);
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.MILITARY_TRUCK.get());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setMission(false);
        entityData.set(CARGO_S, Mth.clamp(tag.getInt("cargaChicos"), 0, MAX_SMALL));
        entityData.set(CARGO_M, Mth.clamp(tag.getInt("cargaMedianos"), 0, MAX_MEDIUM));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("cargaChicos", cargoSmall());
        tag.putInt("cargaMedianos", cargoMedium());
    }
}
