package com.amatista.arsenal.strike;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Busca el objetivo al que apunta el jugador. Si la mira no toca ningún bloque (por ejemplo, al
 * apuntar dentro de un cráter que llega hasta el vacío, o al cielo), el objetivo cae en el suelo
 * que hay debajo del punto donde termina la mira.
 */
public final class Targeting {
    private Targeting() {}

    @Nullable
    public static BlockPos find(Player player, double range, float partialTick) {
        HitResult hit = player.pick(range, partialTick, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bh) return bh.getBlockPos();
        Level level = player.level();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        // prueba varios puntos a lo largo de la mira, del más lejano al más cercano
        for (double d = range; d >= 8; d -= 8) {
            Vec3 end = eye.add(look.scale(d));
            int x = (int) Math.floor(end.x), z = (int) Math.floor(end.z);
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (top > level.getMinBuildHeight()) return new BlockPos(x, top, z);
        }
        // solo hay vacío: el objetivo queda flotando a la altura del mar
        Vec3 end = eye.add(look.scale(range));
        BlockPos p = BlockPos.containing(end.x, level.getSeaLevel() - 1, end.z);
        return level.hasChunkAt(p) ? p : null;
    }
}
