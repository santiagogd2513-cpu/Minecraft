package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.FreightRailBlock;
import com.example.beautifuldecor.block.entity.StationControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * V6.0.0: la ruta de un tren automático: la lista de estaciones (controladores) y en qué punto va. La última estación es el depósito.
 * Aquí está la máquina de estados: VIAJE (va a la parada), ESPERA (parado el tiempo de la estación) y ESTACIONADO (en el depósito
 * cerrado, hasta que abre). Cada tick dice al tren si acelerar, frenar o sujetarse; las distancias vienen de {@link TrainAuto}.
 */
public final class TrainRoute {
    public enum State { TRAVEL, DWELL, PARKED }

    public static final int MAX_STOPS = 32;
    /** Metros que tiene que recorrer después de salir antes de aceptar una llegada (para no "llegar" a la estación de la que sale). */
    private static final double MIN_TRAVEL = 8.0;
    private static final int STUCK_TICKS = 80;

    /** Una parada: copia de lo que dice el controlador (por si su chunk no está cargado). */
    public static final class Stop {
        public BlockPos ctrl;
        public BlockPos rail;
        public String name = "";
        public int dwell = 20;
        public int depart = 40;
        public int travel = 80;
        public int open = 0;
        public int close = 24;

        public Stop(BlockPos ctrl, BlockPos rail) {
            this.ctrl = ctrl;
            this.rail = rail;
        }

        /** Copia los ajustes del controlador si se puede leer; false si el bloque ya no es un controlador. */
        boolean refresh(ServerLevel level) {
            if (!level.isLoaded(this.ctrl)) {
                return true;
            }
            if (level.getBlockEntity(this.ctrl) instanceof StationControllerBlockEntity be) {
                this.name = be.getStationName();
                this.dwell = be.getDwellSec();
                this.depart = be.getDepartKmh();
                this.travel = be.getTravelKmh();
                this.open = be.getOpenHour();
                this.close = be.getCloseHour();
                return true;
            }
            return false;
        }

        public String label(int index) {
            return this.name.isEmpty() ? "#" + (index + 1) : this.name;
        }
    }

    /** Lo que el tren tiene que hacer este tick. */
    public static final class Out {
        public int drive;
        public boolean brake;
        public boolean hand;
        /** Avisos para los pasajeros (null = nada). */
        public String arrived;
        public String departed;
        /** V6.2.0: aviso por el megáfono: "next" (a 200 m), "arrived", "final", "departing" (con el destino), o null. */
        public String say;
        public String sayName;
        public String sayDest;
        public boolean parked;
        public boolean stuck;
    }

    public String name = "";
    public boolean on;
    public final List<Stop> stops = new ArrayList<>();
    public int idx;
    public int from = -1;
    public State state = State.TRAVEL;
    public int dwellLeft;
    public double traveled;

    // ---- solo en ejecución
    private double dist = -1.0;
    private boolean hasDist;
    private double known;
    private int sinceLook = 99;
    private int stuckTicks;
    private final Set<Long> pathChunks = new LinkedHashSet<>();
    private boolean nextSaid;
    /** A cuántos metros de la estación se avisa de que ya viene (bloques). */
    public static final double ANNOUNCE_DIST = 200.0;

    public boolean active() {
        return this.on && !this.stops.isEmpty();
    }

    /** Hay que volver a mirar la vía (el tren cambió de mando o se movió por fuera). */
    public void invalidate() {
        this.hasDist = false;
        this.dist = -1.0;
        this.sinceLook = 99;
        this.stuckTicks = 0;
    }

    public Set<Long> pathChunks() {
        return this.pathChunks;
    }

    public void start() {
        this.on = true;
        this.idx = 0;
        this.from = -1;
        this.state = State.TRAVEL;
        this.traveled = 0.0;
        this.nextSaid = false;
        this.invalidate();
    }

    public void stop() {
        this.on = false;
        this.invalidate();
    }

    public void clear() {
        this.stops.clear();
        this.on = false;
        this.idx = 0;
        this.from = -1;
        this.state = State.TRAVEL;
        this.invalidate();
    }

    public int indexOf(BlockPos ctrl) {
        for (int i = 0; i < this.stops.size(); i++) {
            if (this.stops.get(i).ctrl.equals(ctrl)) {
                return i;
            }
        }
        return -1;
    }

