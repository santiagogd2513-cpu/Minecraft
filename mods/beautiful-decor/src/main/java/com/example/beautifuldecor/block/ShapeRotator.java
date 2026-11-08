package com.example.beautifuldecor.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Gira una forma pensada para "facing=north" a las 4 direcciones horizontales (igual que "y": 90/180/270 en el blockstate). */
public final class ShapeRotator {
    private ShapeRotator() {
    }

    public static VoxelShape rotate(VoxelShape northShape, Direction facing) {
        int steps = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape current = northShape;
        for (int i = 0; i < steps; i++) {
            final VoxelShape[] next = {Shapes.empty()};
            current.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    next[0] = Shapes.or(next[0], Shapes.box(1.0 - z2, y1, x1, 1.0 - z1, y2, x2)));
            current = next[0];
        }
        return current;
    }

    /** Devuelve un arreglo indexado por {@link Direction#get2DDataValue()}. */
    public static VoxelShape[] all(VoxelShape northShape) {
        VoxelShape[] shapes = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = rotate(northShape, d);
        }
        return shapes;
    }
}
