package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * V7.1.0: atropellos. Todo vehículo (y tren) que se mueve y toca a un ser vivo le hace daño que crece con el cuadrado de la velocidad
 * (energía) y depende del vehículo: una bici casi no lo toca, una moto sí (y el conductor, sin protección, se lleva parte del golpe),
 * un coche o camión lo mata a velocidad alta, un tren casi a cualquier velocidad y un avión en despegue no deja salvación.
 * Todo corre en el servidor; la velocidad se saca de lo que se movió el vehículo en el último tick.
 */
public final class VehicleImpact {
    private VehicleImpact() {
    }

    public static final ResourceKey<DamageType> RUN_OVER = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(BeautifulDecor.MOD_ID, "run_over"));

    /**
     * @param at100       daño (corazones/2) que hace a 100 km/h; a otra velocidad escala con (km/h / 100)^2
     * @param minKmh      por debajo de esto no hace nada
     * @param knock       empuje (0 = casi nada, 1 = normal)
     * @param driverShare fracción del daño que se lleva el conductor (sin protección: moto)
     */
    public record Profile(double at100, double minKmh, double knock, double driverShare) {
    }

    public static final Profile BICYCLE = new Profile(3.0, 6.0, 0.25, 0.0);
    public static final Profile MOTORCYCLE = new Profile(26.0, 12.0, 0.8, 0.55);
    public static final Profile CAR = new Profile(34.0, 10.0, 1.0, 0.0);
    public static final Profile TRUCK = new Profile(60.0, 8.0, 0.8, 0.0);
    public static final Profile TRAIN = new Profile(500.0, 3.0, 0.25, 0.0);
    public static final Profile HELICOPTER = new Profile(150.0, 8.0, 1.0, 0.0);
    public static final Profile AIRLINER = new Profile(900.0, 8.0, 1.5, 0.0);
    public static final Profile DRONE = new Profile(6.0, 20.0, 0.6, 0.0);
    public static final Profile RC_CAR = new Profile(3.0, 15.0, 0.5, 0.0);

    private static final int COOLDOWN = 10;
    private static final Map<Entity, Long> LAST_HIT = new WeakHashMap<>();

    /** Un tick de atropellos del vehículo {@code v} (solo servidor). */
    public static void tick(Entity v, Profile p, @Nullable LivingEntity driver) {
        if (!(v.level() instanceof ServerLevel level) || p == null) {
            return;
        }
        Vec3 m = new Vec3(v.getX() - v.xo, v.getY() - v.yo, v.getZ() - v.zo);
        double kmh = m.length() * 20.0 * 3.6;
        if (kmh < p.minKmh() || kmh > 1500.0) {                       // más de 1500: el vehículo se teletransportó, no es un golpe
            return;
        }
        List<AABB> boxes = new ArrayList<>();
        if (v.isMultipartEntity() && v.getParts() != null) {
            for (Entity part : v.getParts()) {
                boxes.add(part.getBoundingBox());
            }
        }
        if (boxes.isEmpty()) {
            boxes.add(v.getBoundingBox());
        }
        double dmg = p.at100() * (kmh / 100.0) * (kmh / 100.0);
        Vec3 dir = new Vec3(m.x, 0.0, m.z);
        dir = dir.lengthSqr() > 1.0e-8 ? dir.normalize() : Vec3.ZERO;
        long now = level.getGameTime();
        for (AABB box : boxes) {
            AABB sweep = box.inflate(0.1, 0.0, 0.1).expandTowards(m);
            for (Entity e : level.getEntities(v, sweep, x -> hittable(x, v))) {
                if (e.getBoundingBox().minY >= box.maxY - 0.15) {     // va parado encima (techo del vagón): no es un golpe
                    continue;
                }
                Long last = LAST_HIT.get(e);
                if (last != null && now - last < COOLDOWN) {
                    continue;
                }
                LAST_HIT.put(e, now);
                hit(level, v, p, driver, (LivingEntity) e, kmh, dmg, dir);
            }
        }
    }

    private static boolean hittable(Entity e, Entity v) {
        return e instanceof LivingEntity && !(e instanceof ArmorStand) && e.isAlive() && !e.isSpectator() && !e.isPassenger()
                && e.getRootVehicle() != v.getRootVehicle();
    }

    private static DamageSource source(ServerLevel level, Entity v, @Nullable LivingEntity driver) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RUN_OVER), v, driver);
    }

    private static void hit(ServerLevel level, Entity v, Profile p, @Nullable LivingEntity driver, LivingEntity victim, double kmh, double dmg, Vec3 dir) {
        DamageSource src = source(level, v, driver);
        victim.hurt(src, (float) Math.max(0.5, dmg));
        if (victim.isAlive() && dir != Vec3.ZERO) {
            double push = p.knock() * Math.min(3.0, kmh / 60.0) * 0.6;
            victim.setDeltaMovement(victim.getDeltaMovement().add(dir.x * push, 0.15 + 0.1 * Math.min(2.0, push), dir.z * push));
            victim.hurtMarked = true;
        }
        if (p.driverShare() > 0.0 && driver != null && driver.isAlive() && dmg > 1.0) {
            driver.hurt(src, (float) (dmg * p.driverShare()));
        }
        if (dmg >= 4.0) {
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), dmg >= 12.0 ? SoundEvents.GENERIC_BIG_FALL : SoundEvents.GENERIC_SMALL_FALL, SoundSource.PLAYERS, 1.0f,
                    0.8f + level.random.nextFloat() * 0.3f);
        }
    }

    /** Aspas del helicóptero: un disco a esa altura sobre el casco; quien lo toque con las aspas girando recibe un golpe mortal. */
    public static void rotor(Entity heli, @Nullable LivingEntity driver, double rpm, double radius, double height) {
        if (rpm < 0.35 || !(heli.level() instanceof ServerLevel level)) {
            return;
        }
        AABB disk = new AABB(heli.getX() - radius, heli.getY() + height, heli.getZ() - radius, heli.getX() + radius, heli.getY() + height + 0.4, heli.getZ() + radius);
        long now = level.getGameTime();
        for (Entity e : level.getEntities(heli, disk, x -> hittable(x, heli))) {
            Long last = LAST_HIT.get(e);
            if (last != null && now - last < COOLDOWN) {
                continue;
            }
            LAST_HIT.put(e, now);
            LivingEntity victim = (LivingEntity) e;
            victim.hurt(source(level, heli, driver), (float) (30.0 * Mth.clamp(rpm, 0.0, 1.0)));
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
    }
}
