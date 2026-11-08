package com.arsenalatomico.ci;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.SalvoPlan;
import com.arsenalatomico.WeaponType;
import com.arsenalatomico.strike.Impacts;
import com.arsenalatomico.strike.LaunchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Solo en la compilación de GitHub (-Datomico.ci): lanza una Lluvia de Acero cerca del punto de
 * aparición, sin jugador, y revisa que salgan y caigan los 24 misiles y que rompan el terreno.
 */
@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID)
public final class CiTest {
    private static MinecraftServer server;
    private static long until;
    private static BlockPos target;
    private static int airBefore;

    private CiTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (System.getProperty("atomico.ci") == null) return;
        server = e.getServer();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int tx = spawn.getX() + 24, tz = spawn.getZ() + 10;
        target = new BlockPos(tx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) - 1, tz);
        airBefore = air(level);
        Vec3 t = Vec3.atBottomCenterOf(target).add(0, 0.5, 0);
        Vec3 site = LaunchManager.launcherSite(level, Vec3.atCenterOf(spawn), t, RandomSource.create(7));
        float yaw = LaunchManager.yawTowards(site, t);
        // sin jugadores el mundo deja de mover entidades a los 15 s; un chunk forzado lo mantiene vivo
        level.setChunkForced(target.getX() >> 4, target.getZ() >> 4, true);
        level.setChunkForced(BlockPos.containing(site).getX() >> 4, BlockPos.containing(site).getZ() >> 4, true);
        LaunchManager.begin(level, null, WeaponType.SALVO, t, site, yaw, 1234L);
        until = level.getGameTime() + new SalvoPlan(site, yaw, t, 1234L).lastImpactTick() + 100;
        System.out.println("PRUEBA: blanco " + target.toShortString() + ", batería en " + BlockPos.containing(site).toShortString()
                + " a " + Math.round(site.distanceTo(t)) + " bloques");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (server == null || e.phase != TickEvent.Phase.END) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() < until) return;
        int destroyed = air(level) - airBefore;
        boolean ok = Impacts.IMPACTS == SalvoPlan.COUNT && destroyed > 60;
        System.out.println("==== PRUEBA DE ARSENAL ATÓMICO ====");
        System.out.println("Misiles caídos: " + Impacts.IMPACTS + " de " + SalvoPlan.COUNT);
        System.out.println("Bloques destruidos alrededor del blanco: " + destroyed);
        System.out.println(ok ? "RESULTADO OK" : "RESULTADO FALLO");
        System.out.println("==== FIN ====");
        server.halt(false);
        server = null;
    }

    /** Bloques de aire en un cubo de 50 alrededor del blanco. */
    private static int air(ServerLevel level) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-25, -12, -25), target.offset(25, 12, 25))) {
            if (level.getBlockState(p).isAir()) n++;
        }
        return n;
    }
}
