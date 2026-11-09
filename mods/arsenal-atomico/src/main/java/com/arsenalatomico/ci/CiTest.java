package com.arsenalatomico.ci;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.SalvoPlan;
import com.arsenalatomico.TwinPlan;
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
 * Solo en la compilación de GitHub (-Datomico.ci): sin ningún jugador, lanza a la vez una Lluvia de
 * Acero y un Cóndor Doble, y revisa que caigan todos los misiles a su hora y rompan el terreno.
 * No carga nada a mano: así se prueba que el mod mantiene cargadas las zonas del ataque.
 */
@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID)
public final class CiTest {
    private static MinecraftServer server;
    private static long until;
    private static BlockPos salvoTarget, twinTarget;
    private static int salvoAir, twinAir;
    private static LaunchManager.Strike salvo, twin;
    private static int twinMode;

    private CiTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (System.getProperty("atomico.ci") == null) return;
        server = e.getServer();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        Vec3 player = Vec3.atCenterOf(spawn);
        salvoTarget = surface(level, spawn.getX() + 24, spawn.getZ() + 10);
        twinTarget = surface(level, spawn.getX() - 40, spawn.getZ() - 30);
        salvoAir = air(level, salvoTarget);
        twinAir = air(level, twinTarget);

        Vec3 a = Vec3.atBottomCenterOf(salvoTarget).add(0, 0.5, 0);
        LaunchManager.Site sa = LaunchManager.chooseSite(level, WeaponType.SALVO, player, a, RandomSource.create(7));
        salvo = LaunchManager.begin(level, null, WeaponType.SALVO, a, sa, 1234L);

        Vec3 b = Vec3.atBottomCenterOf(twinTarget).add(0, 0.5, 0);
        LaunchManager.Site sb = LaunchManager.chooseSite(level, WeaponType.TWIN, player, b, RandomSource.create(8));
        twinMode = sb.mode();
        twin = LaunchManager.begin(level, null, WeaponType.TWIN, b, sb, 4321L);

        until = Math.max(salvo.lastImpactGameTime(), twin.lastImpactGameTime()) + 60;
        System.out.println("PRUEBA: Lluvia de Acero sobre " + salvoTarget.toShortString() + " desde " + BlockPos.containing(sa.pos()).toShortString());
        System.out.println("PRUEBA: Cóndor Doble sobre " + twinTarget.toShortString() + " desde " + BlockPos.containing(sb.pos()).toShortString()
                + (twinMode == TwinPlan.SEA ? " (submarino)" : " (lanzadoras en tierra)"));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (server == null || e.phase != TickEvent.Phase.END) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() < until) return;
        int expected = SalvoPlan.COUNT + 2;
        int dSalvo = air(level, salvoTarget) - salvoAir, dTwin = air(level, twinTarget) - twinAir;
        boolean ok = Impacts.IMPACTS == expected && salvo.allHit() && twin.allHit() && dSalvo > 60 && dTwin > 150;
        System.out.println("==== PRUEBA DE ARSENAL ATÓMICO ====");
        System.out.println("Misiles caídos: " + Impacts.IMPACTS + " de " + expected);
        System.out.println("Lluvia de Acero: " + dSalvo + " bloques destruidos");
        System.out.println("Cóndor Doble (" + (twinMode == TwinPlan.SEA ? "submarino" : "en tierra") + "): " + dTwin + " bloques destruidos");
        System.out.println(ok ? "RESULTADO OK" : "RESULTADO FALLO");
        System.out.println("==== FIN ====");
        server.halt(false);
        server = null;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    /** Bloques de aire en un cubo de 50 alrededor del blanco. */
    private static int air(ServerLevel level, BlockPos target) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-25, -14, -25), target.offset(25, 14, 25))) {
            if (level.getBlockState(p).isAir()) n++;
        }
        return n;
    }
}
