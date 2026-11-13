package com.example.beautifuldecor.lottery;

import com.example.beautifuldecor.entity.DeliveryTruckEntity;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * V6.15.0: el camión AMAZOOM que trae el premio de la lotería. Maneja solo (sin pasajeros): va hacia donde está el ganador; si el
 * ganador está bajo techo, busca la puerta más cercana y se estaciona delante. Allí pita tres veces, deja el premio detrás del camión
 * y se va. Si se atasca o tarda demasiado, aparece junto al destino. Todo es del servidor.
 */
public final class PrizeTruckDriver {
    public static final int ARRIVING = 1;
    public static final int DELIVERING = 2;
    public static final int LEAVING = 3;
    private static final double[] SPEED = {0.0, 0.30, 0.50, 0.80};           // por premio (1 gordo, 2 mediano, 3 pequeño), bloques por tick
    private static final float[] TURN = {0f, 0, 25, -25, 50, -50, 80, -80, 110, -110};
    private static final int GIVE_UP = 3600;

    private PrizeTruckDriver() {
    }

    // ------------------------------------------------------------------ premios

    public static ItemStack[] prize(int tier) {
        return switch (tier) {
            case 1 -> new ItemStack[]{new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 64)};
            case 2 -> new ItemStack[]{new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.GOLD_INGOT, 32)};
            default -> new ItemStack[]{new ItemStack(Items.IRON_INGOT, 32)};
        };
    }

    // ------------------------------------------------------------------ aparecer

    /** Manda un camión con el premio al jugador. Devuelve false si no pudo (no hay sitio). */
    public static boolean dispatch(ServerPlayer player, int tier) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        Vec3 target = target(level, player);
        Vec3 spawn = null;
        double start = level.random.nextDouble() * 360.0;
        for (int i = 0; i < 12 && spawn == null; i++) {
            double ang = Math.toRadians(start + i * 30.0);
            double dist = i < 8 ? 40.0 : 18.0;
            int x = Mth.floor(target.x + Math.cos(ang) * dist);
            int z = Mth.floor(target.z + Math.sin(ang) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 64, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
                continue;
            }
            Vec3 p = new Vec3(x + 0.5, y, z + 0.5);
            if (level.noCollision(new AABB(p.x - 1.5, p.y + 0.1, p.z - 1.5, p.x + 1.5, p.y + 2.8, p.z + 1.5))) {
                spawn = p;
            }
        }
        if (spawn == null) {
            spawn = new Vec3(target.x, target.y, target.z);
        }
        DeliveryTruckEntity truck = ModEntities.DELIVERY_TRUCK.get().create(level);
        if (truck == null) {
            return false;
        }
        truck.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
        truck.setYRot((float) Math.toDegrees(Math.atan2(-(target.x - spawn.x), target.z - spawn.z)));
        truck.startPrize(tier, player.getUUID(), target, spawn);
        level.addFreshEntity(truck);
        return true;
    }

    /** Dónde debe quedar el camión: delante de la puerta más cercana si el ganador está bajo techo, si no, a su lado. */
    private static Vec3 target(ServerLevel level, ServerPlayer player) {
        BlockPos at = player.blockPosition();
        if (level.canSeeSky(at)) {
            return new Vec3(player.getX() + 4.0, player.getY(), player.getZ());
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-24, -6, -24), at.offset(24, 6, 24))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                double d = p.distSqr(at);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        if (best != null) {
            Direction f = level.getBlockState(best).getValue(DoorBlock.FACING);
            Vec3 pick = null;
            for (Direction side : new Direction[]{f, f.getOpposite()}) {
                BlockPos out = best.relative(side, 5);
                if (level.canSeeSky(out.above(2))) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, out.getX(), out.getZ());
                    Vec3 v = new Vec3(out.getX() + 0.5, Math.max(y, best.getY()), out.getZ() + 0.5);
                    if (pick == null) {
                        pick = v;
                    }
                }
            }
            if (pick != null) {
                return pick;
            }
        }
        return new Vec3(player.getX(), player.getY(), player.getZ());      // sin puerta: lo deja donde está el jugador
    }

    // ------------------------------------------------------------------ manejo

    public static void drive(DeliveryTruckEntity t) {
        if (!(t.level() instanceof ServerLevel level)) {
            return;
        }
        t.prizeTicks++;
        Vec3 goal = t.prizeStage == LEAVING ? t.prizeSpawn : t.prizeTarget;
        double dx = goal.x - t.getX();
        double dz = goal.z - t.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (t.prizeStage == ARRIVING) {
            if (dist < 3.0) {
                t.prizeStage = DELIVERING;
                t.prizeTicks = 0;
            } else if (t.prizeTicks > GIVE_UP) {
                t.setPos(t.prizeTarget.x, t.prizeTarget.y + 0.2, t.prizeTarget.z);       // se atascó: aparece en el destino
                t.prizeStage = DELIVERING;
                t.prizeTicks = 0;
            }
        }
        if (t.prizeStage == DELIVERING) {
            t.setDeltaMovement(0.0, t.getDeltaMovement().y, 0.0);
            if (t.prizeTicks == 5 || t.prizeTicks == 20 || t.prizeTicks == 35) {
                t.honk();
            }
            if (t.prizeTicks == 45) {
                drop(level, t);
                t.prizeStage = LEAVING;
                t.prizeTicks = 0;
            }
            return;
        }
        if (t.prizeStage == LEAVING && (t.prizeTicks > 300 || dist < 4.0)) {
            t.discard();
            return;
        }
        steer(level, t, dx, dz, SPEED[Mth.clamp(t.prizeTier, 1, 3)] * (t.prizeStage == LEAVING ? 0.8 : 1.0));
    }

    private static void steer(ServerLevel level, DeliveryTruckEntity t, double dx, double dz, double speed) {
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        AABB box = t.getBoundingBox();
        for (float turn : TURN) {
            float h = want + turn;
            double vx = -Mth.sin(h * Mth.DEG_TO_RAD) * speed;
            double vz = Mth.cos(h * Mth.DEG_TO_RAD) * speed;
            boolean lift = false;
            if (!level.noCollision(t, box.move(vx, 0.0, vz))) {
                if (!level.noCollision(t, box.move(vx, 1.01, vz))) {
                    continue;
                }
                lift = true;
            }
            if (lift && t.onGround()) {
                t.setPos(t.getX(), t.getY() + 1.01, t.getZ());
            }
            float yaw = t.getYRot() + Mth.clamp(Mth.wrapDegrees(h - t.getYRot()), -9f, 9f);
            t.setYRot(yaw);
            t.yRotO = yaw;
            t.setDeltaMovement(vx / 0.82, t.getDeltaMovement().y, vz / 0.82);   // la fricción del suelo (0,82) se come el resto
            return;
        }
        t.setDeltaMovement(0.0, t.getDeltaMovement().y, 0.0);                    // sin salida por ahora: espera al tiempo límite
    }

    private static void drop(ServerLevel level, DeliveryTruckEntity t) {
        float yaw = t.getYRot() * Mth.DEG_TO_RAD;
        double bx = t.getX() + Mth.sin(yaw) * 3.8;                // detrás del camión
        double bz = t.getZ() - Mth.cos(yaw) * 3.8;
        for (ItemStack stack : prize(t.prizeTier)) {
            ItemEntity e = new ItemEntity(level, bx + (level.random.nextDouble() - 0.5), t.getY() + 0.6, bz + (level.random.nextDouble() - 0.5), stack.copy());
            e.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2, (level.random.nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(e);
        }
        ServerPlayer owner = t.prizeOwner == null ? null : level.getServer().getPlayerList().getPlayer(t.prizeOwner);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.beautifuldecor.lottery_delivered"));
        }
    }
}
