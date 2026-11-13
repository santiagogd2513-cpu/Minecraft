package com.example.beautifuldecor.item;

import com.example.beautifuldecor.block.FreightRailBlock;
import com.example.beautifuldecor.entity.RailPath;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.entity.VehiclePaint;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * V4.8.0: ítem de la locomotora y de cada vagón. Se coloca sobre un riel de carga; si queda cerca del final de otro tren (hasta unos
 * 14 bloques) se engancha solo a él, en el mismo sentido; si no, queda suelto mirando hacia donde mira el jugador.
 */
public class TrainCarItem extends Item {
    private final TrainCarEntity.Variant variant;

    public TrainCarItem(TrainCarEntity.Variant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!this.isMyRail(level, pos)) {
            pos = pos.relative(context.getClickedFace());
            if (!this.isMyRail(level, pos)) {
                if (!level.isClientSide && context.getPlayer() != null && level.getBlockState(pos).getBlock() instanceof FreightRailBlock) {
                    context.getPlayer().displayClientMessage(Component.translatable("message.beautifuldecor.train_wrong_rail"), true);
                }
                return InteractionResult.PASS;
            }
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        ServerLevel server = (ServerLevel) level;
        TrainCarEntity car = ModEntities.TRAIN_CAR.get().create(level);
        if (car == null) {
            return InteractionResult.FAIL;
        }
        car.setVariant(this.variant);
        if (this.variant.isSteam()) {
            car.addCargo(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 128));   // V6.16.0: sale con carbón para poder arrancar
        }
        VehiclePaint.restore(car, context.getItemInHand());
        RailPath.Lookup lk = car.railLookup();
        double wb = this.variant.wheelbase;

        // ---- ¿hay un tren cerca al que engancharse?
        Vec3 click = Vec3.atCenterOf(pos);
        TrainCarEntity bestAnchor = null;
        boolean behind = true;
        RailPath.Cursor bestFront = null;
        double best = 14.0;
        List<TrainCarEntity> near = level.getEntitiesOfClass(TrainCarEntity.class, new AABB(pos).inflate(40.0));
        for (TrainCarEntity other : near) {
            if (!other.isPlaced() || other.variant().family != this.variant.family) {
                continue;
            }
            if (other.isTail()) {
                RailPath.Cursor rear = other.rearCursor(lk);
                RailPath.Cursor front = rear.copy();
                front.advance(-TrainCarEntity.stride(other.variant(), this.variant), lk);
                double d = centerOf(front, wb, lk).distanceTo(click);
                if (d < best) {
                    best = d;
                    bestAnchor = other;
                    behind = true;
                    bestFront = front;
                }
            }
            if (other.isHead()) {
                RailPath.Cursor rearOfNew = other.frontCursor();
                rearOfNew.advance(TrainCarEntity.stride(this.variant, other.variant()), lk);
                RailPath.Cursor front = rearOfNew.copy();
                front.advance(wb, lk);
                double d = centerOf(front, wb, lk).distanceTo(click);
                if (d < best) {
                    best = d;
                    bestAnchor = other;
                    behind = false;
                    bestFront = front;
                }
            }
        }
        RailPath.Cursor front;
        if (bestAnchor != null) {
            front = bestFront;
            if (!fits(front, wb, lk)) {
                return fail(player, "message.beautifuldecor.train_no_track");
            }
        } else {
            RailPath.Shape shape = FreightRailBlock.pathShape(level.getBlockState(pos));
            double[] t = new double[2];
            shape.tangent(shape.length() / 2.0, t);
            Vec3 look = player != null ? player.getLookAngle() : Vec3.ZERO;
            int dir = (t[0] * look.x + t[1] * look.z) >= 0 ? 1 : -1;
            front = new RailPath.Cursor(pos.getX(), pos.getY(), pos.getZ(), shape, shape.length() / 2.0, dir);
            front.advance(wb / 2.0, lk);
            if (!fits(front, wb, lk)) {
                return fail(player, "message.beautifuldecor.train_no_track");
            }
        }
        car.placeOn(front);
        server.addFreshEntity(car);
        if (bestAnchor != null) {
            if (behind) {
                bestAnchor.coupleBehind(car);
            } else {
                car.coupleBehind(bestAnchor);
            }
        }
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** El riel de ese sitio es de la familia de este coche (carga o pasajeros). */
    private boolean isMyRail(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FreightRailBlock rail && rail.family() == this.variant.family;
    }

    /** Caben los dos bogies en la vía (hay riel hasta el bogie trasero). */
    private static boolean fits(RailPath.Cursor front, double wheelbase, RailPath.Lookup lk) {
        RailPath.Cursor c = front.copy();
        return Math.abs(c.advance(-wheelbase, lk)) > wheelbase - 1.0e-6;
    }

    private static Vec3 centerOf(RailPath.Cursor front, double wheelbase, RailPath.Lookup lk) {
        RailPath.Cursor rear = front.copy();
        rear.advance(-wheelbase, lk);
        double[] a = new double[3];
        double[] b = new double[3];
        front.worldPoint(a);
        rear.worldPoint(b);
        return new Vec3((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0, (a[2] + b[2]) / 2.0);
    }

    private static InteractionResult fail(Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
        return InteractionResult.FAIL;
    }
}
