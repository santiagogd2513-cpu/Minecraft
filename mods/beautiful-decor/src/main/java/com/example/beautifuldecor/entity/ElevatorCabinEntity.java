package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.ElevatorControllerBlock;
import com.example.beautifuldecor.block.ElevatorDisplayBlock;
import com.example.beautifuldecor.block.ElevatorDoorBlock;
import com.example.beautifuldecor.block.entity.ElevatorControllerBlockEntity;
import com.example.beautifuldecor.client.ElevatorClientHooks;
import com.example.beautifuldecor.elevator.ElevatorLines;
import com.example.beautifuldecor.elevator.ElevatorMotion;
import com.example.beautifuldecor.elevator.ElevatorZone;
import com.example.beautifuldecor.init.ModEntities;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cabina del ascensor en viaje (V2.2.0).
 * <p>
 * Mientras el ascensor está parado, sus bloques son bloques normales del mundo. Cuando sale de un piso, todos los
 * bloques de la zona pasan a esta entidad (con su block entity, si tienen), que sube o baja de forma continua a la
 * velocidad configurada: se ve suave y no hace falta mover bloque por bloque. Al llegar, los bloques vuelven al mundo
 * en el piso de destino.
 * <p>
 * Físicamente: el suelo de la cabina es la caja de colisión de la entidad (se puede pisar y saltar encima); todo lo que
 * está dentro de la zona (jugadores, mobs, ítems) se arrastra con ella, como hace el shulker. El movimiento es
 * determinista, así que cliente y servidor lo calculan igual sin mandarse la posición en cada tick.
 */
public class ElevatorCabinEntity extends Entity implements IEntityAdditionalSpawnData {
    /** Máximo de celdas de una cabina (50 x 50 x 24). */
    public static final int MAX_CELLS = 60000;
    // La curva de velocidad (arranque, crucero, frenado, último paso al llegar) y el "estar de pie sobre la cabina" están en ElevatorMotion
    // (V3.3.1 y V3.3.2).

    /** V3.3.2: el servidor avisa al cliente de que la cabina dio la vuelta (un bloque en el camino): si no, el cliente seguiría subiendo. */
    private static final EntityDataAccessor<Boolean> DATA_RETURNING =
            SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * V3.3.3: altura (base) donde la cabina ya volvió a ser bloques; {@link #NOT_LANDED} mientras viaja. Este dato le llega al cliente justo
     * DESPUÉS de los bloques nuevos (el servidor manda primero los cambios de bloques y luego los datos de las entidades), así que es el
     * momento exacto para dejar de dibujar la cabina y subir al jugador a la superficie del piso nuevo.
     */
    private static final EntityDataAccessor<Integer> DATA_LANDED_Y =
            SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.INT);
    private static final int NOT_LANDED = Integer.MIN_VALUE;

    // ---- geometría
    private int minX;
    private int minZ;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private double floorTop;
    private BlockState[] palette = {Blocks.AIR.defaultBlockState()};
    /** Índice en la paleta de cada celda: (y * sizeZ + z) * sizeX + x. */
    private int[] cells = new int[0];
    /** Solo en el cliente: índices de las celdas con bloque, para dibujar. */
    private int[] drawList = new int[0];
    private final Map<Integer, CompoundTag> blockEntities = new HashMap<>();

    // ---- movimiento
    /**
     * V3.4.0: eje del viaje (ElevatorLines.AXIS_X, AXIS_Y o AXIS_Z). startY, targetY y homeY son coordenadas EN ESE EJE (los nombres son de
     * cuando la cabina solo subía y bajaba): la Y de su base si va arriba-abajo, la X o la Z de su esquina si va de lado.
     */
    private int axis = ElevatorLines.AXIS_Y;
    private double startY;
    private double targetY;
    private double homeY;
    private float speed;
    private int wait;
    private boolean returning;
    /** V3.3.2: ticks de viaje que lleva la cabina (la curva de velocidad va por el tiempo); cliente y servidor cuentan igual. */
    private int moveTicks;
    /** V3.3.2: ya se puso {@link #moveTicks} al día con lo recorrido (al retomar un viaje guardado o al aparecer en un cliente a medias). */
    private boolean resumed;
    /** V3.3.3: ya volvió a ser bloques; sigue {@link #linger} ticks más (sin dibujarse) sosteniendo a quien va encima. */
    private boolean landed;
    private int linger;
    private int lastDir = 1;
    private boolean ready;
    private boolean finished;
    private boolean humStarted;
    private BlockPos sourceCtrl = BlockPos.ZERO;
    private BlockPos destCtrl = BlockPos.ZERO;

