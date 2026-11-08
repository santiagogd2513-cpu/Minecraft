package com.arsenalatomico.entity;

import com.arsenalatomico.MissilePath;
import com.arsenalatomico.registry.ModEntities;
import com.arsenalatomico.strike.Impacts;
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
 * Un misil en vuelo. Sigue su trayectoria exacta (sin física), deja una estela de humo y fuego,
 * y al llegar estalla. El cliente recibe la trayectoria al aparecer y la recorre igual.
 */
public class MissileEntity extends Entity implements IEntityAdditionalSpawnData {
    private MissilePath path;
    private int age;
    /** Tamaño del misil (1 = táctico; los intercontinentales son mucho más grandes). */
    private float scale = 1f;
    /** Fuerza de la explosión al llegar. */
    private float power = 4f;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static MissileEntity launch(ServerLevel level, MissilePath path, float scale, float power) {
        MissileEntity m = new MissileEntity(ModEntities.MISSILE.get(), level);
        m.path = path;
        m.scale = scale;
        m.power = power;
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
            trail(p, d);
        } else if (age >= path.flightTicks) {
            Impacts.missile((ServerLevel) level(), path.p3, power);
            discard();
        }
    }

    /** Estela: fuego en la tobera y humo que se queda en el aire. */
    private void trail(Vec3 p, Vec3 d) {
        Vec3 tail = p.subtract(d.scale(1.2 * scale));
        Level l = level();
        for (int i = 0; i < 2; i++) {
            Vec3 q = tail.subtract(d.scale(i * 0.5));
            l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, q.x, q.y, q.z,
                    (random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
        }
        l.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tail.x, tail.y, tail.z, -d.x * 0.2, -d.y * 0.2, -d.z * 0.2);
        if (age < 12) {
            // el humo del despegue
            for (int i = 0; i < 4; i++) {
                l.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, tail.x, tail.y, tail.z,
                        (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.4);
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
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        Vec3[] v = new Vec3[4];
        for (int i = 0; i < 4; i++) v[i] = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        path = new MissilePath(v[0], v[1], v[2], v[3], buf.readVarInt());
        age = buf.readVarInt();
        scale = buf.readFloat();
        Vec3 p = path.atTick(age);
        setPos(p.x, p.y, p.z);
        xo = p.x;
        yo = p.y;
        zo = p.z;
        aim(path.direction(age));
    }
}
