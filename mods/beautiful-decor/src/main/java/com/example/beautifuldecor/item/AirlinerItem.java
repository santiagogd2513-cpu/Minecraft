package com.example.beautifuldecor.item;

import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.entity.VehiclePaint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * V3.8.0: pone el avión sobre el bloque al que apuntas, mirando hacia donde miras tú (necesita un hueco libre de unos 7 x 7,5 bloques;
 * las alas y la cola no chocan con nada). Clic derecho en el centro del fuselaje para subirte.
 */
public class AirlinerItem extends Item {
    private final Supplier<? extends EntityType<? extends AirlinerEntity>> type;

    public AirlinerItem(Supplier<? extends EntityType<? extends AirlinerEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        Vec3 click = context.getClickLocation();
        BlockPos target = context.getClickedPos().relative(face);
        double x = face == Direction.UP ? click.x : target.getX() + 0.5;
        double y = face == Direction.UP ? click.y : target.getY();
        double z = face == Direction.UP ? click.z : target.getZ() + 0.5;

        AirlinerEntity airliner = this.type.get().create(level);
        if (airliner == null) {
            return InteractionResult.FAIL;
        }
        airliner.moveTo(x, y + 0.01, z, player != null ? player.getYRot() : 0.0f, 0.0f);
        if (!level.noCollision(airliner)) {
            return InteractionResult.FAIL;
        }
        VehiclePaint.restore(airliner, context.getItemInHand());     // sale con los colores con los que lo recogieron
        level.addFreshEntity(airliner);
        level.playSound(null, x, y, z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.8f, 0.8f);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
