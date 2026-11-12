package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.FreightRailBlock;
import com.example.beautifuldecor.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.example.beautifuldecor.item.TrainRemoteItem;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * V4.8.0: coche del tren de carga: la locomotora y los vagones de carbón, alimentos y minerales (una sola clase; la variante decide
 * el diseño, el peso y la carga).
 * <p>
 * Movimiento (todo en el servidor): cada coche recuerda el punto de su bogie delantero sobre la vía ({@link RailPath.Cursor}). El coche
 * de más adelante de una fila ("cabeza") manda: junta el peso de todos los coches y su carga, la pendiente de los rieles y lo que pide
 * quien conduce, calcula la velocidad con {@link TrainPhysics} (más vagones y más carga = más masa = más cuesta acelerar y frenar) y
 * mueve a todos la misma distancia por la vía. Los vagones no tienen límite, solo el que cada mundo aguante.
 * El cliente solo recibe posición y giro (cada coche a la altura de su vía, inclinado con la subida) y los dibuja.
 */
public class TrainCarEntity extends Entity implements PaintableVehicle {
    public enum Cargo { NONE, COAL, FOOD, MINERAL }

    /** Cómo se mueve la locomotora: NONE = no es locomotora (vagón). */
    public enum Drive { NONE, DIESEL, STEAM, ELECTRIC, EMU }

    public enum Variant {
        FREIGHT_LOCO("freight_loco", VehiclePaint.Kind.FREIGHT_LOCO, TrainPhysics.LOCO_MASS_KG, Cargo.NONE, 0, 0.0, 20.0, 11.0, 0, 130.0, TrainPhysics.LOCO_POWER_W, Drive.DIESEL, () -> ModItems.FREIGHT_LOCO.get()),
        COAL_WAGON("coal_wagon", VehiclePaint.Kind.COAL_WAGON, 25_000.0, Cargo.COAL, 6400, 16.0, 14.0, 9.0, 0, 130.0, 0.0, Drive.NONE, () -> ModItems.COAL_WAGON.get()),
        FOOD_WAGON("food_wagon", VehiclePaint.Kind.FOOD_WAGON, 22_000.0, Cargo.FOOD, 5600, 12.0, 14.0, 9.0, 0, 130.0, 0.0, Drive.NONE, () -> ModItems.FOOD_WAGON.get()),
        MINERAL_WAGON("mineral_wagon", VehiclePaint.Kind.MINERAL_WAGON, 27_000.0, Cargo.MINERAL, 4000, 25.0, 14.0, 9.0, 0, 130.0, 0.0, Drive.NONE, () -> ModItems.MINERAL_WAGON.get()),
        // V5.0.0: tren de pasajeros (80 km/h, vapor de carbón). El ténder es parte de la locomotora y lleva el carbón (combustible).
        STEAM_CLASSIC("steam_classic", VehiclePaint.Kind.STEAM_CLASSIC, 105_000.0, Cargo.COAL, 1200, 16.0, 22.0, 14.0, 1, 80.0, 2_200_000.0, Drive.STEAM, () -> ModItems.STEAM_CLASSIC.get()),
        STEAM_MODERN("steam_modern", VehiclePaint.Kind.STEAM_MODERN, 112_000.0, Cargo.COAL, 1200, 16.0, 22.0, 14.0, 1, 80.0, 2_400_000.0, Drive.STEAM, () -> ModItems.STEAM_MODERN.get()),
        COACH_LOW("coach_low", VehiclePaint.Kind.COACH_LOW, 36_000.0, Cargo.NONE, 0, 0.0, 20.0, 14.0, 1, 80.0, 0.0, Drive.NONE, () -> ModItems.COACH_LOW.get()),
        COACH_MID("coach_mid", VehiclePaint.Kind.COACH_MID, 40_000.0, Cargo.NONE, 0, 0.0, 20.0, 14.0, 1, 80.0, 0.0, Drive.NONE, () -> ModItems.COACH_MID.get()),
        COACH_HIGH("coach_high", VehiclePaint.Kind.COACH_HIGH, 44_000.0, Cargo.NONE, 0, 0.0, 20.0, 14.0, 1, 80.0, 0.0, Drive.NONE, () -> ModItems.COACH_HIGH.get()),
        // V5.1.0: metro eléctrico (284 km/h). La cabeza lleva cabina; todos los coches tienen motor propio (EMU), sin combustible.
        METRO_HEAD("metro_head", VehiclePaint.Kind.METRO_HEAD, 62_000.0, Cargo.NONE, 0, 0.0, 20.0, 14.0, 2, 284.0, 2_400_000.0, Drive.ELECTRIC, () -> ModItems.METRO_HEAD.get()),
        METRO_CAR("metro_car", VehiclePaint.Kind.METRO_CAR, 42_000.0, Cargo.NONE, 0, 0.0, 20.0, 14.0, 2, 284.0, 1_200_000.0, Drive.EMU, () -> ModItems.METRO_CAR.get());

