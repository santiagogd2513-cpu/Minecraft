package com.arsenalatomico.entity;

import com.arsenalatomico.registry.ModEntities;
import com.arsenalatomico.registry.ModItems;
import com.arsenalatomico.train.RailPath;
import com.arsenalatomico.train.TrainPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un coche del Tren Lanzamisiles: la locomotora, el vagón de mando o el vagón lanzador. Andan por los rieles
 * normales de Minecraft, enganchados en fila. La física (masa, tracción, frenos, pendiente) y el seguimiento de
 * la vía vienen de Beautiful Decor (santiagogd2513-cpu), adaptados: el que va adelante calcula todo el tren y
 * mueve a todos los coches la misma distancia.
 * <p>
 * Manejo: el jugador se sube a la locomotora (clic derecho). W acelera, S frena (y luego da reversa).
 * Sin conductor, el tren se queda con el freno de mano puesto.
 */
public class TrainCarEntity extends Entity {
    public enum Variant {
        /** Locomotora diésel militar. */
        LOCO("loco", 120_000.0, 20.0, 12.0, TrainPhysics.LOCO_POWER_W),
        /** Vagón de mando: la consola. */
        COMMAND("command", 55_000.0, 18.0, 11.0, 0.0),
        /** Vagón lanzador: lleva los misiles. */
        LAUNCHER("launcher", 70_000.0, 22.0, 14.0, 0.0);

        public final String id;
        public final double emptyKg, length, wheelbase, powerW;

        Variant(String id, double emptyKg, double length, double wheelbase, double powerW) {
            this.id = id;
            this.emptyKg = emptyKg;
            this.length = length;
            this.wheelbase = wheelbase;
            this.powerW = powerW;
        }

        public boolean isLoco() {
            return this == LOCO;
        }

        /** Lo que sobresale el cuerpo por delante y por detrás de los bogies. */
        public double inset() {
            return (length - wheelbase) / 2.0;
        }

        public static Variant byOrdinal(int i) {
            Variant[] v = values();
            return v[Mth.clamp(i, 0, v.length - 1)];
        }
    }

