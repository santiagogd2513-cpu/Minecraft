package net.minecraft.core;
public enum Direction {
    NORTH, EAST, SOUTH, WEST;
    public float toYRot() { return 0f; }
    public Direction getCounterClockWise() { return this; }
}
