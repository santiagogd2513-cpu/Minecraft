package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Helicóptero BOING HX-6 (V3.0.0, parodia de una marca de aviones y helicópteros famosa): cabe el piloto (primer
 * asiento delantero, a la izquierda), un copiloto y dos pasajeros atrás.
 * <ul>
 *   <li><b>Espacio</b> sube, <b>Shift</b> baja, <b>W / S</b> avanzan y retroceden, <b>A / D</b> lo giran a la izquierda y a la
 *       derecha, y <b>Z</b> te baja (solo si estás a menos de 2 bloques del suelo, para no caerte sin querer).</li>
 *   <li>Sin tocar nada se queda flotando en su sitio. Si el piloto desaparece (se desconecta), baja despacio solo.</li>
 *   <li>Los rotores tardan un par de segundos en alcanzar su régimen al subirte; hasta entonces no despega.</li>
 * </ul>
 * Funciona igual que los demás vehículos (y que una barca de vanilla): el cliente del piloto calcula el vuelo, así es
 * fluido, y se lo manda al servidor con el paquete de vehículo de siempre; el servidor solo valida.
 * <p>
 * Ojo: en un servidor dedicado con {@code allow-flight=false}, vanilla expulsa a quien lleva un vehículo flotando más de
 * 4 segundos. En un mundo de un jugador o con {@code allow-flight=true} no pasa nada.
 * <p>
 * La caja de colisión de cualquier entidad no gira con ella: el helicóptero se ve de más de 6 bloques de largo pero
 * choca como una caja de 2,4 x 2,9 (los rotores no chocan con nada).
 */
public class HelicopterEntity extends Entity implements PaintableVehicle, HasVehicleStorage {
    /** Velocidad horizontal máxima hacia adelante, en bloques por tick (V4.4.0: 60 km/h = 16,7 bloques por segundo). */
    public static final float MAX_SPEED = 0.833f;     // V4.4.0: 60 km/h = 16,7 bloques por segundo
    /** Velocidad máxima hacia atrás. */
    public static final float REVERSE_SPEED = 0.3f;
    /** Velocidad vertical máxima al subir o bajar (V4.4.0: 5 bloques por segundo). */
    public static final float CLIMB_SPEED = 0.25f;
    /** Giro máximo por tick con A / D, en grados. */
    public static final float YAW_RATE = 3.2f;
    /** Cuántos bloques de altura sobre el suelo permite bajarse con Z. */
    public static final double EXIT_MAX_HEIGHT = 2.0;
    /** Régimen mínimo de los rotores (0 a 1) para poder despegar y maniobrar. */
    public static final float TAKEOFF_RPM = 0.6f;
    public static final int SEATS = 4;