    public static final double COUPLING_GAP = 0.5;
    public static final double TOP_KMH = 100.0;
    public static final int MAX_SMALL = 3, MAX_MEDIUM = 2;
    private static final double SMALL_KG = 1_500.0, MEDIUM_KG = 4_000.0, PASSENGER_KG = 80.0;
    private static final int MAX_CARS = 64;

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARGO_S = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARGO_M = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);

    // ---- vía y cadena (servidor)
    @Nullable
    private RailPath.Cursor cur;                 // bogie delantero
    private boolean placed;
    @Nullable
    private UUID frontId, rearId;
    private int missingFront, hits;
    private double speed;                        // bloques por tick; positivo = hacia donde mira
    private float slope;

    // ---- cliente
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;
    private float wheelAngle, wheelAngleO, visualSpeed;

    public TrainCarEntity(EntityType<? extends TrainCarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static double stride(Variant ahead, Variant behind) {
        return ahead.inset() + COUPLING_GAP + behind.inset();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VARIANT, 0);
        entityData.define(CARGO_S, 0);
        entityData.define(CARGO_M, 0);
    }

    public Variant variant() {
        return Variant.byOrdinal(entityData.get(VARIANT));
    }

    public void setVariant(Variant v) {
        entityData.set(VARIANT, v.ordinal());
    }

    public int cargoSmall() {
        return entityData.get(CARGO_S);
    }

    public int cargoMedium() {
        return entityData.get(CARGO_M);
    }

    public double totalMassKg() {
        return variant().emptyKg + cargoSmall() * SMALL_KG + cargoMedium() * MEDIUM_KG + getPassengers().size() * PASSENGER_KG;
    }

    /** Velocidad en km/h con signo (cliente y servidor), para el sonido y el dibujo. */
    public float visualSpeed() {
        return visualSpeed;
    }

    public float getWheelAngle(float partial) {
        return Mth.lerp(partial, wheelAngleO, wheelAngle);
    }

    public float getPitchVisual(float partial) {
        return Mth.lerp(partial, xRotO, getXRot());
    }

    // ------------------------------------------------------------------ vía

    /** Los rieles normales de Minecraft, con las mismas 10 formas que usa la geometría del tren. */
    public static RailPath.Lookup lookup(Level level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BaseRailBlock rail)) return null;
            RailShape rs = rail.getRailDirection(state, level, pos, null);
            return RailPath.Shape.valueOf(rs.name());
        };
    }

    public boolean isPlaced() {
        return placed;
    }

    public void placeOn(RailPath.Cursor c) {
        cur = c;
        placed = true;
        updatePose(lookup(level()));
    }

    @Nullable
    public RailPath.Cursor frontCursor() {
        return cur == null ? null : cur.copy();
    }

    @Nullable
    public RailPath.Cursor rearCursor(RailPath.Lookup lk) {
        if (cur == null) return null;
        RailPath.Cursor c = cur.copy();
        c.advance(-variant().wheelbase, lk);
        return c;
    }

    /** Si no se sabe dónde está (recién cargado del mundo), busca el riel bajo el coche. */
    private void snapToRail() {
        RailPath.Lookup lk = lookup(level());
        BlockPos base = blockPosition();
        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = base.getX() + dx, y = base.getY() + dy, z = base.getZ() + dz;
                    RailPath.Shape shape = lk.shapeAt(x, y, z);
                    if (shape == null) continue;
                    double[] t = new double[2];
                    shape.tangent(shape.length() / 2.0, t);
                    float yaw = getYRot() * Mth.DEG_TO_RAD;
                    double dot = t[0] * -Mth.sin(yaw) + t[1] * Mth.cos(yaw);
                    RailPath.Cursor c = new RailPath.Cursor(x, y, z, shape, shape.length() / 2.0, dot >= 0 ? 1 : -1);
                    c.advance(variant().wheelbase / 2.0, lk);
                    placeOn(c);
                    return;
                }
            }
        }
    }

    /** Pone el coche en su sitio según su cursor: posición, giro y pendiente (entre sus dos bogies). */
    private void updatePose(RailPath.Lookup lk) {
        if (cur == null) return;
        RailPath.Cursor rc = rearCursor(lk);
        double[] f = new double[3], r = new double[3];
        cur.worldPoint(f);
        rc.worldPoint(r);
        double dx = f[0] - r[0], dy = f[1] - r[1], dz = f[2] - r[2];
        double h = Math.hypot(dx, dz);
        float yaw = h > 1.0e-4 ? RailPath.yawOf(dx, dz) : getYRot();
        float pitch = h > 1.0e-4 ? (float) -Math.toDegrees(Math.atan2(dy, h)) : 0.0f;
        slope = (float) ((cur.slope() + rc.slope()) * 0.5);
        setPos((f[0] + r[0]) / 2.0, (f[1] + r[1]) / 2.0, (f[2] + r[2]) / 2.0);
        setRot(yaw, pitch);
    }

    // ------------------------------------------------------------------ cadena de coches

    @Nullable
    private TrainCarEntity carById(@Nullable UUID id) {
        if (id == null || !(level() instanceof ServerLevel server)) return null;
        Entity e = server.getEntity(id);
        return e instanceof TrainCarEntity c && !c.isRemoved() ? c : null;
    }

    /** Engancha {@code behind} detrás de este coche. */
    public void coupleBehind(TrainCarEntity behind) {
        rearId = behind.getUUID();
        behind.frontId = getUUID();
    }

    private void unlinkNeighbours() {
        TrainCarEntity front = carById(frontId), rear = carById(rearId);
        if (front != null && getUUID().equals(front.rearId)) front.rearId = null;
        if (rear != null && getUUID().equals(rear.frontId)) rear.frontId = null;
        frontId = null;
        rearId = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) unlinkNeighbours();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!placed || cur == null) {
            snapToRail();
            return;
        }
        if (frontId != null) {
            if (carById(frontId) == null) {
                if (++missingFront > 2400) {
                    frontId = null;
                    missingFront = 0;
                }
                speed = 0;
            } else {
                missingFront = 0;
            }
            return;                                // los de atrás los mueve la cabeza
        }
        driveChain();
    }

    private void driveChain() {
        RailPath.Lookup lk = lookup(level());
        List<TrainCarEntity> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        chain.add(this);
        seen.add(getUUID());
        boolean complete = true;
        TrainCarEntity last = this;
        for (int i = 0; i < MAX_CARS; i++) {
            if (last.rearId == null) break;
            TrainCarEntity next = last.carById(last.rearId);
            if (next == null) {
                complete = false;
                break;
            }
            if (!seen.add(next.getUUID())) {
                last.rearId = null;
                break;
            }
            next.frontId = last.getUUID();
            if (!next.placed || next.cur == null) {
                next.snapToRail();
                complete = false;
                break;
            }
            chain.add(next);
            last = next;
        }

        double empty = 0, total = 0, slopeSum = 0, locoMass = 0, power = 0;
        int locos = 0;
        Player driver = null;
        for (TrainCarEntity car : chain) {
            double m = car.totalMassKg();
            empty += car.variant().emptyKg;
            total += m;
            slopeSum += m * car.slope;
            if (car.variant().powerW > 0) {
                locoMass += car.variant().emptyKg;
                power += car.variant().powerW;
            }
            if (car.variant().isLoco()) {
                locos++;
                if (driver == null && car.getFirstPassenger() instanceof Player p) driver = p;
            }
        }
        double slopeAvg = total > 0 ? slopeSum / total : 0;

        double v = speed;
        int drive = 0;
        boolean brake = false, hand = false;
        if (driver != null) {
            float fwd = driver.zza;
            if (fwd > 0) drive = 1;
            else if (fwd < 0) {
                if (v > 0.02) brake = true;
                else drive = -1;
            }
        } else {
            hand = true;                           // sin conductor, el tren se queda con el freno de mano
        }
        if (!complete) {
            v = 0;
        } else if (hand && Math.abs(v) < 0.004) {
            v = 0;
        } else {
            v = TrainPhysics.step(v, empty, total, locoMass, power, TOP_KMH / 3.6, slopeAvg, drive, brake, hand);
        }

        // mover todos los coches la misma distancia
        for (TrainCarEntity car : chain) car.revalidate(lk);
        if (v != 0.0) {
            RailPath.Cursor lead = v > 0 ? cur.copy() : chain.get(chain.size() - 1).rearCursor(lk);
            double moved = lead.advance(v, lk);
            if (Math.abs(moved) < Math.abs(v) - 1.0e-6) {
                if (Math.abs(v) > 0.4 && driver instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("hud.arsenalatomico.train_end_of_track"), true);
                    level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1f, 0.6f);
                }
                v = 0;
            }
            if (moved != 0.0) for (TrainCarEntity car : chain) car.cur.advance(moved, lk);
        }
        for (TrainCarEntity car : chain) {
            car.speed = v;
            car.updatePose(lk);
        }
        if (driver instanceof ServerPlayer sp && tickCount % 4 == 0) {
            String grade = Math.abs(slopeAvg) < 0.02 ? "—" : (slopeAvg > 0 ? "↑" : "↓") + String.format("%.0f%%", Math.abs(slopeAvg) * 100.0);
            sp.displayClientMessage(Component.translatable("hud.arsenalatomico.train_status", String.format("%.0f", Math.abs(v) * 72.0),
                    chain.size(), String.format("%.0f", total / 1000.0), grade), true);
        }
    }

    /** Si el riel cambió de forma (un riel nuevo al lado) se ajusta. */
    private void revalidate(RailPath.Lookup lk) {
        RailPath.Shape now = lk.shapeAt(cur.x, cur.y, cur.z);
        if (now != null && now != cur.shape) {
            cur.shape = now;
            cur.s = Math.min(cur.s, now.length());
        }
    }

    // ------------------------------------------------------------------ cliente

    private void clientTick() {
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * k, getY() + (lerpY - getY()) * k, getZ() + (lerpZ - getZ()) * k);
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) * (float) k);
            setXRot(getXRot() + (float) (lerpXRot - getXRot()) * (float) k);
            lerpSteps--;
            setRot(getYRot(), getXRot());
        }
        double dx = getX() - xo, dz = getZ() - zo;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double forward = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
        visualSpeed = (float) forward;
        wheelAngleO = wheelAngle;
        wheelAngle += (float) (forward / 0.5);
        if (wheelAngle > Mth.TWO_PI * 16 || wheelAngle < -Mth.TWO_PI * 16) {
            wheelAngle = 0;
            wheelAngleO = 0;
        }
        float abs = Math.abs(visualSpeed);
        // traqueteo de las ruedas y rugido del motor de la locomotora
        if (abs > 0.12f && tickCount % 7 == 0 && (variant().isLoco() || getId() % 3 == 0)) {
            level().playLocalSound(getX(), getY() + 0.5, getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL,
                    Math.min(1.3f, 0.15f + abs * 0.6f), 0.55f + Math.min(0.5f, abs * 0.25f), false);
        }
        if (variant().isLoco() && tickCount % 40 == 0 && !getPassengers().isEmpty()) {
            level().playLocalSound(getX(), getY() + 1, getZ(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 0.25f, 0.4f, false);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = 2;                             // va deprisa: se interpola en 2 ticks
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return !level().isClientSide;              // lo mueve el servidor, aunque el jugador vaya manejando
    }

    /** Un coche mide más de 20 m: sin esto desaparecería al salir su centro de la pantalla. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double r = variant().length / 2.0 + 1.0;
        return new AABB(getX() - r, getY() - 1, getZ() - r, getX() + r, getY() + 6, getZ() + r);
    }

    // ------------------------------------------------------------------ pasajeros

    @Override
    protected boolean canAddPassenger(Entity e) {
        return variant().isLoco() && getPassengers().isEmpty();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected void positionRider(Entity e, MoveFunction move) {
        if (!hasPassenger(e)) return;
        Vec3 off = new Vec3(0.0, 1.7, variant().length / 2.0 - 3.5).xRot(-getXRot() * Mth.DEG_TO_RAD).yRot(-getYRot() * Mth.DEG_TO_RAD);
        move.accept(e, getX() + off.x, getY() + off.y, getZ() + off.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Vec3 off = new Vec3(3.0, 0.2, variant().length / 2.0 - 3.5).yRot(-getYRot() * Mth.DEG_TO_RAD);
        return new Vec3(getX() + off.x, getY() + off.y, getZ() + off.z);
    }

    // ------------------------------------------------------------------ interacción

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (variant() == Variant.LAUNCHER && held.getItem() instanceof com.arsenalatomico.item.MissileItem mi) {
            if (!level().isClientSide) {
                EntityDataAccessor<Integer> slot = mi.medium ? CARGO_M : CARGO_S;
                int max = mi.medium ? MAX_MEDIUM : MAX_SMALL;
                if (entityData.get(slot) >= max) {
                    player.displayClientMessage(Component.translatable(mi.medium ? "message.arsenalatomico.train.full_medium"
                            : "message.arsenalatomico.train.full_small", max), true);
                } else {
                    entityData.set(slot, entityData.get(slot) + 1);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 1f, 0.6f);
                    cargoMessage(player);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (variant() == Variant.LAUNCHER && player.isSecondaryUseActive() && held.isEmpty() && cargoSmall() + cargoMedium() > 0) {
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
        if (variant().isLoco()) {
            if (!level().isClientSide) player.startRiding(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void cargoMessage(Player player) {
        player.displayClientMessage(Component.translatable("message.arsenalatomico.truck.cargo", cargoSmall(), MAX_SMALL, cargoMedium(), MAX_MEDIUM), true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        if (!(source.getEntity() instanceof Player p)) return false;
        hits++;
        if (p.getAbilities().instabuild || hits >= 5) {
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
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.MISSILE_TRAIN.get());
    }

    // ------------------------------------------------------------------ guardado

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Variant")) entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(CARGO_S, Mth.clamp(tag.getInt("cargaChicos"), 0, MAX_SMALL));
        entityData.set(CARGO_M, Mth.clamp(tag.getInt("cargaMedianos"), 0, MAX_MEDIUM));
        frontId = tag.hasUUID("Front") ? tag.getUUID("Front") : null;
        rearId = tag.hasUUID("Rear") ? tag.getUUID("Rear") : null;
        speed = tag.getDouble("Speed");
        if (tag.getBoolean("Placed")) {
            cur = new RailPath.Cursor(tag.getInt("CurX"), tag.getInt("CurY"), tag.getInt("CurZ"), RailPath.Shape.NORTH_SOUTH,
                    tag.getDouble("CurS"), tag.getInt("CurDir") >= 0 ? 1 : -1);
            placed = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Variant", entityData.get(VARIANT));
        tag.putInt("cargaChicos", cargoSmall());
        tag.putInt("cargaMedianos", cargoMedium());
        if (frontId != null) tag.putUUID("Front", frontId);
        if (rearId != null) tag.putUUID("Rear", rearId);
        tag.putDouble("Speed", speed);
        if (placed && cur != null) {
            tag.putBoolean("Placed", true);
            tag.putInt("CurX", cur.x);
            tag.putInt("CurY", cur.y);
            tag.putInt("CurZ", cur.z);
            tag.putDouble("CurS", cur.s);
            tag.putInt("CurDir", cur.dir);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** Un tren completo (locomotora, vagón de mando y vagón lanzador) sobre un riel; null si no hay vía suficiente. */
    @Nullable
    public static List<TrainCarEntity> spawnTrain(ServerLevel level, BlockPos railPos, Vec3 look) {
        RailPath.Lookup lk = lookup(level);
        RailPath.Shape shape = lk.shapeAt(railPos.getX(), railPos.getY(), railPos.getZ());
        if (shape == null) return null;
        Variant[] order = {Variant.LOCO, Variant.COMMAND, Variant.LAUNCHER};
        double[] t = new double[2];
        shape.tangent(shape.length() / 2.0, t);
        int dir = (t[0] * look.x + t[1] * look.z) >= 0 ? 1 : -1;
        RailPath.Cursor front = new RailPath.Cursor(railPos.getX(), railPos.getY(), railPos.getZ(), shape, shape.length() / 2.0, dir);
        front.advance(order[0].wheelbase / 2.0, lk);
        List<TrainCarEntity> cars = new ArrayList<>();
        Variant prev = null;
        RailPath.Cursor prevRear = null;
        for (Variant v : order) {
            if (prev != null) {
                front = prevRear.copy();
                front.advance(-stride(prev, v), lk);
            }
            RailPath.Cursor probe = front.copy();
            if (Math.abs(probe.advance(-v.wheelbase, lk)) < v.wheelbase - 1.0e-6) return null;   // no cabe en la vía
            TrainCarEntity car = ModEntities.TRAIN_CAR.get().create(level);
            if (car == null) return null;
            car.setVariant(v);
            car.placeOn(front.copy());
            cars.add(car);
            prev = v;
            prevRear = car.rearCursor(lk);
        }
        for (int i = 0; i + 1 < cars.size(); i++) cars.get(i).coupleBehind(cars.get(i + 1));
        for (TrainCarEntity c : cars) level.addFreshEntity(c);
        return cars;
    }
}
