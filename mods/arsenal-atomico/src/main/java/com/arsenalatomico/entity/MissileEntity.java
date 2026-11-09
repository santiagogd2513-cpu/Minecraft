package com.arsenalatomico.entity;

import com.arsenalatomico.MissilePath;
import com.arsenalatomico.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * Un misil en vuelo. Sigue su trayectoria exacta (sin física) y deja una estela de humo y fuego.
 * La explosión la pone el LaunchManager a la hora exacta. El cliente recibe la trayectoria al
 * aparecer y la recorre igual.
 */
public class MissileEntity extends Entity implements IEntityAdditionalSpawnData {
    private MissilePath path;
    private int age;
    /** Tamaño del misil (1 = táctico; los intercontinentales son mucho más grandes). */
    private float scale = 1f;
    /** 0 = misil táctico, 1 = intercontinental. */
    private int kind;
    /** Ticks hasta que se enciende el motor (los grandes salen empujados por gas o vapor). */
    private int ignite;
    private boolean soundStarted, ignited, boomed;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Lanza un misil. Estalla cuando lo diga el LaunchManager (por reloj), no él solo. */
    public static MissileEntity launch(ServerLevel level, MissilePath path, float scale, int kind, int ignite) {
        MissileEntity m = new MissileEntity(ModEntities.MISSILE.get(), level);
        m.path = path;
        m.scale = scale;
        m.kind = kind;
        m.ignite = ignite;
        Vec3 p = path.atTick(0);
        m.setPos(p.x, p.y, p.z);
        m.aim(path.direction(0));
        level.addFreshEntity(m);
        return m;
    }

    public MissilePath path() {
        return path;
    }

    public int age() {
        return age;
    }

    public float scale() {
        return scale;
    }

    public int kind() {
        return kind;
    }

    public int ignite() {
        return ignite;
    }

