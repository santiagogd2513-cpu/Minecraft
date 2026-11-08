package com.example.beautifuldecor.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Pieza de mentira de ElevatorShaft: solo anota con qué datos se llama a applySettings. La verificación comprueba que la firma sea
 * IGUAL a la de verdad (tipos y orden de los parámetros).
 */
public final class ElevatorShaft {
    public static Object[] last;

    private ElevatorShaft() {
    }

    public static void applySettings(ServerLevel level, BlockPos pos, Direction facing, int width, int depth, int height,
                                     int offX, int offY, int offZ, float speed, String name, @Nullable ServerPlayer who) {
        last = new Object[] {pos, facing, width, depth, height, offX, offY, offZ, speed, name};
    }
}
