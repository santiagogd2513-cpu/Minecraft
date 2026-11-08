package net.minecraft.core;

/** Pieza de mentira de Direction con los cuatro lados horizontales, con los mismos pasos y el mismo giro a la derecha que Minecraft. */
public enum Direction {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int stepX;
    private final int stepZ;

    Direction(int stepX, int stepZ) {
        this.stepX = stepX;
        this.stepZ = stepZ;
    }

    public int getStepX() {
        return this.stepX;
    }

    public int getStepZ() {
        return this.stepZ;
    }

    /** Como en Minecraft: sur 0, oeste 1, norte 2, este 3. */
    public int get2DDataValue() {
        return switch (this) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
        };
    }

    public static Direction from2DDataValue(int value) {
        return switch (Math.floorMod(value, 4)) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }

    /** Gira 90 grados hacia la derecha mirando desde arriba: norte, este, sur, oeste. */
    public Direction getClockWise() {
        return values()[(this.ordinal() + 1) % 4];
    }
}
