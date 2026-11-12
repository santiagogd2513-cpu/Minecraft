package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** V6.0.0: ajustes de una estación (controlador junto a la vía): nombre, cuánto espera el tren, velocidades y horario del depósito. */
public class StationControllerBlockEntity extends BlockEntity {
    public static final int MAX_NAME = 24;
    public static final int MAX_DWELL = 600;

    private String stationName = "";
    /** Segundos que el tren espera en la estación. */
    private int dwellSec = 20;
    /** Velocidad (km/h) con la que sale (los primeros 120 m) y con la que va hasta la siguiente estación. */
    private int departKmh = 40;
    private int travelKmh = 80;
    /** Horario del depósito (horas del mundo, 0 a 24); solo cuenta si esta es la última parada de la ruta. */
    private int openHour = 0;
    private int closeHour = 24;

    public StationControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATION_CONTROLLER.get(), pos, state);
    }

    public String getStationName() {
        return this.stationName;
    }

    public int getDwellSec() {
        return this.dwellSec;
    }

    public int getDepartKmh() {
        return this.departKmh;
    }

    public int getTravelKmh() {
        return this.travelKmh;
    }

    public int getOpenHour() {
        return this.openHour;
    }

    public int getCloseHour() {
        return this.closeHour;
    }

    public void apply(String name, int dwell, int depart, int travel, int open, int close) {
        this.stationName = name == null ? "" : name.trim().substring(0, Math.min(name.trim().length(), MAX_NAME));
        this.dwellSec = Mth.clamp(dwell, 1, MAX_DWELL);
        this.departKmh = Mth.clamp(depart, 5, 300);
        this.travelKmh = Mth.clamp(travel, 5, 300);
        this.openHour = Mth.clamp(open, 0, 24);
        this.closeHour = Mth.clamp(close, 0, 24);
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Name", this.stationName);
        tag.putInt("Dwell", this.dwellSec);
        tag.putInt("Depart", this.departKmh);
        tag.putInt("Travel", this.travelKmh);
        tag.putInt("Open", this.openHour);
        tag.putInt("Close", this.closeHour);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.stationName = tag.getString("Name");
        if (tag.contains("Dwell")) {
            this.dwellSec = Mth.clamp(tag.getInt("Dwell"), 1, MAX_DWELL);
            this.departKmh = Mth.clamp(tag.getInt("Depart"), 5, 300);
            this.travelKmh = Mth.clamp(tag.getInt("Travel"), 5, 300);
            this.openHour = Mth.clamp(tag.getInt("Open"), 0, 24);
            this.closeHour = Mth.clamp(tag.getInt("Close"), 0, 24);
        }
    }
}
