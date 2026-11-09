package com.arsenalatomico.strike;

import com.arsenalatomico.Config;
import com.arsenalatomico.NukePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que la bomba del Protocolo Hongo le hace al mundo. Se hace de a poco (unas cuantas columnas
 * por tick) para que el servidor no se congele:
 * <ul>
 *   <li>el cráter: un tazón de 46 bloques de radio y 22 de hondo. En el centro la roca quedó derretida
 *       (obsidiana, magma y vidrio verde, como la trinitita de la primera prueba nuclear de verdad), las
 *       paredes de basalto y piedra negra, y un borde levantado con los escombros;</li>
 *   <li>hasta el doble del radio, los árboles salen volando; más allá quedan los troncos pelados;</li>
 *   <li>hasta 104 bloques el suelo queda quemado, la nieve y las plantas desaparecen y hay incendios.</li>
 * </ul>
 */
public final class NukeCrater {
    /** El Protocolo Sol Negro: cráter de 67 bloques de radio, 32 de hondo y quemado hasta 150. */
    public static final double THERMO = 1.45;

    private final ServerLevel level;
    private final Vec3 at;
    private final int x0, y0, z0;
    private final List<int[]> columns = new ArrayList<>();
    private final RandomSource r;
    private int next;
    /** Tamaño: 1 = Protocolo Hongo; el Sol Negro es más grande. */
    private final double craterR, craterDepth, scorchR;

