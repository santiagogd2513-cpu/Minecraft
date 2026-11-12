package com.arsenalatomico.item;

import com.arsenalatomico.entity.TrainCarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.Vec3;

/** El Tren Lanzamisiles en el inventario: clic derecho sobre un riel normal para poner el tren completo, mirando hacia donde miras. */
public class TrainItem extends Item {
    public TrainItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof BaseRailBlock)) {
            pos = pos.relative(ctx.getClickedFace());
            if (!(level.getBlockState(pos).getBlock() instanceof BaseRailBlock)) return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        Vec3 look = p != null ? p.getLookAngle() : Vec3.ZERO;
        if (TrainCarEntity.spawnTrain((ServerLevel) level, pos, look) == null) {
            if (p != null) p.displayClientMessage(Component.translatable("message.arsenalatomico.train.no_track"), true);
            return InteractionResult.FAIL;
        }
        if (p == null || !p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
