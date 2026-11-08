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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carro a control remoto Lamborgonzo Huracanito (V3.1.0): un superdeportivo en miniatura que se maneja como el dron,
 * pero solo por tierra.
 * <p>
 * Igual que el dron, el manejo se calcula en el cliente del piloto (así la cámara no depende de la latencia) y ese cliente
 * le manda al servidor su posición cada tick; el servidor solo valida (pasos cortos, alcance, chunk cargado) y retransmite
 * la posición a los demás jugadores. A diferencia del dron tiene gravedad, sube escalones bajos (losas, escaleras),
 * acelera, frena, da reversa y gira como un carro de verdad. W acelera, S frena y da reversa, A/D giran, Espacio es el
 * freno de mano y R lo apaga: vuelve a ser un bloque, justo donde quedó.
 */
public class RcCarEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> PILOT =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Alcance máximo respecto al jugador que lo maneja, en bloques. */
    public static final double MAX_RANGE = 256.0;
    /** Velocidad máxima en bloques por tick (9 bloques por segundo, más rápido que un jugador corriendo: 5,6). */
    public static final float TOP_SPEED = RcCarPhysics.TOP_SPEED;
    public static final float REVERSE_SPEED = RcCarPhysics.REVERSE_SPEED;
    /** Escalones que sube solo (alturas de prueba, en bloques): losas y escaleras sí, bloques enteros no. */
    private static final double[] STEP_LIFTS = {0.25, 0.5625};
    /** Velocidad a la que se limita si el terreno de delante aún no se cargó. */
    private static final float UNLOADED_LIMIT = 0.2f;
    private static final float WHEEL_RADIUS = 0.08f;
    private static final float MAX_STEER_VISUAL = 0.5f;
    private static final double MAX_FALL_SPEED = 1.2;

    /** Lo máximo que el servidor acepta que el carro se mueva entre dos paquetes seguidos. */
    private static final double MAX_STEP = 2.0;
    /** Ticks sin recibir nada del piloto antes de apagar el carro. */
    private static final int CONTROL_TIMEOUT = 100;
    private static final int MAX_REJECTED_UPDATES = 5;

    /** Servidor: jugador -> carro que está manejando (para bloquear sus acciones de bloques e interacciones). */
    private static final Map<UUID, RcCarEntity> PILOTS = new ConcurrentHashMap<>();

    // ---- servidor
    private Vec3 pilotAnchor;
    private int lastControlTick;
    private int rejectedUpdates;

    // ---- cliente del piloto
    private boolean localControl;
    private Vec3 localAnchor = Vec3.ZERO;
    private float speed;
    private boolean inForward;
    private boolean inBack;
    private boolean inLeft;
    private boolean inRight;
    private boolean inBrake;
    private float viewYaw;
    private float viewPitch;

    // ---- interpolación para quienes miran el carro (igual que la barca)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // ---- visuales (todos los clientes)
    private float wheelRot;
    private float wheelRotO;
    private float steer;
    private float steerO;
    private float visualSpeed;
    private float yawDelta;

    public RcCarEntity(EntityType<? extends RcCarEntity> type, Level level) {
        super(type, level);
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

    /** Servidor: ¿este jugador está manejando un carro RC ahora mismo? */
    public static boolean isPiloting(Player player) {
        if (player == null) {
            return false;
        }
        RcCarEntity car = PILOTS.get(player.getUUID());
        if (car == null) {
            return false;
        }
        if (car.isRemoved() || !car.isPilotedBy(player)) {
            PILOTS.remove(player.getUUID(), car);
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
        this.level().playSound(null, this.blockPosition(), ModSounds.RC_CAR_START.get(),
                SoundSource.PLAYERS, 0.9f, 1.0f);
        return true;
    }

    /** Deja de manejarlo y el carro vuelve a ser un bloque (en el suelo, justo donde está). */
    public void stopPiloting(Player player) {
        if (this.releasePilot(player)) {
            this.packIntoBlock(player);
        }
    }

    private boolean releasePilot(Player player) {
        if (!this.isPilotedBy(player)) {
            return false;
        }
        this.entityData.set(PILOT, Optional.empty());
        PILOTS.remove(player.getUUID(), this);
        this.pilotAnchor = null;
        this.setDeltaMovement(Vec3.ZERO);
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

    /**
     * Vuelve a ser bloque en el suelo, con el frente hacia donde miraba. Si no hay sitio (cayó al vacío, quedó encerrado), el
     * ítem va al inventario del piloto (o al suelo, a sus pies) en vez de perderse.
     */
    private void packIntoBlock(@Nullable Player pilot) {
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
            level.setBlock(target, ModBlocks.RC_CAR.get().defaultBlockState()
                    .setValue(ModBlock.FACING, Direction.fromYRot(this.getYRot())), 3);
        } else {
            this.giveBack(pilot);
        }
        this.discard();
    }

    private void giveBack(@Nullable Player pilot) {
        ItemStack stack = new ItemStack(ModItems.RC_CAR.get());
        if (pilot != null && pilot.level() == this.level()) {
            if (!pilot.getInventory().add(stack)) {
                pilot.spawnAtLocation(stack);
            }
        } else {
            this.spawnAtLocation(stack);
        }
    }

    /**
     * Servidor: el piloto manda dónde está su carro. Se acepta si el paso es corto, está dentro del alcance y el chunk
     * está cargado; si llegan varios paquetes inválidos seguidos se apaga el carro.
     */
    public void acceptPilotUpdate(ServerPlayer player, double x, double y, double z, float yaw) {
        if (this.level().isClientSide || !this.isPilotedBy(player)) {
            return;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
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
        this.setXRot(0.0f);
    }

    // =====================================================================================================
    // Tick
    // =====================================================================================================

    @Override
    public void tick() {
        super.tick();
        this.tickLerp();
        if (this.level().isClientSide) {
            if (this.localControl) {
                this.localDriveTick();
            }
        } else {
            this.serverTick();
        }
        this.updateVisuals();
    }

    private void serverTick() {
        if (!this.isPiloted()) {
            this.freeTick();
            return;
        }

        UUID pilotId = this.getPilotUUID().orElse(null);
        ServerPlayer pilot = pilotId == null || this.level().getServer() == null
                ? null
                : this.level().getServer().getPlayerList().getPlayer(pilotId);
        double farAway = MAX_RANGE + 6.0;
        boolean invalid = pilot == null || pilot.level() != this.level() || !pilot.isAlive()
                || this.tickCount - this.lastControlTick > CONTROL_TIMEOUT
                || (this.pilotAnchor != null && this.position().distanceToSqr(this.pilotAnchor) > farAway * farAway)
                || this.getY() < this.level().getMinBuildHeight() - 4.0;
        if (invalid) {
            if (pilot != null) {
                this.releasePilot(pilot);
            } else {
                this.forceRelease(pilotId);
            }
            this.packIntoBlock(pilot);
            return;
        }

        if (this.tickCount - this.lastControlTick > 2) {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Servidor, sin piloto (por ejemplo, tras recargar el mundo): gravedad y fricción, para que no flote. */
    private void freeTick() {
        Vec3 motion = this.getDeltaMovement();
        boolean grounded = this.onGround();
        double friction = grounded ? 0.8 : 0.99;
        double vx = motion.x * friction;
        double vz = motion.z * friction;
        if (Math.abs(vx) < 0.004) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 0.004) {
            vz = 0.0;
        }
        double vy = grounded ? -0.04 : Math.max((motion.y - 0.08) * 0.98, -MAX_FALL_SPEED);
        this.setDeltaMovement(vx, vy, vz);
        if (vx != 0.0 || vz != 0.0 || !grounded) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    // =====================================================================================================
    // Manejo (cliente del piloto)
    // =====================================================================================================

    public boolean isLocalControl() {
        return this.localControl;
    }

    /** Cliente: este jugador empieza a manejar el carro (el movimiento pasa a calcularlo su propio cliente). */
    public void beginLocalControl(Vec3 anchor, float viewYaw, float viewPitch) {
        this.localControl = true;
        this.localAnchor = anchor;
        this.speed = 0.0f;
        this.clearInput();
        this.lerpSteps = 0;
        this.setViewRotation(viewYaw, viewPitch);
    }

    public void endLocalControl() {
        this.localControl = false;
        this.speed = 0.0f;
        this.clearInput();
    }

    private void clearInput() {
        this.inForward = false;
        this.inBack = false;
        this.inLeft = false;
        this.inRight = false;
        this.inBrake = false;
    }

    /** Cliente: teclas del piloto en este tick. */
    public void setLocalInput(boolean forward, boolean back, boolean left, boolean right, boolean brake) {
        this.inForward = forward;
        this.inBack = back;
        this.inLeft = left;
        this.inRight = right;
        this.inBrake = brake;
    }

    /** Cliente: hacia dónde mira la cámara del piloto (puede mirar a los lados mientras el carro sigue derecho). */
    public void setViewRotation(float yaw, float pitch) {
        this.viewYaw = yaw;
        this.viewPitch = pitch;
    }

    @Override
    public float getViewYRot(float partialTick) {
        return this.localControl ? this.viewYaw : super.getViewYRot(partialTick);
    }

    @Override
    public float getViewXRot(float partialTick) {
        return this.localControl ? this.viewPitch : super.getViewXRot(partialTick);
    }

    private void localDriveTick() {
        Level level = this.level();
        boolean grounded = this.onGround();
        float v = this.speed;

        // ---- gas, freno y reversa
        v = RcCarPhysics.speedAfter(v, grounded, this.inForward, this.inBack, this.inBrake);

        // ---- seguridad: no correr a ciegas hacia un chunk que aún no se cargó
        float yawRad0 = this.getYRot() * Mth.DEG_TO_RAD;
        double hx = -Mth.sin(yawRad0);
        double hz = Mth.cos(yawRad0);
        if (v > UNLOADED_LIMIT) {
            double look = 8.0 + v * 8.0;
            BlockPos ahead = new BlockPos(Mth.floor(this.getX() + hx * look), Mth.floor(this.getY()),
                    Mth.floor(this.getZ() + hz * look));
            if (!level.hasChunkAt(ahead)) {
                v = Math.max(UNLOADED_LIMIT, v - 0.1f);
            }
        }

        // ---- dirección: cuanto más rápido, más abierta la curva
        float steerInput = (this.inRight ? 1.0f : 0.0f) - (this.inLeft ? 1.0f : 0.0f);
        float turn = RcCarPhysics.turnAfter(v, steerInput, this.inBrake);
        float absV = Math.abs(v);
        this.setYRot(this.getYRot() + turn);
        this.yawDelta = turn;

        // ---- movimiento
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        hx = -Mth.sin(yawRad);
        hz = Mth.cos(yawRad);
        double dx = hx * v;
        double dz = hz * v;
        double vy = grounded ? -0.04 : Math.max((this.getDeltaMovement().y - 0.08) * 0.98, -MAX_FALL_SPEED);

        // sube solo los escalones bajos que le caben
        double lift = 0.0;
        if (grounded && absV > 0.01f) {
            AABB swept = this.getBoundingBox().expandTowards(dx, 0.0, dz);
            if (!level.noCollision(this, swept)) {
                for (double h : STEP_LIFTS) {
                    if (level.noCollision(this, swept.move(0.0, h, 0.0))) {
                        lift = h;
                        break;
                    }
                }
            }
        }

        double beforeX = this.getX();
        double beforeY = this.getY();
        double beforeZ = this.getZ();
        this.setDeltaMovement(dx, vy, dz);
        this.move(MoverType.SELF, new Vec3(dx, vy + lift, dz));

        if (this.horizontalCollision) {
            double moved = Math.sqrt((this.getX() - beforeX) * (this.getX() - beforeX)
                    + (this.getZ() - beforeZ) * (this.getZ() - beforeZ));
            if (absV > 0.25f && moved < absV * 0.4) {
                level.playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND,
                        SoundSource.NEUTRAL, 0.25f, 1.8f, false);
            }
            v = (v < 0.0f ? -1.0f : 1.0f) * (float) Math.min(absV * 0.5, moved);
        }

        // ---- alcance máximo (el mismo que valida el servidor): al llegar al borde se queda parado
        if (this.position().distanceToSqr(this.localAnchor) > MAX_RANGE * MAX_RANGE) {
            this.setPos(beforeX, beforeY, beforeZ);
            v = 0.0f;
        }
        this.speed = v;
    }

    // =====================================================================================================
    // Visuales y datos para el cliente (todos los clientes)
    // =====================================================================================================

    private void updateVisuals() {
        if (!this.level().isClientSide) {
            return;
        }
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        float signed = (float) (dx * -Mth.sin(yawRad) + dz * Mth.cos(yawRad));
        this.visualSpeed = signed;
        if (!this.localControl) {
            this.yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        }

        this.wheelRotO = this.wheelRot;
        this.wheelRot += Mth.clamp(signed / WHEEL_RADIUS, -2.2f, 2.2f);

        this.steerO = this.steer;
        float target;
        if (this.localControl) {
            target = ((this.inRight ? 1.0f : 0.0f) - (this.inLeft ? 1.0f : 0.0f)) * MAX_STEER_VISUAL;
        } else {
            target = Mth.clamp(this.yawDelta * 0.1f * (signed < -0.01f ? -1.0f : 1.0f),
                    -MAX_STEER_VISUAL, MAX_STEER_VISUAL);
        }
        this.steer += (target - this.steer) * 0.4f;
    }

    /** Velocidad en bloques por tick (con signo: negativo = reversa), medida por lo que se movió. */
    public float getVisualSpeed() {
        return this.visualSpeed;
    }

    /** Giro del carro en este tick (grados), que el cliente del piloto suma a su vista para que la cámara gire con él. */
    public float getYawDelta() {
        return this.yawDelta;
    }

    public float getWheelRot(float partialTick) {
        return Mth.lerp(partialTick, this.wheelRotO, this.wheelRot);
    }

    public float getSteer(float partialTick) {
        return Mth.lerp(partialTick, this.steerO, this.steer);
    }

    // =====================================================================================================
    // Interpolación (quienes miran el carro) y control local
    // =====================================================================================================

    private void tickLerp() {
        if (this.localControl) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            double x = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
            double y = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
            double z = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
            double yawDiff = Mth.wrapDegrees(this.lerpYRot - (double) this.getYRot());
            this.setYRot(this.getYRot() + (float) yawDiff / (float) this.lerpSteps);
            this.setXRot(this.getXRot() + (float) (this.lerpXRot - (double) this.getXRot()) / (float) this.lerpSteps);
            --this.lerpSteps;
            this.setPos(x, y, z);
            this.setRot(this.getYRot(), this.getXRot());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.localControl) {
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = (double) yRot;
        this.lerpXRot = (double) xRot;
        this.lerpSteps = 3;
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        if (this.localControl) {
            return;
        }
        super.lerpMotion(x, y, z);
    }

    /** El servidor no puede mover el carro que maneja este mismo cliente (la posición la manda el piloto). */
    @Override
    public boolean isControlledByLocalInstance() {
        return this.localControl || super.isControlledByLocalInstance();
    }

    // =====================================================================================================
    // Recoger y golpear
    // =====================================================================================================

    /** Clic derecho agachado (sin el control remoto) lo recoge como ítem si nadie lo está manejando. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() && !this.isPiloted()) {
            if (!this.level().isClientSide) {
                this.pickUp(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void pickUp(Player player) {
        if (!player.getAbilities().instabuild) {
            this.giveBack(player);
        }
        this.discard();
    }

    /** Un golpe de un jugador lo recoge (si nadie lo maneja); el resto del daño no le hace nada. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || this.level().isClientSide) {
            return false;
        }
        if (source.getEntity() instanceof Player player && !this.isPiloted()) {
            this.pickUp(player);
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.RC_CAR.get());
    }

    // =====================================================================================================

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

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
        // Una conexión de piloto es temporal y a propósito no se guarda.
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
