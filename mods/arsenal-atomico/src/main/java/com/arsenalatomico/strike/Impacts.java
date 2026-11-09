package com.arsenalatomico.strike;

import com.arsenalatomico.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Lo que pasa en el mundo cuando cae un misil. */
public final class Impacts {
    private Impacts() {}

    /** Misil táctico (o lo que se le pase de fuerza): explosión, fuego y humo. */
    public static void missile(ServerLevel level, Vec3 at, float power) {
        float p = (float) (power * Config.POWER.get());
        level.explode(null, at.x, at.y + 0.5, at.z, p,
                Config.DESTROY_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1, at.z, 12, p * 0.3, 0.5, p * 0.3, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 2, at.z, 40, p * 0.4, p * 0.3, p * 0.4, 0.04);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, 8, p * 0.3, 0.3, p * 0.3, 0.01);
        IMPACTS++;
    }

    /**
     * Misil intercontinental sin carga nuclear: una explosión grande en el centro y un anillo de
     * explosiones alrededor, una bola de fuego y una columna de humo.
     */
    public static void big(ServerLevel level, Vec3 at, float power) {
        float p = (float) (power * Config.POWER.get());
        Level.ExplosionInteraction mode = Config.DESTROY_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(null, at.x, at.y + 1, at.z, p, mode);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.4;
            level.explode(null, at.x + Math.cos(a) * p * 0.75, at.y + 0.5, at.z + Math.sin(a) * p * 0.75, p * 0.65f, mode);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 2, at.z, 8, p * 0.5, p * 0.3, p * 0.5, 0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 3, at.z, 260, p * 0.45, p * 0.4, p * 0.45, 0.25);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 2, at.z, 60, p * 0.4, 1, p * 0.4, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 4, at.z, 200, p * 0.5, p * 0.5, p * 0.5, 0.06);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 2, at.z, 60, p * 0.35, 1, p * 0.35, 0.03);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.HOSTILE, 16f, 0.5f);
        IMPACTS++;
    }

    /** Cuántos misiles han caído (lo usa la prueba de GitHub). */
    public static int IMPACTS;
}
