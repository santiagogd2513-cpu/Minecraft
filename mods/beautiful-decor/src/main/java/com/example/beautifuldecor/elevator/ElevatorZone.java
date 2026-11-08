package com.example.beautifuldecor.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Zona de la cabina del ascensor (V2.2.0): un prisma de bloques que sale del controlador.
 * <p>
 * El controlador queda FUERA de la zona, pegado a su fondo y al centro. La zona empieza en el bloque de enfrente del
 * controlador (en la dirección {@code facing}) y se extiende {@code depth} bloques hacia allá; a lo ancho queda centrada
 * en el controlador (si el ancho es par, el bloque que sobra va a la derecha). En vertical, la capa de abajo del piso es
 * la que está justo debajo del controlador (el controlador queda a la altura de los pies de quien entra).
 * <p>
 * Desde la V3.3.0 la zona se puede correr en X, Y y Z (el desplazamiento se guarda en el controlador); con 0, 0, 0 todo queda como antes.
 */
public record ElevatorZone(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {

    public static ElevatorZone of(BlockPos controller, Direction facing, int width, int depth, int height) {
        return of(controller, facing, width, depth, height, 0, 0, 0);
    }

    /**
     * Igual que la de arriba, pero con la zona corrida {@code offX}, {@code offY} y {@code offZ} bloques en los ejes del mundo
     * (X hacia el este, Y hacia arriba, Z hacia el sur; V3.3.0). Con 0, 0, 0 es la zona pegada al controlador de siempre.
     */
    public static ElevatorZone of(BlockPos controller, Direction facing, int width, int depth, int height,
                                  int offX, int offY, int offZ) {
        Direction right = facing.getClockWise();
        int left = (width - 1) / 2;
        int rightCount = width - 1 - left;
        int ax = controller.getX() + facing.getStepX() + right.getStepX() * -left;
        int az = controller.getZ() + facing.getStepZ() + right.getStepZ() * -left;
        int bx = controller.getX() + facing.getStepX() * depth + right.getStepX() * rightCount;
        int bz = controller.getZ() + facing.getStepZ() * depth + right.getStepZ() * rightCount;
        int minX = Math.min(ax, bx);
        int minZ = Math.min(az, bz);
        int sizeX = Math.abs(ax - bx) + 1;
        int sizeZ = Math.abs(az - bz) + 1;
        return new ElevatorZone(minX + offX, controller.getY() - 1 + offY, minZ + offZ, sizeX, height, sizeZ);
    }

    public int maxX() {
        return this.minX + this.sizeX - 1;
    }

    public int maxY() {
        return this.minY + this.sizeY - 1;
    }

    public int maxZ() {
        return this.minZ + this.sizeZ - 1;
    }

    public int cells() {
        return this.sizeX * this.sizeY * this.sizeZ;
    }

    public ElevatorZone atY(int newMinY) {
        return new ElevatorZone(this.minX, newMinY, this.minZ, this.sizeX, this.sizeY, this.sizeZ);
    }

    /** V3.4.0: la misma zona corrida {@code dx}, {@code dy}, {@code dz} bloques (para los viajes de lado). */
    public ElevatorZone shifted(int dx, int dy, int dz) {
        return new ElevatorZone(this.minX + dx, this.minY + dy, this.minZ + dz, this.sizeX, this.sizeY, this.sizeZ);
    }

    /** V3.4.0: esquina de la zona en el eje de viaje (0 = X, 1 = Y, 2 = Z). */
    public int minAlong(int axis) {
        return axis == 0 ? this.minX : axis == 2 ? this.minZ : this.minY;
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= maxX() && y >= this.minY && y <= maxY() && z >= this.minZ && z <= maxZ();
    }

    public AABB toAabb() {
        return new AABB(this.minX, this.minY, this.minZ, this.minX + this.sizeX, this.minY + this.sizeY, this.minZ + this.sizeZ);
    }
}
