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

    /** Cuántos misiles han caído (lo usa la prueba de GitHub). */
    public static int IMPACTS;
}
