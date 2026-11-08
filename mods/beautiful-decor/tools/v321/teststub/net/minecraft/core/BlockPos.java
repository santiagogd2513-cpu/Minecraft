package net.minecraft.core;

/** Pieza de mentira de BlockPos (solo lo que usa ElevatorZone en las pruebas). */
public class BlockPos {
    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockPos p && p.x == this.x && p.y == this.y && p.z == this.z;
    }

    @Override
    public int hashCode() {
        return (this.y + this.z * 31) * 31 + this.x;
    }

    @Override
    public String toString() {
        return "BlockPos{" + this.x + ", " + this.y + ", " + this.z + "}";
    }
}