    private void removeAt(int i) {
        this.stops.remove(i);
        if (this.idx > i) {
            this.idx--;
        }
        if (this.idx >= this.stops.size() || this.idx < 0) {
            this.idx = 0;
        }
        this.from = -1;
        this.state = State.TRAVEL;
        this.invalidate();
    }

    /** El riel que queda junto al controlador (el más cercano, de cualquier familia), o null. */
    public static BlockPos findRail(ServerLevel level, BlockPos ctrl) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos p = ctrl.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    if (level.getBlockState(p).getBlock() instanceof FreightRailBlock) {
                        double d = dx * dx + dz * dz + dy * dy * 0.5;
                        if (d < bestD) {
                            bestD = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Un tick de piloto automático.
     *
     * @param front      el bogie delantero de la cabeza
     * @param vTick      velocidad (bloques por tick, hacia adelante)
     * @param noseInset  lo que sobresale el morro por delante de ese bogie
     */
    public Out tick(ServerLevel level, RailPath.Cursor front, int family, double vTick, double empty, double total, double topMs, double noseInset) {
        Out out = new Out();
        out.hand = true;
        if (this.stops.isEmpty()) {
            return out;
        }
        if (this.idx >= this.stops.size() || this.idx < 0) {
            this.idx = 0;
        }
        Stop target = this.stops.get(this.idx);
        double hour = TrainAuto.hourOf(level.getDayTime());

        if (this.state == State.DWELL) {
            if (--this.dwellLeft <= 0) {
                if (this.idx == this.stops.size() - 1 && !TrainAuto.isOpen(hour, target.open, target.close)) {
                    this.state = State.PARKED;
                    out.parked = true;
                } else {
                    this.depart(out);
                }
            }
            return out;
        }
        if (this.state == State.PARKED) {
            if (target.refresh(level) && TrainAuto.isOpen(hour, target.open, target.close)) {
                this.depart(out);
            }
            return out;
        }

        // ---- VIAJE
        this.traveled += Math.max(0.0, vTick);
        if (this.hasDist) {
            this.dist -= Math.max(0.0, vTick);
        }
        boolean near = this.hasDist && this.dist < 60.0;
        if (++this.sinceLook >= (near ? 4 : 10)) {
            this.sinceLook = 0;
            if (!target.refresh(level)) {
                int gone = this.idx;
                this.removeAt(gone);
                out.stuck = this.stops.isEmpty();
                if (this.stops.isEmpty()) {
                    this.on = false;
                }
                return out;
            }
            if (target.rail == null || !(level.getBlockState(target.rail).getBlock() instanceof FreightRailBlock)) {
                BlockPos r = findRail(level, target.ctrl);
                if (r != null) {
                    target.rail = r;
                }
            }
            this.look(level, front, family, target, noseInset);
        }

        int fromIdx = this.from >= 0 && this.from < this.stops.size() ? this.from : this.idx;
        Stop cfg = this.stops.get(fromIdx);
        double cruise = Math.min(topMs, (this.traveled < TrainAuto.DEPART_ZONE ? cfg.depart : cfg.travel) / 3.6);
        double decel = TrainAuto.decel(empty, total);
        double v = vTick * 20.0;
        double tv;
        boolean haveTarget = this.hasDist && this.traveled >= MIN_TRAVEL;
        if (haveTarget) {
            tv = TrainAuto.targetSpeed(this.dist, cruise, decel);
        } else {
            double clear = this.known - TrainAuto.SAFE_GAP;
            tv = Math.min(cruise, TrainAuto.brakeSpeed(clear, decel));
        }
        int d = TrainAuto.decide(v, tv);
        out.drive = d > 0 ? 1 : 0;
        out.brake = d < 0;
        out.hand = false;

        if (haveTarget && !this.nextSaid && this.dist <= ANNOUNCE_DIST && this.idx < this.stops.size() - 1) {
            this.nextSaid = true;
            out.say = "next";
            out.sayName = target.label(this.idx);
        }
        if (haveTarget && (this.dist <= TrainAuto.ARRIVE_DIST || (this.dist < 2.0 && vTick < 0.002))) {
            out.drive = 0;
            out.brake = true;
            out.hand = true;
            out.arrived = target.label(this.idx);
            int n = this.stops.size();
            if (this.idx < n - 1) {
                out.say = this.idx == n - 2 ? "final" : "arrived";
                out.sayName = target.label(this.idx);
            }
            this.state = State.DWELL;
            this.dwellLeft = Math.max(1, target.dwell * 20);
            return out;
        }
        // ¿se quedó sin vía?
        if (!haveTarget && this.known < TrainAuto.SAFE_GAP + 4.0 && vTick < 0.003 && !this.waitingChunks) {
            if (++this.stuckTicks > STUCK_TICKS) {
                this.on = false;
                this.invalidate();
                out.stuck = true;
                out.drive = 0;
                out.hand = true;
            }
        } else {
            this.stuckTicks = 0;
        }
        return out;
    }

    private boolean waitingChunks;

    private void depart(Out out) {
        Stop here = this.stops.get(this.idx);
        out.departed = here.label(this.idx);
        int n = this.stops.size();
        if (n >= 3 && this.idx < n - 2) {
            out.say = "departing";
            out.sayName = here.label(this.idx);
            out.sayDest = this.stops.get(n - 2).label(n - 2);
        }
        this.nextSaid = false;
        this.from = this.idx;
        this.idx = (this.idx + 1) % this.stops.size();
        this.state = State.TRAVEL;
        this.traveled = 0.0;
        this.invalidate();
    }

    private void look(ServerLevel level, RailPath.Cursor front, int family, Stop target, double noseInset) {
        boolean[] unloaded = {false};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RailPath.Lookup lk = (x, y, z) -> {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) {
                unloaded[0] = true;
                return null;
            }
            BlockState state = level.getBlockState(pos);
            return FreightRailBlock.pieceAt(level, pos, state, family);
        };
        int tx = target.rail != null ? target.rail.getX() : Integer.MIN_VALUE;
        int ty = target.rail != null ? target.rail.getY() : 0;
        int tz = target.rail != null ? target.rail.getZ() : 0;
        TrainAuto.Ahead ahead = TrainAuto.look(front, lk, tx, ty, tz, TrainAuto.WINDOW);
        this.hasDist = ahead.found >= 0.0;
        this.dist = this.hasDist ? ahead.found - noseInset : -1.0;
        this.known = ahead.known;
        this.waitingChunks = ahead.cut && unloaded[0];
        this.pathChunks.clear();
        this.pathChunks.addAll(ahead.chunks);
        if (this.waitingChunks) {
            // se pide el chunk que falta (y los de alrededor) para poder seguir viendo la vía
            int cx = ahead.endX >> 4;
            int cz = ahead.endZ >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.pathChunks.add(((long) (cx + dx) & 0xFFFFFFFFL) | (((long) (cz + dz) & 0xFFFFFFFFL) << 32));
                }
            }
        }
    }

    // =====================================================================================================
    // Guardado
    // =====================================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", this.name);
        tag.putBoolean("On", this.on);
        tag.putInt("Idx", this.idx);
        tag.putInt("From", this.from);
        tag.putInt("State", this.state.ordinal());
        tag.putInt("Dwell", this.dwellLeft);
        ListTag list = new ListTag();
        for (Stop s : this.stops) {
            CompoundTag t = new CompoundTag();
            t.putLong("Ctrl", s.ctrl.asLong());
            if (s.rail != null) {
                t.putLong("Rail", s.rail.asLong());
            }
            t.putString("Name", s.name);
            t.putInt("DwellSec", s.dwell);
            t.putInt("Depart", s.depart);
            t.putInt("Travel", s.travel);
            t.putInt("Open", s.open);
            t.putInt("Close", s.close);
            list.add(t);
        }
        tag.put("Stops", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.name = tag.getString("Name");
        this.on = tag.getBoolean("On");
        this.idx = tag.getInt("Idx");
        this.from = tag.getInt("From");
        int st = tag.getInt("State");
        this.state = State.values()[Math.max(0, Math.min(st, State.values().length - 1))];
        this.dwellLeft = tag.getInt("Dwell");
        this.stops.clear();
        ListTag list = tag.getList("Stops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_STOPS; i++) {
            CompoundTag t = list.getCompound(i);
            Stop s = new Stop(BlockPos.of(t.getLong("Ctrl")), t.contains("Rail") ? BlockPos.of(t.getLong("Rail")) : null);
            s.name = t.getString("Name");
            s.dwell = t.getInt("DwellSec");
            s.depart = t.getInt("Depart");
            s.travel = t.getInt("Travel");
            s.open = t.getInt("Open");
            s.close = t.getInt("Close");
            this.stops.add(s);
        }
        if (this.idx >= this.stops.size()) {
            this.idx = 0;
        }
        this.invalidate();
    }
}