        public final String id;
        public final VehiclePaint.Kind kind;
        public final double emptyKg;
        public final Cargo cargo;
        /** Capacidad en unidades de carga (un objeto = 1; un bloque compacto = 9). En la locomotora de vapor es el carbón del ténder. */
        public final int capacity;
        public final double kgPerUnit;
        /** Largo del coche (m) y distancia (m) entre sus dos bogies. */
        public final double length;
        public final double wheelbase;
        /** Familia de vía por la que anda: 0 = carga, 1 = pasajeros. */
        public final int family;
        public final double topKmh;
        public final double powerW;
        public final Drive drive;
        private final Supplier<Item> item;

        Variant(String id, VehiclePaint.Kind kind, double emptyKg, Cargo cargo, int capacity, double kgPerUnit, double length, double wheelbase,
                int family, double topKmh, double powerW, Drive drive, Supplier<Item> item) {
            this.id = id;
            this.kind = kind;
            this.emptyKg = emptyKg;
            this.cargo = cargo;
            this.capacity = capacity;
            this.kgPerUnit = kgPerUnit;
            this.length = length;
            this.wheelbase = wheelbase;
            this.family = family;
            this.topKmh = topKmh;
            this.powerW = powerW;
            this.drive = drive;
            this.item = item;
        }

        public Item item() {
            return this.item.get();
        }

        /** Lo que sobresale el cuerpo por delante y por detrás de los bogies (m). */
        public double inset() {
            return (this.length - this.wheelbase) / 2.0;
        }

        public boolean isLoco() {
            return this.drive == Drive.DIESEL || this.drive == Drive.STEAM || this.drive == Drive.ELECTRIC;
        }

        public boolean isSteam() {
            return this.drive == Drive.STEAM;
        }

        /** Vagón de pasajeros (tiene asientos). */
        public boolean isCoach() {
            return !this.isLoco() && this.cargo == Cargo.NONE;
        }

        /** Los dibuja con vidrios translúcidos. */
        public boolean hasGlass() {
            return this.isLoco() || this.isCoach();
        }

        public int seats() {
            return TrainSeats.of(this.id).length;
        }

        public static Variant byOrdinal(int i) {
            Variant[] all = values();
            return all[Mth.clamp(i, 0, all.length - 1)];
        }
    }

    /** Hueco entre los cuerpos de dos coches enganchados (m). */
    public static final double COUPLING_GAP = 0.5;

    /** Distancia (m) entre el bogie trasero del coche de adelante y el delantero del que va detrás. */
    public static double stride(Variant ahead, Variant behind) {
        return ahead.inset() + COUPLING_GAP + behind.inset();
    }
    private static final int MAX_CARS = 4096;
    /** Cada pasajero suma esto al peso (kg). */
    public static final double PASSENGER_KG = 80.0;
    private static final int INPUT_TIMEOUT = 10;

