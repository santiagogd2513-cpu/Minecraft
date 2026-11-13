package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.ModBlock;
import com.example.beautifuldecor.init.ModBlocks;
import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dron pilotable.
 * <p>
 * V1.8.5: el vuelo se calcula en el cliente del piloto (así la cámara no depende de la latencia ni del eco del
 * servidor) y el cliente le manda al servidor su posición cada tick. El servidor solo valida (pasos cortos,
 * alcance, chunk cargado) y retransmite la posición a los demás jugadores.
 */
public class DroneEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> PILOT =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /**
     * Alcance máximo del dron respecto al jugador que lo pilota, en bloques (V3.0.0: 548; antes 48).
     * Ojo: el servidor solo manda mundo y entidades hasta la distancia de renderizado del jugador, así que en la
     * práctica el dron llega hasta donde llegue esa distancia (o 548 si es menor).
     */
    public static final double MAX_RANGE = 548.0;
    /**
     * Velocidad máxima en bloques por tick (V3.0.1: 5,81 bloques/s, un 3,5 % más que un jugador corriendo, que va a 5,612 bloques/s;
     * antes 0,22 = 4,4 bloques/s).
     */
    public static final float TOP_SPEED = 0.2904f;

    /** Lo máximo que el servidor acepta que el dron se mueva entre dos paquetes seguidos. */
    private static final double MAX_STEP = 1.5;
    /** Ticks sin recibir nada del piloto antes de apagar el dron. */
    private static final int CONTROL_TIMEOUT = 100;
    private static final int MAX_REJECTED_UPDATES = 5;

    /** Marca en los datos del jugador mientras pilota: si se desconecta o el servidor se cierra, al volver se le quita. */
    public static final String PILOT_MARKER = "BeautifulDecorDronePilot";

    /** Servidor: jugador -> dron que está pilotando (para bloquear sus acciones de bloques e interacciones). */
    private static final Map<UUID, DroneEntity> PILOTS = new ConcurrentHashMap<>();

    // ---- servidor
    private Vec3 pilotAnchor;
    private int lastControlTick;
    private int rejectedUpdates;

    // ---- cliente
    private boolean localControl;
    private Vec3 localAnchor = Vec3.ZERO;
    private float inputForward;
    private float inputStrafe;
    private float inputVertical;
    private float leanForward;
    private float leanForwardO;
    private float leanSide;
    private float leanSideO;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setSilent(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PILOT, Optional.empty());
    }

    public Optional<UUID> getPilotUUID() {
        return this.entityData.get(PILOT);
    }

    public boolean isPiloted() {
        return this.getPilotUUID().isPresent();
    }

    public boolean isPilotedBy(Player player) {
        return player != null && this.getPilotUUID().filter(player.getUUID()::equals).isPresent();
    }

    /** Servidor: ¿este jugador está pilotando un dron ahora mismo? */
    public static boolean isPiloting(Player player) {
        if (player == null) {
            return false;
        }
        DroneEntity drone = PILOTS.get(player.getUUID());
        if (drone == null) {
            return false;
        }
        if (drone.isRemoved() || !drone.isPilotedBy(player)) {
            PILOTS.remove(player.getUUID(), drone);
            return false;
        }
        return true;
    }

    // =====================================================================================================
    // Servidor
    // =====================================================================================================

    public boolean startPiloting(ServerPlayer player) {
        if (this.level().isClientSide || this.isPiloted()) {
            return false;
        }

        this.pilotAnchor = player.position();
        this.lastControlTick = this.tickCount;
        this.rejectedUpdates = 0;
        this.entityData.set(PILOT, Optional.of(player.getUUID()));
        PILOTS.put(player.getUUID(), this);
        player.getPersistentData().putBoolean(PILOT_MARKER, true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        this.setRot(player.getYRot(), player.getXRot());
        this.level().playSound(null, this.blockPosition(), ModSounds.DRONE_STARTUP.get(),
                SoundSource.PLAYERS, 0.85f, 1.0f);
        return true;
    }

    /** Deja de pilotar y el dron vuelve a ser un bloque (en el suelo, justo debajo de donde está). */
    public void stopPiloting(Player player) {
        if (this.releasePilot(player)) {
            this.packIntoBlock();
        }
    }

    private boolean releasePilot(Player player) {
        if (!this.isPilotedBy(player)) {
            return false;
        }

        this.entityData.set(PILOT, Optional.empty());
        PILOTS.remove(player.getUUID(), this);
        player.getPersistentData().remove(PILOT_MARKER);
        this.pilotAnchor = null;
        this.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** El piloto ya no existe (desconectó, cambió de dimensión...): se limpia el estado sin tocarlo. */
    private void forceRelease(UUID pilotId) {
        this.entityData.set(PILOT, Optional.empty());
        if (pilotId != null) {
            PILOTS.remove(pilotId, this);
        }
        this.pilotAnchor = null;
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void packIntoBlock() {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        Level level = this.level();
        BlockPos.MutableBlockPos cursor = this.blockPosition().mutable();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        while (cursor.getY() > minY
                && level.getBlockState(cursor.below()).getCollisionShape(level, cursor.below()).isEmpty()) {
            cursor.move(Direction.DOWN);
        }
        BlockPos target = cursor.immutable();
        // Si quedó metido dentro de un bloque, sube hasta encontrar un hueco.
        for (int i = 0; i < 4 && target.getY() < maxY && !level.getBlockState(target).canBeReplaced(); i++) {
            target = target.above();
        }
        if (target.getY() > minY && level.getBlockState(target).canBeReplaced()) {
            level.setBlock(target, ModBlocks.DRONE.get().defaultBlockState()
                    .setValue(ModBlock.FACING, Direction.fromYRot(this.getYRot())), 3);
        } else {
            level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(),
                    new ItemStack(ModItems.DRONE.get())));
        }
        this.discard();
    }

    /**
     * Servidor: el piloto manda dónde está su dron. Se acepta si el paso es corto, está dentro del alcance y
     * el chunk está cargado; si llegan varios paquetes inválidos seguidos se apaga el dron.
     */
    public void acceptPilotUpdate(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        if (this.level().isClientSide || !this.isPilotedBy(player)) {
            return;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }

        this.lastControlTick = this.tickCount;
        Vec3 target = new Vec3(x, y, z);
        Vec3 current = this.position();
        double reach = MAX_RANGE + 2.0;
        boolean valid = target.distanceToSqr(current) <= MAX_STEP * MAX_STEP
                && (this.pilotAnchor == null || target.distanceToSqr(this.pilotAnchor) <= reach * reach)
                && y > this.level().getMinBuildHeight() - 8.0
                && y < this.level().getMaxBuildHeight() + 8.0
                && this.level().hasChunkAt(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
        if (!valid) {
            this.rejectedUpdates++;
            if (this.rejectedUpdates >= MAX_REJECTED_UPDATES) {
                this.stopPiloting(player);
            }
            return;
        }

        this.rejectedUpdates = 0;
        this.setDeltaMovement(target.subtract(current));
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(Mth.clamp(pitch, -90.0f, 90.0f));
    }

    // =====================================================================================================
    // Tick
    // =====================================================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientTick();
            return;
        }
        VehicleImpact.tick(this, VehicleImpact.DRONE, null);      // V7.1.0: atropellos

        if (!this.isPiloted()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        UUID pilotId = this.getPilotUUID().orElse(null);
        ServerPlayer pilot = pilotId == null || this.level().getServer() == null
                ? null
                : this.level().getServer().getPlayerList().getPlayer(pilotId);
        double farAway = MAX_RANGE + 6.0;
        boolean invalid = pilot == null || pilot.level() != this.level() || !pilot.isAlive()
                || this.tickCount - this.lastControlTick > CONTROL_TIMEOUT
                || (this.pilotAnchor != null && this.position().distanceToSqr(this.pilotAnchor) > farAway * farAway);
        if (invalid) {
            if (pilot != null) {
                this.releasePilot(pilot);
            } else {
                this.forceRelease(pilotId);
            }
            this.packIntoBlock();
            return;
        }

        pilot.setNoGravity(true);
        if (this.tickCount - this.lastControlTick > 2) {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    // =====================================================================================================
    // Cliente
    // =====================================================================================================

    public boolean isLocalControl() {
        return this.localControl;
    }

    /** Cliente: este jugador empieza a volar el dron (la posición pasa a calcularla su propio cliente). */
    public void beginLocalControl(Vec3 anchor, float yaw, float pitch) {
        this.localControl = true;
        this.localAnchor = anchor;
        this.inputForward = 0.0f;
        this.inputStrafe = 0.0f;
        this.inputVertical = 0.0f;
        this.setViewRotation(yaw, pitch);
    }

    public void endLocalControl() {
        this.localControl = false;
        this.inputForward = 0.0f;
        this.inputStrafe = 0.0f;
        this.inputVertical = 0.0f;
    }

    /** Cliente: teclas pulsadas (adelante/atrás, derecha/izquierda, arriba/abajo), cada una entre -1 y 1. */
    public void setLocalInput(float forward, float strafe, float vertical) {
        this.inputForward = Mth.clamp(forward, -1.0f, 1.0f);
        this.inputStrafe = Mth.clamp(strafe, -1.0f, 1.0f);
        this.inputVertical = Mth.clamp(vertical, -1.0f, 1.0f);
    }

    /** Cliente: la cámara del dron mira exactamente a donde mira el jugador (sin interpolar de más). */
    public void setViewRotation(float yaw, float pitch) {
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    /** Inclinación hacia delante (-1 a 1) para la cámara y el modelo, ya suavizada entre ticks. */
    public float getLeanForward(float partialTick) {
        return Mth.lerp(partialTick, this.leanForwardO, this.leanForward);
    }

    /** Inclinación lateral (-1 a 1; positivo = se mueve hacia su derecha). */
    public float getLeanSide(float partialTick) {
        return Mth.lerp(partialTick, this.leanSideO, this.leanSide);
    }

    private void clientTick() {
        if (this.localControl) {
            this.localFlightTick();
        }
        this.updateLean();
    }

    private void localFlightTick() {
        Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 right = Vec3.directionFromRotation(0.0f, this.getYRot() + 90.0f);
        Vec3 desired = forward.scale(this.inputForward)
                .add(right.scale(this.inputStrafe))
                .add(0.0, this.inputVertical, 0.0);
        if (desired.lengthSqr() > 1.0) {
            desired = desired.normalize();
        }

        Vec3 targetVelocity = desired.scale(TOP_SPEED);
        Vec3 velocity = this.getDeltaMovement().scale(0.72).add(targetVelocity.scale(0.28));
        if (targetVelocity.lengthSqr() < 0.0001 && velocity.lengthSqr() < 0.0004) {
            velocity = Vec3.ZERO;
        }
        this.setDeltaMovement(velocity);
        if (velocity != Vec3.ZERO) {
            this.move(MoverType.SELF, velocity);
        }

        // Alcance máximo (el mismo que valida el servidor) y límites del mundo.
        Vec3 offset = this.position().subtract(this.localAnchor);
        double distance = offset.length();
        if (distance > MAX_RANGE) {
            Vec3 clamped = this.localAnchor.add(offset.scale(MAX_RANGE / distance));
            this.setPos(clamped.x, clamped.y, clamped.z);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        }
        double minY = this.level().getMinBuildHeight() + 1.0;
        double maxY = this.level().getMaxBuildHeight() - 1.0;
        if (this.getY() < minY || this.getY() > maxY) {
            this.setPos(this.getX(), Mth.clamp(this.getY(), minY, maxY), this.getZ());
        }
    }

    private void updateLean() {
        this.leanForwardO = this.leanForward;
        this.leanSideO = this.leanSide;
        Vec3 velocity = this.getDeltaMovement();
        Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot());
        Vec3 right = Vec3.directionFromRotation(0.0f, this.getYRot() + 90.0f);
        float targetForward = Mth.clamp((float) velocity.dot(forward) / TOP_SPEED, -1.0f, 1.0f);
        float targetSide = Mth.clamp((float) velocity.dot(right) / TOP_SPEED, -1.0f, 1.0f);
        this.leanForward += (targetForward - this.leanForward) * 0.35f;
        this.leanSide += (targetSide - this.leanSide) * 0.35f;
    }

    /** El servidor no puede mover el dron que vuela este mismo cliente (la posición la manda el piloto). */
    @Override
    public boolean isControlledByLocalInstance() {
        return this.localControl || super.isControlledByLocalInstance();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.localControl) {
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
        // Evita que otros jugadores lo vean dar una vuelta entera al cruzar de 180 a -180 grados.
        while (this.getYRot() - this.yRotO < -180.0f) {
            this.yRotO -= 360.0f;
        }
        while (this.getYRot() - this.yRotO >= 180.0f) {
            this.yRotO += 360.0f;
        }
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        if (this.localControl) {
            return;
        }
        super.lerpMotion(x, y, z);
    }

    // =====================================================================================================

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.isPiloted()) {
            UUID pilotId = this.getPilotUUID().orElse(null);
            Player pilot = pilotId == null || this.level().getServer() == null
                    ? null
                    : this.level().getServer().getPlayerList().getPlayer(pilotId);
            if (pilot != null) {
                this.releasePilot(pilot);
            } else if (pilotId != null) {
                PILOTS.remove(pilotId, this);
            }
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(PILOT, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // A pilot connection is temporary and is intentionally not persisted.
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }
}
