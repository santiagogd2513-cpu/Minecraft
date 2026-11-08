package com.amatista.arsenal.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Búnker militar subterráneo: una caseta en la superficie, una escalera que baja ~48 bloques,
 * ocho salas y la bóveda del fondo con el Designador orbital. Es raro (ver structure_set).
 */
public class BunkerStructure extends Structure {
    public static final Codec<BunkerStructure> CODEC = simpleCodec(BunkerStructure::new);

    public BunkerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        int surface = height(ctx, x, z);
        // nada de terrenos muy empinados (la caseta quedaría colgando)
        int min = surface, max = surface;
        for (int[] d : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            int h = height(ctx, x + d[0], z + d[1]);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (max - min > 7) return Optional.empty();
        // ni en el agua
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        if (!col.getBlock(surface - 1).getFluidState().isEmpty()) return Optional.empty();
        if (surface < ctx.chunkGenerator().getSeaLevel()) return Optional.empty();

        int cycles = 3;
        int bottom = ctx.heightAccessor().getMinBuildHeight() + 8;
        while (cycles > 1 && surface - 16 * cycles - 2 < bottom) cycles--;
        if (surface - 16 * cycles - 2 < bottom) return Optional.empty();

        Rotation rot = Rotation.getRandom(ctx.random());
        long seed = ctx.random().nextLong();
        BlockPos origin = new BlockPos(x, surface, z);
        int c = cycles;
        return Optional.of(new GenerationStub(origin, b -> BunkerPiece.addAll(b, origin, rot, surface, c, seed)));
    }

    private static int height(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BUNKER.get();
    }
}