    public ElevatorCabinEntity(EntityType<? extends ElevatorCabinEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    // =====================================================================================================
    // Captura: los bloques de la zona pasan a la entidad
    // =====================================================================================================

    /** Bloques que se mueven con el ascensor (los del propio sistema y los líquidos se quedan donde están). */
    public static boolean isMovable(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        Block block = state.getBlock();
        return !(block instanceof ElevatorControllerBlock || block instanceof ElevatorDisplayBlock
                || block instanceof ElevatorDoorBlock);
    }

    /**
     * Recoge los bloques de {@code zone}, los quita del mundo y crea la cabina, parada y lista para arrancar tras
     * {@code waitTicks} ticks. No la agrega al mundo (lo hace quien la llama).
     */
    public static ElevatorCabinEntity capture(ServerLevel level, ElevatorZone zone, int targetY, float blocksPerSecond,
                                              BlockPos sourceCtrl, BlockPos destCtrl, int waitTicks) {
        return capture(level, zone, ElevatorLines.AXIS_Y, targetY, blocksPerSecond, sourceCtrl, destCtrl, waitTicks);
    }

    /**
     * V3.4.0: igual, pero la cabina viaja en el eje {@code axis} (X, Y o Z) hasta que la esquina de su zona en ese eje quede en {@code target}.
     */
    public static ElevatorCabinEntity capture(ServerLevel level, ElevatorZone zone, int axis, int target, float blocksPerSecond,
                                              BlockPos sourceCtrl, BlockPos destCtrl, int waitTicks) {
        ElevatorCabinEntity cabin = new ElevatorCabinEntity(ModEntities.ELEVATOR_CABIN.get(), level);
        int sx = zone.sizeX();
        int sy = zone.sizeY();
        int sz = zone.sizeZ();
        int[] cells = new int[sx * sy * sz];
        Map<BlockState, Integer> index = new HashMap<>();
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        index.put(palette.get(0), 0);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<BlockPos> toClear = new ArrayList<>();
        double[] layerTop = new double[sy];
        int[] layerSolid = new int[sy];

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    p.set(zone.minX() + x, zone.minY() + y, zone.minZ() + z);
                    BlockState state = level.getBlockState(p);
                    if (!isMovable(state)) {
                        continue;
                    }
                    int i = (y * sz + z) * sx + x;
                    Integer paletteIndex = index.get(state);
                    if (paletteIndex == null) {
                        paletteIndex = palette.size();
                        palette.add(state);
                        index.put(state, paletteIndex);
                    }
                    cells[i] = paletteIndex;
                    if (state.hasBlockEntity()) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be != null) {
                            cabin.blockEntities.put(i, be.saveWithFullMetadata());
                        }
                    }
                    VoxelShape shape = state.getCollisionShape(level, p);
                    if (!shape.isEmpty()) {
                        layerSolid[y]++;
                        layerTop[y] = Math.max(layerTop[y], shape.max(Direction.Axis.Y));
                    }
                    toClear.add(p.immutable());
                }
            }
        }

        // El suelo: la primera capa que cubre buena parte de la base y las capas macizas encima de ella.
        int layerCells = sx * sz;
        int first = -1;
        for (int y = 0; y < sy; y++) {
            if (layerSolid[y] * 10 >= layerCells * 6) {
                first = y;
                break;
            }
        }
        double floorTop = 0.0;
        if (first >= 0) {
            int y = first;
            while (y < sy && layerSolid[y] * 10 >= layerCells * 6) {
                floorTop = y + layerTop[y];
                y++;
            }
        }

        // Ahora sí, se quitan del mundo (sin soltar nada: el contenido de los cofres viaja con la cabina).
        for (BlockPos pos : toClear) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }

        cabin.minX = zone.minX();
        cabin.minZ = zone.minZ();
        cabin.sizeX = sx;
        cabin.sizeY = sy;
        cabin.sizeZ = sz;
        cabin.palette = palette.toArray(new BlockState[0]);
        cabin.cells = cells;
        cabin.floorTop = floorTop;
        cabin.axis = axis;
        cabin.startY = zone.minAlong(axis);
        cabin.homeY = zone.minAlong(axis);
        cabin.targetY = target;
        cabin.speed = blocksPerSecond / 20.0f;
        cabin.wait = waitTicks;
        cabin.sourceCtrl = sourceCtrl;
        cabin.destCtrl = destCtrl;
        cabin.ready = true;
        cabin.setPos(zone.minX() + sx / 2.0, zone.minY(), zone.minZ() + sz / 2.0);
        cabin.refreshBox();
        return cabin;
    }

    // =====================================================================================================
    // Datos para el renderizador
    // =====================================================================================================

    public int getSizeX() {
        return this.sizeX;
    }

    public int getSizeY() {
        return this.sizeY;
    }

    public int getSizeZ() {
        return this.sizeZ;
    }

    public int[] getCells() {
        return this.cells;
    }

    public int[] getDrawList() {
        return this.drawList;
    }

    public BlockState paletteState(int index) {
        return index >= 0 && index < this.palette.length ? this.palette[index] : this.palette[0];
    }

    public boolean isReady() {
        return this.ready;
    }

    /** V3.3.3: ya volvió a ser bloques (en el cliente: ya llegaron los bloques nuevos). No se dibuja ni suena. */
    public boolean isLanded() {
        return this.landed;
    }

    /** Velocidad actual en bloques por tick (para el volumen y el tono del zumbido). */
    public float getSpeedFraction() {
        if (!this.ready || this.wait > 0 || this.speed <= 0.0f) {
            return 0.0f;
        }
        double remaining = Math.abs(this.targetY - axisPos());
        if (remaining < ElevatorMotion.ARRIVED) {
            return 0.0f;
        }
        double distance = Math.abs(this.targetY - this.startY);
        return (float) Math.min(1.0, ElevatorMotion.speedAt(this.speed, distance, this.moveTicks) / this.speed);
    }

    /**
     * ¿Es la cabina de la columna de controladores que está en (x, z)? V3.3.0: la zona puede estar corrida lejos del controlador,
     * así que se mira la columna de sus controladores de salida y llegada; la regla de antes (zona pegada al controlador) se
     * conserva por si hay una cabina guardada de una versión anterior.
     */
    /**
     * V3.4.0: ¿esta cabina está viajando por una línea que pasa por el controlador {@code any}? Sale o va a él, o (arriba-abajo) es su
     * columna, o (de lado) está en su línea: misma altura y misma Z si va en X, misma X si va en Z.
     */
    public boolean matchesStop(BlockPos any) {
        if (!this.ready || this.landed) {
            return false;
        }
        if (any.equals(this.sourceCtrl) || any.equals(this.destCtrl)) {
            return true;
        }
        if (this.axis == ElevatorLines.AXIS_Y) {
            return matchesColumn(any.getX(), any.getZ());
        }
        if (any.getY() != this.sourceCtrl.getY()) {
            return false;
        }
        return this.axis == ElevatorLines.AXIS_X ? any.getZ() == this.sourceCtrl.getZ() : any.getX() == this.sourceCtrl.getX();
    }

    /** V3.4.0: ¿está viajando (todavía no volvió a ser bloques)? */
    public boolean isTravelling() {
        return this.ready && !this.landed;
    }

    public BlockPos getSourceController() {
        return this.sourceCtrl;
    }

    public BlockPos getDestController() {
        return this.destCtrl;
    }

    public boolean matchesColumn(int x, int z) {
        if (!this.ready || this.landed) {
            // V3.3.3: una cabina que ya volvió a ser bloques (y solo espera unos ticks para desaparecer) ya no cuenta como "en viaje"
            return false;
        }
        if ((x == this.sourceCtrl.getX() && z == this.sourceCtrl.getZ()) || (x == this.destCtrl.getX() && z == this.destCtrl.getZ())) {
            return true;
        }
        return x >= this.minX - 1 && x <= this.minX + this.sizeX && z >= this.minZ - 1 && z <= this.minZ + this.sizeZ;
    }

    // =====================================================================================================
    // Cada tick (cliente y servidor hacen lo mismo)
    // =====================================================================================================

    @Override
    public void tick() {
        super.tick();
        if (!this.ready) {
            return;
        }
        if (this.level().isClientSide) {
            pollSync();
        }
        if (this.landed) {
            tickLanded();
            return;
        }
        if (this.finished) {
            return;
        }
        refreshBox();
        if (this.wait > 0) {
            this.wait--;
            keepOnFloor(riders());
            return;
        }
        double remaining = this.targetY - axisPos();
        if (Math.abs(remaining) < ElevatorMotion.ARRIVED) {
            if (this.level().isClientSide) {
                // V3.3.3: el cliente ya llegó pero el servidor todavía no mandó los bloques: hay que seguir sosteniendo al jugador
                keepOnFloor(riders());
                return;
            }
            arrive();
            return;
        }
        if (this.level().isClientSide && !this.humStarted) {
            this.humStarted = true;
            ElevatorClientHooks.startHum(this);
        }
        this.lastDir = remaining > 0.0 ? 1 : -1;
        // V3.3.2: la velocidad sigue una curva en el tiempo (arranca desde cero, acelera suave, crucero, frena suave); ver ElevatorMotion.
        double distance = Math.abs(this.targetY - this.startY);
        double travelled = Math.abs(axisPos() - this.startY);
        if (!this.resumed) {
            // Primer tick de viaje: si la cabina ya había recorrido algo (viaje guardado, o aparece en un cliente a medias), se retoma la curva ahí.
            this.resumed = true;
            this.moveTicks = ElevatorMotion.ticksFor(this.speed, distance, travelled);
        }
        // V3.3.1: el último trozo (menos de ARRIVE_SNAP) se da de golpe y, en ese mismo tick, la cabina vuelve a ser bloques.
        boolean last = ElevatorMotion.isLastStep(this.speed, distance, travelled, this.moveTicks);
        double dy = this.lastDir * ElevatorMotion.stepFor(this.speed, distance, travelled, this.moveTicks);
        this.moveTicks++;

        if (!this.level().isClientSide && blockedAhead(dy)) {
            handleBlocked();
            return;
        }
        List<Entity> riders = riders();
        if (dy != 0.0) {
            moveBy(dy, riders);
        }
        keepOnFloor(riders);
        if (last) {
            arrive();
        }
    }

    private void moveBy(double dy, List<Entity> riders) {
        if (this.axis != ElevatorLines.AXIS_Y) {
            // V3.4.0: de lado: el suelo y luego lo que va encima, en el mismo sentido
            setAxisPos(axisPos() + dy);
            for (Entity rider : riders) {
                carrySide(rider, dy);
            }
            return;
        }
        if (dy > 0.0) {
            // Subir: primero lo que va encima, luego el suelo.
            for (Entity rider : riders) {
                carry(rider, dy);
            }
            setAxisPos(axisPos() + dy);
        } else {
            // Bajar: primero el suelo, luego lo que va encima (así cae justo encima, sin vuelo ni choque).
            setAxisPos(axisPos() + dy);
            for (Entity rider : riders) {
                carry(rider, dy);
            }
        }
    }

    /**
     * V3.4.0: lleva de lado a quien va en la cabina. Al jugador del servidor no se lo mueve: su posición la manda su cliente (que lo lleva él
     * mismo), y si el servidor también lo corriera, al comparar las dos posiciones lo creería "movido mal" y lo haría volver atrás.
     */
    private void carrySide(Entity rider, double d) {
        if (rider instanceof ServerPlayer) {
            return;
        }
        double dx = this.axis == ElevatorLines.AXIS_X ? d : 0.0;
        double dz = this.axis == ElevatorLines.AXIS_Z ? d : 0.0;
        if (d * d <= ElevatorMotion.TINY_MOVE_SQR) {
            rider.setPos(rider.getX() + dx, rider.getY(), rider.getZ() + dz);
        } else {
            rider.move(MoverType.SHULKER, new Vec3(dx, 0.0, dz));
        }
    }

    private void carry(Entity rider, double dy) {
        if (dy * dy <= ElevatorMotion.TINY_MOVE_SQR) {
            // V3.3.4: Entity.move ignora los movimientos de menos de ~0,0003 bloques, y al arrancar y frenar la curva suave da pasos así de
            // pequeños: el pasajero se quedaba un pelo atrás. Un paso tan pequeño se da exacto.
            rider.setPos(rider.getX(), rider.getY() + dy, rider.getZ());
        } else {
            rider.move(MoverType.SHULKER, new Vec3(0.0, dy, 0.0));
        }
        if (dy < 0.0) {
            rider.fallDistance = 0.0f;
        }
    }

    /**
     * V3.3.1: quien está de pie sobre la cabina (o hundido en su suelo) queda siempre justo encima del suelo, a la velocidad que sea.
     * La colisión normal busca la cabina por la posición de su centro y a veces no la encuentra (cabinas anchas, un tick con la caja
     * cambiada); entonces el jugador se hundía un poco en cada tick y, a poca velocidad, salía por debajo antes de que el movimiento
     * de la cabina lo alcanzara. Con esto no puede pasar: si los pies están en el suelo, se suben a su superficie. Al jugador del
     * servidor no se le toca: su posición la manda su cliente, que es quien hace esto con su propio jugador.
     */
    private void keepOnFloor(List<Entity> riders) {
        if (this.floorTop <= 0.0) {
            return;
        }
        double bottom = getY();
        double top = bottom + this.floorTop;
        for (Entity rider : riders) {
            if (rider instanceof ServerPlayer) {
                continue;
            }
            double feet = rider.getY();
            if (!ElevatorMotion.standing(feet, bottom, top) || !insideFootprint(rider, originX(), originZ())) {
                continue;
            }
            double lifted = ElevatorMotion.liftedFeet(feet, bottom, top);
            if (lifted > feet) {
                // V3.3.3: exacto (setPos) en vez de Entity.move, que ignora movimientos de menos de ~0,0003 bloques: así nunca queda un pelo
                // hundido en el suelo (y al llegar, un pelo dentro del bloque nuevo basta para atravesarlo)
                rider.setPos(rider.getX(), lifted, rider.getZ());
            }
            Vec3 motion = rider.getDeltaMovement();
            if (motion.y < 0.0) {
                rider.setDeltaMovement(motion.x, 0.0, motion.z);
            }
            rider.setOnGround(true);
            rider.fallDistance = 0.0f;
        }
    }

    /**
     * Quién va en la cabina. V3.3.4: la búsqueda llega hasta un bloque por encima de la cabina (ElevatorMotion.riderTop). Antes terminaba en
     * el techo de la zona: en una cabina que es solo una plataforma (alto 1) eso es justo la superficie, y quien estaba de pie encima quedaba en
     * el borde de la caja, que Minecraft no cuenta; no era pasajero (al subir temblaba, al bajar se quedaba atrás).
     */
    private List<Entity> riders() {
        double ox = originX();
        double oz = originZ();
        AABB zone = new AABB(ox, getY() + ElevatorMotion.riderBottom(), oz, ox + this.sizeX,
                getY() + ElevatorMotion.riderTop(this.sizeY, this.floorTop), oz + this.sizeZ);
        boolean client = this.level().isClientSide;
        return this.level().getEntities(this, zone, entity -> canCarry(entity, client));
    }

    private static boolean canCarry(Entity entity, boolean client) {
        if (entity instanceof ElevatorCabinEntity || entity instanceof HangingEntity || entity.isPassenger()
                || entity.noPhysics || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof Player player && player.getAbilities().flying) {
            return false;
        }
        if (client) {
            // En el cliente solo se mueve al jugador local; lo demás lo manda el servidor.
            return ElevatorClientHooks.isLocalPlayer(entity);
        }
        return true;
    }

    /** V3.4.0: esquina oeste de la cabina ahora (se mueve si va de lado; si va arriba-abajo es minX). */
    private double originX() {
        return getX() - this.sizeX / 2.0;
    }

    /** V3.4.0: esquina norte de la cabina ahora (se mueve si va de lado; si va arriba-abajo es minZ). */
    private double originZ() {
        return getZ() - this.sizeZ / 2.0;
    }

    /** V3.4.0: posición de la cabina en su eje de viaje (la Y de su base, o la X o la Z de su esquina). */
    private double axisPos() {
        return this.axis == ElevatorLines.AXIS_X ? originX() : this.axis == ElevatorLines.AXIS_Z ? originZ() : getY();
    }

    /** V3.4.0: pone la cabina en {@code value} en su eje de viaje (y su caja de colisión con ella). */
    private void setAxisPos(double value) {
        if (this.axis == ElevatorLines.AXIS_X) {
            this.setPos(value + this.sizeX / 2.0, this.getY(), this.getZ());
        } else if (this.axis == ElevatorLines.AXIS_Z) {
            this.setPos(this.getX(), this.getY(), value + this.sizeZ / 2.0);
        } else {
            this.setPos(this.getX(), value, this.getZ());
        }
        refreshBox();
    }

    private boolean insideFootprint(Entity rider, double ox, double oz) {
        return rider.getX() >= ox && rider.getX() <= ox + this.sizeX && rider.getZ() >= oz && rider.getZ() <= oz + this.sizeZ;
    }

    /** La caja de colisión es solo el suelo (ancho completo): así se puede pisar, pero se puede entrar y salir. */
    private void refreshBox() {
        double top = this.floorTop > 0.0 ? this.floorTop : 0.05;
        double ox = originX();
        double oz = originZ();
        this.setBoundingBox(new AABB(ox, getY(), oz, ox + this.sizeX, getY() + top, oz + this.sizeZ));
    }

    // =====================================================================================================
    // Servidor: obstáculos, llegada, emergencias
    // =====================================================================================================

    /** ¿Hay un bloque macizo en la capa que la cabina está por invadir? */
    /**
     * ¿Hay un bloque macizo en la capa que la cabina está por invadir? V3.4.0: en cualquier eje (la capa de arriba o de abajo si sube o baja;
     * la de delante si va de lado). Una capa de lado en un chunk sin cargar cuenta como bloqueada.
     */
    private boolean blockedAhead(double dy) {
        double pos = axisPos();
        int size = this.axis == ElevatorLines.AXIS_X ? this.sizeX : this.axis == ElevatorLines.AXIS_Z ? this.sizeZ : this.sizeY;
        int layer = ElevatorLines.leadingSlice(pos, pos + dy, size);
        if (layer == ElevatorLines.NO_SLICE) {
            return false;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (this.axis == ElevatorLines.AXIS_Y) {
            if (this.level().isOutsideBuildHeight(layer)) {
                return true;
            }
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    p.set(this.minX + x, layer, this.minZ + z);
                    BlockState state = this.level().getBlockState(p);
                    if (!state.getCollisionShape(this.level(), p).isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
        int baseY = (int) Math.round(getY());
        int across = this.axis == ElevatorLines.AXIS_X ? this.sizeZ : this.sizeX;
        for (int c = 0; c < across; c++) {
            if (this.axis == ElevatorLines.AXIS_X) {
                p.set(layer, baseY, this.minZ + c);
            } else {
                p.set(this.minX + c, baseY, layer);
            }
            if (!this.level().isLoaded(p)) {
                return true;
            }
            for (int y = 0; y < ElevatorLines.sweepHeight(this.axis, this.sizeY); y++) {
                if (this.axis == ElevatorLines.AXIS_X) {
                    p.set(layer, baseY + y, this.minZ + c);
                } else {
                    p.set(this.minX + c, baseY + y, layer);
                }
                BlockState state = this.level().getBlockState(p);
                if (!state.getCollisionShape(this.level(), p).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleBlocked() {
        if (!this.returning && Math.abs(axisPos() - this.homeY) > 1.0E-6) {
            // Primer choque: vuelve al piso del que salió.
            startReturn();
            // V3.3.2: se le avisa al cliente (datos sincronizados); antes seguía subiendo y llevaba al jugador al aire.
            this.entityData.set(DATA_RETURNING, true);
            notifyNear("message.beautifuldecor.elevator.blocked");
            return;
        }
        // No puede avanzar ni volver: se deja en el entero más cercano hacia atrás.
        int base = ElevatorLines.stuckBase(axisPos(), this.lastDir);
        this.finished = true;
        placeBlocks(base);
        notifyNear("message.beautifuldecor.elevator.stuck");
        land(base);
    }

    /** La cabina da la vuelta hacia el piso del que salió: nuevo viaje desde donde está, con la curva desde cero. */
    private void startReturn() {
        this.returning = true;
        this.startY = axisPos();
        this.targetY = this.homeY;
        this.moveTicks = 0;
        this.resumed = true;
    }

    /**
     * V3.3.2: el cliente se entera de que la cabina dio la vuelta y hace lo mismo (sin "@Override" ni en este ni en el siguiente método a
     * propósito: si el gancho no coincidiera con esta versión, simplemente no se llamaría y todo sigue como en la V3.3.1).
     */
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if ((DATA_RETURNING.equals(key) || DATA_LANDED_Y.equals(key)) && this.level().isClientSide) {
            pollSync();
        }
    }

    /**
     * V3.3.2: la posición de la cabina la calculan cliente y servidor por su cuenta; si el servidor manda un aviso de posición (lo hace al
     * cambiar los datos sincronizados) se ignora, para que la cabina del cliente no pegue un salto ni se desfase del jugador que lleva.
     */
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (!this.ready) {
            this.setPos(x, y, z);
        }
    }

    private void arrive() {
        if (this.level().isClientSide || this.finished) {
            return;
        }
        this.finished = true;
        setAxisPos(this.targetY);
        int base = (int) Math.round(this.targetY);
        placeBlocks(base);
        BlockPos controller = this.returning ? this.sourceCtrl : this.destCtrl;
        if (this.level().isLoaded(controller)
                && this.level().getBlockEntity(controller) instanceof ElevatorControllerBlockEntity ctrl) {
            ctrl.setCabinHere(true);
        }
        this.level().playSound(null, this.getX(), Math.round(getY()) + 1.0, this.getZ(), ModSounds.ELEVATOR_DING.get(),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        land(base);
    }

    // =====================================================================================================
    // V3.3.3: la llegada sin hueco
    // =====================================================================================================

    /**
     * Servidor: los bloques ya están puestos con su base en {@code baseY}. Antes la cabina se quitaba aquí mismo, y el cliente recibía el
     * "quitar la cabina" un tick ANTES que los bloques: en ese hueco el jugador caía un poquito, quedaba con los pies dentro del bloque
     * nuevo y lo atravesaba (a cualquier velocidad). Ahora la cabina se queda {@link ElevatorMotion#LINGER_TICKS} ticks más, quieta y sin
     * dibujarse, y le avisa al cliente dónde quedaron los bloques (ese aviso le llega justo después de los bloques).
     */
    private void land(int baseY) {
        this.finished = true;
        this.landed = true;
        this.linger = ElevatorMotion.LINGER_TICKS;
        setAxisPos(baseY);
        this.entityData.set(DATA_LANDED_Y, baseY);
    }

    /** Ya volvió a ser bloques: el servidor cuenta los ticks que le quedan; el cliente sigue sosteniendo al jugador sobre el piso. */
    private void tickLanded() {
        refreshBox();
        if (this.level().isClientSide) {
            keepOnFloor(riders());
            return;
        }
        this.linger--;
        if (this.linger <= 0) {
            this.discard();
        }
    }

    /**
     * Cliente: lee los datos sincronizados (dio la vuelta / ya volvió a ser bloques). Se llama en cada tick y también cuando llegan los
     * datos, así no depende de un solo gancho de Minecraft.
     */
    private void pollSync() {
        if (!this.ready) {
            return;
        }
        if (!this.returning && !this.landed && Boolean.TRUE.equals(this.entityData.get(DATA_RETURNING))) {
            startReturn();
        }
        Integer landedY = this.entityData.get(DATA_LANDED_Y);
        if (!this.landed && landedY != null && landedY != NOT_LANDED) {
            landOnClient(landedY);
        }
    }

    /**
     * Cliente: los bloques del piso nuevo ya están en el mundo del cliente. Se deja de dibujar la cabina (lo hace el renderizador con
     * {@link #isLanded()}), se la pone en su sitio final y quien iba encima queda justo sobre la superficie del piso nuevo, nunca dentro.
     */
    private void landOnClient(int baseY) {
        // (baseY es la coordenada final en el eje de viaje: la Y de la base, o la X o la Z de la esquina si iba de lado; V3.4.0)
        this.landed = true;
        double rest = baseY - axisPos();
        double ox = originX();
        double oz = originZ();
        double low = (this.axis == ElevatorLines.AXIS_Y ? Math.min(getY(), baseY) : getY()) - ElevatorMotion.LAND_WINDOW;
        double high = (this.axis == ElevatorLines.AXIS_Y ? Math.max(getY(), baseY) : getY()) + ElevatorMotion.riderTop(this.sizeY, this.floorTop);
        double x0 = this.axis == ElevatorLines.AXIS_X ? Math.min(ox, baseY) : ox;
        double z0 = this.axis == ElevatorLines.AXIS_Z ? Math.min(oz, baseY) : oz;
        double x1 = (this.axis == ElevatorLines.AXIS_X ? Math.max(ox, baseY) : ox) + this.sizeX;
        double z1 = (this.axis == ElevatorLines.AXIS_Z ? Math.max(oz, baseY) : oz) + this.sizeZ;
        List<Entity> riders = this.level().getEntities(this, new AABB(x0, low, z0, x1, high, z1), entity -> canCarry(entity, true));
        setAxisPos(baseY);
        if (this.floorTop <= 0.0) {
            return;
        }
        double finalTop = getY() + this.floorTop;
        for (Entity rider : riders) {
            if (!insideFootprint(rider, ox, oz)) {
                continue;
            }
            if (this.axis != ElevatorLines.AXIS_Y && rest != 0.0) {
                // de lado: la cabina del cliente iba un poco atrasada; quien iba encima se corre lo mismo que ella (exacto)
                rider.setPos(rider.getX() + (this.axis == ElevatorLines.AXIS_X ? rest : 0.0), rider.getY(),
                        rider.getZ() + (this.axis == ElevatorLines.AXIS_Z ? rest : 0.0));
            }
            double feet = rider.getY();
            double lifted = ElevatorMotion.landedFeet(feet, finalTop, this.axis == ElevatorLines.AXIS_Y ? rest : 0.0);
            if (lifted > feet) {
                // exacto (setPos): Entity.move ignora movimientos de menos de ~0,0003 bloques, y un pelo dentro del bloque basta para atravesarlo
                rider.setPos(rider.getX(), lifted, rider.getZ());
                Vec3 motion = rider.getDeltaMovement();
                if (motion.y < 0.0) {
                    rider.setDeltaMovement(motion.x, 0.0, motion.z);
                }
                rider.setOnGround(true);
            }
            rider.fallDistance = 0.0f;
        }
    }

    /**
     * Devuelve los bloques al mundo con su esquina en {@code base} en el eje de viaje (V3.4.0: la Y de la base si va arriba-abajo, la X o la
     * Z de la esquina si va de lado; las otras dos coordenadas, donde está).
     */
    private void placeBlocks(int base) {
        int ox = this.axis == ElevatorLines.AXIS_X ? base : (int) Math.round(originX());
        int oy = this.axis == ElevatorLines.AXIS_Y ? base : (int) Math.round(getY());
        int oz = this.axis == ElevatorLines.AXIS_Z ? base : (int) Math.round(originZ());
        placeBlocksAt(ox, oy, oz);
    }

    /** Devuelve los bloques al mundo con la esquina de la zona en {@code ox}, {@code baseY}, {@code oz}. */
    private void placeBlocksAt(int ox, int baseY, int oz) {
        Level level = this.level();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < this.sizeY; y++) {
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    int i = (y * this.sizeZ + z) * this.sizeX + x;
                    int paletteIndex = this.cells[i];
                    if (paletteIndex == 0) {
                        continue;
                    }
                    p.set(ox + x, baseY + y, oz + z);
                    if (level.isOutsideBuildHeight(p)) {
                        continue;
                    }
                    BlockState old = level.getBlockState(p);
                    if (!old.isAir() && !(old.getBlock() instanceof LiquidBlock)) {
                        level.destroyBlock(p, true);
                    }
                    level.setBlock(p, this.palette[paletteIndex], 2 | 16);
                    CompoundTag tag = this.blockEntities.get(i);
                    if (tag != null) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be != null) {
                            be.load(tag);
                            be.setChanged();
                        }
                    }
                }
            }
        }
    }

    private void notifyNear(String key) {
        if (this.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(this) < 32.0 * 32.0) {
                    player.displayClientMessage(Component.translatable(key), true);
                }
            }
        }
    }

    /** Si la matan (comando /kill, etc.) los bloques no se pierden: vuelven al mundo donde estén. */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.ready && !this.finished && reason.shouldDestroy()) {
            this.finished = true;
            placeBlocks((int) Math.round(axisPos()));
        }
        super.remove(reason);
    }

    /**
     * V3.3.1: el cliente quita la cabina cuando el servidor la quita (al llegar), y como va un poco atrasado todavía puede faltarle un
     * trecho. Ese trecho se le da al jugador local aquí mismo: así, cuando aparecen los bloques del piso, ya está justo encima y no
     * hundido en ellos. (Sin "@Override" a propósito: si el gancho no existiera en esta versión, simplemente no se llamaría.)
     */
    public void onClientRemoval() {
        // V3.3.3: si ya llegó el aviso de "volvió a ser bloques", el jugador ya quedó sobre el piso nuevo (landOnClient)
        if (!this.level().isClientSide || !this.ready || this.finished || this.landed) {
            return;
        }
        double rest = this.targetY - axisPos();
        if (Math.abs(rest) > ElevatorMotion.ARRIVED && Math.abs(rest) <= ElevatorMotion.CLIENT_CATCH_UP) {
            moveBy(rest, riders());
        }
    }

    // =====================================================================================================
    // Entidad: reglas generales
    // =====================================================================================================

    @Override
    public boolean canBeCollidedWith() {
        return this.ready && this.floorTop > 0.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RETURNING, false);
        this.entityData.define(DATA_LANDED_Y, NOT_LANDED);
    }

    // =====================================================================================================
    // Guardado en disco
    // =====================================================================================================

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!this.ready) {
            return;
        }
        tag.putInt("MinX", this.minX);
        tag.putInt("MinZ", this.minZ);
        tag.putInt("SizeX", this.sizeX);
        tag.putInt("SizeY", this.sizeY);
        tag.putInt("SizeZ", this.sizeZ);
        tag.putDouble("FloorTop", this.floorTop);
        ListTag list = new ListTag();
        for (BlockState state : this.palette) {
            list.add(writeState(state));
        }
        tag.put("Palette", list);
        tag.putIntArray("Cells", this.cells);
        ListTag entities = new ListTag();
        for (Map.Entry<Integer, CompoundTag> entry : this.blockEntities.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putInt("I", entry.getKey());
            item.put("T", entry.getValue());
            entities.add(item);
        }
        tag.put("BlockEntities", entities);
        tag.putDouble("StartY", this.startY);
        tag.putDouble("TargetY", this.targetY);
        tag.putDouble("HomeY", this.homeY);
        tag.putFloat("Speed", this.speed);
        tag.putInt("Wait", this.wait);
        tag.putBoolean("Returning", this.returning);
        tag.putInt("Axis", this.axis);
        tag.putInt("MoveTicks", this.moveTicks);
        tag.putBoolean("Landed", this.landed);
        tag.putLong("Source", this.sourceCtrl.asLong());
        tag.putLong("Dest", this.destCtrl.asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.contains("Cells")) {
            return;
        }
        this.minX = tag.getInt("MinX");
        this.minZ = tag.getInt("MinZ");
        this.sizeX = tag.getInt("SizeX");
        this.sizeY = tag.getInt("SizeY");
        this.sizeZ = tag.getInt("SizeZ");
        this.floorTop = tag.getDouble("FloorTop");
        ListTag list = tag.getList("Palette", Tag.TAG_COMPOUND);
        this.palette = new BlockState[Math.max(1, list.size())];
        this.palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < list.size(); i++) {
            this.palette[i] = readState(list.getCompound(i));
        }
        this.cells = tag.getIntArray("Cells");
        this.blockEntities.clear();
        ListTag entities = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag item = entities.getCompound(i);
            this.blockEntities.put(item.getInt("I"), item.getCompound("T"));
        }
        this.startY = tag.getDouble("StartY");
        this.targetY = tag.getDouble("TargetY");
        this.homeY = tag.getDouble("HomeY");
        this.speed = tag.getFloat("Speed");
        this.wait = tag.getInt("Wait");
        this.returning = tag.getBoolean("Returning");
        // V3.4.0: las cabinas guardadas por una versión anterior no traen eje: subían y bajaban
        int savedAxis = tag.contains("Axis") ? tag.getInt("Axis") : ElevatorLines.AXIS_Y;
        this.axis = savedAxis >= ElevatorLines.AXIS_X && savedAxis <= ElevatorLines.AXIS_Z ? savedAxis : ElevatorLines.AXIS_Y;
        this.entityData.set(DATA_RETURNING, this.returning);
        // V3.3.2: sin "MoveTicks" (cabina guardada por una versión anterior) o al retomar, la curva se pone al día con lo recorrido en el primer tick
        this.moveTicks = tag.getInt("MoveTicks");
        this.resumed = false;
        // V3.3.3: guardada justo en los ticks de después de llegar: sus bloques ya están en el mundo; solo falta quitarla (sin volver a ponerlos)
        if (tag.getBoolean("Landed")) {
            this.finished = true;
            this.landed = true;
            this.linger = 1;
        }
        this.sourceCtrl = BlockPos.of(tag.getLong("Source"));
        this.destCtrl = BlockPos.of(tag.getLong("Dest"));
        this.ready = this.cells.length == this.sizeX * this.sizeY * this.sizeZ && this.cells.length > 0;
        buildDrawList();
        if (this.ready) {
            refreshBox();
        }
    }

    private static CompoundTag writeState(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        CompoundTag props = new CompoundTag();
        for (Property<?> property : state.getProperties()) {
            props.putString(property.getName(), propertyValue(state, property));
        }
        tag.put("Props", props);
        return tag;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static BlockState readState(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
        Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        CompoundTag props = tag.getCompound("Props");
        for (String key : props.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            if (property != null) {
                state = withProperty(state, property, props.getString(key));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }

    // =====================================================================================================
    // Red: al aparecer en un cliente se le manda toda la cabina (paleta + celdas comprimidas)
    // =====================================================================================================

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeBoolean(this.ready);
        if (!this.ready) {
            return;
        }
        buf.writeInt(this.minX);
        buf.writeInt(this.minZ);
        buf.writeVarInt(this.sizeX);
        buf.writeVarInt(this.sizeY);
        buf.writeVarInt(this.sizeZ);
        buf.writeDouble(this.floorTop);
        buf.writeByte(this.axis);
        buf.writeDouble(this.startY);
        buf.writeDouble(this.targetY);
        buf.writeDouble(this.homeY);
        buf.writeBoolean(this.returning);
        buf.writeFloat(this.speed);
        buf.writeVarInt(this.wait);
        buf.writeVarInt(this.palette.length);
        for (BlockState state : this.palette) {
            buf.writeVarInt(Block.getId(state));
        }
        // celdas: pares (índice de paleta, repeticiones); el aire seguido casi no ocupa nada
        int runs = 0;
        for (int i = 0; i < this.cells.length; i++) {
            if (i == 0 || this.cells[i] != this.cells[i - 1]) {
                runs++;
            }
        }
        buf.writeVarInt(runs);
        int i = 0;
        while (i < this.cells.length) {
            int value = this.cells[i];
            int j = i;
            while (j < this.cells.length && this.cells[j] == value) {
                j++;
            }
            buf.writeVarInt(value);
            buf.writeVarInt(j - i);
            i = j;
        }
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return;
        }
        this.minX = buf.readInt();
        this.minZ = buf.readInt();
        this.sizeX = buf.readVarInt();
        this.sizeY = buf.readVarInt();
        this.sizeZ = buf.readVarInt();
        this.floorTop = buf.readDouble();
        int readAxis = buf.readByte();
        this.axis = readAxis >= ElevatorLines.AXIS_X && readAxis <= ElevatorLines.AXIS_Z ? readAxis : ElevatorLines.AXIS_Y;
        this.startY = buf.readDouble();
        this.targetY = buf.readDouble();
        // V3.3.2: el piso de salida y si ya dio la vuelta viajan con la cabina (un cliente que llega a mitad de la vuelta la retoma bien)
        this.homeY = buf.readDouble();
        this.returning = buf.readBoolean();
        this.speed = buf.readFloat();
        this.wait = buf.readVarInt();
        int paletteSize = buf.readVarInt();
        this.palette = new BlockState[Math.max(1, paletteSize)];
        this.palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < paletteSize; i++) {
            this.palette[i] = Block.stateById(buf.readVarInt());
        }
        int total = this.sizeX * this.sizeY * this.sizeZ;
        this.cells = new int[total];
        int runs = buf.readVarInt();
        int at = 0;
        for (int r = 0; r < runs; r++) {
            int value = buf.readVarInt();
            int length = buf.readVarInt();
            for (int k = 0; k < length && at < total; k++) {
                this.cells[at++] = value;
            }
        }
        this.ready = total > 0;
        buildDrawList();
        if (this.ready) {
            refreshBox();
        }
    }

    private void buildDrawList() {
        int count = 0;
        for (int cell : this.cells) {
            if (cell != 0) {
                count++;
            }
        }
        this.drawList = new int[count];
        int at = 0;
        for (int i = 0; i < this.cells.length; i++) {
            if (this.cells[i] != 0) {
                this.drawList[at++] = i;
            }
        }
    }

    /** Evita que un jugador quede "dentro" de la cabina por su caja de colisión al reaparecer: nada especial. */
    @Nullable
    public ElevatorZone currentZone() {
        if (!this.ready) {
            return null;
        }
        return new ElevatorZone((int) Math.round(originX()), (int) Math.floor(getY()), (int) Math.round(originZ()), this.sizeX, this.sizeY, this.sizeZ);
    }
}
