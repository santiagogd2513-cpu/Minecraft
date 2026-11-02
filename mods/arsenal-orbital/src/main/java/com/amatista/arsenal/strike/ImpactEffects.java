package com.amatista.arsenal.strike;

import com.amatista.arsenal.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** El impacto real del rayo: cráter, escombros volando, fuego, daño, partículas y humo que dura. */
public final class ImpactEffects {
    private static final List<Lingering> LINGERING = new ArrayList<>();

    private record Lingering(ServerLevel level, Vec3 center, double radius, long until) {}

    public static void impact(ServerLevel level, BlockPos target, @Nullable ServerPlayer owner) {
        Vec3 c = Vec3.atCenterOf(target).add(0, 0.5, 0);
        int r = Config.CRATER_RADIUS.get();
        boolean destroy = Config.DESTROY_BLOCKS.get();
        RandomSource rnd = level.getRandom();

        // Sonido (lo oyen todos alrededor)
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 12f, 0.45f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 16f, 0.55f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 12f, 0.5f);

        // Daño y empuje antes de romper bloques
        damageEntities(level, c, r, owner);

        if (destroy) {
            carveCrater(level, target, r, rnd);
        }
        // Explosión central (también hace el sonido y empuje de ítems)
        level.explode(null, c.x, c.y, c.z, Math.min(10f, r * 0.55f),
                destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);

        // Partículas forzadas para que se vean desde lejos (la cámara está a decenas de bloques)
        burst(level, ParticleTypes.EXPLOSION_EMITTER, c, 16, r * 0.45, r * 0.2, r * 0.45, 0);
        burst(level, ParticleTypes.FLASH, c, 6, 1, 1, 1, 0);
        burst(level, ParticleTypes.FLAME, c, 500, r * 0.6, r * 0.35, r * 0.6, 0.35);
        burst(level, ParticleTypes.LAVA, c, 160, r * 0.5, r * 0.2, r * 0.5, 0);
        burst(level, ParticleTypes.LARGE_SMOKE, c, 400, r * 0.6, r * 0.4, r * 0.6, 0.15);
        burst(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c, 120, r * 0.5, r * 0.2, r * 0.5, 0.03);
        burst(level, ParticleTypes.ELECTRIC_SPARK, c, 300, r * 0.5, r * 0.5, r * 0.5, 0.8);
        burst(level, ParticleTypes.END_ROD, c, 200, r * 0.3, r * 0.3, r * 0.3, 0.9);
        burst(level, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 2, 0), 250, 1.5, 6, 1.5, 0.25);

        // Humo y fuego que siguen saliendo del cráter durante un minuto
        LINGERING.add(new Lingering(level, c, r, level.getGameTime() + 20 * 60));
    }

    private static void damageEntities(ServerLevel level, Vec3 c, int r, @Nullable ServerPlayer owner) {
        double reach = r * 2.5;
        AABB box = new AABB(c.x - reach, c.y - reach, c.z - reach, c.x + reach, c.y + reach, c.z + reach);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == owner) continue;
            double d = e.position().distanceTo(c);
            if (d > reach) continue;
            float f = (float) (1.0 - d / reach);
            e.hurt(level.damageSources().explosion(null, owner), 6f + 70f * f);
            e.setSecondsOnFire(10);
            Vec3 dir = e.position().subtract(c);
            if (dir.lengthSqr() < 0.01) dir = new Vec3(0, 1, 0);
            Vec3 push = dir.normalize().scale(3.5 * f).add(0, 1.4 * f, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
    }

    private static void carveCrater(ServerLevel level, BlockPos target, int r, RandomSource rnd) {
        int debrisLeft = Config.DEBRIS_COUNT.get();
        int rr = r + 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        List<BlockPos> floor = new ArrayList<>();
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dz = -rr; dz <= rr; dz++) {
                for (int dy = -rr; dy <= rr; dy++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + (dy * 1.5) * (dy * 1.5));
                    // borde irregular
                    double edge = r * (0.88 + 0.24 * hashNoise(dx, dy, dz));
                    BlockPos pos = target.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(level, pos) < 0) continue; // roca madre, etc.
                    if (dist <= edge) {
                        boolean canFly = debrisLeft > 0 && level.getBlockEntity(pos) == null
                                && state.getFluidState().isEmpty() && dist > r * 0.35
                                && rnd.nextFloat() < 0.18f;
                        if (canFly) {
                            debrisLeft--;
                            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
                            fb.dropItem = false;
                            Vec3 out = new Vec3(dx, Math.abs(dy) + 2, dz).normalize();
                            double speed = 0.9 + rnd.nextDouble() * 1.6;
                            fb.setDeltaMovement(out.x * speed, 0.8 + rnd.nextDouble() * 1.4, out.z * speed);
                        } else {
                            if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
                            level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                        if (dy <= -r / 2 + 1) floor.add(pos);
                    } else if (dist <= edge + 2.2 && state.getFluidState().isEmpty()
                            && level.getBlockEntity(pos) == null && rnd.nextFloat() < 0.55f) {
                        // borde quemado: piedra negra, basalto y magma
                        float p = rnd.nextFloat();
                        BlockState scorched = p < 0.45f ? Blocks.BLACKSTONE.defaultBlockState()
                                : p < 0.75f ? Blocks.BASALT.defaultBlockState()
                                : p < 0.9f ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                        level.setBlock(pos, scorched, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
        // fuego en el fondo del cráter
        for (BlockPos pos : floor) {
            if (rnd.nextFloat() > 0.2f) continue;
            BlockPos below = pos.below();
            if (level.getBlockState(pos).isAir() && level.getBlockState(below).isSolidRender(level, below)) {
                level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
            }
        }
    }

    /** Ruido determinista simple (0..1) para que el borde del cráter no sea una esfera perfecta. */
    private static double hashNoise(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    private static void burst(ServerLevel level, ParticleOptions type, Vec3 c, int count,
                              double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c) > 512 * 512) continue;
            level.sendParticles(p, type, true, c.x, c.y, c.z, count, dx, dy, dz, speed);
        }
    }

    /** Humo y llamas que siguen saliendo del cráter. */
    static void tickLingering() {
        if (LINGERING.isEmpty()) return;
        Iterator<Lingering> it = LINGERING.iterator();
        while (it.hasNext()) {
            Lingering l = it.next();
            long now = l.level().getGameTime();
            if (now > l.until()) {
                it.remove();
                continue;
            }
            if (now % 4 != 0) continue;
            double r = l.radius();
            float fade = Mth.clamp((l.until() - now) / 600f, 0.2f, 1f);
            burst(l.level(), ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, l.center(), Math.round(6 * fade), r * 0.4, 0.5, r * 0.4, 0.02);
            burst(l.level(), ParticleTypes.LARGE_SMOKE, l.center(), Math.round(8 * fade), r * 0.5, 0.5, r * 0.5, 0.05);
            burst(l.level(), ParticleTypes.FLAME, l.center().add(0, -r * 0.3, 0), Math.round(10 * fade), r * 0.4, 0.3, r * 0.4, 0.02);
            if (now % 20 == 0) {
                l.level().playSound(null, l.center().x, l.center().y, l.center().z,
                        SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 3f, 0.6f);
            }
        }
    }

    private ImpactEffects() {}
}