    private static final EntityDataAccessor<Integer> PAINT_BODY = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_HANDLE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT_RIM = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PAINT_EXTRA = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FILL = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);

    // ---- vía y cadena (servidor)
    @Nullable
    private RailPath.Cursor cur;
    private boolean placed;
    @Nullable
    private UUID frontId;
    @Nullable
    private UUID rearId;
    private int missingFront;
    private double speed;                       // bloques por tick; positivo = hacia donde mira
    private double fuelAcc;                     // carbón quemado que aún no llega a una unidad entera
    private float slope;                        // pendiente del tramo donde está (seno, en el sentido de marcha)
    private int inputFlags;
    private long inputTime = Long.MIN_VALUE;
    /** V6.0.0: la ruta del piloto automático (se guarda en la locomotora). */
    private final TrainRoute route = new TrainRoute();

    // ---- carga
    private final ArrayList<ItemStack> hold = new ArrayList<>();
    private int units;

    // ---- cliente
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private float wheelAngle;
    private float wheelAngleO;
    private float visualSpeed;
    @Nullable
    private int[] paintPreview;
    private final PaintExtras paintExtras = new PaintExtras();

    public TrainCarEntity(EntityType<? extends TrainCarEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // =====================================================================================================
    // Datos
    // =====================================================================================================

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PAINT_BODY, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_HANDLE, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_RIM, VehiclePaint.ORIGINAL);
        this.entityData.define(PAINT_EXTRA, PaintExtras.EMPTY);
        this.entityData.define(VARIANT, 0);
        this.entityData.define(FILL, 0);
    }

    public Variant variant() {
        return Variant.byOrdinal(this.entityData.get(VARIANT));
    }

    public void setVariant(Variant variant) {
        this.entityData.set(VARIANT, variant.ordinal());
    }

    @Override
    public VehiclePaint.Kind paintKind() {
        return this.variant().kind;
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
                    this.entityData.set(PAINT_EXTRA, PaintExtras.with(this.entityData.get(PAINT_EXTRA), channel - VehiclePaint.EXTRA_FIRST, value));
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

    /** Masa de este coche ahora (kg): vacío + lo que lleva. */
    public double totalMassKg() {
        return this.variant().emptyKg + this.fillUnits() * this.variant().kgPerUnit + this.getPassengers().size() * PASSENGER_KG;
    }

    public int fillUnits() {
        return this.entityData.get(FILL);
    }

    /** Qué tan lleno está (0..1). */
    public float fillFraction() {
        int cap = this.variant().capacity;
        return cap <= 0 ? 0.0f : Mth.clamp((float) this.fillUnits() / (float) cap, 0.0f, 1.0f);
    }

    // =====================================================================================================
    // Carga
    // =====================================================================================================

    public static boolean isCompactBlock(ItemStack stack) {
        return stack.is(Items.COAL_BLOCK) || stack.is(Tags.Items.STORAGE_BLOCKS);
    }

    public static int unitsPer(ItemStack stack) {
        return isCompactBlock(stack) ? 9 : 1;
    }

    public static boolean isMineral(ItemStack stack) {
        return stack.is(Tags.Items.ORES) || stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.RAW_MATERIALS)
                || stack.is(Tags.Items.STORAGE_BLOCKS) || stack.is(Tags.Items.NUGGETS) || stack.is(Tags.Items.DUSTS)
                || stack.is(Items.NETHERITE_SCRAP) || stack.is(Items.QUARTZ) || stack.is(Items.AMETHYST_SHARD) || stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.REDSTONE) || stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.DIAMOND) || stack.is(Items.EMERALD)
                || stack.is(Items.COAL_ORE) || stack.is(Items.DEEPSLATE_COAL_ORE);
    }

    public static boolean accepts(Cargo cargo, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (cargo) {
            case COAL -> stack.is(ItemTags.COALS) || stack.is(Items.COAL_BLOCK);
            case FOOD -> stack.getItem().isEdible();
            case MINERAL -> isMineral(stack);
            default -> false;
        };
    }

    public boolean acceptsItem(ItemStack stack) {
        return accepts(this.variant().cargo, stack);
    }

    public boolean hasCargo() {
        return this.units > 0 || this.fillUnits() > 0;
    }

    /** Carga lo que se pueda de ese montón (se le quita a {@code stack}); devuelve cuántos objetos entraron. */
    public int addCargo(ItemStack stack) {
        if (!this.acceptsItem(stack)) {
            return 0;
        }
        int per = unitsPer(stack);
        int room = (this.variant().capacity - this.units) / per;
        int n = Math.min(stack.getCount(), room);
        if (n <= 0) {
            return 0;
        }
        int left = n;
        for (ItemStack h : this.hold) {
            if (left <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameTags(h, stack)) {
                int space = Math.min(h.getMaxStackSize(), 64) - h.getCount();
                int put = Math.min(space, left);
                if (put > 0) {
                    h.grow(put);
                    left -= put;
                }
            }
        }
        while (left > 0) {
            ItemStack part = stack.copy();
            int put = Math.min(left, Math.min(part.getMaxStackSize(), 64));
            part.setCount(put);
            this.hold.add(part);
            left -= put;
        }
        stack.shrink(n);
        this.units += n * per;
        this.entityData.set(FILL, this.units);
        return n;
    }

    /** Saca el último montón de la carga; null si está vacío. */
    @Nullable
    public ItemStack takeCargo() {
        if (this.hold.isEmpty()) {
            return null;
        }
        ItemStack out = this.hold.remove(this.hold.size() - 1);
        this.units = Math.max(0, this.units - out.getCount() * unitsPer(out));
        if (this.hold.isEmpty()) {
            this.units = 0;
        }
        this.entityData.set(FILL, this.units);
        return out;
    }

    private void tellCargo(Player player) {
        Variant v = this.variant();
        if (v.isSteam()) {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.train_fuel", this.fillUnits(), v.capacity), true);
            return;
        }
        player.displayClientMessage(Component.translatable("hud.beautifuldecor.train_cargo", this.fillUnits(), v.capacity,
                String.format("%.1f", this.totalMassKg() / 1000.0)), true);
    }

    // =====================================================================================================
    // Subir, recoger, cargar
    // =====================================================================================================

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.beautifuldecor." + this.variant().id);
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
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < this.variant().seats();
    }

    /** Posición de los pies de quien va montado: el asiento que le toca (locomotora: conductor a la izquierda, acompañante a la derecha). */
    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        double[][] seats = TrainSeats.of(this.variant().id);
        if (seats.length == 0) {
            return;
        }
        int index = Math.min(this.getPassengers().indexOf(passenger), seats.length - 1);
        double side = seats[index][0];
        double fwd = seats[index][2];
        double height = seats[index][1];
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double lx = Mth.cos(yaw);
        double lz = Mth.sin(yaw);
        double theta = -this.getXRot() * Mth.DEG_TO_RAD;                   // positivo = frente hacia arriba
        double up = height * Math.cos(theta) + fwd * Math.sin(theta);
        double f = fwd * Math.cos(theta) - height * Math.sin(theta);
        callback.accept(passenger, this.getX() + fx * f + lx * side, this.getY() + up, this.getZ() + fz * f + lz * side);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double lx = Mth.cos(yaw);
        double lz = Mth.sin(yaw);
        for (double d : new double[] {2.6, -2.6}) {
            Vec3 pos = new Vec3(this.getX() + lx * d, this.getY() + 0.1, this.getZ() + lz * d);
            if (this.level().noCollision(passenger, passenger.getBoundingBox().move(pos.subtract(passenger.position())))) {
                return pos;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        boolean client = this.level().isClientSide;
        ItemStack held = player.getItemInHand(hand);
        Variant v = this.variant();
        // ---- V6.0.0: Mando de trenes
        if (held.getItem() instanceof TrainRemoteItem) {
            if (!client && player instanceof ServerPlayer sp) {
                if (v.isLoco()) {
                    TrainRemoteItem.link(held, this.getUUID());
                    TrainRemoteItem.openMenu(sp, this);
                } else {
                    sp.displayClientMessage(Component.translatable("message.beautifuldecor.train_remote.only_loco"), true);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        // ---- carga (o carbón del ténder)
        if (v.cargo != Cargo.NONE && this.acceptsItem(held)) {
            if (client) {
                return InteractionResult.SUCCESS;
            }
            int moved = this.addCargo(held);
            if (player.isSecondaryUseActive()) {
                // agachado: también pasa del inventario todo lo que sea de la misma clase
                for (int i = 0; i < player.getInventory().getContainerSize() && this.fillUnits() < v.capacity; i++) {
                    ItemStack slot = player.getInventory().getItem(i);
                    if (slot != held && ItemStack.isSameItemSameTags(slot, held)) {
                        moved += this.addCargo(slot);
                    }
                }
            }
            if (moved > 0) {
                this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.GRAVEL_PLACE, SoundSource.NEUTRAL, 0.8f, 0.9f);
            }
            this.tellCargo(player);
            return InteractionResult.CONSUME;
        }
        // ---- locomotora o vagón de pasajeros: subirse
        if (v.isLoco() || v.isCoach()) {
            if (player.isSecondaryUseActive()) {
                if (!this.isVehicle()) {
                    if (!client) {
                        this.pickUp(player);
                    }
                    return InteractionResult.sidedSuccess(client);
                }
                return InteractionResult.PASS;
            }
            if (client) {
                return InteractionResult.SUCCESS;
            }
            if (player.startRiding(this)) {
                if (player instanceof ServerPlayer sp) {
                    int idx = this.getPassengers().indexOf(player);
                    if (v.isLoco() && idx == 0) {
                        sp.displayClientMessage(Component.translatable("hud.beautifuldecor.train_controls"), true);
                    } else if (v.isCoach()) {
                        sp.displayClientMessage(Component.translatable("hud.beautifuldecor.train_seated", idx + 1, v.seats()), true);
                    }
                }
                return InteractionResult.CONSUME;
            }
            if (v.isCoach()) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.train_full"), true);
            }
            return InteractionResult.PASS;
        }
        // ---- vagón de carga
        if (player.isSecondaryUseActive()) {
            if (client) {
                return InteractionResult.SUCCESS;
            }
            if (this.hasCargo()) {
                ItemStack out = this.takeCargo();
                if (out != null && !player.getInventory().add(out)) {
                    this.spawnAtLocation(out);
                }
                this.tellCargo(player);
            } else {
                this.pickUp(player);
            }
            return InteractionResult.CONSUME;
        }
        if (!client) {
            this.tellCargo(player);
        }
        return InteractionResult.sidedSuccess(client);
    }

    private void pickUp(Player player) {
        ItemStack stack = VehiclePaint.carry(this, this.getPickResult());
        if (!player.getAbilities().instabuild && !player.getInventory().add(stack)) {
            this.spawnAtLocation(stack);
        }
        this.discard();
    }

    /** Un golpe de un jugador lo recoge (si no lo está montando nadie); la carga cae al suelo. */
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
        return new ItemStack(this.variant().item());
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            for (ItemStack stack : this.hold) {
                this.spawnAtLocation(stack);
            }
            this.hold.clear();
            this.unlinkNeighbours();
        }
        super.remove(reason);
    }

    // =====================================================================================================
    // Entrada del conductor (llega por TrainInputPacket)
    // =====================================================================================================

    public static final int INPUT_FORWARD = 1;
    public static final int INPUT_BACK = 2;
    public static final int INPUT_BRAKE = 4;

    public TrainRoute route() {
        return this.route;
    }

    public void setInput(int flags) {
        this.inputFlags = flags & 7;
        this.inputTime = this.level().getGameTime();
    }

    // =====================================================================================================
    // Vía
    // =====================================================================================================

    private RailPath.Lookup lookup() {
        Level level = this.level();
        int family = this.variant().family;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            return FreightRailBlock.pieceAt(level, pos, state, family);
        };
    }

    public boolean isPlaced() {
        return this.placed;
    }

    /** Pone el bogie delantero en ese punto de la vía (lo usa el ítem al colocar el coche). */
    public void placeOn(RailPath.Cursor cursor) {
        this.cur = cursor;
        this.placed = true;
        this.updatePose(this.lookup());
    }

    @Nullable
    public RailPath.Cursor frontCursor() {
        return this.cur == null ? null : this.cur.copy();
    }

    /** Punto del bogie trasero. */
    @Nullable
    public RailPath.Cursor rearCursor(RailPath.Lookup lookup) {
        if (this.cur == null) {
            return null;
        }
        RailPath.Cursor c = this.cur.copy();
        c.advance(-this.variant().wheelbase, lookup);
        return c;
    }

    public RailPath.Lookup railLookup() {
        return this.lookup();
    }

    /** Busca el riel bajo el coche cuando no se sabe dónde está (recién cargado del mundo o creado con un comando). */
    private void snapToRail() {
        RailPath.Lookup lk = this.lookup();
        BlockPos base = this.blockPosition();
        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = base.getX() + dx;
                    int y = base.getY() + dy;
                    int z = base.getZ() + dz;
                    RailPath.Piece shape = lk.shapeAt(x, y, z);
                    if (shape == null) {
                        continue;
                    }
                    double[] t = new double[2];
                    shape.tangent(shape.length() / 2.0, t);
                    float yaw = this.getYRot() * Mth.DEG_TO_RAD;
                    double dot = t[0] * -Mth.sin(yaw) + t[1] * Mth.cos(yaw);
                    RailPath.Cursor c = new RailPath.Cursor(x, y, z, shape, shape.length() / 2.0, dot >= 0 ? 1 : -1);
                    c.advance(this.variant().wheelbase / 2.0, lk);
                    this.placeOn(c);
                    return;
                }
            }
        }
    }

    /** Pone cada coche en el sitio que le toca según su cursor: posición, giro y pendiente. */
    private void updatePose(RailPath.Lookup lookup) {
        if (this.cur == null) {
            return;
        }
        RailPath.Cursor rc = this.rearCursor(lookup);
        double[] f = new double[3];
        double[] r = new double[3];
        this.cur.worldPoint(f);
        rc.worldPoint(r);
        double dx = f[0] - r[0];
        double dy = f[1] - r[1];
        double dz = f[2] - r[2];
        double h = Math.hypot(dx, dz);
        float yaw = h > 1.0e-4 ? RailPath.yawOf(dx, dz) : this.getYRot();
        float pitch = h > 1.0e-4 ? (float) -Math.toDegrees(Math.atan2(dy, h)) : 0.0f;
        this.slope = (float) ((this.cur.slope() + rc.slope()) * 0.5);
        this.setPos((f[0] + r[0]) / 2.0, (f[1] + r[1]) / 2.0, (f[2] + r[2]) / 2.0);
        this.setRot(yaw, pitch);
    }

    // =====================================================================================================
    // Cadena de coches
    // =====================================================================================================

    @Nullable
    private TrainCarEntity carById(@Nullable UUID id) {
        if (id == null || !(this.level() instanceof ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(id);
        return e instanceof TrainCarEntity car && !car.isRemoved() ? car : null;
    }

    /** Engancha {@code behind} detrás de este coche (y este delante de él). */
    public void coupleBehind(TrainCarEntity behind) {
        this.rearId = behind.getUUID();
        behind.frontId = this.getUUID();
    }

    public boolean isHead() {
        return this.frontId == null;
    }

    public boolean isTail() {
        return this.rearId == null;
    }

    private void unlinkNeighbours() {
        TrainCarEntity front = this.carById(this.frontId);
        TrainCarEntity rear = this.carById(this.rearId);
        if (front != null && this.getUUID().equals(front.rearId)) {
            front.rearId = null;
        }
        if (rear != null && this.getUUID().equals(rear.frontId)) {
            rear.frontId = null;
        }
        this.frontId = null;
        this.rearId = null;
    }

    /** Cuántos coches tiene la fila de este coche hacia atrás (contándolo). */
    public int carsBehindIncludingThis() {
        int n = 1;
        TrainCarEntity c = this;
        for (int i = 0; i < MAX_CARS; i++) {
            c = c.carById(c.rearId);
            if (c == null) {
                break;
            }
            n++;
        }
        return n;
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
        if (!this.placed || this.cur == null) {
            this.snapToRail();
            return;
        }
        if (this.frontId != null) {
            if (this.carById(this.frontId) == null) {
                // el de adelante no está cargado (o ya no existe): se espera; si pasa mucho tiempo se suelta
                if (++this.missingFront > 2400) {
                    this.frontId = null;
                    this.missingFront = 0;
                }
                this.speed = 0.0;
            } else {
                this.missingFront = 0;
            }
            return;                                                       // los de atrás los mueve la cabeza
        }
        this.driveChain();
    }

    private void driveChain() {
        RailPath.Lookup lk = this.lookup();
        List<TrainCarEntity> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        chain.add(this);
        seen.add(this.getUUID());
        boolean complete = true;
        TrainCarEntity last = this;
        for (int i = 0; i < MAX_CARS; i++) {
            if (last.rearId == null) {
                break;
            }
            TrainCarEntity next = last.carById(last.rearId);
            if (next == null) {
                complete = false;                                          // el de atrás no está cargado: la fila se queda quieta
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

        // ---- masa, pendiente, locomotoras y lo que pide el conductor
        double empty = 0.0;
        double total = 0.0;
        double slopeSum = 0.0;
        int locos = 0;
        double locoMass = 0.0;
        double power = 0.0;
        double topMs = Double.MAX_VALUE;
        int fuelLeft = 0;
        List<TrainCarEntity> burners = new ArrayList<>();
        int flags = 0;
        Player driver = null;
        long now = this.level().getGameTime();
        for (TrainCarEntity car : chain) {
            double m = car.totalMassKg();
            empty += car.variant().emptyKg;
            total += m;
            slopeSum += m * car.slope;
            topMs = Math.min(topMs, car.variant().topKmh / 3.6);
            if (car.variant().powerW > 0.0 && (!car.variant().isSteam() || car.fillUnits() > 0)) {
                locoMass += car.variant().emptyKg;
                power += car.variant().powerW;
                if (car.variant().isSteam()) {
                    burners.add(car);
                }
            }
            if (car.variant().isLoco()) {
                locos++;
                if (car.variant().isSteam()) {
                    fuelLeft += car.fillUnits();
                }
                if (!car.getPassengers().isEmpty() && car.getPassengers().get(0) instanceof Player p) {
                    if (driver == null) {
                        driver = p;
                    }
                    if (now - car.inputTime <= INPUT_TIMEOUT) {
                        flags |= car.inputFlags;
                    }
                }
            }
        }
        double slope = total > 0.0 ? slopeSum / total : 0.0;

        // ---- V6.0.0: piloto automático (si nadie está pidiendo nada con las teclas)
        TrainCarEntity autoCar = null;
        for (TrainCarEntity car : chain) {
            if (car.variant().isLoco() && car.route.active()) {
                autoCar = car;
                break;
            }
        }
        TrainRoute.Out auto = null;
        if (autoCar != null && this.level() instanceof ServerLevel sl) {
            if (this.tickCount % 10 == 0) {
                this.keepChunks(sl, chain, autoCar.route);
            }
            if (complete && flags == 0) {
                auto = autoCar.route.tick(sl, this.cur.copy(), this.variant().family, this.speed, empty, total, topMs, this.variant().inset());
                this.announce(chain, auto);
            } else {
                autoCar.route.invalidate();
            }
        }

        // ---- física
        double v = this.speed;
        int drive = 0;
        boolean brake = false;
        boolean hand = false;
        if (auto != null) {
            drive = auto.drive;
            brake = auto.brake;
            hand = auto.hand;
        } else if (driver != null) {
            boolean fwd = (flags & INPUT_FORWARD) != 0;
            boolean back = (flags & INPUT_BACK) != 0;
            if ((flags & INPUT_BRAKE) != 0) {
                brake = true;
                hand = true;
            } else if (fwd) {
                drive = 1;
            } else if (back) {
                if (v > 0.02) {
                    brake = true;
                } else {
                    drive = -1;
                }
            }
        } else {
            hand = true;                                                   // sin conductor, el tren se queda con el freno puesto
        }
        if (!complete) {
            v = 0.0;
        } else if (hand && Math.abs(v) < 0.004) {
            v = 0.0;                                                       // parado con el freno de mano: no se va cuesta abajo
        } else {
            v = TrainPhysics.step(v, empty, total, locoMass, power, topMs, slope, drive, brake, hand);
            if (drive != 0) {
                for (TrainCarEntity car : burners) {
                    car.burn(TrainPhysics.FUEL_PER_TICK);
                }
            }
        }

        // ---- mover todos la misma distancia
        boolean blocked = false;
        for (TrainCarEntity car : chain) {
            car.revalidate(lk);
        }
        if (v != 0.0) {
            RailPath.Cursor lead = v > 0.0 ? this.cur.copy() : chain.get(chain.size() - 1).rearCursor(lk);
            double moved = lead.advance(v, lk);
            if (Math.abs(moved) < Math.abs(v) - 1.0e-6) {
                blocked = true;
                if (Math.abs(v) > 0.4 && driver instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("hud.beautifuldecor.train_end_of_track"), true);
                    this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0f, 0.6f);
                }
                v = 0.0;
            }
            if (moved != 0.0) {
                for (TrainCarEntity car : chain) {
                    car.cur.advance(moved, lk);
                }
            }
        }
        for (TrainCarEntity car : chain) {
            car.speed = v;
            car.updatePose(lk);
        }

        if (driver instanceof ServerPlayer sp && this.tickCount % 4 == 0) {
            String grade = Math.abs(slope) < 0.02 ? "—" : (slope > 0 ? "↑" : "↓") + String.format("%.0f%%", Math.abs(slope) * 100.0);
            boolean steamTrain = this.variant().isSteam();
            sp.displayClientMessage(Component.translatable(steamTrain && power <= 0.0 ? "hud.beautifuldecor.train_no_fuel" : "hud.beautifuldecor.train_status",
                    String.format("%.0f", Math.abs(v) * 72.0), chain.size(), String.format("%.0f", total / 1000.0), grade, locos,
                    steamTrain ? String.valueOf(fuelLeft) : "—"), true);
        }
    }

    /** V6.0.0: mantiene cargados los chunks del tren y de la vía que tiene por delante. */
    private void keepChunks(ServerLevel sl, List<TrainCarEntity> chain, TrainRoute route) {
        Set<Long> near = new java.util.HashSet<>();
        for (TrainCarEntity car : chain) {
            near.add(TrainAuto.chunkKey(Mth.floor(car.getX()), Mth.floor(car.getZ())));
            if (car.cur != null) {
                near.add(TrainAuto.chunkKey(car.cur.x, car.cur.z));
            }
        }
        Set<Long> far = new java.util.HashSet<>(route.pathChunks());
        far.removeAll(near);
        TrainChunks.keep(sl, near, far);
    }

    /** V6.0.0: avisos del piloto a quienes van a bordo (y a quien esté cerca si se quedó sin vía). */
    private void announce(List<TrainCarEntity> chain, TrainRoute.Out out) {
        Component msg = null;
        if (out.stuck) {
            msg = Component.translatable("hud.beautifuldecor.train_auto_stuck");
        } else if (out.arrived != null) {
            msg = Component.translatable("hud.beautifuldecor.train_auto_arrived", out.arrived);
        } else if (out.parked) {
            msg = Component.translatable("hud.beautifuldecor.train_auto_parked");
        } else if (out.departed != null) {
            msg = Component.translatable("hud.beautifuldecor.train_auto_departed", out.departed);
        }
        if (msg == null) {
            return;
        }
        for (TrainCarEntity car : chain) {
            for (Entity p : car.getPassengers()) {
                if (p instanceof ServerPlayer sp) {
                    sp.displayClientMessage(msg, true);
                }
            }
        }
        if (out.stuck) {
            for (Player p : this.level().players()) {
                if (p.distanceToSqr(this) < 64.0 * 64.0 && p instanceof ServerPlayer sp && !this.hasPassenger(sp)) {
                    sp.displayClientMessage(msg, true);
                }
            }
        }
    }

    /** Quema carbón del ténder (unidades; se acumula hasta completar una). */
    private void burn(double amount) {
        this.fuelAcc += amount;
        while (this.fuelAcc >= 1.0 && this.units > 0) {
            this.fuelAcc -= 1.0;
            this.burnOne();
        }
    }

    private void burnOne() {
        boolean done = false;
        for (int i = this.hold.size() - 1; i >= 0 && !done; i--) {
            ItemStack h = this.hold.get(i);
            if (unitsPer(h) == 1) {
                h.shrink(1);
                if (h.isEmpty()) {
                    this.hold.remove(i);
                }
                done = true;
            }
        }
        if (!done && !this.hold.isEmpty()) {
            // un bloque de carbón se desmenuza: 1 de las 9 unidades se quema y las otras 8 quedan como carbón suelto
            ItemStack h = this.hold.get(this.hold.size() - 1);
            h.shrink(1);
            if (h.isEmpty()) {
                this.hold.remove(this.hold.size() - 1);
            }
            this.hold.add(new ItemStack(Items.COAL, 8));
        }
        this.units = Math.max(0, this.units - 1);
        if (this.hold.isEmpty()) {
            this.units = 0;
        }
        this.entityData.set(FILL, this.units);
    }

    /** Si el riel de este coche cambió de forma (por un riel nuevo al lado) se ajusta; si desapareció, el coche se queda donde está. */
    private void revalidate(RailPath.Lookup lk) {
        RailPath.Piece now = lk.shapeAt(this.cur.x, this.cur.y, this.cur.z);
        if (now != null && now != this.cur.shape) {
            this.cur.shape = now;
            this.cur.s = Math.min(this.cur.s, now.length());
        }
    }

    // =====================================================================================================
    // Cliente
    // =====================================================================================================

    private void clientTick() {
        this.tickLerp();
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double forward = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
        this.visualSpeed = (float) forward;
        this.wheelAngleO = this.wheelAngle;
        this.wheelAngle += (float) (forward / 0.5);
        if (this.wheelAngle > Mth.TWO_PI * 16.0f || this.wheelAngle < -Mth.TWO_PI * 16.0f) {
            this.wheelAngle = 0.0f;
            this.wheelAngleO = 0.0f;
        }
        // humo de la chimenea de la locomotora de vapor encendida
        if (this.variant().isSteam() && this.fillUnits() > 0) {
            boolean classic = this.variant() == Variant.STEAM_CLASSIC;
            if (this.tickCount % (Math.abs(this.visualSpeed) > 0.05f ? 2 : 8) == 0) {
                double fwd = classic ? 9.1 : 7.5;
                double up = (classic ? 6.3 : 5.0) * Math.cos(-this.getXRot() * Mth.DEG_TO_RAD) + fwd * Math.sin(-this.getXRot() * Mth.DEG_TO_RAD);
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX() - Mth.sin(yaw) * fwd, this.getY() + up,
                        this.getZ() + Mth.cos(yaw) * fwd, 0.0, 0.07, 0.0);
            }
        }
        // traqueteo de las ruedas: la locomotora y uno de cada cuatro vagones
        float abs = Math.abs(this.visualSpeed);
        if (abs > 0.12f && this.tickCount % 7 == 0 && (this.variant().isLoco() || this.getId() % 4 == 0)) {
            this.level().playLocalSound(this.getX(), this.getY() + 0.5, this.getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL,
                    Math.min(1.3f, 0.15f + abs * 0.6f), 0.55f + Math.min(0.5f, abs * 0.25f), false);
        }
    }

    private void tickLerp() {
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

    /** Un coche va deprisa: se interpola en 2 ticks (con 10 se vería muy atrasado respecto a los rieles). */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = (double) yRot;
        this.lerpXRot = (double) xRot;
        this.lerpSteps = 2;
    }

    public float getWheelAngle(float partialTick) {
        return Mth.lerp(partialTick, this.wheelAngleO, this.wheelAngle);
    }

    /** Cabeceo en grados (positivo = frente hacia abajo), interpolado entre ticks. */
    public float getPitchVisual(float partialTick) {
        return Mth.lerp(partialTick, this.xRotO, this.getXRot());
    }

    // =====================================================================================================
    // Guardado
    // =====================================================================================================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        VehiclePaint.read(this, tag);
        if (tag.contains("Variant")) {
            this.entityData.set(VARIANT, tag.getInt("Variant"));
        }
        this.hold.clear();
        this.units = 0;
        ListTag list = tag.getList("Hold", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                this.hold.add(stack);
                this.units += stack.getCount() * unitsPer(stack);
            }
        }
        this.entityData.set(FILL, this.units);
        this.frontId = tag.hasUUID("Front") ? tag.getUUID("Front") : null;
        this.rearId = tag.hasUUID("Rear") ? tag.getUUID("Rear") : null;
        this.speed = tag.getDouble("Speed");
        if (tag.contains("Route")) {
            this.route.load(tag.getCompound("Route"));
        }
        if (tag.getBoolean("Placed")) {
            // la forma del riel se vuelve a leer en el primer tick; mientras tanto se toma la forma recta
            this.cur = new RailPath.Cursor(tag.getInt("CurX"), tag.getInt("CurY"), tag.getInt("CurZ"), RailPath.Shape.NORTH_SOUTH,
                    tag.getDouble("CurS"), tag.getInt("CurDir") >= 0 ? 1 : -1);
            this.placed = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        VehiclePaint.write(this, tag);
        tag.putInt("Variant", this.entityData.get(VARIANT));
        ListTag list = new ListTag();
        for (ItemStack stack : this.hold) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("Hold", list);
        if (this.frontId != null) {
            tag.putUUID("Front", this.frontId);
        }
        if (this.rearId != null) {
            tag.putUUID("Rear", this.rearId);
        }
        tag.putDouble("Speed", this.speed);
        if (!this.route.stops.isEmpty() || !this.route.name.isEmpty()) {
            tag.put("Route", this.route.save());
        }
        if (this.placed && this.cur != null) {
            tag.putBoolean("Placed", true);
            tag.putInt("CurX", this.cur.x);
            tag.putInt("CurY", this.cur.y);
            tag.putInt("CurZ", this.cur.z);
            tag.putDouble("CurS", this.cur.s);
            tag.putInt("CurDir", this.cur.dir);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
