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
 * Avión de pasajeros BOING 7G7 Gigante (V3.8.0, parodia de una marca de aviones famosa): 62 bloques de largo y 58 de envergadura, 4 turbinas,
 * cabina de mando y cabina de pasajeros HUECAS (el jugador va sentado por dentro) y capacidad para {@link #SEATS} jugadores en total
 * (piloto, copiloto y 128 pasajeros en filas de 2-4-2). El primero que se sube es el piloto.
 * <ul>
 *   <li><b>W</b> acelera, <b>S</b> frena (en tierra, ya parado, retrocede despacio), <b>A / D</b> lo giran.</li>
 *   <li><b>Espacio</b>: el morro se inclina hacia arriba y el avión sube; en tierra despega al pasar de {@link #TAKEOFF_SPEED}.
 *       <b>Shift</b>: el morro se inclina hacia abajo y baja (en tierra no hace nada). Sin tocar nada vuela recto y nivelado.</li>
 *   <li><b>F</b> recoge o baja el tren de aterrizaje (solo se recoge en el aire; con el tren arriba, al tocar tierra se arrastra y frena).</li>
 *   <li><b>Z</b> te baja (solo a menos de 2 bloques del suelo). Shift no te baja: en vanilla es la tecla de bajarse, aquí es la de descender.</li>
 * </ul>
 * Funciona igual que el helicóptero y que una barca de vanilla: el cliente del piloto calcula el vuelo y se lo manda al servidor con el
 * paquete de vehículo de siempre (posición, giro y cabeceo); el servidor solo valida. El cabeceo es el {@code xRot} de la entidad
 * (positivo = morro hacia abajo), así viaja solo hasta los demás jugadores.
 * <p>
 * Ojo: en un servidor dedicado con {@code allow-flight=false}, vanilla expulsa a quien lleva un vehículo flotando más de 4 segundos.
 * La caja de colisión no gira ni cubre las alas: choca como una caja de 7 x 7,5 x 7 bloques (el modelo se ve de 62 de largo); para subirte
 * haz clic derecho en el centro del fuselaje, cerca de las alas.
 */
public class AirlinerEntity extends Entity implements PaintableVehicle, HasVehicleStorage {
    /** Velocidad máxima hacia adelante en bloques por tick (V4.4.0: 9 = 180 bloques por segundo = 648 km/h). */
    public static final float MAX_SPEED = 9.0f;     // V4.4.0: 648 km/h (avión comercial) = 180 bloques por segundo
    public static final float REVERSE_SPEED = 0.35f;
    /** Velocidad mínima para despegar desde el suelo (V4.4.0: 72 bloques por segundo = 260 km/h). */
    public static final float TAKEOFF_SPEED = 3.6f;
    /** En el aire nunca baja de esta velocidad (V4.4.0: 56 bloques por segundo = 200 km/h): no hay pérdida de sustentación. */
    public static final float MIN_AIR_SPEED = 2.8f;
    /** Velocidad vertical máxima al subir y al bajar (11 y 12 bloques por segundo). */
    public static final float CLIMB_SPEED = 0.55f;
    public static final float DESCEND_SPEED = 0.6f;
    /** Cuántos bloques de altura sobre el suelo permite bajarse con Z. */
    public static final double EXIT_MAX_HEIGHT = 2.0;
    /** Régimen mínimo de las turbinas (0 a 1) para poder acelerar. */
    public static final float TAKEOFF_RPM = 0.6f;
    /** Plazas: piloto y copiloto + 16 filas de 8 pasajeros. */
    public static final int SEATS = 130;
    /** Altura mínima sobre el suelo para poder recoger el tren. */
    public static final double GEAR_RETRACT_MIN_HEIGHT = 3.0;
    /** Por debajo de esta altura (bloques) sobre el suelo se considera apoyado en las ruedas. */
    private static final double GROUND_TOUCH_HEIGHT = 0.35;
    /** Cabeceo máximo (grados): hacia arriba, y hacia abajo en el aire. Cerca del suelo se limita para que la cola no se entierre. */
    public static final float MAX_PITCH_UP = 14.0f;
    public static final float MAX_PITCH_DOWN = 12.0f;
    public static final float GROUND_PITCH_UP = 5.0f;
    private static final float PITCH_RATE = 0.45f;
    /** El avión se inclina alrededor del punto del suelo bajo las ruedas principales, esta distancia (bloques) detrás del centro. */
    public static final double PIVOT_BACK = 1.5;
    /** Altura de los pies de quien va sentado (bloques): asiento a 4 bloques menos 0,75 (altura de la pose sentada). */
    private static final double SEAT_FEET_Y = 16.0 / 4.0 - 0.75;

    // Colores que el jugador le puso con la tecla R (-1 = el de siempre).

    /** V6.6.0: luces encendidas (faros que brillan y luz real alrededor; ver VehicleLights). */
    private static final EntityDataAccessor<Boolean> LIGHT =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.BOOLEAN);

    public boolean isLightOn() {
        return this.entityData.get(LIGHT);
    }

    private void toggleLight(ServerPlayer player) {
        boolean on = !this.isLightOn();
        this.entityData.set(LIGHT, on);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.8f, on ? 1.2f : 0.9f);
        player.displayClientMessage(Component.translatable(on ? "hud.beautifuldecor.motorcycle_light_on" : "hud.beautifuldecor.motorcycle_light_off"), true);
    }

    /** V6.6.0: ¿apunta el jugador a este punto del interior (x izquierda, y arriba, z adelante, en metros desde el centro) con tolerancia r? */
    private boolean lookingAt(Player player, double lx, double ly, double lz, double r) {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double px = this.getX() - Mth.sin(yaw) * lz + Mth.cos(yaw) * lx;
        double py = this.getY() + ly;
        double pz = this.getZ() + Mth.cos(yaw) * lz + Mth.sin(yaw) * lx;
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
        double t = (px - eye.x) * look.x + (py - eye.y) * look.y + (pz - eye.z) * look.z;
        if (t < 0.05 || t > 4.0) {
            return false;
        }
        double cx = eye.x + look.x * t - px;
        double cy = eye.y + look.y * t - py;
        double cz = eye.z + look.z * t - pz;
        return cx * cx + cy * cy + cz * cz < r * r;
    }

    /** V6.6.0: botón de luces en el pedestal (tools/v380/airliner.py: btn_light, 1/4 de bloque por unidad). */
    private static final double[] LIGHT_BUTTON = {2.0 / 4.0, 18.5 / 4.0, 85.0 / 4.0};
    /** V6.6.0: los pilotos van sentados 75 cm más alto para ver por el parabrisas (el modelo lo sube igual: tools/v380/airliner.py). */
    private static final double PILOT_RAISE = 0.75;

    private static final EntityDataAccessor<Integer> PAINT_BODY =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_HANDLE =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_RIM =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PAINT_EXTRA =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.STRING);
    /** true = tren de aterrizaje recogido. */
    /** V4.7.0: cantidad de objetos en el almacenamiento (sincronizada). */
    private static final EntityDataAccessor<Integer> CARGO =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GEAR_UP =
            SynchedEntityData.defineId(AirlinerEntity.class, EntityDataSerializers.BOOLEAN);

    private static final float SPOOL_UP = 0.0045f;     // V4.4.0: ~11 s hasta el régimen máximo; puede empezar la carrera al 60 % (~6,5 s)
    private static final float SPOOL_DOWN = 0.003f;
    private static final float GEAR_SPEED = 0.03f;
    private static final float UNLOADED_LIMIT = 1.0f;

    /** Asientos: {lado (positivo = izquierda), altura de los pies, adelante} en bloques desde el centro del avión a nivel del suelo. */
    private static final double[][] SEAT_POS = buildSeatPositions();

    private static double[][] buildSeatPositions() {
        double[][] seats = new double[SEATS][];
        seats[0] = new double[]{2.5, SEAT_FEET_Y + PILOT_RAISE, 80.5 / 4.0};            // piloto (izquierda), asiento alto (V6.6.0)
        seats[1] = new double[]{-2.5, SEAT_FEET_Y + PILOT_RAISE, 80.5 / 4.0};           // copiloto
        double[] xs = {13.5, 10.5, 4.5, 1.5, -1.5, -4.5, -10.5, -13.5};   // 2 + pasillo + 4 + pasillo + 2 (unidades de 1/4 de bloque)
        int n = 2;
        for (int row = 0; row < 16; row++) {
            double z = (45 - 6 * row) / 4.0;
            for (double x : xs) {
                seats[n++] = new double[]{x / 4.0, SEAT_FEET_Y, z};
            }
        }
        if (n != SEATS) {
            throw new IllegalStateException("asientos del avión: " + n);
        }
        return seats;
    }

    // ---- interpolación para quienes miran el avión (igual que la barca)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // ---- cliente del piloto
    private float speed;
    private double velY;
    private boolean wasControlled;
    private boolean inForward;
    private boolean inBack;
    private boolean inLeft;
    private boolean inRight;
    private boolean inUp;
    private boolean inDown;
    private int scrapeWarning;

    // ---- visuales (todos los clientes)
    private float rpm;
    private float fanAngle;
    private float fanAngleO;
    private float wheelAngle;
    private float wheelAngleO;
    private float gearPos;
    private float gearPosO;
    private float yawDelta;
    private float pitchDelta;
    private float visualForward;
    private float visualSpeed;
    private float visualClimb;
    private double visVelX;
    private double visVelY;
    private double visVelZ;

    // ---- vista previa del menú de colores (solo cliente)
    @Nullable
    private int[] paintPreview;
    private final PaintExtras paintExtras = new PaintExtras();

    // ---- servidor
    private Vec3 lastRiddenVelocity = Vec3.ZERO;
    private boolean wasRidden;

    public AirlinerEntity(EntityType<? extends AirlinerEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.storage = new VehicleStorage.Box(this, 6);          // V3.9.0
    }

    // =====================================================================================================
    // Datos sincronizados y colores
    // =====================================================================================================

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PAINT_BODY, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_HANDLE, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_RIM, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_EXTRA, PaintExtras.EMPTY);
        this.entityData.define(GEAR_UP, Boolean.FALSE);
        this.entityData.define(LIGHT, Boolean.FALSE);
        this.entityData.define(CARGO, 0);
    }

    @Override
    public VehiclePaint.Kind paintKind() {
        return VehiclePaint.Kind.AIRLINER;
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

    // =====================================================================================================
    // Asientos, subir, bajar, recoger
    // =====================================================================================================

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
        return SEAT_FEET_Y + 0.35;
    }

    /** Posición (bloques, respecto al centro del avión a nivel del suelo) de los pies de quien ocupa el asiento número {@code index}. */
    public static double[] seatOffset(int index) {
        return SEAT_POS[Mth.clamp(index, 0, SEATS - 1)].clone();
    }

    /**
     * Cada pasajero ocupa un asiento según su orden de subida (0 = piloto, 1 = copiloto, luego las filas de adelante hacia atrás).
     * El asiento gira con el avión: primero el cabeceo (alrededor del punto bajo las ruedas principales) y luego el rumbo.
     */
    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int index = Math.min(this.getPassengers().indexOf(passenger), SEATS - 1);
        double[] seat = SEAT_POS[Math.max(index, 0)];
        double side = seat[0];
        double up = seat[1];
        double fwd = seat[2] + PIVOT_BACK;
        double theta = -this.getXRot() * Mth.DEG_TO_RAD;                  // positivo = morro hacia arriba
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double y = up * cos + fwd * sin;
        double f = fwd * cos - up * sin - PIVOT_BACK;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double ox = Mth.cos(yawRad) * side - Mth.sin(yawRad) * f;
        double oz = Mth.sin(yawRad) * side + Mth.cos(yawRad) * f;
        callback.accept(passenger, this.getX() + ox, this.getY() + y, this.getZ() + oz);
    }

    /** V6.5.0: el piloto y el copiloto pueden tocar la palanca del tren de aterrizaje del pedestal. */
    @Override
    public boolean canRiderInteract() {
        return true;
    }

    /** V6.5.0: posición de la palanca del tren en el modelo (tools/v380/airliner.py: 1 unidad = 1/4 de bloque): x izquierda, y arriba, z adelante. */
    private static final double[] GEAR_LEVER = {0.0, 19.5 / 4.0, 81.0 / 4.0};

    private boolean lookingAtGearLever(Player player) {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double px = this.getX() - Mth.sin(yaw) * GEAR_LEVER[2] + Mth.cos(yaw) * GEAR_LEVER[0];
        double py = this.getY() + GEAR_LEVER[1];
        double pz = this.getZ() + Mth.cos(yaw) * GEAR_LEVER[2] + Mth.sin(yaw) * GEAR_LEVER[0];
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
        double t = (px - eye.x) * look.x + (py - eye.y) * look.y + (pz - eye.z) * look.z;
        if (t < 0.05 || t > 4.0) {
            return false;
        }
        double cx = eye.x + look.x * t - px;
        double cy = eye.y + look.y * t - py;
        double cz = eye.z + look.z * t - pz;
        return cx * cx + cy * cy + cz * cz < 0.35 * 0.35;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getVehicle() == this && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive()) {
            boolean gear = this.lookingAtGearLever(player);
            boolean light = !gear && this.lookingAt(player, LIGHT_BUTTON[0], LIGHT_BUTTON[1], LIGHT_BUTTON[2], 0.3);
            if (!gear && !light) {
                return InteractionResult.PASS;
            }
            if (!this.level().isClientSide && this.getControllingPassenger() == player && player instanceof ServerPlayer sp) {
                if (gear) {
                    this.requestGear(sp);
                } else {
                    this.toggleLight(sp);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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
                this.level().playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ModSounds.AIRLINER_START.get(),
                        SoundSource.NEUTRAL, 3.0f, 1.0f);
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
        return new ItemStack(ModItems.AIRLINER.get());
    }

    /** Distancia (en bloques, hasta {@code max}) entre las ruedas y el suelo o el agua; {@code max + 1} si no hay nada debajo. */
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
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.airliner_exit_blocked"), true);
            return;
        }
        player.stopRiding();
    }

    /** Servidor: el piloto pidió recoger o bajar el tren de aterrizaje con F. */
    public void requestGear(ServerPlayer player) {
        if (!this.hasPassenger(player) || this.getControllingPassenger() != player) {
            return;
        }
        boolean up = this.isGearUp();
        if (!up && (this.onGround() || this.heightAboveGround(GEAR_RETRACT_MIN_HEIGHT + 1.0) < GEAR_RETRACT_MIN_HEIGHT)) {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.airliner_gear_ground"), true);
            return;
        }
        this.entityData.set(GEAR_UP, !up);
        this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
                up ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.NEUTRAL, 3.0f, 0.55f);
        player.displayClientMessage(Component.translatable(up ? "hud.beautifuldecor.airliner_gear_down" : "hud.beautifuldecor.airliner_gear_up"), true);
    }

    public boolean isGearUp() {
        return this.entityData.get(GEAR_UP);
    }

    /** Al bajarte apareces al lado de la puerta delantera (izquierda o derecha, donde haya sitio), apoyado en el suelo. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double leftX = Mth.cos(yawRad);
        double leftZ = Mth.sin(yawRad);
        double fwdX = -Mth.sin(yawRad);
        double fwdZ = Mth.cos(yawRad);
        for (double along : new double[]{15.0, -15.0, 0.0}) {
            for (double side : new double[]{5.5, -5.5, 7.0, -7.0}) {
                Vec3 base = new Vec3(this.getX() + leftX * side + fwdX * along, this.getY(), this.getZ() + leftZ * side + fwdZ * along);
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
        VehicleLights.update(this, this.isLightOn(), true);       // V6.4.0: foco potente (V6.6.0: con el botón de luces)

        if (this.isPilotedHere()) {
            this.flyTick();
        } else {
            this.wasControlled = false;
            if (!this.level().isClientSide && !this.isVehicle()) {
                this.freeTick();
            } else {
                this.setDeltaMovement(Vec3.ZERO);
                if (!this.level().isClientSide) {
                    this.lastRiddenVelocity = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
                    this.wasRidden = true;
                }
            }
        }
        this.updateVisuals();
        if (!this.level().isClientSide) {
            VehicleImpact.tick(this, VehicleImpact.AIRLINER, this.getControllingPassenger());      // V7.1.0: atropellos
        }
    }

    /**
     * V3.9.0: solo vuela el cliente del jugador que lo pilota. Antes se usaba {@code isControlledByLocalInstance()}, que en el servidor es
     * verdadero también cuando el avión está VACÍO: el servidor le aplicaba el vuelo (con la velocidad mínima en el aire) y el avión
     * se iba solo, como embrujado, en cuanto se ponía.
     */
    private boolean isPilotedHere() {
        return this.getControllingPassenger() instanceof Player pilot && pilot.isLocalPlayer();
    }

    private void tickLerp() {
        if (this.isPilotedHere()) {
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

    /** Servidor, sin piloto: sigue un poco por inercia y baja despacio hasta posarse, enderezando el morro. */
    private void freeTick() {
        Vec3 motion = this.getDeltaMovement();
        if (this.wasRidden) {
            this.wasRidden = false;
            motion = this.lastRiddenVelocity.scale(0.9);
        }
        boolean grounded = this.onGround();
        double friction = grounded ? 0.9 : 0.995;
        double vx = motion.x * friction;
        double vz = motion.z * friction;
        if (Math.abs(vx) < 0.004) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 0.004) {
            vz = 0.0;
        }
        double vy = grounded ? -0.08 : Math.max(motion.y - 0.02, -0.3);
        this.setDeltaMovement(vx, vy, vz);
        if (vx != 0.0 || vz != 0.0 || !grounded) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
        float pitch = this.getXRot();
        if (pitch != 0.0f) {
            this.setXRot(pitch + Mth.clamp(-pitch, -1.0f, 1.0f));
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
        return FlightPhysics.airlinerLoad(this.entityData.get(CARGO));
    }


    /** V4.7.0: objetos en el almacenamiento (el servidor lo cuenta cada segundo y lo sincroniza: el cliente que conduce no ve el contenido). */
    private void syncCargo() {
        if (!this.level().isClientSide && this.hasStorage() && this.tickCount % 20 == 0) {
            int items = 0;
            net.minecraft.world.SimpleContainer box = this.getStorage();
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
        if (!this.wasControlled) {                     // empieza a pilotar este cliente: recoge la velocidad con la que ya iba
            this.wasControlled = true;
            double heading = this.getYRot() * Mth.DEG_TO_RAD;
            this.speed = (float) (this.visVelX * -Math.sin(heading) + this.visVelZ * Math.cos(heading));
            this.velY = this.visVelY;
        }
        boolean powered = this.rpm >= TAKEOFF_RPM;
        double height = this.heightAboveGround(8.0);
        // V3.9.0: "en el aire" = no apoya Y tiene aire debajo (recién puesto, onGround() aún es falso aunque esté sobre el suelo)
        boolean airborne = !this.onGround() && height > GROUND_TOUCH_HEIGHT;
        boolean gearUp = this.isGearUp();

        // ---- rumbo con A / D: gira menos cuanto más rápido va y nada si está casi parado
        float steer = (this.inRight ? 1.0f : 0.0f) - (this.inLeft ? 1.0f : 0.0f);
        float absSpeed = Math.abs(this.speed);
        float rate = Mth.lerp(Mth.clamp(absSpeed / MAX_SPEED, 0.0f, 1.0f), 1.8f, 0.9f) * Mth.clamp(absSpeed / 0.8f, 0.0f, 1.0f);
        this.setYRot(this.getYRot() + (powered ? steer * rate : 0.0f));

        // ---- aceleración (V4.7.0: física con el peso de lo que lleva)
        float load = this.loadFactor();
        float vr = FlightPhysics.takeoffSpeed(TAKEOFF_SPEED, load);         // V4.7.0: velocidad de despegue según el peso
        float vs = FlightPhysics.stallSpeed(MIN_AIR_SPEED, load);           // y la de pérdida: por debajo, en el aire, se cae
        float airFloor = vs * 0.7f;
        float thrust = powered ? 1.0f : 0.0f;
        if (airborne) {
            if (this.inForward && !this.inBack) {
                this.speed = Math.min(MAX_SPEED, this.speed + 0.006f * thrust / load);       // V4.7.0: empuje de turbinas ~2,4 m/s²
            } else if (this.inBack && !this.inForward) {
                this.speed = Math.max(airFloor, this.speed - 0.005f);
            } else {
                this.speed = Math.max(airFloor, this.speed - 0.0015f);
            }
        } else {
            if (this.inForward && !this.inBack) {
                this.speed = Math.min(MAX_SPEED, this.speed + 0.009f * thrust / load);        // V4.4.0: en pista tarda ~15 s en llegar a la velocidad de rotación
            } else if (this.inBack && !this.inForward) {
                this.speed = this.speed > 0.0f ? Math.max(0.0f, this.speed - 0.012f / (float) Math.sqrt(load)) : Math.max(-REVERSE_SPEED, this.speed - 0.01f);   // V4.7.0: frenos ~4,8 m/s²; con la bodega llena frena peor
            } else {
                this.speed = this.speed > 0.0f ? Math.max(0.0f, this.speed - 0.0012f) : Math.min(0.0f, this.speed + 0.01f);
            }
            if (gearUp && this.speed > 0.1f) {                                    // aterrizó sin tren: se arrastra sobre la panza
                this.speed *= 0.93f;
                if (this.scrapeWarning-- <= 0) {
                    this.scrapeWarning = 40;
                    if (level.isClientSide && this.getControllingPassenger() instanceof Player player) {
                        player.displayClientMessage(Component.translatable("hud.beautifuldecor.airliner_gear_belly"), true);
                    }
                }
            }
        }

        // ---- seguridad: no volar a ciegas hacia un chunk que aún no se cargó
        float heading = this.getYRot() * Mth.DEG_TO_RAD;
        double hx = -Mth.sin(heading);
        double hz = Mth.cos(heading);
        if (this.speed > UNLOADED_LIMIT) {
            double look = 12.0 + this.speed * 8.0;
            BlockPos ahead = new BlockPos(Mth.floor(this.getX() + hx * look), Mth.floor(this.getY()), Mth.floor(this.getZ() + hz * look));
            if (!level.hasChunkAt(ahead)) {
                this.speed = Math.max(UNLOADED_LIMIT, this.speed - 0.3f);
            }
        }

        // ---- Espacio sube (morro arriba), Shift baja (morro abajo), nada = vuelo recto y nivelado
        boolean canLift = powered && (airborne || this.speed >= vr);
        double climbTarget;
        float pitchTarget = 0.0f;
        float limitUp = GROUND_PITCH_UP + (MAX_PITCH_UP - GROUND_PITCH_UP) * (float) Mth.clamp((height - 1.0) / 4.0, 0.0, 1.0);
        float limitDown = MAX_PITCH_DOWN * (float) Mth.clamp((height - 1.5) / 6.0, 0.0, 1.0);
        if (this.inUp && !this.inDown && canLift) {
            // V4.7.0: en la pista el ascenso crece poco a poco al pasar la velocidad de despegue (se va separando del suelo); en el aire depende del margen sobre la pérdida
            float frac = airborne ? FlightPhysics.liftFraction(this.speed, vs * 0.9f) : FlightPhysics.liftFraction(this.speed, vr);
            climbTarget = CLIMB_SPEED * FlightPhysics.climbScale(load) * Math.max(frac, airborne ? 0.0f : 0.05f);
            pitchTarget = -limitUp * (0.4f + 0.6f * frac);
        } else if (this.inDown && !this.inUp && airborne) {
            climbTarget = -DESCEND_SPEED * (float) Math.sqrt(load) * Mth.clamp(height / 6.0, 0.25, 1.0);        // se frena al acercarse al suelo
            pitchTarget = limitDown;
        } else if (!airborne) {
            climbTarget = -0.08;
        } else {
            climbTarget = 0.0;
        }
        if (airborne) {
            float sink = FlightPhysics.stallSink(this.speed, vs);          // V4.7.0: demasiado lento para su peso: se cae
            if (sink > 0.0f) {
                climbTarget = Math.min(climbTarget, -sink);
                pitchTarget = Math.max(pitchTarget, 0.0f);
            }
        }
        if (climbTarget > 0.0 && this.getY() + this.getBbHeight() + 1.0 >= level.getMaxBuildHeight()) {
            climbTarget = 0.0;                                                        // techo del mundo
            pitchTarget = 0.0f;
        }
        this.velY += Mth.clamp(climbTarget - this.velY, -0.04, 0.012 / load);        // cargado, cuesta frenar la caída (no se puede "redondear" al tocar)               // V4.4.0: se despega y se asienta poco a poco
        float pitch = this.getXRot();
        this.setXRot(pitch + Mth.clamp(pitchTarget - pitch, -PITCH_RATE, PITCH_RATE));

        // ---- movimiento (el servidor repite el mismo movimiento sin problema)
        double impact = this.velY;
        this.setDeltaMovement(hx * this.speed, this.velY, hz * this.speed);
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 after = this.getDeltaMovement();                                         // move() ya anuló lo que chocó
        this.velY = after.y;
        if (this.horizontalCollision) {
            this.speed *= 0.5f;
        }
        // V4.7.0: tocar el suelo con velocidad de caída: suave, duro (rebota y se frena) o estrellarse. El daño a los pasajeros lo aplica el servidor (updateVisuals).
        if (airborne && this.onGround() && impact < -0.06) {
            FlightPhysics.Landing land = FlightPhysics.landing((float) -impact, load);
            if (land.kind() != FlightPhysics.Kind.SOFT) {
                boolean crash = land.kind() == FlightPhysics.Kind.CRASH;
                this.speed *= crash ? 0.25f : 0.7f;
                this.velY = crash ? 0.0 : -impact * 0.3;
                if (level.isClientSide && this.getControllingPassenger() instanceof Player pilot) {
                    pilot.displayClientMessage(Component.translatable(crash ? "hud.beautifuldecor.airliner_crash" : "hud.beautifuldecor.airliner_hard_landing"), true);
                    level.playLocalSound(this.getX(), this.getY(), this.getZ(), crash ? SoundEvents.GENERIC_EXPLODE : SoundEvents.ANVIL_LAND,
                            SoundSource.NEUTRAL, crash ? 1.5f : 1.0f, crash ? 0.8f : 0.5f, false);
                }
            }
        }
    }

    // V4.7.0: servidor: daño a los pasajeros según lo fuerte que tocó el suelo
    private boolean serverAir;
    private double serverPrevDy;

    private void serverLandingCheck(double dy) {
        if (this.level().isClientSide) {
            return;
        }
        boolean air = !this.onGround();
        if (this.serverAir && !air && this.serverPrevDy < -0.06 && this.isVehicle()) {
            FlightPhysics.Landing land = FlightPhysics.landing((float) -this.serverPrevDy, this.loadFactor());
            if (land.damage() > 0.0f) {
                for (Entity passenger : this.getPassengers()) {
                    passenger.hurt(this.level().damageSources().flyIntoWall(), land.damage());
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0f, 0.5f);
            }
        }
        this.serverAir = air;
        if (air) {
            this.serverPrevDy = dy;
        }
    }

    // =====================================================================================================
    // Visuales (todos los clientes)
    // =====================================================================================================

    private void updateVisuals() {
        double dx = this.getX() - this.xo;
        double dy = this.getY() - this.yo;
        double dz = this.getZ() - this.zo;
        this.visVelX = dx;
        this.visVelY = dy;
        this.visVelZ = dz;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        this.visualForward = (float) (dx * -Mth.sin(yawRad) + dz * Mth.cos(yawRad));
        this.visualSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        this.visualClimb = (float) dy;
        this.yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        this.pitchDelta = this.getXRot() - this.xRotO;
        this.serverLandingCheck(dy);

        // ---- turbinas: tardan en arrancar y en pararse
        float targetRpm = this.isVehicle() ? 1.0f : 0.0f;
        this.rpm = this.rpm < targetRpm ? Math.min(targetRpm, this.rpm + SPOOL_UP) : Math.max(targetRpm, this.rpm - SPOOL_DOWN);
        this.fanAngleO = this.fanAngle;
        this.fanAngle += this.rpm * 0.9f;
        if (this.fanAngle > Mth.TWO_PI) {
            this.fanAngle -= Mth.TWO_PI;
            this.fanAngleO -= Mth.TWO_PI;
        }

        // ---- ruedas: giran con lo que avanza en tierra (radio de la rueda: 0,875 bloques)
        this.wheelAngleO = this.wheelAngle;
        if (this.onGround() && this.gearPos < 0.5f) {
            this.wheelAngle += this.visualForward / 0.875f;
            if (this.wheelAngle > Mth.TWO_PI * 8.0f || this.wheelAngle < -Mth.TWO_PI * 8.0f) {
                this.wheelAngle = 0.0f;
                this.wheelAngleO = 0.0f;
            }
        }

        // ---- tren: se pliega o se despliega poco a poco
        this.gearPosO = this.gearPos;
        float gearTarget = this.isGearUp() ? 1.0f : 0.0f;
        this.gearPos = this.gearPos < gearTarget ? Math.min(gearTarget, this.gearPos + GEAR_SPEED)
                : Math.max(gearTarget, this.gearPos - GEAR_SPEED);
    }

    /** Régimen de las turbinas de 0 (paradas) a 1 (a tope). */
    public float getRpm() {
        return this.rpm;
    }

    public float getFanAngle(float partialTick) {
        return Mth.lerp(partialTick, this.fanAngleO, this.fanAngle);
    }

    public float getWheelAngle(float partialTick) {
        return Mth.lerp(partialTick, this.wheelAngleO, this.wheelAngle);
    }

    /** Tren de aterrizaje: 0 = abajo, 1 = recogido. */
    public float getGearPosition(float partialTick) {
        return Mth.lerp(partialTick, this.gearPosO, this.gearPos);
    }

    /** Cabeceo en grados (positivo = morro hacia abajo), interpolado entre ticks. */
    public float getPitchVisual(float partialTick) {
        return Mth.lerp(partialTick, this.xRotO, this.getXRot());
    }

    /** Velocidad horizontal en bloques por tick, medida por lo que se movió. */
    public float getVisualSpeed() {
        return this.visualSpeed;
    }

    public float getVisualClimb() {
        return this.visualClimb;
    }

    /** Giro del avión en este tick (grados), que el cliente de quien va dentro suma a su vista. */
    public float getYawDelta() {
        return this.yawDelta;
    }

    /** Cambio de cabeceo en este tick (grados, positivo = morro hacia abajo), que también se suma a la vista de quien va dentro. */
    public float getPitchDelta() {
        return this.pitchDelta;
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
    // V3.9.0: almacenamiento (6 filas)
    // =====================================================================================================

    private final SimpleContainer storage;

    @Override
    public int storageRows() {
        return 6;
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
        VehicleLights.clear(this);
        if (reason.shouldDestroy()) {
            VehicleStorage.drop(this, this.storage);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        VehiclePaint.read(this, tag);
        VehicleStorage.load(this.storage, tag);
        this.entityData.set(GEAR_UP, tag.getBoolean("GearUp"));
        this.entityData.set(LIGHT, tag.getBoolean("Light"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        VehiclePaint.write(this, tag);
        VehicleStorage.save(this.storage, tag);
        tag.putBoolean("GearUp", this.isGearUp());
        tag.putBoolean("Light", this.isLightOn());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