    private NukeCrater(ServerLevel level, Vec3 at, double k) {
        craterR = NukePlan.CRATER_R * k;
        craterDepth = NukePlan.CRATER_DEPTH * k;
        scorchR = NukePlan.SCORCH_R * k;
        this.level = level;
        this.at = at;
        x0 = Mth.floor(at.x);
        y0 = Mth.floor(at.y);
        z0 = Mth.floor(at.z);
        r = RandomSource.create((long) x0 * 341873128712L + z0);
        int R = (int) Math.round(scorchR);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (dx * dx + dz * dz <= R * R) columns.add(new int[]{dx, dz});
            }
        }
        // del centro hacia afuera
        columns.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
    }

    /** La detonación: daño a lo que esté vivo cerca y, si la configuración deja, el cráter. */
    public static NukeCrater detonate(ServerLevel level, Vec3 at) {
        return detonate(level, at, 1);
    }

    /** {@code k}: tamaño (1 = Protocolo Hongo, {@link #THERMO} = Protocolo Sol Negro). */
    public static NukeCrater detonate(ServerLevel level, Vec3 at, double size) {
        double reach = NukePlan.CRATER_R * size * 2.3;
        AABB box = new AABB(at, at).inflate(reach, reach * 0.8, reach);
        for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof LivingEntity)) {
            double d = e.position().distanceTo(at);
            if (d > reach) continue;
            float k = (float) (1 - d / reach);
            e.hurt(level.damageSources().explosion(null, null), 60 + 400 * k * k);
            e.setSecondsOnFire(8 + (int) (12 * k));
            Vec3 push = e.position().subtract(at).normalize().scale(3.5 * k).add(0, 1.2 * k, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 4, at.z, 30, 16, 8, 16, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 6, at.z, 500, 30, 12, 30, 0.4);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 10, at.z, 400, 40, 20, 40, 0.1);
        level.playSound(null, at.x, at.y, at.z, com.arsenalatomico.registry.ModSounds.NUKE_BLAST.get(), SoundSource.HOSTILE, 64f, 1f);
        Impacts.IMPACTS++;
        return Config.DESTROY_BLOCKS.get() ? new NukeCrater(level, at, size) : null;
    }

    /** Hace otras columnas; true cuando terminó. */
    public boolean step(int columnsPerTick) {
        int end = Math.min(columns.size(), next + columnsPerTick);
        for (; next < end; next++) column(columns.get(next)[0], columns.get(next)[1]);
        return next >= columns.size();
    }

    public boolean done() {
        return next >= columns.size();
    }

    private void column(int dx, int dz) {
        int x = x0 + dx, z = z0 + dz;
        double d = Math.sqrt(dx * dx + dz * dz);
        double R = craterR;
        // el suelo de verdad, debajo de los árboles
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        while (g > level.getMinBuildHeight() && isTree(level.getBlockState(new BlockPos(x, g, z)))) g--;
        double noise = (r.nextDouble() - 0.5) * 1.4;

        if (d <= R) {
            // el tazón: más hondo en el centro; las orillas suben hacia el borde
            double u = d / R;
            double floorY = y0 - craterDepth * (1 - u * u) + noise;
            if (u > 0.85) floorY += 5 * Math.pow((u - 0.85) / 0.15, 2);
            int fy = (int) Math.round(floorY);
            int clearTo = Math.min(level.getMaxBuildHeight() - 1, Math.max(top, y0) + 40);
            for (int y = fy + 1; y <= clearTo; y++) put(x, y, z, Blocks.AIR.defaultBlockState());
            put(x, fy, z, craterFloor(u));
            put(x, fy - 1, z, craterUnder(u));
            if (u < 0.3 && r.nextFloat() < 0.04) put(x, fy + 1, z, Blocks.FIRE.defaultBlockState());
            return;
        }
        if (d <= R * 1.35) {
            // el borde: escombros amontonados que bajan hacia afuera
            double u = (d - R) / (R * 0.35);
            int rim = (int) Math.round(y0 + 5 * (1 - u) * (1 - u) + noise);
            int newTop = Math.max(g, rim);
            for (int y = g + 1; y <= newTop; y++) put(x, y, z, rubble(y == newTop));
            for (int y = newTop + 1; y <= top + 2; y++) put(x, y, z, Blocks.AIR.defaultBlockState());
            if (r.nextFloat() < 0.12) put(x, newTop + 1, z, Blocks.FIRE.defaultBlockState());
            return;
        }
        // más afuera: árboles arrancados o pelados, suelo quemado, plantas y nieve fuera, y fuego
        boolean blown = d <= R * 2.0;
        for (int y = g + 1; y <= top + 1; y++) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.is(BlockTags.LEAVES) || (blown && s.is(BlockTags.LOGS)) || s.canBeReplaced() || s.is(BlockTags.FLOWERS)) {
                put(x, y, z, Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos gp = new BlockPos(x, g, z);
        BlockState ground = level.getBlockState(gp);
        BlockState burnt = scorch(ground, d / scorchR);
        if (burnt != null) put(x, g, z, burnt);
        float fire = (float) (0.16 * (1 - d / scorchR));
        if (r.nextFloat() < fire && level.getBlockState(gp).isFaceSturdy(level, gp, net.minecraft.core.Direction.UP)) {
            put(x, g + 1, z, Blocks.FIRE.defaultBlockState());
        }
    }

    private static boolean isTree(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    /** El piso del cráter: vidrio y obsidiana en el centro, basalto en medio y tierra quemada en la orilla. */
    private BlockState craterFloor(double u) {
        float k = r.nextFloat();
        if (u < 0.22) {
            if (k < 0.22) return Blocks.GREEN_STAINED_GLASS.defaultBlockState();   // trinitita
            if (k < 0.42) return Blocks.MAGMA_BLOCK.defaultBlockState();
            if (k < 0.5) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        if (u < 0.55) {
            if (k < 0.08) return Blocks.MAGMA_BLOCK.defaultBlockState();
            if (k < 0.18) return Blocks.LIME_STAINED_GLASS.defaultBlockState();
            if (k < 0.55) return Blocks.BLACKSTONE.defaultBlockState();
            return k < 0.8 ? Blocks.BASALT.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (k < 0.35) return Blocks.TUFF.defaultBlockState();
        if (k < 0.65) return Blocks.COARSE_DIRT.defaultBlockState();
        return k < 0.85 ? Blocks.GRAVEL.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    private BlockState craterUnder(double u) {
        if (u < 0.3) return Blocks.OBSIDIAN.defaultBlockState();
        return r.nextFloat() < 0.5 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    private BlockState rubble(boolean surface) {
        float k = r.nextFloat();
        if (surface) return k < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : k < 0.8 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        return k < 0.4 ? Blocks.DIRT.defaultBlockState() : k < 0.7 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    /** El suelo quemado según lo que había (null = se queda igual). Cuanto más lejos, menos cambia. */
    private BlockState scorch(BlockState s, double far) {
        if (r.nextDouble() < far * far * 0.8) return null;
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.MOSS_BLOCK)) {
            float k = r.nextFloat();
            return k < 0.55 ? Blocks.COARSE_DIRT.defaultBlockState() : k < 0.85 ? Blocks.DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
        }
        if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
            return far < 0.55 && r.nextFloat() < 0.35 ? Blocks.GLASS.defaultBlockState() : null;
        }
        if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) return Blocks.WATER.defaultBlockState();
        if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE)) return Blocks.WATER.defaultBlockState();
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL)) return far < 0.6 ? Blocks.AIR.defaultBlockState() : Blocks.FIRE.defaultBlockState();
        return null;
    }

    private void put(int x, int y, int z, BlockState s) {
        if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
        BlockPos p = new BlockPos(x, y, z);
        BlockState old = level.getBlockState(p);
        if (old.is(Blocks.BEDROCK) || old.equals(s)) return;
        level.setBlock(p, s, 2 | 16);
    }

    public Vec3 center() {
        return at;
    }
}