    private void aim(Vec3 d) {
        setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
        setXRot((float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
    }

    @Override
    public void tick() {
        super.tick();
        if (path == null) {
            if (!level().isClientSide) discard();
            return;
        }
        age++;
        Vec3 p = path.atTick(age);
        Vec3 d = path.direction(age);
        setPos(p.x, p.y, p.z);
        aim(d);
        if (level().isClientSide) {
            if (!soundStarted) {
                soundStarted = true;
                com.arsenalatomico.client.MissileSounds.start(this);
            }
            if (!ignited && age >= ignite) {
                ignited = true;
                if (ignite > 0) com.arsenalatomico.client.MissileSounds.ignite(this);
            }
            if (!boomed && age >= path.flightTicks - 1) {
                boomed = true;
                com.arsenalatomico.client.MissileSounds.impact(path.p3, kind);
            }
            if (kind == 2) {
                if (age == com.arsenalatomico.CondorPlan.SEP1 || age == com.arsenalatomico.CondorPlan.SEP2) {
                    com.arsenalatomico.client.MissileSounds.separate(this, age == com.arsenalatomico.CondorPlan.SEP1 ? 1 : 2);
                }
                condorTrail(p, d);
            } else if (kind == 1) bigTrail(p, d);
            else trail(p, d);
        } else if (age > path.flightTicks + 60) {
            discard();   // por si el LaunchManager ya no está (no debería pasar)
        }
    }

    /** Estela: fuego que sale de la tobera y una columna de humo blanco que se queda en el aire. */
    private void trail(Vec3 p, Vec3 d) {
        Vec3 tail = p.subtract(d.scale(1.45 * scale));
        Level l = level();
        // llamas que salen disparadas hacia atrás
        for (int i = 0; i < 3; i++) {
            double k = 0.35 + random.nextDouble() * 0.35;
            l.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tail.x, tail.y, tail.z,
                    -d.x * k + (random.nextDouble() - 0.5) * 0.06, -d.y * k + (random.nextDouble() - 0.5) * 0.06,
                    -d.z * k + (random.nextDouble() - 0.5) * 0.06);
        }
        if (age % 2 == 0) l.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, tail.x, tail.y, tail.z, 0, 0, 0);
        Vec3 q = tail.subtract(d.scale(2.5 * scale));
        l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, q.x, q.y, q.z,
                (random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
        if (age < 14) {
            // la nube del despegue, que se esparce por el suelo
            for (int i = 0; i < 5; i++) {
                l.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, tail.x, tail.y, tail.z,
                        (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.5);
            }
            l.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, tail.x, tail.y, tail.z,
                    (random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2);
        }
    }

    /** Estela del Cóndor: primero la etapa grande (con la nube del silo), luego la segunda, y la ojiva con plasma. */
    private void condorTrail(Vec3 p, Vec3 d) {
        Level l = level();
        int s1 = com.arsenalatomico.CondorPlan.SEP1, s2 = com.arsenalatomico.CondorPlan.SEP2;
        if (age < s1) {
            Vec3 tail = p.subtract(d.scale(13.8 * scale));
            for (int i = 0; i < 10; i++) {
                double k = 0.8 + random.nextDouble() * 0.8;
                l.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tail.x, tail.y, tail.z,
                        -d.x * k + (random.nextDouble() - 0.5) * 0.25, -d.y * k + (random.nextDouble() - 0.5) * 0.25,
                        -d.z * k + (random.nextDouble() - 0.5) * 0.25);
            }
            l.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, tail.x, tail.y, tail.z, 0, 0, 0);
            for (int i = 0; i < 4; i++) {
                Vec3 q = tail.subtract(d.scale(5 + i * 3)).add((random.nextDouble() - 0.5) * 2.4, (random.nextDouble() - 0.5) * 2.4,
                        (random.nextDouble() - 0.5) * 2.4);
                l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, q.x, q.y, q.z,
                        (random.nextDouble() - 0.5) * 0.04, 0.01, (random.nextDouble() - 0.5) * 0.04);
            }
            if (age < 70) {
                // el humo y el fuego que salen por la boca del silo
                Vec3 rim = path.p0.add(0, 14.5, 0);
                for (int i = 0; i < 10; i++) {
                    l.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                            rim.x + (random.nextDouble() - 0.5) * 6, rim.y, rim.z + (random.nextDouble() - 0.5) * 6,
                            (random.nextDouble() - 0.5) * 0.9, 0.05 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.9);
                }
            }
        } else if (age < s2) {
            Vec3 tail = p.subtract(d.scale(2.2 * scale));
            for (int i = 0; i < 4; i++) {
                double k = 0.5 + random.nextDouble() * 0.5;
                l.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tail.x, tail.y, tail.z, -d.x * k, -d.y * k, -d.z * k);
            }
            l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, tail.x, tail.y, tail.z, 0, 0.01, 0);
        } else {
            // la ojiva entrando a la atmósfera: chispas de plasma que quedan atrás
            for (int i = 0; i < 4; i++) {
                Vec3 q = p.add((random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2);
                l.addAlwaysVisibleParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME, true, q.x, q.y, q.z,
                        -d.x * 0.3, -d.y * 0.3, -d.z * 0.3);
            }
        }
    }

    /** Estela del intercontinental: antes de encenderse, solo gas o vapor; después, fuego y una columna de humo. */
    private void bigTrail(Vec3 p, Vec3 d) {
        Level l = level();
        double len = 9.6 * scale;
        Vec3 tail = p.subtract(d.scale(len));
        boolean water = !l.getFluidState(BlockPos.containing(tail)).isEmpty();
        if (age < ignite) {
            // salida en frío: el gas lo saca del contenedor (o el vapor del agua)
            for (int i = 0; i < 6; i++) {
                l.addAlwaysVisibleParticle(water ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.CLOUD, true,
                        tail.x + (random.nextDouble() - 0.5) * 2, tail.y, tail.z + (random.nextDouble() - 0.5) * 2,
                        (random.nextDouble() - 0.5) * 0.3, 0.2, (random.nextDouble() - 0.5) * 0.3);
            }
            if (age < 3 || water) {
                for (int i = 0; i < 8; i++) {
                    l.addAlwaysVisibleParticle(water ? ParticleTypes.SPLASH : ParticleTypes.LARGE_SMOKE, true,
                            tail.x + (random.nextDouble() - 0.5) * 3, tail.y + 1, tail.z + (random.nextDouble() - 0.5) * 3,
                            (random.nextDouble() - 0.5) * 0.4, 0.3, (random.nextDouble() - 0.5) * 0.4);
                }
            }
            return;
        }
        for (int i = 0; i < 6; i++) {
            double k = 0.6 + random.nextDouble() * 0.6;
            l.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tail.x, tail.y, tail.z,
                    -d.x * k + (random.nextDouble() - 0.5) * 0.15, -d.y * k + (random.nextDouble() - 0.5) * 0.15,
                    -d.z * k + (random.nextDouble() - 0.5) * 0.15);
        }
        l.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, tail.x, tail.y, tail.z, 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            Vec3 q = tail.subtract(d.scale(4 + i * 2.5)).add((random.nextDouble() - 0.5) * 1.6, (random.nextDouble() - 0.5) * 1.6,
                    (random.nextDouble() - 0.5) * 1.6);
            l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, q.x, q.y, q.z,
                    (random.nextDouble() - 0.5) * 0.03, 0.01, (random.nextDouble() - 0.5) * 0.03);
        }
        if (age < ignite + 40) {
            // el humo del despegue, que llena todo alrededor de la lanzadora
            for (int i = 0; i < 6; i++) {
                l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, tail.x, tail.y, tail.z,
                        (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.6);
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        for (Vec3 v : new Vec3[]{path.p0, path.p1, path.p2, path.p3}) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
        buf.writeVarInt(path.flightTicks);
        buf.writeVarInt(age);
        buf.writeFloat(scale);
        buf.writeVarInt(kind);
        buf.writeVarInt(ignite);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        Vec3[] v = new Vec3[4];
        for (int i = 0; i < 4; i++) v[i] = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        path = new MissilePath(v[0], v[1], v[2], v[3], buf.readVarInt());
        age = buf.readVarInt();
        scale = buf.readFloat();
        kind = buf.readVarInt();
        ignite = buf.readVarInt();
        Vec3 p = path.atTick(age);
        setPos(p.x, p.y, p.z);
        xo = p.x;
        yo = p.y;
        zo = p.z;
        aim(path.direction(age));
    }
}