    // V3.2.0: colores que el jugador le puso con la tecla R (-1 = el de siempre).
    /** V4.7.0: cantidad de objetos en el almacenamiento (sincronizada). */
    private static final EntityDataAccessor<Integer> CARGO =
            SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_BODY =
            SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_HANDLE =
            SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_RIM =
            SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    // V3.6.0: los colores de "Otros" (vidrios, asientos, luces...) viajan juntos en un texto (ver PaintExtras).
    private static final EntityDataAccessor<String> PAINT_EXTRA =
            SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.STRING);

    private static final float SPOOL_UP = 0.0055f;     // V4.4.0: ~9 s hasta el régimen máximo (antes 2,5 s); puede despegar al 60 % (~5,5 s)
    private static final float SPOOL_DOWN = 0.004f;
    /** Descenso máximo sin piloto (bloques por tick): autorrotación suave. */
    private static final double AUTOROTATE_FALL = 0.3;
    private static final float UNLOADED_LIMIT = 0.7f;

    /** Asientos: {lado (positivo = izquierda), adelante}. El primero es el piloto. */
    private static final double[][] SEATS_POS = {
            {0.375, 0.0}, {-0.375, 0.0}, {0.375, -0.875}, {-0.375, -0.875}};

    // ---- interpolación para quienes miran el helicóptero (igual que la barca)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // ---- cliente del piloto
    private double velX;
    private double velY;
    private double velZ;
    private boolean inForward;
    private boolean inBack;
    private boolean inLeft;
    private boolean inRight;
    private boolean inUp;
    private boolean inDown;

    // ---- visuales (todos los clientes)
    private float rpm;
    private float rpmO;
    private float rotorAngle;
    private float rotorAngleO;
    private float tailAngle;
    private float tailAngleO;
    private float pitchVis;
    private float pitchVisO;
    private float rollVis;
    private float rollVisO;
    private float yawDelta;
    private float visualForward;
    private float visualSpeed;
    private float visualClimb;

    // ---- vista previa del menú de colores (solo cliente)
    @Nullable
    private int[] paintPreview;
    private final PaintExtras paintExtras = new PaintExtras();

    // ---- servidor
    private Vec3 lastRiddenVelocity = Vec3.ZERO;
    private boolean wasRidden;

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.storage = new VehicleStorage.Box(this, 3);          // V3.9.0
    }

    // =====================================================================================================
    // Asientos, subir, bajar, recoger
    // =====================================================================================================

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CARGO, 0);
        this.entityData.define(PAINT_BODY, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_HANDLE, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_RIM, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_EXTRA, PaintExtras.EMPTY);
    }

    // =====================================================================================================
    // Colores (V3.2.0)
    // =====================================================================================================

    @Override
    public VehiclePaint.Kind paintKind() {
        return VehiclePaint.Kind.HELICOPTER;
    }

    @Override
    public int getPaintRaw(int channel) {
        return switch (channel) {
            case VehiclePaint.BODY -> this.entityData.get(PAINT_BODY);
            case VehiclePaint.HANDLE -> this.entityData.get(PAINT_HANDLE);
            case VehiclePaint.RIM -> this.entityData.get(PAINT_RIM);
            default -> channel >= VehiclePaint.EXTRA_FIRST
                    ? this.paintExtras.get(this.entityData.get(PAINT_EXTRA), channel - VehiclePaint.EXTRA_FIRST)
                    : VehiclePaint.ORIGINAL;
        };
    }

    @Override
    public void setPaintRaw(int channel, int rgbOrOriginal) {
        int value = VehiclePaint.clean(this.paintKind(), channel, rgbOrOriginal);
        switch (channel) {
            case VehiclePaint.BODY -> this.entityData.set(PAINT_BODY, value);
            case VehiclePaint.HANDLE -> this.entityData.set(PAINT_HANDLE, value);
            case VehiclePaint.RIM -> this.entityData.set(PAINT_RIM, value);
            default -> {
                if (channel >= VehiclePaint.EXTRA_FIRST && channel < VehiclePaint.CHANNELS) {
                    this.entityData.set(PAINT_EXTRA, PaintExtras.with(this.entityData.get(PAINT_EXTRA),
                            channel - VehiclePaint.EXTRA_FIRST, value));
                }
            }
        }
    }

    @Override
    @Nullable
    public int[] getPaintPreview() {
        return this.paintPreview;
    }

    @Override
    public void setPaintPreview(@Nullable int[] colors) {
        this.paintPreview = colors == null ? null : colors.clone();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < SEATS;
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = this.getFirstPassenger();
        return first instanceof LivingEntity living ? living : null;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.37;
    }

    /** Cuatro asientos: piloto y copiloto delante, dos pasajeros en la banca de atrás. */
    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int index = Math.min(this.getPassengers().indexOf(passenger), SEATS - 1);
        double side = SEATS_POS[index][0];
        double forward = SEATS_POS[index][1];
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double ox = Mth.cos(yawRad) * side - Mth.sin(yawRad) * forward;
        double oz = Mth.sin(yawRad) * side + Mth.cos(yawRad) * forward;
        double y = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
        callback.accept(passenger, this.getX() + ox, y, this.getZ() + oz);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!this.isVehicle()) {
                if (!this.level().isClientSide) {
                    this.pickUp(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.startRiding(this)) {
            if (this.getControllingPassenger() == player) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HELICOPTER_START.get(),
                        SoundSource.NEUTRAL, 1.6f, 1.0f);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private void pickUp(Player player) {
        ItemStack stack = VehiclePaint.carry(this, this.getPickResult());
        if (!player.getAbilities().instabuild && !player.getInventory().add(stack)) {
            this.spawnAtLocation(stack);
        }
        this.discard();
    }

    /** Un golpe de un jugador lo recoge (si no lo está montando nadie); el resto del daño no le hace nada. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || this.level().isClientSide) {
            return false;
        }
        if (source.getEntity() instanceof Player player && !this.isVehicle()) {
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(VehiclePaint.carry(this, this.getPickResult()));
            }
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.HELICOPTER.get());
    }

    /** Distancia (en bloques, hasta {@code max}) entre las patas y el suelo o el agua; {@code max + 1} si no hay nada debajo. */
    public double heightAboveGround(double max) {
        AABB box = this.getBoundingBox();
        for (double d = 0.0; d <= max; d += 0.25) {
            AABB lowered = box.move(0.0, -d - 0.01, 0.0);
            if (!this.level().noCollision(this, lowered) || this.level().containsAnyLiquid(lowered)) {
                return d;
            }
        }
        return max + 1.0;
    }

    public boolean canExit() {
        return this.heightAboveGround(EXIT_MAX_HEIGHT + 0.5) <= EXIT_MAX_HEIGHT;
    }

    /** Servidor: el jugador pidió bajarse con Z. */
    public void requestExit(ServerPlayer player) {
        if (!this.hasPassenger(player)) {
            return;
        }
        if (!this.canExit()) {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.helicopter_exit_blocked"), true);
            return;
        }
        player.stopRiding();
    }

    /** Al bajarte apareces al lado del helicóptero (izquierda o derecha, donde haya sitio), apoyado en el suelo. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double leftX = Mth.cos(yawRad);
        double leftZ = Mth.sin(yawRad);
        for (double side : new double[]{2.2, -2.2, 3.0, -3.0}) {
            Vec3 base = new Vec3(this.getX() + leftX * side, this.getY(), this.getZ() + leftZ * side);
            for (double lift : new double[]{0.0, 1.0}) {
                Vec3 best = null;
                for (double dy = lift; dy >= -2.5; dy -= 0.25) {
                    Vec3 target = base.add(0.0, dy, 0.0);
                    AABB box = passenger.getBoundingBox().move(target.subtract(passenger.position()));
                    if (!this.level().noCollision(passenger, box)) {
                        break;
                    }
                    best = target;
                }
                if (best != null) {
                    return best;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // =====================================================================================================
    // Tick
    // =====================================================================================================

    @Override
    public void tick() {
        super.tick();
        this.syncCargo();
        this.tickLerp();

        if (this.isControlledByLocalInstance()) {
            this.flyTick();
        } else if (!this.level().isClientSide && !this.isVehicle()) {
            this.freeTick();
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.lastRiddenVelocity = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
                this.wasRidden = true;
            }
        }
        this.updateVisuals();
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
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
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = (double) yRot;
        this.lerpXRot = (double) xRot;
        this.lerpSteps = 10;
    }

    /** Servidor, sin piloto: baja despacio hasta posarse (y sigue deslizando un poco si el piloto desapareció en pleno vuelo). */
    private void freeTick() {
        Vec3 motion = this.getDeltaMovement();
        if (this.wasRidden) {
            this.wasRidden = false;
            motion = this.lastRiddenVelocity.scale(0.9);
        }
        boolean grounded = this.onGround();
        double friction = grounded ? 0.7 : 0.97;
        double vx = motion.x * friction;
        double vz = motion.z * friction;
        if (Math.abs(vx) < 0.004) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 0.004) {
            vz = 0.0;
        }
        double vy = grounded ? -0.04 : Math.max(motion.y - 0.03, -AUTOROTATE_FALL);
        this.setDeltaMovement(vx, vy, vz);
        if (vx != 0.0 || vz != 0.0 || !grounded) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    // =====================================================================================================
    // Vuelo (cliente del piloto)
    // =====================================================================================================

    /** Cliente: teclas del piloto en este tick. */
    public void setRiderInput(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
        this.inForward = forward;
        this.inBack = back;
        this.inLeft = left;
        this.inRight = right;
        this.inUp = up;
        this.inDown = down;
    }

    /** V4.7.0: peso relativo con lo que lleva (1 = vacío). */
    private float loadFactor() {
        return FlightPhysics.heliLoad(this.entityData.get(CARGO));
    }


    /** V4.7.0: objetos en el almacenamiento (el servidor lo cuenta cada segundo y lo sincroniza: el cliente que conduce no ve el contenido). */
    private void syncCargo() {
        if (!this.level().isClientSide && this instanceof HasVehicleStorage st && st.hasStorage() && this.tickCount % 20 == 0) {
            int items = 0;
            net.minecraft.world.SimpleContainer box = st.getStorage();
            for (int i = 0; i < box.getContainerSize(); i++) {
                items += box.getItem(i).getCount();
            }
            if (items != this.entityData.get(CARGO)) {
                this.entityData.set(CARGO, items);
            }
        }
    }

    private void flyTick() {
        Level level = this.level();
        boolean powered = this.rpm >= TAKEOFF_RPM;
        float power = Mth.clamp((this.rpm - TAKEOFF_RPM) / (1.0f - TAKEOFF_RPM), 0.0f, 1.0f);

        // ---- giro con A / D
        float steer = (this.inRight ? 1.0f : 0.0f) - (this.inLeft ? 1.0f : 0.0f);
        float turn = powered ? steer * YAW_RATE * power : 0.0f;
        this.setYRot(this.getYRot() + turn);
        this.yawDelta = turn;

        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double hx = -Mth.sin(yawRad);
        double hz = Mth.cos(yawRad);
        double lx = Mth.cos(yawRad);
        double lz = Mth.sin(yawRad);

        // ---- adelante / atrás (la inercia se va hacia donde apunta el morro; el deslizamiento lateral se frena rápido)
        double forward = this.velX * hx + this.velZ * hz;
        double lateral = this.velX * lx + this.velZ * lz;
        double target = 0.0;
        if (powered) {
            target = this.inForward && !this.inBack ? MAX_SPEED : this.inBack && !this.inForward ? -REVERSE_SPEED : 0.0;
            if (this.onGround()) {
                target *= 0.2;                                          // V4.4.0: en tierra apenas rueda; para avanzar de verdad primero se levanta
            }
        }
        double accel = (this.inForward || this.inBack) ? 0.009 : 0.006;     // V4.7.0: ~3 m/s² (antes ~12): tarda en arrancar y en parar
        if (target * forward < 0.0) {
            accel = 0.012;                                                // frenar (inclinar contra el avance) es algo más rápido
        }
        accel /= this.loadFactor();   // V3.4.0: más empuje para llegar a 68 b/s en unos 2,5 segundos
        forward += Mth.clamp(target - forward, -accel, accel);
        lateral *= 0.94;                                                  // V4.7.0: se desliza más antes de agarrarse

        // ---- seguridad: no volar a ciegas hacia un chunk que aún no se cargó
        if (forward > UNLOADED_LIMIT) {
            double look = 10.0 + forward * 6.0;
            BlockPos ahead = new BlockPos(Mth.floor(this.getX() + hx * look), Mth.floor(this.getY()),
                    Mth.floor(this.getZ() + hz * look));
            if (!level.hasChunkAt(ahead)) {
                forward = Math.max(UNLOADED_LIMIT, forward - 0.3);
            }
        }
        this.velX = hx * forward + lx * lateral;
        this.velZ = hz * forward + lz * lateral;

        // ---- Espacio sube, Shift baja, nada = se queda flotando
        double climbTarget;
        if (!powered) {
            climbTarget = this.onGround() ? -0.04 : -0.3;
        } else if (this.inUp && !this.inDown) {
            // V4.7.0: el rotor levanta menos con carga (puede no levantar del todo) y hace falta tenerlo casi a tope
            float margin = FlightPhysics.heliLiftMargin(this.loadFactor());
            climbTarget = CLIMB_SPEED * margin * FlightPhysics.heliRpmFactor(this.rpm, TAKEOFF_RPM, margin);
        } else if (this.inDown && !this.inUp) {
            climbTarget = -CLIMB_SPEED;
        } else {
            float margin = FlightPhysics.heliLiftMargin(this.loadFactor());
            climbTarget = margin < 0.3f && !this.onGround() ? -(0.3f - margin) * 0.15 : 0.0;   // sobrecargado y sin empujar hacia arriba: se hunde despacio
        }
        if (climbTarget > 0.0 && this.getY() + this.getBbHeight() + 1.0 >= level.getMaxBuildHeight()) {
            climbTarget = 0.0;                                           // techo del mundo
        }
        this.velY += Mth.clamp(climbTarget - this.velY, -0.03, 0.012 / this.loadFactor());     // V4.4.0: se levanta y se asienta poco a poco

        // ---- movimiento (el servidor repite el mismo movimiento sin problema)
        this.setDeltaMovement(this.velX, this.velY, this.velZ);
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 after = this.getDeltaMovement();                          // move() ya anuló lo que chocó
        this.velX = after.x;
        this.velY = after.y;
        this.velZ = after.z;
        if (this.horizontalCollision) {
            this.velX *= 0.5;
            this.velZ *= 0.5;
        }
    }

    // =====================================================================================================
    // Visuales (todos los clientes)
    // =====================================================================================================

    private void updateVisuals() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        this.visualForward = (float) (dx * -Mth.sin(yawRad) + dz * Mth.cos(yawRad));
        float lateral = (float) (dx * Mth.cos(yawRad) + dz * Mth.sin(yawRad));
        this.visualSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        this.visualClimb = (float) (this.getY() - this.yo);
        if (!this.isControlledByLocalInstance()) {
            this.yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        }

        // ---- rotores: tardan en arrancar y en pararse
        this.rpmO = this.rpm;
        float targetRpm = this.isVehicle() ? 1.0f : 0.0f;
        this.rpm = this.rpm < targetRpm ? Math.min(targetRpm, this.rpm + SPOOL_UP) : Math.max(targetRpm, this.rpm - SPOOL_DOWN);
        this.rotorAngleO = this.rotorAngle;
        this.tailAngleO = this.tailAngle;
        this.rotorAngle += this.rpm * 0.95f;
        this.tailAngle += this.rpm * 1.6f;
        if (this.rotorAngle > Mth.TWO_PI) {
            this.rotorAngle -= Mth.TWO_PI;
            this.rotorAngleO -= Mth.TWO_PI;
        }
        if (this.tailAngle > Mth.TWO_PI) {
            this.tailAngle -= Mth.TWO_PI;
            this.tailAngleO -= Mth.TWO_PI;
        }

        // ---- inclinación: el morro baja al avanzar y sube al frenar; se escora hacia donde gira y se desliza
        this.pitchVisO = this.pitchVis;
        this.rollVisO = this.rollVis;
        float pitchTarget = Mth.clamp(this.visualForward / MAX_SPEED * 12.0f, -7.0f, 12.0f);
        float rollTarget = Mth.clamp(-this.yawDelta * 2.2f + lateral * 18.0f, -14.0f, 14.0f);
        if (this.rpm < 0.3f) {
            pitchTarget = 0.0f;
            rollTarget = 0.0f;
        }
        this.pitchVis += (pitchTarget - this.pitchVis) * 0.15f;
        this.rollVis += (rollTarget - this.rollVis) * 0.15f;
    }

    /** Régimen de los rotores de 0 (parados) a 1 (a tope). */
    public float getRpm() {
        return this.rpm;
    }

    public float getRotorAngle(float partialTick) {
        return Mth.lerp(partialTick, this.rotorAngleO, this.rotorAngle);
    }

    public float getTailAngle(float partialTick) {
        return Mth.lerp(partialTick, this.tailAngleO, this.tailAngle);
    }

    /** Cabeceo en grados (positivo = morro hacia abajo). */
    public float getPitchVisual(float partialTick) {
        return Mth.lerp(partialTick, this.pitchVisO, this.pitchVis);
    }

    /** Alabeo en grados (positivo = se inclina hacia su izquierda). */
    public float getRollVisual(float partialTick) {
        return Mth.lerp(partialTick, this.rollVisO, this.rollVis);
    }

    /** Velocidad horizontal en bloques por tick, medida por lo que se movió. */
    public float getVisualSpeed() {
        return this.visualSpeed;
    }

    public float getVisualClimb() {
        return this.visualClimb;
    }

    /** Giro del helicóptero en este tick (grados), que el cliente del piloto suma a su vista. */
    public float getYawDelta() {
        return this.yawDelta;
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
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // =====================================================================================================
    // V3.9.0: almacenamiento (3 filas)
    // =====================================================================================================

    private final SimpleContainer storage;

    @Override
    public int storageRows() {
        return 3;
    }

    @Override
    public SimpleContainer getStorage() {
        return this.storage;
    }

    @Override
    public Component storageTitle() {
        return Component.translatable("container.beautifuldecor.storage." + this.paintKind().texture());
    }

    /** Al recogerlo, destruirlo o matarlo, lo que lleva dentro cae al suelo. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy()) {
            VehicleStorage.drop(this, this.storage);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        VehiclePaint.read(this, tag);
        VehicleStorage.load(this.storage, tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        VehiclePaint.write(this, tag);
        VehicleStorage.save(this.storage, tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
