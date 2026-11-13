package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.radio.RadioStations;
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
import net.minecraft.sounds.SoundEvent;
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
 * Base común de los vehículos que se conducen montado en ellos (V2.1.0): moto, bicicleta y camioneta.
 * <p>
 * Funciona igual que una barca de vanilla: al subirte, TU cliente calcula el movimiento (así el manejo es fluido) y
 * el juego se lo manda al servidor con el paquete de vehículo de siempre. El servidor solo valida, y cada tick que
 * pasa sin conductor aplica gravedad y fricción (el vehículo no se queda flotando).
 * <ul>
 *   <li>W acelera, S frena (y da reversa), A/D giran (cuanto más rápido vas, más abierta es la curva),
 *       Espacio = freno de mano, G = luz delantera, H = claxon (o timbre), Shift = bajarse.</li>
 *   <li>Por seguridad, si el chunk de delante todavía no está cargado, el vehículo limita su velocidad.</li>
 *   <li>Cada tipo define sus números en un {@link Spec} (velocidad máxima, aceleración, giro, etc.).</li>
 * </ul>
 */
public abstract class VehicleEntity extends Entity implements PaintableVehicle, HasVehicleStorage, SkinnableVehicle {
    /** Números de manejo y de aspecto de cada tipo de vehículo. */
    public record Spec(
            float maxSpeed,          // bloques por tick (x20 = bloques por segundo)
            float reverseSpeed,      // velocidad máxima en reversa
            float acceleration,      // aceleración inicial (se reduce al acercarse al tope)
            float maxTurn,           // giro máximo por tick, en grados
            float lateralGrip,       // aceleración lateral máxima: define lo abiertas que son las curvas rápidas
            float coastDrag,         // multiplicador por tick al soltar el gas (1 = no frena solo)
            float wheelRadius,       // radio de la rueda en bloques (para girarla al avanzar)
            float maxLean,           // inclinación máxima en curvas (grados)
            float leanGain,          // cuánto se inclina por cada grado de giro (negativo = hacia afuera)
            float parkedLean,        // inclinación al estar parado (pata de cabra)
            float steerGain,         // giro visual de las ruedas delanteras por grado de giro
            float maxSteerVisual,    // tope del giro visual (radianes)
            double[] stepLifts,      // escalones que sube solo (alturas de prueba)
            float dismountDistance,  // a qué distancia aparece el jugador al bajarse
            float lookLimit,         // cuánto puede girar la cabeza el conductor respecto al vehículo (grados)
            float fovBoost,          // cuánto se abre el campo de visión a velocidad máxima (grados)
            float pitchLow,          // tono del motor parado
            float pitchHigh) {       // tono del motor a tope
    }

    /** V4.7.0: cantidad de objetos en el almacenamiento (sincronizada). */
    private static final EntityDataAccessor<Integer> CARGO =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    /** V6.7.0: skin del vehículo (0 = la de siempre) y sirena encendida (solo en la skin de policía). */
    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SIREN =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LIGHT =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    // V3.2.0: colores que el jugador le puso con la tecla R (-1 = el de siempre).
    private static final EntityDataAccessor<Integer> PAINT_BODY =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_HANDLE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_RIM =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    // V3.6.0: los colores de "Otros" (vidrios, asientos, luces...) viajan juntos en un texto (ver PaintExtras).
    private static final EntityDataAccessor<String> PAINT_EXTRA =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    // V3.5.0: la radio (tecla X yendo montado): emisora, su nombre, encendida y volumen. Todos los que están cerca la oyen igual.
    private static final EntityDataAccessor<String> RADIO_URL =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> RADIO_NAME =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> RADIO_ON =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RADIO_VOLUME =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

    /** Velocidad a la que se limita el vehículo si el terreno de delante aún no se ha cargado. */
    private static final float UNLOADED_LIMIT = 0.9f;

