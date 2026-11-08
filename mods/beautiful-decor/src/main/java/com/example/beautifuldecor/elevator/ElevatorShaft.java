package com.example.beautifuldecor.elevator;

import com.example.beautifuldecor.block.ElevatorControllerBlock;
import com.example.beautifuldecor.block.entity.ElevatorControllerBlockEntity;
import com.example.beautifuldecor.entity.ElevatorCabinEntity;
import com.example.beautifuldecor.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lógica del ascensor (V2.2.0), toda del lado del servidor.
 * <p>
 * Un ascensor es la columna de controladores que comparten x y z. Cada controlador es un piso (de abajo hacia arriba:
 * piso 1, 2, 3...), sin límite de pisos ni de distancia entre ellos más allá de la altura del mundo.
 * <p>
 * V3.4.0: también líneas horizontales. Los controladores a la misma altura y en línea recta (misma Z: línea en X; misma X: línea en Z), a
 * hasta {@link ElevatorLines#ROW_RANGE} bloques, con la misma cabina (tamaño, dirección y desplazamiento) y sin bloques en medio quedan
 * conectados: la cabina va de lado entre ellos. Cada viaje es en línea recta; un controlador puede tener pisos en su columna y paradas a
 * los lados.
 */
public final class ElevatorShaft {
    /** Ticks que la cabina espera antes de arrancar (para que las puertas alcancen a cerrarse). */
    public static final int DEPARTURE_WAIT = 24;
    /**
     * Hasta dónde (en x y z, desde la columna del controlador) se busca la cabina en viaje: la zona puede estar corrida hasta
     * {@link ElevatorControllerBlockEntity#MAX_OFFSET} bloques y medir hasta 50, así que el centro puede quedar a ~90 bloques.
     */
    private static final int CABIN_SEARCH = 140 + ElevatorLines.ROW_RANGE;
    /** Cuántos controladores, como mucho, se juntan al repartir los ajustes por una red de columnas y líneas. */
    private static final int MAX_NETWORK = 256;

    private ElevatorShaft() {
    }

    /** Un piso para mostrar en la pantalla. */
    public record Floor(BlockPos pos, String name, boolean cabinHere) {
    }

    /** V3.4.0: una parada para la pantalla: un piso de la columna ({@code axis} = Y) o una parada de una línea de lado (X o Z). */
    public record Stop(BlockPos pos, String name, boolean cabinHere, int axis) {
    }

    // ------------------------------------------------------------------------------------------ pisos

    /** Controladores de la columna de {@code any}, de abajo hacia arriba. */
    public static List<BlockPos> column(Level level, BlockPos any) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(any.getX(), 0, any.getZ());
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            p.setY(y);
            if (level.getBlockState(p).is(ModBlocks.ELEVATOR_CONTROLLER.get())) {
                result.add(p.immutable());
            }
        }
        return result;
    }

    public static String defaultName(int index) {
        return "Piso " + (index + 1);
    }

    /** Pisos de la columna de {@code any}, de abajo hacia arriba, con su nombre (el escrito o "Piso N"). */
    public static List<Floor> floors(Level level, BlockPos any) {
        List<Floor> floors = new ArrayList<>();
        List<BlockPos> column = column(level, any);
        for (int i = 0; i < column.size(); i++) {
            BlockPos pos = column.get(i);
            if (level.getBlockEntity(pos) instanceof ElevatorControllerBlockEntity be) {
                String name = be.getFloorName().isEmpty() ? defaultName(i) : be.getFloorName();
                floors.add(new Floor(pos, name, be.isCabinHere()));
            }
        }
        return floors;
    }

    // ------------------------------------------------------------------------------------------ líneas de lado (V3.4.0)

    /**
     * Controladores de la línea horizontal de {@code any} en el eje {@code axis} (X o Z): misma altura y misma Z (o X), hasta
     * {@link ElevatorLines#ROW_RANGE} bloques hacia cada lado, ordenados de oeste a este (o de norte a sur). Solo mira chunks cargados.
     */
    public static List<BlockPos> row(Level level, BlockPos any, int axis) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int center = axis == ElevatorLines.AXIS_X ? any.getX() : any.getZ();
        for (int c = center - ElevatorLines.ROW_RANGE; c <= center + ElevatorLines.ROW_RANGE; c++) {
            if (axis == ElevatorLines.AXIS_X) {
                p.set(c, any.getY(), any.getZ());
            } else {
                p.set(any.getX(), any.getY(), c);
            }
            if (!level.isLoaded(p)) {
                continue;
            }
            if (level.getBlockState(p).is(ModBlocks.ELEVATOR_CONTROLLER.get())) {
                result.add(p.immutable());
            }
        }
        return result;
    }

    /** La línea de {@code any} en el eje {@code axis}: su columna (Y) o su línea de lado (X o Z). */
    public static List<BlockPos> line(Level level, BlockPos any, int axis) {
        return axis == ElevatorLines.AXIS_Y ? column(level, any) : row(level, any, axis);
    }

    /** ¿Qué línea comparten dos controladores? (ver {@link ElevatorLines#lineAxis}) */
    public static int lineAxis(BlockPos a, BlockPos b) {
        return ElevatorLines.lineAxis(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    /** ¿Tienen la misma cabina? (dirección, ancho, fondo, alto y desplazamiento iguales: así la zona de uno, corrida, es la del otro) */
    public static boolean sameCabin(ElevatorControllerBlockEntity a, ElevatorControllerBlockEntity b) {
        return a.getFacing() == b.getFacing() && a.getWidth() == b.getWidth() && a.getDepth() == b.getDepth() && a.getHeight() == b.getHeight()
                && a.getOffsetX() == b.getOffsetX() && a.getOffsetY() == b.getOffsetY() && a.getOffsetZ() == b.getOffsetZ();
    }

    /**
     * ¿Están conectados de lado {@code a} y {@code b}? Comparten una línea horizontal (cerca), tienen la misma cabina y entre sus zonas no hay
     * bloques macizos (sin contar las zonas de las paradas de esa línea donde está la cabina, que se mueve con ella).
     */
    public static boolean linked(Level level, BlockPos a, BlockPos b) {
        int axis = lineAxis(a, b);
        if (axis != ElevatorLines.AXIS_X && axis != ElevatorLines.AXIS_Z || !ElevatorLines.inRange(axis, a.getX(), a.getZ(), b.getX(), b.getZ())) {
            return false;
        }
        ElevatorControllerBlockEntity ca = controller(level, a);
        ElevatorControllerBlockEntity cb = controller(level, b);
        if (ca == null || cb == null || !sameCabin(ca, cb)) {
            return false;
        }
        List<ElevatorZone> parked = new ArrayList<>();
        for (BlockPos stop : row(level, a, axis)) {
            ElevatorControllerBlockEntity be = controller(level, stop);
            if (be != null && be.isCabinHere()) {
                parked.add(be.zone());
            }
        }
        return sweepIsClear(level, ca.zone(), cb.zone(), parked, ElevatorLines.sweepHeight(axis, ca.zone().sizeY()));
    }

    /**
     * V3.4.0: las paradas que muestra la pantalla del controlador {@code own}, en el orden de la pantalla: primero los pisos de su columna (de
     * arriba hacia abajo, como un tablero de ascensor) y luego las paradas de lado conectadas (línea en X de oeste a este, luego línea en Z de
     * norte a sur). El nombre es el escrito o "Piso N" (columna) / "Parada N" (línea, contando a lo largo de ella).
     */
    public static List<Stop> stops(Level level, BlockPos own) {
        List<Stop> stops = new ArrayList<>();
        List<BlockPos> column = column(level, own);
        for (int i = column.size() - 1; i >= 0; i--) {
            BlockPos pos = column.get(i);
            ElevatorControllerBlockEntity be = controller(level, pos);
            if (be != null) {
                stops.add(new Stop(pos, be.getFloorName().isEmpty() ? defaultName(i) : be.getFloorName(), be.isCabinHere(), ElevatorLines.AXIS_Y));
            }
        }
        for (int axis : new int[]{ElevatorLines.AXIS_X, ElevatorLines.AXIS_Z}) {
            List<BlockPos> row = row(level, own, axis);
            for (int i = 0; i < row.size(); i++) {
                BlockPos pos = row.get(i);
                if (pos.equals(own) || !linked(level, own, pos)) {
                    continue;
                }
                ElevatorControllerBlockEntity be = controller(level, pos);
                if (be != null) {
                    stops.add(new Stop(pos, be.getFloorName().isEmpty() ? "Parada " + (i + 1) : be.getFloorName(), be.isCabinHere(), axis));
                }
            }
        }
        return stops;
    }

    /**
     * La red de {@code start}: su columna, las paradas de lado conectadas, las columnas de esas paradas, y así (hasta {@link #MAX_NETWORK}).
     * Se calcula con los ajustes de antes de cambiarlos, para repartirlos a todo lo que estaba conectado.
     */
    public static Set<BlockPos> network(Level level, BlockPos start) {
        Set<BlockPos> seen = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_NETWORK) {
            BlockPos node = queue.poll();
            List<BlockPos> next = new ArrayList<>(column(level, node));
            for (int axis : new int[]{ElevatorLines.AXIS_X, ElevatorLines.AXIS_Z}) {
                for (BlockPos other : row(level, node, axis)) {
                    if (!seen.contains(other) && linked(level, node, other)) {
                        next.add(other);
                    }
                }
            }
            for (BlockPos other : next) {
                if (seen.size() >= MAX_NETWORK) {
                    break;
                }
                if (seen.add(other)) {
                    queue.add(other);
                }
            }
        }
        return seen;
    }

    @Nullable
    public static ElevatorControllerBlockEntity controller(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ElevatorControllerBlockEntity be ? be : null;
    }

    // ------------------------------------------------------------------------------------------ cabina en viaje

    /** La cabina que está viajando por una línea de {@code any} (su columna o, V3.4.0, una línea de lado que pasa por él), si hay una. */
    @Nullable
    public static ElevatorCabinEntity travellingCabin(Level level, BlockPos any) {
        AABB box = new AABB(any.getX() - CABIN_SEARCH, level.getMinBuildHeight(), any.getZ() - CABIN_SEARCH,
                any.getX() + CABIN_SEARCH + 1, level.getMaxBuildHeight(), any.getZ() + CABIN_SEARCH + 1);
        List<ElevatorCabinEntity> found = level.getEntitiesOfClass(ElevatorCabinEntity.class, box, cabin -> cabin.matchesStop(any));
        return found.isEmpty() ? null : found.get(0);
    }

    /** V3.4.0: ¿hay una cabina en viaje que sale de, o va a, un controlador en alguna línea de {@code any}? (para no inventar otra cabina) */
    private static boolean cabinHeadingNear(Level level, BlockPos any) {
        AABB box = new AABB(any.getX() - CABIN_SEARCH, level.getMinBuildHeight(), any.getZ() - CABIN_SEARCH,
                any.getX() + CABIN_SEARCH + 1, level.getMaxBuildHeight(), any.getZ() + CABIN_SEARCH + 1);
        return !level.getEntitiesOfClass(ElevatorCabinEntity.class, box, cabin -> cabin.isTravelling()
                && (cabin.matchesStop(any) || lineAxis(cabin.getSourceController(), any) != ElevatorLines.NO_LINE
                || lineAxis(cabin.getDestController(), any) != ElevatorLines.NO_LINE)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ ajustes y alta

    /**
     * Guarda los ajustes de un controlador y los reparte a todos los de su columna (tamaño, desplazamiento, velocidad y dirección
     * son comunes; el nombre es solo de este piso). No se puede cambiar mientras la cabina viaja.
     */
    public static void applySettings(ServerLevel level, BlockPos pos, Direction facing, int width, int depth, int height,
                                     int offX, int offY, int offZ, float speed, String name, @Nullable ServerPlayer who) {
        ElevatorControllerBlockEntity self = controller(level, pos);
        if (self == null) {
            return;
        }
        if (travellingCabin(level, pos) != null) {
            tell(who, "message.beautifuldecor.elevator.busy_settings");
            return;
        }
        self.setFloorName(name);
        // V3.4.0: a toda la red (la columna y las paradas de lado conectadas, con los ajustes de ANTES): así la línea sigue conectada
        Set<BlockPos> group = network(level, pos);
        for (BlockPos other : group) {
            ElevatorControllerBlockEntity be = controller(level, other);
            if (be == null) {
                continue;
            }
            be.setShape(width, depth, height, speed);
            be.setOffset(offX, offY, offZ);
        }
        if (facing.getAxis().isHorizontal()) {
            for (BlockPos other : group) {
                BlockState state = level.getBlockState(other);
                if (state.getValue(ElevatorControllerBlock.FACING) != facing) {
                    level.setBlock(other, state.setValue(ElevatorControllerBlock.FACING, facing), 3);
                }
            }
        }
        tell(who, "message.beautifuldecor.elevator.saved");
    }

    /**
     * Un controlador nuevo se une al ascensor de su columna: copia tamaño, velocidad y dirección del más cercano. Si es el
     * primero de la columna, la cabina se considera en este piso (es donde se construye).
     */
    public static void joinColumn(ServerLevel level, BlockPos newPos) {
        ElevatorControllerBlockEntity self = controller(level, newPos);
        if (self == null) {
            return;
        }
        BlockPos nearest = null;
        for (BlockPos other : column(level, newPos)) {
            if (!other.equals(newPos) && (nearest == null
                    || Math.abs(other.getY() - newPos.getY()) < Math.abs(nearest.getY() - newPos.getY()))) {
                nearest = other;
            }
        }
        if (nearest == null) {
            // V3.4.0: sin pisos en su columna, se une a la línea de lado más cercana (copia la cabina; la cabina sigue donde estaba)
            for (int axis : new int[]{ElevatorLines.AXIS_X, ElevatorLines.AXIS_Z}) {
                for (BlockPos other : row(level, newPos, axis)) {
                    if (!other.equals(newPos) && (nearest == null || newPos.distManhattan(other) < newPos.distManhattan(nearest))) {
                        nearest = other;
                    }
                }
            }
            if (nearest == null) {
                self.setCabinHereSilently(true);
                return;
            }
        }
        ElevatorControllerBlockEntity ref = controller(level, nearest);
        if (ref != null) {
            self.copyShapeFrom(ref);
            BlockState state = level.getBlockState(newPos);
            Direction facing = level.getBlockState(nearest).getValue(ElevatorControllerBlock.FACING);
            if (state.getValue(ElevatorControllerBlock.FACING) != facing) {
                level.setBlock(newPos, state.setValue(ElevatorControllerBlock.FACING, facing), 3);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ viaje

    /** Manda la cabina al piso del controlador {@code targetPos} (llamada desde su propia columna). */
    public static void requestTrip(ServerLevel level, BlockPos targetPos, @Nullable ServerPlayer who) {
        requestTrip(level, targetPos, null, who);
    }

    /**
     * Manda la cabina al controlador {@code targetPos}, pedida desde una pantalla vinculada al controlador {@code callerPos} (V3.4.0: puede
     * estar en su columna o en una línea de lado). La cabina se busca primero en la línea que comparten los dos, luego en las otras líneas
     * del destino; el viaje es siempre en línea recta.
     */
    public static void requestTrip(ServerLevel level, BlockPos targetPos, @Nullable BlockPos callerPos, @Nullable ServerPlayer who) {
        ElevatorControllerBlockEntity target = controller(level, targetPos);
        if (target == null) {
            tell(who, "message.beautifuldecor.elevator.missing");
            return;
        }
        if (travellingCabin(level, targetPos) != null || (callerPos != null && travellingCabin(level, callerPos) != null)) {
            tell(who, "message.beautifuldecor.elevator.busy");
            return;
        }
        int shared = callerPos == null ? ElevatorLines.AXIS_Y : lineAxis(callerPos, targetPos);
        if (shared == ElevatorLines.NO_LINE) {
            shared = ElevatorLines.AXIS_Y;
        }
        // 1) la cabina en la línea que comparten la pantalla y el destino; 2) si no hay, buscando sus bloques en esa línea
        BlockPos sourcePos = nearestCabin(level, targetPos, target, line(level, targetPos, shared));
        if (sourcePos == null) {
            sourcePos = cabinByBlocks(level, target, line(level, targetPos, shared), shared);
        }
        // 3) la cabina en otra línea del destino (por ejemplo, está en una parada de lado y la pides desde la columna)
        if (sourcePos == null) {
            for (int axis : new int[]{ElevatorLines.AXIS_Y, ElevatorLines.AXIS_X, ElevatorLines.AXIS_Z}) {
                if (axis != shared) {
                    sourcePos = nearestCabin(level, targetPos, target, line(level, targetPos, axis));
                    if (sourcePos != null) {
                        break;
                    }
                }
            }
        }
        if (sourcePos == null) {
            if (cabinHeadingNear(level, targetPos)) {
                tell(who, "message.beautifuldecor.elevator.busy");
                return;
            }
            if (networkHasCabin(level, targetPos)) {
                // V3.4.0: la cabina está en otra línea de la red (no hace esquinas): no se inventa otra aquí
                tell(who, "message.beautifuldecor.elevator.not_straight");
                return;
            }
            // Sin cabina en ningún lado (por ejemplo, quedó atascada lejos): se considera aquí.
            sourcePos = targetPos;
            target.setCabinHereSilently(true);
        }
        if (sourcePos.equals(targetPos)) {
            tell(who, "message.beautifuldecor.elevator.here");
            return;
        }
        ElevatorControllerBlockEntity source = controller(level, sourcePos);
        if (source == null) {
            tell(who, "message.beautifuldecor.elevator.missing");
            return;
        }
        int axis = lineAxis(sourcePos, targetPos);
        if (axis == ElevatorLines.NO_LINE) {
            tell(who, "message.beautifuldecor.elevator.missing");
            return;
        }
        if (axis != ElevatorLines.AXIS_Y && !sameCabin(source, target)) {
            tell(who, "message.beautifuldecor.elevator.mismatch");
            return;
        }

        ElevatorZone from = source.zone();
        ElevatorZone to = from.shifted(targetPos.getX() - sourcePos.getX(), targetPos.getY() - sourcePos.getY(),
                targetPos.getZ() - sourcePos.getZ());
        if (to.minY() < level.getMinBuildHeight() || to.maxY() >= level.getMaxBuildHeight()) {
            tell(who, "message.beautifuldecor.elevator.out_of_world");
            return;
        }
        for (int cx = Math.min(from.minX(), to.minX()) >> 4; cx <= Math.max(from.maxX(), to.maxX()) >> 4; cx++) {
            for (int cz = Math.min(from.minZ(), to.minZ()) >> 4; cz <= Math.max(from.maxZ(), to.maxZ()) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    tell(who, "message.beautifuldecor.elevator.unloaded");
                    return;
                }
            }
        }
        if (!pathIsClear(level, from, to, axis)) {
            tell(who, axis == ElevatorLines.AXIS_Y ? "message.beautifuldecor.elevator.blocked" : "message.beautifuldecor.elevator.blocked_side");
            return;
        }

        source.setCabinHere(false);
        ElevatorCabinEntity cabin = ElevatorCabinEntity.capture(level, from, axis, to.minAlong(axis), source.getSpeed(),
                sourcePos, targetPos, DEPARTURE_WAIT);
        level.addFreshEntity(cabin);
    }

    /** V3.4.0: ¿hay una parada con la cabina en algún lugar de la red de {@code any} (columnas y líneas conectadas)? */
    private static boolean networkHasCabin(Level level, BlockPos any) {
        for (BlockPos pos : network(level, any)) {
            ElevatorControllerBlockEntity be = controller(level, pos);
            if (be != null && be.isCabinHere()) {
                return true;
            }
        }
        return false;
    }

    /** La parada con la cabina en {@code line} más cercana al destino (en una línea de lado, solo las que tienen la misma cabina). */
    @Nullable
    private static BlockPos nearestCabin(Level level, BlockPos targetPos, ElevatorControllerBlockEntity target, List<BlockPos> line) {
        BlockPos best = null;
        for (BlockPos pos : line) {
            ElevatorControllerBlockEntity be = controller(level, pos);
            if (be == null || !be.isCabinHere()) {
                continue;
            }
            int axis = lineAxis(pos, targetPos);
            if (axis == ElevatorLines.NO_LINE || axis != ElevatorLines.AXIS_Y && !pos.equals(targetPos) && !sameCabin(be, target)) {
                continue;
            }
            if (best == null || pos.distManhattan(targetPos) < best.distManhattan(targetPos)) {
                best = pos;
            }
        }
        return best;
    }

    /** Sin cabina marcada (por ejemplo, quedó atascada): la parada de {@code line} donde hay bloques de cabina, que se marca. */
    @Nullable
    private static BlockPos cabinByBlocks(Level level, ElevatorControllerBlockEntity target, List<BlockPos> line, int axis) {
        int best = 0;
        BlockPos found = null;
        for (BlockPos pos : line) {
            ElevatorControllerBlockEntity be = controller(level, pos);
            if (be == null || axis != ElevatorLines.AXIS_Y && !sameCabin(be, target)) {
                continue;
            }
            int count = countMovable(level, be.zone(), 8);
            if (count > best) {
                best = count;
                found = pos;
            }
        }
        if (found != null) {
            ElevatorControllerBlockEntity be = controller(level, found);
            if (be != null) {
                be.setCabinHereSilently(true);
            }
        }
        return found;
    }

    /**
     * ¿No hay bloques macizos (fuera de la propia cabina) entre la zona de salida y la de llegada? V3.4.0: en cualquier eje (es la caja que
     * cubre las dos zonas, que van en línea recta).
     */
    private static boolean pathIsClear(ServerLevel level, ElevatorZone from, ElevatorZone to, int axis) {
        return sweepIsClear(level, from, to, List.of(from), ElevatorLines.sweepHeight(axis, from.sizeY()));
    }

    /**
     * ¿Está libre la caja que cubre {@code a} y {@code b} (en línea recta), sin contar las celdas de las zonas {@code skip} (donde está la
     * cabina, que se mueve con ella)? Si un chunk del camino no está cargado, no se puede saber: cuenta como no libre. Se revisan
     * {@code height} capas desde la base (de lado, también el sitio de quien va de pie encima: ElevatorLines.sweepHeight).
     */
    private static boolean sweepIsClear(Level level, ElevatorZone a, ElevatorZone b, List<ElevatorZone> skip, int height) {
        int xMin = Math.min(a.minX(), b.minX());
        int xMax = Math.max(a.maxX(), b.maxX());
        int yMin = Math.min(a.minY(), b.minY());
        int yMax = Math.max(a.minY(), b.minY()) + Math.max(1, height) - 1;
        int zMin = Math.min(a.minZ(), b.minZ());
        int zMax = Math.max(a.maxZ(), b.maxZ());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = zMin; z <= zMax; z++) {
            for (int x = xMin; x <= xMax; x++) {
                p.set(x, yMin, z);
                if (!level.isLoaded(p)) {
                    return false;
                }
                for (int y = yMin; y <= yMax; y++) {
                    if (inAny(skip, x, y, z)) {
                        continue;
                    }
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (!state.getCollisionShape(level, p).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean inAny(List<ElevatorZone> zones, int x, int y, int z) {
        for (ElevatorZone zone : zones) {
            if (zone.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Cuenta hasta {@code cap} bloques movibles dentro de la zona. */
    private static int countMovable(Level level, ElevatorZone zone, int cap) {
        int count = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = zone.minY(); y <= zone.maxY() && count < cap; y++) {
            for (int z = zone.minZ(); z <= zone.maxZ() && count < cap; z++) {
                for (int x = zone.minX(); x <= zone.maxX() && count < cap; x++) {
                    p.set(x, y, z);
                    if (ElevatorCabinEntity.isMovable(level.getBlockState(p))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void tell(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
