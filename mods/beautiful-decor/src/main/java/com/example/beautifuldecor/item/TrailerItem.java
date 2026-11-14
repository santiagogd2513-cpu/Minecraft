package com.example.beautifuldecor.item;

import com.example.beautifuldecor.entity.TrailerEntity;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * V7.9.0: pone un remolque de la tractomula (furgón, troncos o cisterna) sobre el bloque al que apuntas, mirando como tú. El perno queda donde apuntas
 * y el remolque se extiende hacia atrás. Para engancharlo: retrocede con el tractor por debajo (se engancha solo) o aprieta el botón naranja del tablero.
 */
public class TrailerItem extends Item {
    private final int kind;

    public TrailerItem(int kind, Properties properties) {
        super(properties);
        this.kind = kind;
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

        TrailerEntity trailer = ModEntities.TRAILER.get().create(level);
        if (trailer == null) {
            return InteractionResult.FAIL;
        }
        trailer.setKind(this.kind);
        trailer.moveTo(x, y + 0.01, z, player != null ? player.getYRot() : 0.0f, 0.0f);
        level.addFreshEntity(trailer);
        level.playSound(null, x, y, z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5f, 1.2f);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
