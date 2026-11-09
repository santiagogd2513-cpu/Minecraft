package com.arsenalatomico.item;

import com.arsenalatomico.entity.TruckEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** El camión militar en el inventario: clic derecho en el suelo para ponerlo, mirando hacia donde miras. */
public class TruckItem extends Item {
    public TruckItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!level.isClientSide) {
            Player p = ctx.getPlayer();
            float yaw = p != null ? p.getYRot() : 0;
            TruckEntity t = TruckEntity.create(level, Vec3.atBottomCenterOf(pos), yaw, false);
            if (!level.noCollision(t, t.getBoundingBox())) return InteractionResult.FAIL;
            level.addFreshEntity(t);
            if (p == null || !p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
