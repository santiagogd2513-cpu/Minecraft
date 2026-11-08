package com.amatista.arsenal.strike;

import com.amatista.arsenal.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        int r = Config.crater(1f, 110);
        boolean destroy = Config.DESTROY_BLOCKS.get();
        RandomSource rnd = level.getRandom();

        // Sonido (lo oyen todos alrededor)
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), SoundSource.BLOCKS, 12f, 0.45f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.DEEP_THUNDER.get(), SoundSource.WEATHER, 16f, 0.55f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.BEAM_IMPACT.get(), SoundSource.WEATHER, 12f, 0.5f);

        // Daño y empuje antes de romper bloques
        damageEntities(level, c, r, owner);

        if (destroy) {
            carveCrater(level, target, r, rnd);
            if (Config.PIERCE_BEDROCK.get()) drillShaft(level, target, shaftRadius(1f), rnd);
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

    /**
     * Impacto de Fobos: cráter enorme (1,6 veces el normal), fragmentos de la luna saliendo
     * disparados, explosiones secundarias en anillo, fuego y humo durante dos minutos.
     */
    public static void moonImpact(ServerLevel level, BlockPos target, @Nullable ServerPlayer owner) {
        Vec3 c = Vec3.atCenterOf(target).add(0, 0.5, 0);
        int r = Config.crater(1.6f, 120);
        boolean destroy = Config.DESTROY_BLOCKS.get();
        RandomSource rnd = level.getRandom();

        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), SoundSource.BLOCKS, 16f, 0.35f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.DEEP_THUNDER.get(), SoundSource.WEATHER, 20f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.COLLAPSE.get(), SoundSource.HOSTILE, 10f, 0.5f);

        damageEntities(level, c, r, owner);
        if (destroy) carveCrater(level, target, r, rnd);
        level.explode(null, c.x, c.y, c.z, 12f, destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        // explosiones secundarias en anillo
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI * 2 * i / 10 + rnd.nextDouble() * 0.3;
            double d = r * (0.8 + rnd.nextDouble() * 0.4);
            level.explode(null, c.x + Math.cos(ang) * d, c.y, c.z + Math.sin(ang) * d, 5f,
                    destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        // fragmentos de Fobos (roca oscura) saliendo disparados
        if (destroy) {
            BlockState[] rock = {Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                    Blocks.TUFF.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState()};
            for (int i = 0; i < 90; i++) {
                BlockPos at = BlockPos.containing(c.x + rnd.nextGaussian() * 3, c.y + 2 + rnd.nextDouble() * 4, c.z + rnd.nextGaussian() * 3);
                if (!level.getBlockState(at).isAir()) continue;
                FallingBlockEntity fb = FallingBlockEntity.fall(level, at, rock[rnd.nextInt(rock.length)]);
                fb.dropItem = false;
                double ang = rnd.nextDouble() * Math.PI * 2, sp = 1.2 + rnd.nextDouble() * 2.2;
                fb.setDeltaMovement(Math.cos(ang) * sp, 1.2 + rnd.nextDouble() * 2.0, Math.sin(ang) * sp);
            }
        }
        burst(level, ParticleTypes.EXPLOSION_EMITTER, c, 30, r * 0.5, r * 0.25, r * 0.5, 0);
        burst(level, ParticleTypes.FLASH, c, 10, 2, 2, 2, 0);
        burst(level, ParticleTypes.FLAME, c, 900, r * 0.6, r * 0.4, r * 0.6, 0.45);
        burst(level, ParticleTypes.LAVA, c, 300, r * 0.5, r * 0.3, r * 0.5, 0);
        burst(level, ParticleTypes.LARGE_SMOKE, c, 700, r * 0.7, r * 0.5, r * 0.7, 0.2);
        burst(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c, 220, r * 0.6, r * 0.3, r * 0.6, 0.04);
        burst(level, ParticleTypes.ASH, c, 600, r * 0.8, r * 0.5, r * 0.8, 0.1);
        burst(level, ParticleTypes.WHITE_ASH, c, 300, r * 0.8, r * 0.5, r * 0.8, 0.1);
        LINGERING.add(new Lingering(level, c, r, level.getGameTime() + 20 * 120));
    }

    /** Altura a la que flota la singularidad sobre el objetivo. */
    public static final double SINGULARITY_HEIGHT = 14.0;

    /**
     * La singularidad absorbe: arranca bloques que salen girando en espiral hacia ella, atrae a
     * los mobs y jugadores, y destruye todo lo que llega al centro. progress va de 0 a 1.
     */
    public static void singularityPull(ServerLevel level, BlockPos target, float progress, @Nullable ServerPlayer owner) {
        Vec3 c = Vec3.atCenterOf(target).add(0, SINGULARITY_HEIGHT, 0);
        RandomSource rnd = level.getRandom();
        int maxR = Config.crater(1.3f, 100);
        float reachR = 4f + maxR * Math.min(1f, progress * 1.3f);
        boolean destroy = Config.DESTROY_BLOCKS.get();
        long time = level.getGameTime();

        // bloques arrancados (cada vez más)
        if (destroy) {
            int perTick = 4 + Math.round(progress * 22);
            for (int i = 0; i < perTick; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(rnd.nextDouble()) * reachR;
                double dy = -SINGULARITY_HEIGHT - rnd.nextDouble() * reachR * 0.6 + rnd.nextDouble() * 6;
                BlockPos pos = BlockPos.containing(c.x + Math.cos(ang) * rr, c.y + dy, c.z + Math.sin(ang) * rr);
                // buscar el bloque de la superficie en esa columna
                for (int k = 0; k < 12 && level.getBlockState(pos).isAir() && pos.getY() > level.getMinBuildHeight(); k++) pos = pos.below();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null
                        || !state.getFluidState().isEmpty()) continue;
                FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
                fb.dropItem = false;
                fb.setNoGravity(true);
            }
        }
        // arrastrar en espiral todo lo que está cerca (bloques que vuelan, ítems, mobs, jugadores)
        double field = reachR + 20;
        AABB box = new AABB(c.x - field, c.y - field, c.z - field, c.x + field, c.y + field, c.z + field);
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box, en -> !(en instanceof ServerPlayer p && p == owner))) {
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > field) continue;
            if (d < 2.2) {
                if (e instanceof FallingBlockEntity || e instanceof net.minecraft.world.entity.item.ItemEntity) {
                    e.discard();
                } else if (e instanceof LivingEntity living) {
                    living.hurt(level.damageSources().fellOutOfWorld(), 12f);
                }
                continue;
            }
            Vec3 dir = to.normalize();
            Vec3 swirl = new Vec3(-dir.z, 0, dir.x).normalize();
            double strength = (0.08 + 0.5 * progress) * (1.0 - d / field) + 0.05;
            Vec3 pull = dir.scale(strength * (e instanceof FallingBlockEntity ? 2.2 : 1.0)).add(swirl.scale(strength * 0.9));
            if (e instanceof FallingBlockEntity fb) {
                fb.setNoGravity(true);
                fb.time = 1; // que no se pose como bloque mientras vuela
                fb.setDeltaMovement(fb.getDeltaMovement().scale(0.85).add(pull));
            } else {
                e.setDeltaMovement(e.getDeltaMovement().add(pull.scale(0.6)));
                if (e instanceof LivingEntity) e.fallDistance = 0;
            }
            e.hurtMarked = true;
        }
        // partículas: remolino oscuro y chispas que caen hacia el centro
        if (time % 2 == 0) {
            burst(level, ParticleTypes.REVERSE_PORTAL, c, 60, 3 + reachR * 0.2, 3 + reachR * 0.1, 3 + reachR * 0.2, 0.4);
            burst(level, ParticleTypes.PORTAL, c, 40, reachR * 0.4, 4, reachR * 0.4, 1.2);
            burst(level, ParticleTypes.SQUID_INK, c, 20, 1.5, 1.5, 1.5, 0.05);
        }
        if (time % 20 == 0) {
            level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.VOID_DRONE.get(), SoundSource.HOSTILE, 6f, 0.3f + progress * 0.4f);
            level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.HEARTBEAT.get(), SoundSource.HOSTILE, 8f, 0.5f + progress);
        }
    }

    /** La singularidad colapsa: todo lo que absorbió sale en una explosión enorme y deja un cráter esférico. */
    public static void singularityCollapse(ServerLevel level, BlockPos target, @Nullable ServerPlayer owner) {
        Vec3 c = Vec3.atCenterOf(target).add(0, SINGULARITY_HEIGHT, 0);
        int r = Config.crater(1.4f, 110);
        boolean destroy = Config.DESTROY_BLOCKS.get();
        RandomSource rnd = level.getRandom();
        // limpiar lo que quedaba girando
        for (FallingBlockEntity fb : level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(c, c).inflate(80))) fb.discard();
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), SoundSource.HOSTILE, 16f, 0.4f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), SoundSource.BLOCKS, 16f, 0.3f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), SoundSource.HOSTILE, 12f, 0.5f);
        damageEntities(level, Vec3.atCenterOf(target), r, owner);
        if (destroy) carveCrater(level, target, r, rnd);
        level.explode(null, c.x, c.y - SINGULARITY_HEIGHT, c.z, 12f, destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        burst(level, ParticleTypes.EXPLOSION_EMITTER, c, 30, r * 0.5, r * 0.3, r * 0.5, 0);
        burst(level, ParticleTypes.FLASH, c, 12, 2, 2, 2, 0);
        burst(level, ParticleTypes.REVERSE_PORTAL, c, 1500, r * 0.6, r * 0.6, r * 0.6, 1.5);
        burst(level, ParticleTypes.END_ROD, c, 700, r * 0.4, r * 0.4, r * 0.4, 1.4);
        burst(level, ParticleTypes.SONIC_BOOM, c, 12, r * 0.3, 2, r * 0.3, 0);
        burst(level, ParticleTypes.DRAGON_BREATH, c, 600, r * 0.6, r * 0.3, r * 0.6, 0.3);
        burst(level, ParticleTypes.LARGE_SMOKE, c, 400, r * 0.6, r * 0.4, r * 0.6, 0.2);
        LINGERING.add(new Lingering(level, Vec3.atCenterOf(target), r, level.getGameTime() + 20 * 90));
    }

    /**
     * El rayo de Prometeo quema el suelo antes de la explosión: el área se derrite (magma,
     * obsidiana, vidrio), salen llamas y explosiones pequeñas cada vez más seguidas.
     */
    public static void starBurn(ServerLevel level, BlockPos target, float progress) {
        Vec3 c = Vec3.atCenterOf(target).add(0, 0.5, 0);
        RandomSource rnd = level.getRandom();
        boolean destroy = Config.DESTROY_BLOCKS.get();
        int reach = Math.round(6 + 34 * progress);
        if (destroy) {
            for (int i = 0; i < 18 + Math.round(progress * 40); i++) {
                double ang = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * reach;
                BlockPos col = BlockPos.containing(c.x + Math.cos(ang) * rr, c.y + 6, c.z + Math.sin(ang) * rr);
                BlockPos pos = col;
                for (int k = 0; k < 20 && level.getBlockState(pos).isAir() && pos.getY() > level.getMinBuildHeight(); k++) pos = pos.below();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || level.getBlockEntity(pos) != null) continue;
                float p = rnd.nextFloat();
                BlockState melted = rr < reach * 0.35 ? Blocks.AIR.defaultBlockState()
                        : p < 0.35f ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : p < 0.55f ? Blocks.OBSIDIAN.defaultBlockState()
                        : p < 0.7f ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : p < 0.85f ? Blocks.GLASS.defaultBlockState()
                        : Blocks.LAVA.defaultBlockState();
                level.setBlock(pos, melted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                BlockPos above = pos.above();
                if (level.getBlockState(above).isAir() && rnd.nextFloat() < 0.4f) {
                    level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
                }
            }
        }
        long time = level.getGameTime();
        int every = Math.max(3, 14 - Math.round(progress * 11));
        if (time % every == 0) {
            double ang = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * reach * 0.8;
            level.explode(null, c.x + Math.cos(ang) * rr, c.y, c.z + Math.sin(ang) * rr, 2.5f + 2f * progress,
                    destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        if (time % 2 == 0) {
            burst(level, ParticleTypes.FLAME, c, 60, reach * 0.4, 1, reach * 0.4, 0.2);
            burst(level, ParticleTypes.END_ROD, c.add(0, 4, 0), 40, 3, 12, 3, 0.6);
            burst(level, ParticleTypes.LAVA, c, 12, reach * 0.3, 0.5, reach * 0.3, 0);
        }
        if (time % 20 == 0) level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.MACHINE_HUM.get(), SoundSource.BLOCKS, 12f, 0.5f + progress);
        // quema a los mobs que están en el rayo
        double rad = reach;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(rad, 40, rad))) {
            if (e.position().distanceToSqr(c.x, e.getY(), c.z) < rad * rad) {
                e.setSecondsOnFire(10);
                e.hurt(level.damageSources().inFire(), 4f + 10f * progress);
            }
        }
    }

    /** Explosión final del rayo de Prometeo: el cráter más grande, pozo ancho hasta el fondo del mundo. */
    public static void starImpact(ServerLevel level, BlockPos target, @Nullable ServerPlayer owner) {
        Vec3 c = Vec3.atCenterOf(target).add(0, 0.5, 0);
        int r = Config.crater(2f, 120);
        boolean destroy = Config.DESTROY_BLOCKS.get();
        RandomSource rnd = level.getRandom();
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), SoundSource.BLOCKS, 20f, 0.25f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), SoundSource.HOSTILE, 20f, 0.3f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.DEEP_THUNDER.get(), SoundSource.WEATHER, 24f, 0.35f);
        damageEntities(level, c, r, owner);
        if (destroy) {
            carveCrater(level, target, r, rnd);
            if (Config.PIERCE_BEDROCK.get()) drillShaft(level, target, shaftRadius(2f), rnd);
        }
        level.explode(null, c.x, c.y, c.z, 16f, destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        for (int i = 0; i < 14; i++) {
            double ang = Math.PI * 2 * i / 14, d = r * (0.9 + rnd.nextDouble() * 0.3);
            level.explode(null, c.x + Math.cos(ang) * d, c.y, c.z + Math.sin(ang) * d, 6f,
                    destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        burst(level, ParticleTypes.EXPLOSION_EMITTER, c, 40, r * 0.5, r * 0.3, r * 0.5, 0);
        burst(level, ParticleTypes.FLASH, c, 14, 3, 3, 3, 0);
        burst(level, ParticleTypes.END_ROD, c, 1200, r * 0.5, r * 0.6, r * 0.5, 1.6);
        burst(level, ParticleTypes.SOUL_FIRE_FLAME, c, 900, r * 0.6, r * 0.5, r * 0.6, 0.5);
        burst(level, ParticleTypes.FLAME, c, 900, r * 0.6, r * 0.4, r * 0.6, 0.5);
        burst(level, ParticleTypes.LARGE_SMOKE, c, 700, r * 0.7, r * 0.5, r * 0.7, 0.2);
        burst(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c, 260, r * 0.6, r * 0.3, r * 0.6, 0.04);
        burst(level, ParticleTypes.ELECTRIC_SPARK, c, 700, r * 0.6, r * 0.6, r * 0.6, 1.2);
        LINGERING.add(new Lingering(level, c, r, level.getGameTime() + 20 * 180));
    }

    private static final BlockState[] CRYSTALS = {
            Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(),
            Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState()};

    /** La chispa florece: el suelo alrededor se va convirtiendo en cristal y brotan racimos de amatista. */
    public static void genesisBloom(ServerLevel level, BlockPos target, float progress) {
        Vec3 c = Vec3.atCenterOf(target).add(0, 0.5, 0);
        RandomSource rnd = level.getRandom();
        boolean destroy = Config.DESTROY_BLOCKS.get();
        int reach = Math.round(5 + 40 * progress);
        if (destroy) {
            for (int i = 0; i < 14 + Math.round(progress * 30); i++) {
                double ang = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * reach;
                BlockPos pos = BlockPos.containing(c.x + Math.cos(ang) * rr, c.y + 8, c.z + Math.sin(ang) * rr);
                for (int k = 0; k < 24 && level.getBlockState(pos).isAir() && pos.getY() > level.getMinBuildHeight(); k++) pos = pos.below();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || level.getBlockEntity(pos) != null || state.getDestroySpeed(level, pos) < 0) continue;
                level.setBlock(pos, CRYSTALS[rnd.nextInt(CRYSTALS.length)], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                BlockPos above = pos.above();
                if (level.getBlockState(above).isAir() && rnd.nextFloat() < 0.35f) {
                    level.setBlock(above, Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        long time = level.getGameTime();
        if (time % 2 == 0) {
            burst(level, ParticleTypes.END_ROD, c.add(0, 20, 0), 50, reach * 0.3, 8, reach * 0.3, 0.3);
            burst(level, ParticleTypes.GLOW, c, 40, reach * 0.5, 2, reach * 0.5, 0.1);
            burst(level, ParticleTypes.ELECTRIC_SPARK, c.add(0, 20, 0), 30, 4, 4, 4, 0.6);
            burst(level, ParticleTypes.WAX_ON, c, 30, reach * 0.4, 1, reach * 0.4, 0.2);
        }
        if (time % 10 == 0) {
            level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), SoundSource.BLOCKS, 8f, 0.5f + progress * 1.2f);
            level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), SoundSource.BLOCKS, 8f, 0.6f + progress);
        }
    }

    /** Supernova de Génesis: el cráter más grande, revestido de cristales que brillan. */
    public static void genesisImpact(ServerLevel level, BlockPos target, @Nullable ServerPlayer owner) {
        Vec3 c = Vec3.atCenterOf(target).add(0, 0.5, 0);
        int r = Config.crater(2.1f, 120);
        boolean destroy = Config.DESTROY_BLOCKS.get();
        RandomSource rnd = level.getRandom();
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), SoundSource.BLOCKS, 20f, 0.25f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), SoundSource.HOSTILE, 20f, 0.35f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), SoundSource.HOSTILE, 16f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), SoundSource.BLOCKS, 20f, 0.5f);
        damageEntities(level, c, r, owner);
        if (destroy) {
            carveCrater(level, target, r, rnd);
            // revestir el cráter de cristal
            int rr = r + 3;
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rr) continue;
                    BlockPos col = target.offset(dx, r / 2 + 4, dz);
                    BlockPos pos = col;
                    for (int k = 0; k < r + 12 && level.getBlockState(pos).isAir() && pos.getY() > level.getMinBuildHeight(); k++) pos = pos.below();
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir() || level.getBlockEntity(pos) != null || st.getDestroySpeed(level, pos) < 0 || !st.getFluidState().isEmpty()) continue;
                    if (rnd.nextFloat() < 0.8f) level.setBlock(pos, CRYSTALS[rnd.nextInt(CRYSTALS.length)], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    BlockPos above = pos.above();
                    if (level.getBlockState(above).isAir() && rnd.nextFloat() < 0.18f) {
                        level.setBlock(above, Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
            // columnas de cristal alrededor
            for (int i = 0; i < 9; i++) {
                double ang = Math.PI * 2 * i / 9;
                BlockPos base = BlockPos.containing(c.x + Math.cos(ang) * (r + 6), c.y + 10, c.z + Math.sin(ang) * (r + 6));
                for (int k = 0; k < 30 && level.getBlockState(base).isAir() && base.getY() > level.getMinBuildHeight(); k++) base = base.below();
                int hgt = 6 + rnd.nextInt(10);
                for (int y = 1; y <= hgt; y++) {
                    BlockPos p = base.above(y);
                    if (!level.getBlockState(p).isAir()) break;
                    level.setBlock(p, y == hgt ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        level.explode(null, c.x, c.y + 4, c.z, 14f, Level.ExplosionInteraction.NONE);
        burst(level, ParticleTypes.EXPLOSION_EMITTER, c, 30, r * 0.5, r * 0.3, r * 0.5, 0);
        burst(level, ParticleTypes.FLASH, c, 16, 3, 3, 3, 0);
        burst(level, ParticleTypes.END_ROD, c, 1600, r * 0.6, r * 0.7, r * 0.6, 1.8);
        burst(level, ParticleTypes.GLOW, c, 900, r * 0.7, r * 0.5, r * 0.7, 0.6);
        burst(level, ParticleTypes.ELECTRIC_SPARK, c, 900, r * 0.6, r * 0.6, r * 0.6, 1.2);
        burst(level, ParticleTypes.WAX_ON, c, 600, r * 0.6, r * 0.4, r * 0.6, 0.5);
        burst(level, ParticleTypes.SOUL_FIRE_FLAME, c, 500, r * 0.6, r * 0.5, r * 0.6, 0.4);
        burst(level, ParticleTypes.CHERRY_LEAVES, c, 600, r * 0.7, r * 0.5, r * 0.7, 0.2);
        LINGERING.add(new Lingering(level, c, r, level.getGameTime() + 20 * 180));
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

    /** Radio del pozo que abre el rayo, crece con el tamaño de los cráteres. */
    private static int shaftRadius(float factor) {
        return Math.max(2, Math.min(28, Math.round(Config.SHAFT_RADIUS.get() * factor * (float) Math.sqrt(Config.CRATER_SCALE.get()))));
    }

    /**
     * Un cráter que se está excavando: se rompe capa por capa, de arriba hacia abajo, unos
     * cuantos miles de bloques por tick, para que los cráteres enormes no congelen el juego.
     */
    private static final class CraterJob {
        final ServerLevel level;
        final BlockPos target;
        final int r, reach, dyMax;
        final boolean pierce;
        final RandomSource rnd;
        final List<BlockPos> floor = new ArrayList<>();
        int debrisLeft, dy;

        CraterJob(ServerLevel level, BlockPos target, int r, RandomSource rnd) {
            this.level = level;
            this.target = target.immutable();
            this.r = r;
            this.rnd = rnd;
            this.pierce = Config.PIERCE_BEDROCK.get();
            this.debrisLeft = Config.DEBRIS_COUNT.get();
            // el borde irregular llega hasta r * 1.12, y el borde quemado 2 bloques más
            this.reach = (int) Math.ceil(r * 1.12 + 3);
            this.dyMax = (int) Math.ceil(reach / 1.5) + 1;
            this.dy = dyMax;
        }

        /** Excava capas hasta gastar el presupuesto; true cuando terminó. */
        boolean step(int budget) {
            BlockState air = Blocks.AIR.defaultBlockState();
            int side = 2 * reach + 1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (dy >= -dyMax && budget > 0) {
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz + (dy * 1.5) * (dy * 1.5));
                        double edge = r * (0.88 + 0.24 * hashNoise(dx, dy, dz));
                        if (dist > edge + 2.2) continue;
                        pos.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                        if (level.isOutsideBuildHeight(pos)) continue;
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) continue;
                        boolean unbreakable = state.getDestroySpeed(level, pos) < 0; // roca madre, etc.
                        if (unbreakable && !pierce) continue;
                        if (dist <= edge) {
                            boolean canFly = debrisLeft > 0 && !unbreakable && level.getBlockEntity(pos) == null
                                    && state.getFluidState().isEmpty() && dist > r * 0.35
                                    && rnd.nextFloat() < 0.18f * Math.min(1f, 22f / r);
                            BlockPos at = pos.immutable();
                            if (canFly) {
                                debrisLeft--;
                                FallingBlockEntity fb = FallingBlockEntity.fall(level, at, state);
                                fb.dropItem = false;
                                Vec3 out = new Vec3(dx, Math.abs(dy) + 2, dz).normalize();
                                double speed = 0.9 + rnd.nextDouble() * 1.6;
                                fb.setDeltaMovement(out.x * speed, 0.8 + rnd.nextDouble() * 1.4, out.z * speed);
                            } else {
                                if (level.getBlockEntity(at) != null) level.removeBlockEntity(at);
                                level.setBlock(at, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            }
                            if (dy <= -r / 3 + 1 && rnd.nextFloat() < 0.2f) floor.add(at);
                        } else if (!unbreakable && state.getFluidState().isEmpty()
                                && level.getBlockEntity(pos) == null && rnd.nextFloat() < 0.55f) {
                            // borde quemado: piedra negra, basalto y magma
                            float p = rnd.nextFloat();
                            BlockState scorched = p < 0.45f ? Blocks.BLACKSTONE.defaultBlockState()
                                    : p < 0.75f ? Blocks.BASALT.defaultBlockState()
                                    : p < 0.9f ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                    : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                            level.setBlock(pos.immutable(), scorched, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
                budget -= side * side;
                dy--;
            }
            if (dy >= -dyMax) return false;
            // fuego en el fondo del cráter
            for (BlockPos f : floor) {
                BlockPos below = f.below();
                if (level.getBlockState(f).isAir() && level.getBlockState(below).isSolidRender(level, below)) {
                    level.setBlock(f, BaseFireBlock.getState(level, f), Block.UPDATE_ALL);
                }
            }
            return true;
        }
    }

    private static final List<CraterJob> CRATERS = new ArrayList<>();

    private static void carveCrater(ServerLevel level, BlockPos target, int r, RandomSource rnd) {
        CraterJob job = new CraterJob(level, target, r, rnd);
        // los cráteres pequeños se hacen al instante, como antes
        if (!job.step(r <= 26 ? Integer.MAX_VALUE : 60_000)) CRATERS.add(job);
    }

    /** Sigue excavando los cráteres grandes (lo llama el tick del servidor). */
    static void tickCraters() {
        if (CRATERS.isEmpty()) return;
        Iterator<CraterJob> it = CRATERS.iterator();
        while (it.hasNext()) {
            CraterJob j = it.next();
            if (j.step(60_000)) it.remove();
        }
    }

    /**
     * El rayo atraviesa todo: abre un pozo desde el cráter hasta el fondo del mundo,
     * rompiendo también la roca madre. Las paredes quedan de magma, obsidiana y piedra negra.
     */
    private static void drillShaft(ServerLevel level, BlockPos target, int radius, RandomSource rnd) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int bottom = level.getMinBuildHeight();
        int wall = radius + 2;
        for (int y = target.getY(); y >= bottom; y--) {
            // el pozo se estrecha un poco con la profundidad y tiene bordes irregulares
            double depth = (target.getY() - y) / (double) Math.max(1, target.getY() - bottom);
            double rr = radius * (1.0 - 0.25 * depth);
            for (int dx = -wall; dx <= wall; dx++) {
                for (int dz = -wall; dz <= wall; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    double edge = rr * (0.9 + 0.2 * hashNoise(dx, y, dz));
                    BlockPos pos = new BlockPos(target.getX() + dx, y, target.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (d <= edge) {
                        if (state.isAir()) continue;
                        if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    } else if (d <= edge + 1.6 && !state.isAir() && state.getFluidState().isEmpty()
                            && level.getBlockEntity(pos) == null && rnd.nextFloat() < 0.6f) {
                        float p = rnd.nextFloat();
                        BlockState glass = p < 0.35f ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                : p < 0.6f ? Blocks.OBSIDIAN.defaultBlockState()
                                : p < 0.85f ? Blocks.BLACKSTONE.defaultBlockState()
                                : Blocks.CRYING_OBSIDIAN.defaultBlockState();
                        level.setBlock(pos, glass, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
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
                        com.amatista.arsenal.registry.ModSounds.PLASMA_CRACKLE.get(), SoundSource.BLOCKS, 3f, 0.6f);
            }
        }
    }

    private ImpactEffects() {}
}
