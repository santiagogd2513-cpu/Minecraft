package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * V7.9.0: remolque de la tractomula (furgón de carga, de troncos o cisterna). El origen de la entidad es el perno de enganche; el remolque se
 * extiende hacia atrás. Enganchado, lo mueve el tractor ({@link #follow}) con el modelo de "barra rígida": el eje trasero siempre sigue el rumbo del
 * remolque a {@link #AXLE_BACK} bloques del perno, así que en las curvas el remolque cierra por dentro como uno de verdad. No tiene colisión propia
 * (solo la del tractor) y por eso en curvas cerradas puede atravesar una pared. Suelto, se apoya en las patas y se queda donde está.
 */
public class TrailerEntity extends Entity {
    public static final int CARGO = 0;
    public static final int LOGS = 1;
    public static final int TANK = 2;
    /** V7.10.0: niñera de dos pisos (3 autos abajo y 3 arriba) con rampa baja y rampa alta. */
    public static final int CARRIER = 3;

    /** Distancia del perno al promedio de los ejes traseros (tools/v790/trailers.py: 160 px; la niñera, 212 px). */
    public static final double AXLE_BACK = 10.0;
    public static final double AXLE_BACK_CARRIER = 13.25;
    private static final double WHEEL_RADIUS = 0.5;
    private static final double MAX_ARTICULATION = 82.0;

    /** Troncos que caben (5 de ancho x 22 de largo x 4 de alto, cada uno a medio bloque) y lo que pesa cada uno en la física. */
    public static final int MAX_LOGS = 440;
    private static final int LOG_KG = 100;
    /** Cisterna: 32 cubos (1 mB = 1 kg). */
    public static final int TANK_CAPACITY = 32_000;
    private static final int CARGO_ROWS = 6;
    private static final int EMPTY_KG = 6500;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TrailerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRACTOR_ID = SynchedEntityData.defineId(TrailerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LOG_PILE = SynchedEntityData.defineId(TrailerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> FLUID_ID = SynchedEntityData.defineId(TrailerEntity.class, EntityDataSerializers.STRING);
    /** Niñera: 0 = rampas cerradas, 1 = rampa baja abierta (cubierta de abajo), 2 = rampa alta abierta (cubierta de arriba). */
    private static final EntityDataAccessor<Integer> RAMP = SynchedEntityData.defineId(TrailerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLUID_AMOUNT = SynchedEntityData.defineId(TrailerEntity.class, EntityDataSerializers.INT);

    /** Caja de colisión (solo para apuntarle y para posarse en el suelo): ancho, largo, desfase del centro (negativo = atrás), alto. */
    private static final VehicleHull.Spec HULL = new VehicleHull.Spec(2.6, 11.6, -5.8, 3.0);
    private static final VehicleHull.Spec HULL_CARRIER = new VehicleHull.Spec(2.8, 16.3, -8.15, 6.4);

    // ---- V7.10.0: niñera (medidas en bloques, del modelo: tools/v790/trailers.py)
    /** Dónde termina la cubierta (distancia hacia atrás desde el perno) y dónde queda el centro de cada casilla de una cubierta. */
    public static final double DECK_END = 16.25;
    private static final double[] SLOT_D = {3.3, 8.4, 13.5};
    /** Altura de cada cubierta (0 = baja, 1 = alta) y largo de su rampa. */
    private static final double[] DECK_H = {1.1875, 3.75};
    private static final double[] RAMP_LEN = {4.0, 8.5};
    /** Tramo de suelo antes de la rampa por el que "llega" el auto. */
    private static final double GROUND_LEN = 2.5;
    /** Bloques por tick que avanza un auto por la rampa. */
    private static final double GLIDE = 0.28;
    private static final float RAMP_SPEED = 0.02f;

    // ---- carga (servidor y cliente: el cliente solo lo usa para dibujar)
    private final List<Item> logs = new ArrayList<>();
    private final SimpleContainer storage = new Box(this, CARGO_ROWS);
    private final FluidTank tank = new FluidTank(TANK_CAPACITY);
    private BlockState[] pileStates = new BlockState[0];

    // ---- visuales
    private float wheelRot;
    private float wheelRotO;
    /** Cabeceo (grados, positivo = cola arriba) para que el remolque siga la cuesta con su eje trasero. */
    private float pitch;
    private float pitchO;
    private int age;
    private double vy;
    // ---- niñera
    private final java.util.Set<VehicleEntity> carried = new java.util.HashSet<>();
    private float rampLow;
    private float rampLowO;
    private float rampHigh;
    private float rampHighO;

    public TrailerEntity(EntityType<? extends TrailerEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** Cajón del almacenamiento: vale mientras estés cerca del remolque. */
    private static final class Box extends SimpleContainer {
        private final Entity owner;

        Box(Entity owner, int rows) {
            super(rows * 9);
            this.owner = owner;
        }

        @Override
        public boolean stillValid(Player player) {
            return !this.owner.isRemoved() && player.distanceToSqr(this.owner) < 400.0;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, CARGO);
        this.entityData.define(RAMP, 0);
        this.entityData.define(TRACTOR_ID, -1);
        this.entityData.define(LOG_PILE, "");
        this.entityData.define(FLUID_ID, "");
        this.entityData.define(FLUID_AMOUNT, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LOG_PILE.equals(key) && this.level().isClientSide) {
            this.rebuildPile(this.entityData.get(LOG_PILE));
        }
    }

    // ------------------------------------------------------------------------------------------------ datos básicos
    public int getKind() {
        return this.entityData.get(KIND);
    }

    public void setKind(int kind) {
        this.entityData.set(KIND, Mth.clamp(kind, CARGO, CARRIER));
    }

    private VehicleHull.Spec hull() {
        return this.getKind() == CARRIER ? HULL_CARRIER : HULL;
    }

    public double axleBack() {
        return this.getKind() == CARRIER ? AXLE_BACK_CARRIER : AXLE_BACK;
    }

    public boolean isCoupled() {
        return this.entityData.get(TRACTOR_ID) >= 0;
    }

    @Nullable
    public SemiTruckEntity getTractor() {
        int id = this.entityData.get(TRACTOR_ID);
        if (id < 0) {
            return null;
        }
        Entity e = this.level().getEntity(id);
        return e instanceof SemiTruckEntity t && !t.isRemoved() ? t : null;
    }

    public void setTractor(SemiTruckEntity tractor) {
        this.entityData.set(TRACTOR_ID, tractor.getId());
        this.pushMass(tractor);
    }

    /** Lo suelta el tractor: queda en el sitio sobre sus patas. */
    public void release() {
        this.entityData.set(TRACTOR_ID, -1);
        this.setDeltaMovement(Vec3.ZERO);
    }

    public float getWheelRot(float partialTick) {
        return Mth.lerp(partialTick, this.wheelRotO, this.wheelRot);
    }

    /** Cabeceo para dibujar (grados, positivo = cola arriba). */
    public float getPitch(float partialTick) {
        return Mth.lerp(partialTick, this.pitchO, this.pitch);
    }

    public BlockState[] getPileStates() {
        return this.pileStates;
    }

    public FluidStack getFluid() {
        return this.tank.getFluid();
    }

    /** Masa del remolque con su carga (kg) para la física del tractor. */
    public int totalKg() {
        return switch (this.getKind()) {
            case LOGS -> EMPTY_KG + this.logs.size() * LOG_KG;
            case TANK -> EMPTY_KG + this.tank.getFluidAmount();
            case CARRIER -> {
                double kg = 9000;
                for (VehicleEntity car : this.carried) {
                    if (!car.isRemoved() && car.getCarrierId() == this.getId()) {
                        kg += car.totalMassKg();
                    }
                }
                yield (int) kg;
            }
            default -> EMPTY_KG + this.cargoItems();
        };
    }

    private int cargoItems() {
        int n = 0;
        for (int i = 0; i < this.storage.getContainerSize(); i++) {
            n += this.storage.getItem(i).getCount();
        }
        return n;
    }

    private void pushMass(SemiTruckEntity tractor) {
        if (!this.level().isClientSide) {
            tractor.setTrailerKg(this.totalKg());
        }
    }

    // ------------------------------------------------------------------------------------------------ movimiento
    /** Llamado por el tractor en cada tick (servidor y cliente): coloca el remolque con el modelo de barra rígida. */
    public void follow(SemiTruckEntity tractor) {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.xo;
        this.yOld = this.yo;
        this.zOld = this.zo;
        this.yRotO = this.getYRot();
        this.pitchO = this.pitch;
        this.wheelRotO = this.wheelRot;
        this.age++;

        final double L = this.axleBack();
        Vec3 pin = tractor.hitchPoint();
        double yawRad = Math.toRadians(this.getYRot());
        // el eje trasero de ahora: detrás del perno viejo, en el rumbo del remolque
        double ax = this.getX() + Math.sin(yawRad) * L;
        double az = this.getZ() - Math.cos(yawRad) * L;
        double dx = ax - pin.x;
        double dz = az - pin.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        float newYaw;
        if (len < 1.0e-3 || len > L * 1.6) {
            newYaw = tractor.getYRot();                                      // recién enganchado desde lejos o tras un teletransporte
        } else {
            newYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        }
        // no dejar que se doble más de lo que dobla uno real
        float rel = Mth.wrapDegrees(newYaw - tractor.getYRot());
        rel = (float) Mth.clamp(rel, -MAX_ARTICULATION, MAX_ARTICULATION);
        newYaw = tractor.getYRot() + rel;

        double moved = Math.sqrt((pin.x - this.xo) * (pin.x - this.xo) + (pin.z - this.zo) * (pin.z - this.zo));
        double dirX = -Math.sin(Math.toRadians(newYaw));
        double dirZ = Math.cos(Math.toRadians(newYaw));
        double along = (pin.x - this.xo) * dirX + (pin.z - this.zo) * dirZ;
        if (moved > 1.0e-4) {
            this.wheelRot += (float) Mth.clamp(Math.signum(along) * moved / WHEEL_RADIUS, -2.2, 2.2);
        }

        this.setYRot(newYaw);
        this.setXRot(0.0f);
        this.setPos(pin.x, pin.y, pin.z);
        VehicleHull.refresh(this, this.hull());

        // cabeceo: el eje trasero se apoya donde esté el suelo
        double yawNow = Math.toRadians(newYaw);
        double rx = pin.x + Math.sin(yawNow) * L;
        double rz = pin.z - Math.cos(yawNow) * L;
        double ground = this.groundAt(rx, pin.y + 1.2, rz, pin.y - 3.0);
        float target = (float) Math.toDegrees(Math.atan2(ground - pin.y, L));
        target = Mth.clamp(target, -18.0f, 18.0f);
        this.pitch += (target - this.pitch) * 0.25f;

        this.tickRamp();
        this.cargoTick(pin);
        if (!this.level().isClientSide && this.age % 10 == 0) {
            tractor.setTrailerKg(this.totalKg());
        }
    }

    /** Altura de la superficie más alta (con colisión) bajo (x, z) entre las alturas dadas; si no hay, el piso de abajo. */
    private double groundAt(double x, double fromY, double z, double floorY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int by = Mth.floor(fromY); by >= Mth.floor(floorY); by--) {
            pos.set(bx, by, bz);
            BlockState state = this.level().getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(this.level(), pos);
            if (!shape.isEmpty()) {
                return by + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return floorY;
    }

    @Override
    public void tick() {
        if (this.isCoupled()) {
            if (!this.level().isClientSide) {
                SemiTruckEntity t = this.getTractor();
                if (t == null || t.getTrailerId() != this.getId()) {         // el tractor ya no está: queda suelto
                    this.release();
                }
            }
            return;                                                         // en lo demás lo mueve el tractor
        }
        super.tick();
        this.pitchO = this.pitch;
        this.pitch += (0.0f - this.pitch) * 0.2f;
        this.wheelRotO = this.wheelRot;
        if (!this.level().isClientSide) {
            this.settle();
            this.syncFluid();
        }
        VehicleHull.refresh(this, this.hull());
        if (this.getKind() == CARRIER) {
            this.age++;
            this.tickRamp();
            this.cargoTick(this.position());
        }
    }

    /** Suelto: solo cae y se posa. */
    private void settle() {
        if (this.onGround()) {
            this.vy = -0.04;
        } else {
            this.vy = (this.vy - VehicleEntity.GRAVITY) * 0.99;
        }
        this.setDeltaMovement(0.0, this.vy, 0.0);
        if (!this.onGround() || this.level().getGameTime() % 40 == 0) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        VehicleHull.refresh(this, this.hull());
        super.move(type, delta);
        VehicleHull.fixPosition(this, this.hull());
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.isCoupled()) {
            return;                                                         // la posición la calcula el tractor
        }
        this.setPos(x, y, z);
        this.setRot(yRot, xRot);
        VehicleHull.refresh(this, this.hull());
    }

    // ------------------------------------------------------------------------------------------------ interacción
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        switch (this.getKind()) {
            case LOGS -> this.useLogs(sp, hand, held);
            case TANK -> this.useTank(sp, hand, held);
            case CARRIER -> this.cycleRamp(sp);
            default -> {
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, who) -> new ChestMenu(VehicleStorage.menuType(CARGO_ROWS), id, inv, this.storage, CARGO_ROWS),
                        Component.translatable("container.beautifuldecor.storage.trailer_cargo")));
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.5f, 1.1f);
            }
        }
        if (this.getTractor() != null) {
            this.getTractor().setTrailerKg(this.totalKg());
        }
        return InteractionResult.CONSUME;
    }

    // ---- troncos
    private void useLogs(ServerPlayer player, InteractionHand hand, ItemStack held) {
        if (held.is(ItemTags.LOGS)) {
            int want = player.isSecondaryUseActive() ? held.getCount() : 1;
            int space = MAX_LOGS - this.logs.size();
            int n = Math.min(want, space);
            if (n <= 0) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_full"), true);
                return;
            }
            for (int i = 0; i < n; i++) {
                this.logs.add(held.getItem());
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(n);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 0.8f, 0.9f);
            this.syncLogs();
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_logs", this.logs.size(), MAX_LOGS), true);
        } else if (held.isEmpty() && !this.logs.isEmpty()) {
            // mano vacía: baja hasta una pila (agachado: todo lo que quepa en una pila) del último tronco cargado
            Item last = this.logs.get(this.logs.size() - 1);
            int n = 0;
            int max = player.isSecondaryUseActive() ? last.getMaxStackSize() : 1;
            while (n < max && !this.logs.isEmpty() && this.logs.get(this.logs.size() - 1) == last) {
                this.logs.remove(this.logs.size() - 1);
                n++;
            }
            ItemStack out = new ItemStack(last, n);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 0.6f, 1.1f);
            this.syncLogs();
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_logs", this.logs.size(), MAX_LOGS), true);
        } else {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_logs", this.logs.size(), MAX_LOGS), true);
        }
    }

    /** Pone en el dato sincronizado la lista de troncos como "id=cantidad;..." para que los clientes dibujen la pila. */
    private void syncLogs() {
        StringBuilder sb = new StringBuilder();
        Item cur = null;
        int n = 0;
        for (Item item : this.logs) {
            if (item != cur) {
                if (cur != null) {
                    sb.append(BuiltInRegistries.ITEM.getKey(cur)).append('=').append(n).append(';');
                }
                cur = item;
                n = 0;
            }
            n++;
        }
        if (cur != null) {
            sb.append(BuiltInRegistries.ITEM.getKey(cur)).append('=').append(n).append(';');
        }
        this.entityData.set(LOG_PILE, sb.toString());
    }

    private void rebuildPile(String text) {
        List<BlockState> list = new ArrayList<>();
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(part.substring(0, eq));
            int n;
            try {
                n = Integer.parseInt(part.substring(eq + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item instanceof BlockItem bi) {
                BlockState state = bi.getBlock().defaultBlockState();
                if (state.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
                    state = state.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Z);
                }
                for (int i = 0; i < n && list.size() < MAX_LOGS; i++) {
                    list.add(state);
                }
            }
        }
        this.pileStates = list.toArray(new BlockState[0]);
    }

    // ================================================================================================ niñera (V7.10.0)

    public int getRamp() {
        return this.entityData.get(RAMP);
    }

    /** Cuánto bajó cada rampa (radianes, 0 = plana) para dibujar. */
    public float getRampLow(float partialTick) {
        return Mth.lerp(partialTick, this.rampLowO, this.rampLow);
    }

    public float getRampHigh(float partialTick) {
        return Mth.lerp(partialTick, this.rampHighO, this.rampHigh);
    }

    private static float rampAngle(int deck) {
        return (float) Math.asin(DECK_H[deck] / RAMP_LEN[deck]);
    }

    private void tickRamp() {
        this.rampLowO = this.rampLow;
        this.rampHighO = this.rampHigh;
        int state = this.getRamp();
        float lowGoal = state == 1 ? rampAngle(0) : 0.0f;
        float highGoal = state == 2 ? rampAngle(1) : 0.0f;
        this.rampLow += Mth.clamp(lowGoal - this.rampLow, -RAMP_SPEED, RAMP_SPEED);
        this.rampHigh += Mth.clamp(highGoal - this.rampHigh, -RAMP_SPEED, RAMP_SPEED);
    }

    /** La rampa de esa cubierta ya terminó de bajar (los autos solo suben y bajan con la rampa lista). */
    private boolean rampReady(int deck) {
        return this.getRamp() == deck + 1 && Math.abs((deck == 0 ? this.rampLow : this.rampHigh) - rampAngle(deck)) < 0.02f;
    }

    /** Botón del tablero (o clic derecho en la niñera): cerrada -> rampa baja -> rampa alta -> cerrada. */
    public void cycleRamp(ServerPlayer player) {
        if (this.getKind() != CARRIER) {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.ramp_none"), true);
            return;
        }
        for (VehicleEntity car : this.carried) {
            if (car.carryDir != 0) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.ramp_busy"), true);
                return;
            }
        }
        int next = (this.getRamp() + 1) % 3;
        this.entityData.set(RAMP, next);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 0.8f, 0.6f);
        player.displayClientMessage(Component.translatable("hud.beautifuldecor.ramp_" + (next == 0 ? "closed" : next == 1 ? "low" : "high")), true);
    }

    /** Un auto avisa que va sobre esta niñera (en cada tick suyo; servidor y cliente). */
    public void registerCarried(VehicleEntity car) {
        this.carried.add(car);
    }

    private static double[] pathPoint(int slot, double s) {
        int deck = slot / 3;
        double rampEnd = DECK_END + Math.sqrt(RAMP_LEN[deck] * RAMP_LEN[deck] - DECK_H[deck] * DECK_H[deck]);
        double horiz = rampEnd - DECK_END;
        if (s <= GROUND_LEN) {
            return new double[] {rampEnd + GROUND_LEN - s, 0.0};
        }
        if (s <= GROUND_LEN + RAMP_LEN[deck]) {
            double u = (s - GROUND_LEN) / RAMP_LEN[deck];
            return new double[] {rampEnd - u * horiz, u * DECK_H[deck]};
        }
        return new double[] {DECK_END - (s - GROUND_LEN - RAMP_LEN[deck]), DECK_H[deck]};
    }

    private static double pathLength(int slot) {
        int deck = slot / 3;
        return GROUND_LEN + RAMP_LEN[deck] + (DECK_END - SLOT_D[slot % 3]);
    }

    /** Servidor y cliente: coloca los autos sobre la cubierta; el servidor además los sube, los baja y busca autos nuevos. */
    private void cargoTick(Vec3 pin) {
        if (this.getKind() != CARRIER) {
            return;
        }
        boolean server = !this.level().isClientSide;
        java.util.Iterator<VehicleEntity> it = this.carried.iterator();
        while (it.hasNext()) {
            VehicleEntity car = it.next();
            if (car.isRemoved() || car.getCarrierId() != this.getId()) {
                it.remove();
                continue;
            }
            if (server) {
                int slot = car.getCarrySlot();
                double len = pathLength(slot);
                double prog = car.getCarryProg();
                if (car.carryDir == 0 && prog < len - 0.01) {
                    car.carryDir = 1;                                         // quedó a medias al guardar el mundo
                }
                if (car.carryDir != 0) {
                    prog += car.carryDir * GLIDE;
                    if (car.carryDir > 0 && prog >= len) {
                        prog = len;
                        car.carryDir = 0;
                    }
                    if (car.carryDir < 0 && prog <= 0.0) {
                        this.placeCar(car, pin, 0.0);
                        car.detach();
                        it.remove();
                        continue;
                    }
                    car.setCarryProg((float) prog);
                } else if (this.wantsOut(car, slot)) {
                    car.carryDir = -1;
                }
            }
            this.placeCar(car, pin, car.getCarryProg());
        }
        if (server && this.age % 2 == 0 && this.getRamp() != 0) {
            this.loadScan(pin);
        }
    }

    private void placeCar(VehicleEntity car, Vec3 pin, double prog) {
        double[] p = pathPoint(car.getCarrySlot(), prog);
        double yawRad = Math.toRadians(this.getYRot());
        double rise = Math.min(p[0], DECK_END) * Math.sin(Math.toRadians(this.pitch));
        double x = pin.x + Math.sin(yawRad) * p[0];
        double z = pin.z - Math.cos(yawRad) * p[0];
        double y = pin.y + p[1] + rise;
        car.xo = car.getX();
        car.yo = car.getY();
        car.zo = car.getZ();
        car.yRotO = car.getYRot();
        if (!this.level().isClientSide) {
            car.setDeltaMovement(x - car.getX(), y - car.getY(), z - car.getZ());
        }
        car.setYRot(this.getYRot());
        car.setXRot(0.0f);
        car.setPos(x, y, z);
        car.refreshHull();
        car.fallDistance = 0.0f;
    }

    /** El que va manejando un auto ya sentado en su casilla aprieta una tecla de avance o retroceso con la rampa de su cubierta lista: baja. */
    private boolean wantsOut(VehicleEntity car, int slot) {
        int deck = slot / 3;
        if (!this.rampReady(deck) || !(car.getControllingPassenger() instanceof Player driver)
                || Math.abs(driver.zza) < 0.1f) {
            return false;
        }
        for (VehicleEntity other : this.carried) {
            if (other != car && other.getCarrierId() == this.getId() && other.getCarrySlot() / 3 == deck
                    && other.getCarrySlot() > slot) {
                if (driver instanceof ServerPlayer sp && this.age % 20 == 0) {
                    sp.displayClientMessage(Component.translatable("hud.beautifuldecor.ramp_blocked"), true);
                }
                return false;                                                  // hay otro auto más cerca de la rampa
            }
        }
        return true;
    }

    /** Busca un auto con conductor que llegue despacio y derecho a la rampa abierta y lo sube a la primera casilla libre de esa cubierta. */
    private void loadScan(Vec3 pin) {
        int deck = this.getRamp() - 1;
        if (deck < 0 || !this.rampReady(deck)) {
            return;
        }
        boolean[] used = new boolean[6];
        for (VehicleEntity car : this.carried) {
            if (car.getCarrierId() == this.getId()) {
                used[Mth.clamp(car.getCarrySlot(), 0, 5)] = true;
            }
        }
        int free = -1;
        for (int k = 0; k < 3; k++) {
            if (!used[deck * 3 + k]) {
                free = deck * 3 + k;
                break;
            }
        }
        if (free < 0) {
            return;
        }
        double yawRad = Math.toRadians(this.getYRot());
        double rampEnd = DECK_END + Math.sqrt(RAMP_LEN[deck] * RAMP_LEN[deck] - DECK_H[deck] * DECK_H[deck]);
        double startD = rampEnd + GROUND_LEN;
        double cx = pin.x + Math.sin(yawRad) * startD;
        double cz = pin.z - Math.cos(yawRad) * startD;
        net.minecraft.world.phys.AABB zone = new net.minecraft.world.phys.AABB(cx - 6, pin.y - 2.0, cz - 6, cx + 6, pin.y + 4.0, cz + 6);
        for (VehicleEntity car : this.level().getEntitiesOfClass(VehicleEntity.class, zone)) {
            if (car.isCarried() || !(car instanceof PickupEntity || car instanceof SupercarEntity)
                    || !(car.getControllingPassenger() instanceof Player driver) || driver.zza < 0.1f
                    || Math.abs(car.getVisualSpeed()) > 0.5f) {
                continue;
            }
            double dx = car.getX() - pin.x;
            double dz = car.getZ() - pin.z;
            double back = dx * Math.sin(yawRad) - dz * Math.cos(yawRad);
            double lateral = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);
            double yawDiff = Math.abs(Mth.wrapDegrees(car.getYRot() - this.getYRot()));
            if (back < rampEnd + 0.3 || back > startD + 2.5 || Math.abs(lateral) > 0.8 || yawDiff > 30.0
                    || car.getY() < pin.y - 1.0 || car.getY() > pin.y + 1.5) {
                continue;
            }
            car.attachTo(this, free, (float) (startD - back));
            this.carried.add(car);
            this.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.8f, 0.8f);
            return;                                                          // uno por vez
        }
    }

    // ---- cisterna
    private void useTank(ServerPlayer player, InteractionHand hand, ItemStack held) {
        boolean creative = player.getAbilities().instabuild;
        Fluid bucketFluid = null;
        if (held.is(Items.WATER_BUCKET)) {
            bucketFluid = Fluids.WATER;
        } else if (held.is(Items.LAVA_BUCKET)) {
            bucketFluid = Fluids.LAVA;
        } else if (held.is(Items.MILK_BUCKET)) {
            bucketFluid = ForgeMod.MILK.get();
        }
        if (bucketFluid != null) {
            FluidStack in = new FluidStack(bucketFluid, 1000);
            if (this.tank.fill(in, IFluidHandler.FluidAction.SIMULATE) == 1000) {
                this.tank.fill(in, IFluidHandler.FluidAction.EXECUTE);
                if (!creative) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
        } else if (held.is(Items.BUCKET)) {
            FluidStack inside = this.tank.getFluid();
            Item filled = null;
            if (inside.getAmount() >= 1000) {
                if (inside.getFluid() == Fluids.WATER) {
                    filled = Items.WATER_BUCKET;
                } else if (inside.getFluid() == Fluids.LAVA) {
                    filled = Items.LAVA_BUCKET;
                } else if (inside.getFluid() == ForgeMod.MILK.get()) {
                    filled = Items.MILK_BUCKET;
                }
            }
            if (filled != null) {
                this.tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                if (!creative) {
                    held.shrink(1);
                    ItemStack out = new ItemStack(filled);
                    if (held.isEmpty()) {
                        player.setItemInHand(hand, out);
                    } else if (!player.getInventory().add(out)) {
                        player.drop(out, false);
                    }
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
        } else if (!held.isEmpty()) {
            // otros recipientes de líquidos (cubos de otros mods, tanques portátiles...)
            FluidUtil.interactWithFluidHandler(player, hand, this.tank);
        }
        this.syncFluid();
        FluidStack now = this.tank.getFluid();
        if (now.isEmpty()) {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_tank_empty", TANK_CAPACITY / 1000), true);
        } else {
            player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_tank", now.getDisplayName(),
                    now.getAmount() / 1000, TANK_CAPACITY / 1000), true);
        }
    }

    private void syncFluid() {
        FluidStack fs = this.tank.getFluid();
        String id = fs.isEmpty() ? "" : String.valueOf(BuiltInRegistries.FLUID.getKey(fs.getFluid()));
        if (!id.equals(this.entityData.get(FLUID_ID))) {
            this.entityData.set(FLUID_ID, id);
        }
        if (fs.getAmount() != this.entityData.get(FLUID_AMOUNT)) {
            this.entityData.set(FLUID_AMOUNT, fs.getAmount());
        }
    }

    // ------------------------------------------------------------------------------------------------ recoger / romper
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || this.level().isClientSide) {
            return false;
        }
        if (source.getEntity() instanceof Player player && !this.isCoupled()) {
            if (this.getKind() == CARRIER && !this.carried.isEmpty()) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_has_cars"), true);
                return true;
            }
            if (this.getKind() == TANK && !this.tank.isEmpty()) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.trailer_drain_first"), true);
                return true;
            }
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(this.getPickResult());
            }
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            VehicleStorage.drop(this, this.storage);
            for (VehicleEntity car : this.carried) {
                if (!car.isRemoved() && car.getCarrierId() == this.getId()) {
                    car.detach();
                }
            }
            this.carried.clear();
            for (Item item : this.logs) {
                this.spawnAtLocation(new ItemStack(item));
            }
            this.logs.clear();
        }
        super.remove(reason);
    }

    public ItemStack getPickResult() {
        return switch (this.getKind()) {
            case LOGS -> new ItemStack(ModItems.TRAILER_LOGS.get());
            case TANK -> new ItemStack(ModItems.TRAILER_TANK.get());
            case CARRIER -> new ItemStack(ModItems.TRAILER_CARRIER.get());
            default -> new ItemStack(ModItems.TRAILER_CARGO.get());
        };
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ guardado
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setKind(tag.getInt("Kind"));
        this.logs.clear();
        ListTag list = tag.getList("Logs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(c.getString("id"));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.get(id);
                for (int k = 0; k < c.getInt("n") && this.logs.size() < MAX_LOGS; k++) {
                    this.logs.add(item);
                }
            }
        }
        this.syncLogs();
        if (tag.contains("Fluid", Tag.TAG_COMPOUND)) {
            this.tank.readFromNBT(tag.getCompound("Fluid"));
        }
        VehicleStorage.load(this.storage, tag);
        this.syncFluid();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", this.getKind());
        ListTag list = new ListTag();
        Item cur = null;
        int n = 0;
        for (Item item : this.logs) {
            if (item != cur) {
                if (cur != null) {
                    list.add(logEntry(cur, n));
                }
                cur = item;
                n = 0;
            }
            n++;
        }
        if (cur != null) {
            list.add(logEntry(cur, n));
        }
        tag.put("Logs", list);
        CompoundTag fluid = new CompoundTag();
        this.tank.writeToNBT(fluid);
        tag.put("Fluid", fluid);
        VehicleStorage.save(this.storage, tag);
    }

    private static CompoundTag logEntry(Item item, int n) {
        CompoundTag c = new CompoundTag();
        c.putString("id", String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
        c.putInt("n", n);
        return c;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
