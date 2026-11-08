package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.ElevatorControllerBlock;
import com.example.beautifuldecor.elevator.ElevatorZone;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Controlador de ascensor (V2.2.0): guarda el tamaño de la cabina, la velocidad, el nombre del piso y si la cabina
 * está parada en este piso (eso es lo que abre las puertas de este piso).
 * <p>
 * Todos los controladores que están en la misma columna (mismo x y z) forman un solo ascensor y comparten tamaño,
 * desplazamiento, velocidad y dirección; cada uno tiene su propio nombre.
 * <p>
 * V3.3.0: la zona se puede correr en X, Y y Z (hasta {@link #MAX_OFFSET} bloques) respecto de donde quedaría pegada al controlador.
 */
public class ElevatorControllerBlockEntity extends BlockEntity {
    public static final int MAX_WIDTH = 50;
    public static final int MAX_DEPTH = 50;
    public static final int MAX_HEIGHT = 24;
    public static final int MAX_CELLS = 60000;
    public static final float MIN_SPEED = 1.0f;
    public static final float MAX_SPEED = 20.0f;
    public static final int MAX_NAME = 24;
    /** Cuánto se puede correr la zona en cada eje, hacia un lado o hacia el otro (V3.3.0). */
    public static final int MAX_OFFSET = 64;

    private int width = 3;
    private int depth = 3;
    private int height = 4;
    /** Bloques por segundo. */
    private float speed = 4.0f;
    /** Cuánto está corrida la zona en X (este +), Y (arriba +) y Z (sur +) respecto de la posición de siempre. */
    private int offX;
    private int offY;
    private int offZ;
    private String floorName = "";
    private boolean cabinHere;

    public ElevatorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_CONTROLLER.get(), pos, state);
    }

    public int getWidth() {
        return this.width;
    }

    public int getDepth() {
        return this.depth;
    }

    public int getHeight() {
        return this.height;
    }

    public float getSpeed() {
        return this.speed;
    }

    public int getOffsetX() {
        return this.offX;
    }

    public int getOffsetY() {
        return this.offY;
    }

    public int getOffsetZ() {
        return this.offZ;
    }

    public String getFloorName() {
        return this.floorName;
    }

    public boolean isCabinHere() {
        return this.cabinHere;
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(ElevatorControllerBlock.FACING);
    }

    /** Fija tamaño y velocidad respetando los límites (ancho y fondo hasta 50, alto hasta 24, 60 000 celdas en total). */
    public void setShape(int width, int depth, int height, float speed) {
        this.width = Mth.clamp(width, 1, MAX_WIDTH);
        this.depth = Mth.clamp(depth, 1, MAX_DEPTH);
        this.height = Mth.clamp(height, 1, MAX_HEIGHT);
        while (this.width * this.depth * this.height > MAX_CELLS && this.height > 1) {
            this.height--;
        }
        this.speed = Mth.clamp(speed, MIN_SPEED, MAX_SPEED);
        this.setChanged();
    }

    /** Corre la zona en X, Y y Z (cada valor entre -MAX_OFFSET y MAX_OFFSET). */
    public void setOffset(int x, int y, int z) {
        this.offX = Mth.clamp(x, -MAX_OFFSET, MAX_OFFSET);
        this.offY = Mth.clamp(y, -MAX_OFFSET, MAX_OFFSET);
        this.offZ = Mth.clamp(z, -MAX_OFFSET, MAX_OFFSET);
        this.setChanged();
    }

    public void setFloorName(String name) {
        String clean = name == null ? "" : name.trim();
        this.floorName = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
        this.setChanged();
    }

    public void copyShapeFrom(ElevatorControllerBlockEntity other) {
        setShape(other.width, other.depth, other.height, other.speed);
        setOffset(other.offX, other.offY, other.offZ);
    }

    public ElevatorZone zone() {
        return ElevatorZone.of(this.worldPosition, getFacing(), this.width, this.depth, this.height, this.offX, this.offY, this.offZ);
    }

    /** Marca el piso donde está la cabina sin hacer ruido (al colocar el primer controlador o al recuperar un ascensor). */
    public void setCabinHereSilently(boolean here) {
        this.cabinHere = here;
        this.setChanged();
    }

    /** Marca si la cabina está parada en este piso. Al cambiar suena el mecanismo de las puertas. */
    public void setCabinHere(boolean here) {
        if (this.cabinHere == here) {
            return;
        }
        this.cabinHere = here;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            ElevatorZone zone = zone();
            this.level.playSound(null, zone.minX() + zone.sizeX() / 2.0, zone.minY() + 1.0, zone.minZ() + zone.sizeZ() / 2.0,
                    ModSounds.ELEVATOR_DOOR.get(), SoundSource.BLOCKS, 0.9f, here ? 1.0f : 0.9f);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Width", this.width);
        tag.putInt("Depth", this.depth);
        tag.putInt("Height", this.height);
        tag.putFloat("Speed", this.speed);
        tag.putInt("OffX", this.offX);
        tag.putInt("OffY", this.offY);
        tag.putInt("OffZ", this.offZ);
        tag.putString("FloorName", this.floorName);
        tag.putBoolean("CabinHere", this.cabinHere);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Width")) {
            this.width = Mth.clamp(tag.getInt("Width"), 1, MAX_WIDTH);
            this.depth = Mth.clamp(tag.getInt("Depth"), 1, MAX_DEPTH);
            this.height = Mth.clamp(tag.getInt("Height"), 1, MAX_HEIGHT);
            this.speed = Mth.clamp(tag.getFloat("Speed"), MIN_SPEED, MAX_SPEED);
        }
        // Los mundos de antes de la V3.3.0 no traen desplazamiento: quedan en 0, 0, 0 (la zona de siempre).
        this.offX = Mth.clamp(tag.getInt("OffX"), -MAX_OFFSET, MAX_OFFSET);
        this.offY = Mth.clamp(tag.getInt("OffY"), -MAX_OFFSET, MAX_OFFSET);
        this.offZ = Mth.clamp(tag.getInt("OffZ"), -MAX_OFFSET, MAX_OFFSET);
        this.floorName = tag.getString("FloorName");
        this.cabinHere = tag.getBoolean("CabinHere");
    }
}