    /** V6.7.0: botón de la sirena del interior (solo skin de policía). */
    public static final int ACTION_SIREN = 3;
    public static final int ACTION_LIGHT = 0;
    public static final int ACTION_HORN = 1;
    /** V6.3.0: solo para los botones del interior (el radio se abre en el cliente). */
    public static final int ACTION_RADIO = 2;

    // ---- interpolación para quienes miran el vehículo (igual que la barca)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // ---- cliente del conductor
    private float speed;
    private boolean inForward;
    private boolean inBack;
    private boolean inLeft;
    private boolean inRight;
    private boolean inBrake;

    // ---- visuales (todos los clientes)
    private float wheelRot;
    private float wheelRotO;
    private float lean;
    private float leanO;
    private float steer;
    private float steerO;
    private float visualSpeed;
    private float yawDelta;

    // ---- vista previa del menú de colores (solo cliente)
    @Nullable
    private int[] paintPreview;
    private final PaintExtras paintExtras = new PaintExtras();

    // ---- servidor
    private Vec3 lastRiddenVelocity = Vec3.ZERO;
    private boolean wasRidden;
    private long lastHonkTick = -100L;

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        int rows = this.storageRows();                                     // V3.9.0: cada tipo dice cuántas filas de almacenamiento tiene (0 = ninguna)
        this.storage = rows > 0 ? new VehicleStorage.Box(this, rows) : null;
    }

    // =====================================================================================================
    // V3.9.0: almacenamiento
    // =====================================================================================================

    @Nullable
    private final SimpleContainer storage;

    /** Filas de 9 casillas. Los vehículos de dos ruedas no tienen (0); los demás lo redefinen. */
    @Override
    public int storageRows() {
        return 0;
    }

    @Override
    @Nullable
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

    // =====================================================================================================
    // Lo que define cada tipo de vehículo
    // =====================================================================================================

    protected abstract Spec spec();

    /** Sonido de arranque al sentarse el conductor (null = ninguno). */
    @Nullable
    protected abstract SoundEvent getStartSound();

    protected abstract SoundEvent getHornSound();

    // ---- V6.7.0: skins y sirena
    /** Cuántas skins tiene (la 0 es la original). */
    public int skinCount() {
        return 1;
    }

    /** Número de la skin de policía, o -1 si no tiene. */
    public int policeSkin() {
        return -1;
    }

    /** Clave de idioma del nombre de cada skin. */
    public String skinKey(int skin) {
        return "skin.beautifuldecor.generic." + skin;
    }

    public int getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setSkin(int skin) {
        this.entityData.set(SKIN, Math.max(0, Math.min(this.skinCount() - 1, skin)));
        if (!this.hasSiren()) {
            this.entityData.set(SIREN, Boolean.FALSE);
        }
    }

    public boolean hasSiren() {
        return this.skinHasSiren(this.getSkin());
    }

    /** V6.8.0: true si esa skin lleva sirena (por defecto, solo la de policía; la camioneta también la da a los bomberos). */
    public boolean skinHasSiren(int skin) {
        return this.policeSkin() >= 0 && skin == this.policeSkin();
    }

    public boolean isSirenOn() {
        return this.hasSiren() && this.entityData.get(SIREN);
    }

    public void toggleSiren(ServerPlayer player) {
        if (!this.hasSiren()) {
            return;
        }
        boolean on = !this.isSirenOn();
        this.entityData.set(SIREN, on);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.6f, on ? 1.3f : 0.8f);
        player.displayClientMessage(Component.translatable(on ? "hud.beautifuldecor.siren_on" : "hud.beautifuldecor.siren_off"), true);
    }

    /** V6.3.0: true si este vehículo tiene botones en su interior (claxon, luces, radio); entonces las teclas quedan apagadas salvo que se activen en la configuración. */
    public boolean hasCabButtons() {
        return false;
    }

    /** V6.3.0: botones del interior: {x izquierda, y arriba, z adelante, radio, acción} en metros desde el centro del vehículo; acción = ACTION_*. */
    protected double[][] cabButtons() {
        return NO_BUTTONS;
    }

    private static final double[][] NO_BUTTONS = new double[0][];

    protected float hornVolume() {
        return 1.6f;
    }

    /** Bucle del motor (solo cliente); null si no tiene motor (bicicleta). */
    @Nullable
    public SoundEvent getEngineLoop() {
        return null;
    }

    /** Cuántas personas caben (el primero que se sube conduce). */
    protected int seatCount() {
        return 1;
    }

    public Spec getSpec() {
        return this.spec();
    }

    public float getMaxSpeed() {
        return this.spec().maxSpeed();
    }

    /** Fracciones de la velocidad máxima en las que "cambia de marcha" el sonido del motor (6 tramos). */
    public float[] getGearFractions() {
        return new float[]{0.0f, 0.105f, 0.21f, 0.358f, 0.526f, 0.737f, 1.0f};
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < this.seatCount();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIGHT, Boolean.FALSE);
        this.entityData.define(SKIN, 0);
        this.entityData.define(SIREN, Boolean.FALSE);
        this.entityData.define(CARGO, 0);
        this.entityData.define(PAINT_BODY, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_HANDLE, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_RIM, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_EXTRA, PaintExtras.EMPTY);
        this.entityData.define(RADIO_URL, "");
        this.entityData.define(RADIO_NAME, "");
        this.entityData.define(RADIO_ON, Boolean.FALSE);
        this.entityData.define(RADIO_VOLUME, RadioStations.DEFAULT_VOLUME);
    }

    // =====================================================================================================
    // Radio (V3.5.0)
    // =====================================================================================================

    /** Dirección de la emisora elegida ("" = ninguna). */
    public String getRadioUrl() {
        return this.entityData.get(RADIO_URL);
    }

    /** Nombre de la emisora elegida, para el menú. */
    public String getRadioName() {
        return this.entityData.get(RADIO_NAME);
    }

    /** Encendida (suena mientras alguien va en el vehículo). */
    public boolean isRadioOn() {
        return this.entityData.get(RADIO_ON);
    }

    /** Volumen de 0 a 100. */
    public int getRadioVolume() {
        return this.entityData.get(RADIO_VOLUME);
    }

    /** ¿Debe sonar ahora? Encendida, con emisora y con alguien a bordo (un vehículo estacionado y vacío no suena). */
    public boolean radioShouldPlay() {
        return !this.isRemoved() && this.isRadioOn() && !this.getRadioUrl().isEmpty() && this.isVehicle();
    }

    /** Servidor: elige una emisora (ya revisada) y enciende la radio. */
    public void setRadioStation(String url, String name) {
        this.entityData.set(RADIO_URL, url);
        this.entityData.set(RADIO_NAME, name);
        this.entityData.set(RADIO_ON, Boolean.TRUE);
    }

    /** Servidor: enciende o apaga (sin emisora no se enciende). */
    public void setRadioOn(boolean on) {
        this.entityData.set(RADIO_ON, on && !this.getRadioUrl().isEmpty());
    }

    /** Servidor: volumen de 0 a 100. */
    public void setRadioVolume(int volume) {
        this.entityData.set(RADIO_VOLUME, RadioStations.clampVolume(volume));
    }

    // =====================================================================================================
    // Colores (V3.2.0)
    // =====================================================================================================

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

    public boolean isLightOn() {
        return this.entityData.get(LIGHT);
    }

    // =====================================================================================================
    // Subir, bajar, recoger
    // =====================================================================================================

    /** V6.3.0: quien va montado puede apretar los botones del tablero. */
    @Override
    public boolean canRiderInteract() {
        return this.cabButtons().length > 0;
    }

    /** V6.3.0: acción del botón del interior al que apunta el jugador (distancia de rayo a punto menor que el radio del botón), o -1. */
    private int buttonUnderCrosshair(Player player) {
        double yaw = this.getYRot() * Mth.DEG_TO_RAD;
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
        int best = -1;
        double bestT = 1.0e9;
        for (double[] b : this.cabButtons()) {
            double px = this.getX() - Mth.sin((float) yaw) * b[2] + Mth.cos((float) yaw) * b[0];
            double py = this.getY() + b[1];
            double pz = this.getZ() + Mth.cos((float) yaw) * b[2] + Mth.sin((float) yaw) * b[0];
            double t = (px - eye.x) * look.x + (py - eye.y) * look.y + (pz - eye.z) * look.z;
            if (t < 0.05 || t > 3.0) {
                continue;
            }
            double cx = eye.x + look.x * t - px;
            double cy = eye.y + look.y * t - py;
            double cz = eye.z + look.z * t - pz;
            if ((int) b[4] == ACTION_SIREN && !this.hasSiren()) {
                continue;
            }
            if (cx * cx + cy * cy + cz * cz < b[3] * b[3] && t < bestT) {
                bestT = t;
                best = (int) b[4];
            }
        }
        return best;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getVehicle() == this && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive()) {
            int action = this.buttonUnderCrosshair(player);
            if (action < 0) {
                return InteractionResult.PASS;
            }
            if (this.level().isClientSide) {
                if (action == ACTION_RADIO) {
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> com.example.beautifuldecor.client.VehicleRadioScreen.tryOpen(net.minecraft.client.Minecraft.getInstance()));
                }
            } else if (player instanceof ServerPlayer sp && this.getControllingPassenger() == player) {
                if (action == ACTION_LIGHT) {
                    this.toggleLight(sp);
                } else if (action == ACTION_HORN) {
                    this.honk();
                } else if (action == ACTION_SIREN) {
                    this.toggleSiren(sp);
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
            SoundEvent start = this.getStartSound();
            if (start != null && this.getControllingPassenger() == player) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), start,
                        SoundSource.NEUTRAL, 1.0f, 1.0f);
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
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = this.getFirstPassenger();
        return first instanceof LivingEntity living ? living : null;
    }

    /** Al bajarte con Shift apareces al lado del vehículo (izquierda o derecha, donde haya sitio). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double leftX = Mth.cos(yawRad);
        double leftZ = Mth.sin(yawRad);
        double distance = this.spec().dismountDistance();
        for (double side : new double[]{distance, -distance}) {
            for (double up : new double[]{0.0, 1.0}) {
                Vec3 target = new Vec3(this.getX() + leftX * side, this.getY() + up, this.getZ() + leftZ * side);
                AABB box = passenger.getBoundingBox().move(target.subtract(passenger.position()));
                if (this.level().noCollision(passenger, box)) {
                    return target;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // =====================================================================================================
    // Luz y claxon (servidor)
    // =====================================================================================================

    public void toggleLight(ServerPlayer player) {
        boolean on = !this.isLightOn();
        this.entityData.set(LIGHT, on);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK,
                SoundSource.NEUTRAL, 0.6f, on ? 1.2f : 0.9f);
        player.displayClientMessage(Component.translatable(
                on ? "hud.beautifuldecor.motorcycle_light_on" : "hud.beautifuldecor.motorcycle_light_off"), true);
    }

    public void honk() {
        long now = this.level().getGameTime();
        if (now - this.lastHonkTick < 8L) {
            return;
        }
        this.lastHonkTick = now;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), this.getHornSound(),
                SoundSource.NEUTRAL, this.hornVolume(), 1.0f);
    }

    // =====================================================================================================
    // Tick
    // =====================================================================================================

    /** V6.13.0: medidas del casco para la caja de colisión (null = la caja normal del tipo de entidad). Cada vehículo pone las de su modelo. */
    protected VehicleHull.Spec hull() {
        return null;
    }

    /** Pone la caja de colisión según la posición y el giro de ahora. */
    protected final void refreshHull() {
        VehicleHull.Spec h = this.hull();
        if (h != null) {
            VehicleHull.refresh(this, h);
        }
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        VehicleHull.Spec h = this.hull();
        if (h == null) {
            super.move(type, delta);
            return;
        }
        VehicleHull.refresh(this, h);
        super.move(type, delta);
        VehicleHull.fixPosition(this, h);
    }

    @Override
    public void tick() {
        super.tick();
        this.syncCargo();
        this.tickLerp();
        VehicleLights.update(this, this.isLightOn(), false);      // V6.4.0: luz real delante de los faros

        if (this.isControlledByLocalInstance()) {
            this.driveTick();
        } else if (!this.level().isClientSide && !this.isVehicle()) {
            this.freeTick();
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.lastRiddenVelocity = new Vec3(this.getX() - this.xo, 0.0, this.getZ() - this.zo);
                this.wasRidden = true;
            }
        }
        this.updateVisuals();
        this.refreshHull();
        if (!this.level().isClientSide) {
            VehicleImpact.tick(this, this.impactProfile(), this.getControllingPassenger());      // V7.1.0: atropellos
        }
    }

    /** V7.1.0: cómo golpea este vehículo (null = no golpea). */
    protected VehicleImpact.Profile impactProfile() {
        if (this instanceof BicycleEntity) {
            return VehicleImpact.BICYCLE;
        }
        if (this instanceof MotorcycleEntity) {
            return VehicleImpact.MOTORCYCLE;
        }
        if (this instanceof DeliveryTruckEntity truck) {
            return truck.prizeTier > 0 ? null : VehicleImpact.TRUCK;
        }
        return VehicleImpact.CAR;
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

    /** Servidor, sin conductor: gravedad y fricción (y sigue deslizando un poco si el conductor se bajó a toda velocidad). */
    private void freeTick() {
        Vec3 motion = this.getDeltaMovement();
        if (this.wasRidden) {
            this.wasRidden = false;
            motion = this.lastRiddenVelocity.scale(0.9);
        }
        boolean grounded = this.onGround();
        double friction = grounded ? 0.82 : 0.99;
        double vx = motion.x * friction;
        double vz = motion.z * friction;
        if (Math.abs(vx) < 0.004) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 0.004) {
            vz = 0.0;
        }
        double vy = grounded ? -0.04 : (motion.y - 0.08) * 0.98;
        this.setDeltaMovement(vx, vy, vz);
        if (vx != 0.0 || vz != 0.0 || !grounded) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    // =====================================================================================================
    // Manejo (cliente del conductor)
    // =====================================================================================================

    /** Cliente: teclas del conductor en este tick. */
    public void setRiderInput(boolean forward, boolean back, boolean left, boolean right, boolean brake) {
        this.inForward = forward;
        this.inBack = back;
        this.inLeft = left;
        this.inRight = right;
        this.inBrake = brake;
    }

    /** Pendiente suavizada (tangente, positivo = cuesta arriba) que recorre el vehículo. */
    private double slopeSmooth;

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

    /** Números de física de este vehículo (masa, potencia, frenos...). Cada tipo define los suyos. */
    protected VehiclePhysics.Params physics() {
        return new VehiclePhysics.Params(1500, 120_000, 3.0, 7.5, 3.5, 0.8, 0.015, 0.9, this.spec().maxSpeed() * 20.0, this.spec().reverseSpeed() * 20.0);
    }

    /** Masa de ahora: la del vehículo vacío + 80 kg por persona + 1 kg por objeto del almacenamiento. */
    public double totalMassKg() {
        return this.physics().massKg() + 80.0 * this.getPassengers().size() + this.entityData.get(CARGO);
    }

    private void driveTick() {
        Spec spec = this.spec();
        float maxSpeed = spec.maxSpeed();
        float reverseSpeed = spec.reverseSpeed();
        Level level = this.level();
        boolean grounded = this.onGround();
        float v = this.speed;

        // ---- V4.6.0: física real (ver VehiclePhysics): el motor empuja con F = P / v, el aire y las ruedas frenan, los frenos tienen una fuerza máxima
        // que rinde menos con más peso, y la pendiente suma o resta g·sen(θ). Más peso o más velocidad = cuesta más acelerar y frenar.
        if (grounded) {
            int drive = 0;
            boolean braking = false;
            if (this.inForward) {
                if (v < -0.02f) {
                    braking = true;                                  // adelante mientras va en reversa = frenar
                } else {
                    drive += 1;
                }
            }
            if (this.inBack) {
                if (v > 0.03f) {
                    braking = true;                                  // atrás mientras avanza = frenar
                } else {
                    drive -= 1;
                }
            }
            double tan = this.slopeSmooth;
            double slope = tan / Math.sqrt(1.0 + tan * tan);
            v = (float) VehiclePhysics.step(this.physics(), v, this.totalMassKg(), slope, drive, braking, this.inBrake);
        } else {
            v *= 0.999f;
        }
        v = Mth.clamp(v, -reverseSpeed, maxSpeed * 1.6f);             // cuesta abajo puede pasar de su tope (hasta 1,6 veces)

        // ---- seguridad: no correr a ciegas hacia un chunk que aún no se cargó
        float yawRad0 = this.getYRot() * Mth.DEG_TO_RAD;
        double hx = -Mth.sin(yawRad0);
        double hz = Mth.cos(yawRad0);
        if (v > UNLOADED_LIMIT) {
            double look = 10.0 + v * 4.0;
            BlockPos ahead = new BlockPos(Mth.floor(this.getX() + hx * look), Mth.floor(this.getY()),
                    Mth.floor(this.getZ() + hz * look));
            if (!level.hasChunkAt(ahead)) {
                v = Math.max(UNLOADED_LIMIT, v - 0.6f);
            }
        }

        // ---- dirección: cuanto más rápido, más abierta la curva
        float steerInput = (this.inRight ? 1.0f : 0.0f) - (this.inLeft ? 1.0f : 0.0f);
        float turn = 0.0f;
        float absV = Math.abs(v);
        if (steerInput != 0.0f && absV > 0.02f) {
            float byGrip = (float) Math.toDegrees(spec.lateralGrip() / Math.max(absV, 0.05f));
            turn = Math.min(spec.maxTurn(), byGrip) * Math.min(1.0f, absV / 0.35f);
            if (this.inBrake) {
                turn *= 1.4f;
            }
            turn *= steerInput * (v < 0.0f ? -1.0f : 1.0f);
        }
        this.setYRot(this.getYRot() + turn);
        this.yawDelta = turn;

        // ---- movimiento
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        hx = -Mth.sin(yawRad);
        hz = Mth.cos(yawRad);
        double dx = hx * v;
        double dz = hz * v;
        double vy = grounded ? -0.04 : (this.getDeltaMovement().y - 0.08) * 0.98;

        // sube solo los escalones que le caben (el servidor repite el mismo movimiento sin problema)
        double lift = 0.0;
        if (grounded && absV > 0.01f) {
            AABB swept = this.getBoundingBox().expandTowards(dx, 0.0, dz);
            if (!level.noCollision(this, swept)) {
                for (double h : spec.stepLifts()) {
                    if (level.noCollision(this, swept.move(0.0, h, 0.0))) {
                        lift = h;
                        break;
                    }
                }
            }
        }

        double beforeX = this.getX();
        double beforeZ = this.getZ();
        double beforeY = this.getY();
        this.setDeltaMovement(dx, vy, dz);
        this.move(MoverType.SELF, new Vec3(dx, vy + lift, dz));
        // pendiente que va recorriendo (suavizada: los escalones y saltos no la disparan)
        double flat = Math.sqrt((this.getX() - beforeX) * (this.getX() - beforeX) + (this.getZ() - beforeZ) * (this.getZ() - beforeZ));
        if (grounded && flat > 0.03) {
            double raw = Mth.clamp((this.getY() - beforeY) / flat, -0.6, 0.6);
            this.slopeSmooth += (raw - this.slopeSmooth) * 0.12;
        } else {
            this.slopeSmooth *= 0.9;
        }

        if (this.horizontalCollision) {
            double moved = Math.sqrt((this.getX() - beforeX) * (this.getX() - beforeX)
                    + (this.getZ() - beforeZ) * (this.getZ() - beforeZ));
            if (absV > 1.5f && moved < absV * 0.4) {
                level.playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND,
                        SoundSource.NEUTRAL, 0.7f, 0.6f, false);
            }
            v = (v < 0.0f ? -1.0f : 1.0f) * (float) Math.min(absV * 0.5, moved);
        }
        this.speed = v;
    }

    // =====================================================================================================
    // Visuales (todos los clientes)
    // =====================================================================================================

    private void updateVisuals() {
        Spec spec = this.spec();
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        float signed = (float) (dx * -Mth.sin(yawRad) + dz * Mth.cos(yawRad));
        this.visualSpeed = signed;
        if (!this.isControlledByLocalInstance()) {
            this.yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        }

        this.wheelRotO = this.wheelRot;
        this.wheelRot += Mth.clamp(signed / spec.wheelRadius(), -2.2f, 2.2f);

        this.leanO = this.lean;
        float target;
        if (this.isVehicle()) {
            // positivo = se inclina hacia su izquierda; girar a la derecha (yaw sube) inclina a la derecha
            target = Mth.clamp(-this.yawDelta * Math.min(1.0f, Math.abs(signed) * 1.2f) * spec.leanGain(),
                    -spec.maxLean(), spec.maxLean());
        } else {
            target = Math.abs(signed) < 0.02f ? spec.parkedLean() : 0.0f;       // apoyado en la pata de cabra
        }
        this.lean += (target - this.lean) * 0.25f;

        this.steerO = this.steer;
        float steerTarget = this.isVehicle()
                ? Mth.clamp(this.yawDelta * spec.steerGain(), -spec.maxSteerVisual(), spec.maxSteerVisual()) : 0.0f;
        this.steer += (steerTarget - this.steer) * 0.4f;
    }

    /** Velocidad en bloques por tick (con signo: negativo = reversa), medida por lo que se movió. */
    public float getVisualSpeed() {
        return this.visualSpeed;
    }

    /** Giro del vehículo en este tick (grados), que el cliente del conductor suma a su vista. */
    public float getYawDelta() {
        return this.yawDelta;
    }

    public float getWheelRot(float partialTick) {
        return Mth.lerp(partialTick, this.wheelRotO, this.wheelRot);
    }

    public float getLean(float partialTick) {
        return Mth.lerp(partialTick, this.leanO, this.lean);
    }

    public float getSteer(float partialTick) {
        return Mth.lerp(partialTick, this.steerO, this.steer);
    }

    public boolean isParked() {
        return !this.isVehicle() && Math.abs(this.visualSpeed) < 0.02f;
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

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(LIGHT, tag.getBoolean("Light"));
        this.entityData.set(SKIN, Math.max(0, Math.min(this.skinCount() - 1, tag.getInt("Skin"))));
        this.entityData.set(SIREN, tag.getBoolean("Siren"));
        VehiclePaint.read(this, tag);
        // V3.5.0: la radio (los vehículos guardados antes no la traen: apagada, sin emisora, volumen de fábrica)
        String url = RadioStations.cleanUrl(tag.getString("RadioUrl"));
        this.entityData.set(RADIO_URL, url == null ? "" : url);
        this.entityData.set(RADIO_NAME, url == null ? "" : RadioStations.cleanName(tag.getString("RadioName")));
        this.entityData.set(RADIO_ON, url != null && tag.getBoolean("RadioOn"));
        this.entityData.set(RADIO_VOLUME, tag.contains("RadioVolume") ? RadioStations.clampVolume(tag.getInt("RadioVolume")) : RadioStations.DEFAULT_VOLUME);
        VehicleStorage.load(this.storage, tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Light", this.isLightOn());
        tag.putInt("Skin", this.getSkin());
        tag.putBoolean("Siren", this.isSirenOn());
        VehiclePaint.write(this, tag);
        tag.putString("RadioUrl", this.getRadioUrl());
        tag.putString("RadioName", this.getRadioName());
        tag.putBoolean("RadioOn", this.isRadioOn());
        tag.putInt("RadioVolume", this.getRadioVolume());
        VehicleStorage.save(this.storage, tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
