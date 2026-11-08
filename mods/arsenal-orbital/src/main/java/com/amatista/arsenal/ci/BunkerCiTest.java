package com.amatista.arsenal.ci;

import com.amatista.arsenal.ArsenalOrbital;
import com.amatista.arsenal.block.PedestalBlockEntity;
import com.amatista.arsenal.registry.ModBlocks;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Solo en la compilación de GitHub (-Darsenal.ci): arranca un servidor, busca el búnker más
 * cercano en el mundo generado, lo revisa, imprime cortes del plano y cierra el servidor.
 */
@Mod.EventBusSubscriber(modid = ArsenalOrbital.MODID)
public final class BunkerCiTest {
    private BunkerCiTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (System.getProperty("arsenal.ci") == null) return;
        MinecraftServer srv = e.getServer();
        StringBuilder out = new StringBuilder();
        boolean ok = false;
        try {
            ok = run(srv, out);
        } catch (Throwable t) {
            out.append("EXCEPCION ").append(t).append('\n');
            for (StackTraceElement st : t.getStackTrace()) out.append("  ").append(st).append('\n');
        }
        out.append(ok ? "RESULTADO OK\n" : "RESULTADO FALLO\n");
        try {
            Files.writeString(Path.of("ci_bunker.txt"), out.toString());
        } catch (Exception ignored) {
        }
        System.out.println("==== PRUEBA DEL BUNKER ====\n" + out + "==== FIN ====");
        srv.halt(false);
    }

    private static boolean run(MinecraftServer srv, StringBuilder out) throws Exception {
        ServerLevel lvl = srv.overworld();
        Holder<Structure> h = lvl.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(ArsenalOrbital.MODID, "bunker")));
        Pair<BlockPos, Holder<Structure>> found = lvl.getChunkSource().getGenerator()
                .findNearestMapStructure(lvl, HolderSet.direct(h), BlockPos.ZERO, 120, false);
        if (found == null) {
            out.append("No se encontró ningún búnker cerca del origen\n");
            return false;
        }
        BlockPos p = found.getFirst();
        out.append("Búnker más cercano en ").append(p.toShortString()).append(" (a ")
                .append((int) Math.sqrt(p.distSqr(BlockPos.ZERO))).append(" bloques del origen)\n");
        StructureStart start = lvl.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(h.value());
        if (start == null || !start.isValid()) {
            out.append("El chunk no tiene el inicio del búnker\n");
            return false;
        }
        BoundingBox bb = start.getBoundingBox();
        out.append("Caja: ").append(bb).append(", piezas: ").append(start.getPieces().size()).append('\n');
        for (int cx = (bb.minX() >> 4) - 1; cx <= (bb.maxX() >> 4) + 1; cx++)
            for (int cz = (bb.minZ() >> 4) - 1; cz <= (bb.maxZ() >> 4) + 1; cz++) lvl.getChunk(cx, cz);

        // revisar las vitrinas
        int pedestals = 0, withItem = 0, rainbow = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockPos q : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
            BlockState s = lvl.getBlockState(q);
            if (s.is(ModBlocks.ARTIFACT_PEDESTAL.get())) {
                pedestals++;
                if (lvl.getBlockEntity(q) instanceof PedestalBlockEntity be) {
                    if (!be.getItem().isEmpty()) {
                        withItem++;
                        out.append("Artefacto en ").append(q.toShortString()).append(": ").append(be.getItem()).append('\n');
                    }
                    if (be.isRainbow()) rainbow++;
                }
            }
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            counts.merge(id, 1, Integer::sum);
        }
        out.append("Pedestales: ").append(pedestals).append(", con artefacto: ").append(withItem).append(", arcoíris: ").append(rainbow).append('\n');
        out.append("Bloques: ").append(counts).append('\n');

        // cortes del plano
        int floor = findFloor(lvl, bb);
        out.append("Piso de las salas: y=").append(floor).append('\n');
        slice(lvl, bb, floor, out, "PLANO A LA ALTURA DE LAS SALAS (y=" + floor + ")");
        slice(lvl, bb, floor + 2, out, "PLANO UN POCO MÁS ARRIBA (y=" + (floor + 2) + ")");
        int surf = bb.maxY() - 8;
        slice(lvl, bb, surf, out, "PLANO DE LA CASETA (y=" + surf + ")");
        // corte vertical por el medio de la escalera, en la dirección más larga
        out.append("CORTE VERTICAL (de arriba hacia abajo)\n");
        boolean alongX = bb.getXSpan() >= bb.getZSpan();
        for (int y = bb.maxY(); y >= bb.minY(); y--) {
            StringBuilder row = new StringBuilder(String.format("%4d ", y));
            int a0 = alongX ? bb.minX() : bb.minZ(), a1 = alongX ? bb.maxX() : bb.maxZ();
            int fixed = alongX ? (p.getZ()) : (p.getX());
            for (int a = a0; a <= a1; a++) {
                BlockPos q = alongX ? new BlockPos(a, y, fixed) : new BlockPos(fixed, y, a);
                row.append(ch(lvl.getBlockState(q)));
            }
            out.append(row).append('\n');
        }
        return pedestals == ROOMS + 1 && withItem == 1 && rainbow == 1;
    }

    private static final int ROOMS = 8;

    private static int findFloor(ServerLevel lvl, BoundingBox bb) {
        for (BlockPos q : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
            if (lvl.getBlockEntity(q) instanceof PedestalBlockEntity be && be.isRainbow()) return q.getY() - 1;
        }
        return bb.minY() + 2;
    }

    private static void slice(ServerLevel lvl, BoundingBox bb, int y, StringBuilder out, String title) {
        out.append(title).append('\n');
        for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
            StringBuilder row = new StringBuilder();
            for (int x = bb.minX(); x <= bb.maxX(); x++) row.append(ch(lvl.getBlockState(new BlockPos(x, y, z))));
            out.append(row.toString().replaceAll("\\s+$", "")).append('\n');
        }
    }

    /** Una letra por bloque para los planos. */
    private static char ch(BlockState s) {
        if (s.isAir()) return ' ';
        Block b = s.getBlock();
        if (b == ModBlocks.ARTIFACT_PEDESTAL.get()) return 'P';
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (id.contains("stairs")) return '/';
        if (id.contains("glass")) return 'o';
        if (id.equals("sea_lantern") || id.contains("lantern") || id.contains("froglight") || id.equals("shroomlight") || id.equals("end_rod")) return '*';
        if (id.contains("door")) return 'D';
        if (id.contains("sign")) return 'S';
        if (id.contains("chest") || id.equals("barrel")) return 'c';
        if (id.contains("bed")) return 'b';
        if (id.equals("water") || id.equals("lava")) return '~';
        if (id.contains("concrete") || id.contains("brick") || id.contains("polished") || id.contains("smooth") || id.contains("tiles")
                || id.contains("quartz") || id.equals("iron_block")) return '#';
        if (s.isSolid()) return '.';
        return '+';
    }
}
