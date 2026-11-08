package com.example.beautifuldecor.item;

import com.example.beautifuldecor.block.ModBlock;
import com.example.beautifuldecor.block.RcCarBlock;
import com.example.beautifuldecor.entity.RcCarEntity;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Control remoto del carro RC (V3.1.0), distinto al del dron. Clic derecho sobre el bloque del carro lo enciende y empieza
 * a manejarlo; clic derecho sobre el carro ya encendido (entidad) lo hace desde RcCarInteractionEvents.
 */
public class RcCarRemoteItem extends Item {
    public RcCarRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RcCarBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // El bloque del carro se convierte en la entidad idéntica (mismo modelo y color) y se enciende.
        float yaw = state.getValue(ModBlock.FACING).toYRot();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        RcCarEntity car = new RcCarEntity(ModEntities.RC_CAR.get(), level);
        car.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        level.addFreshEntity(car);
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            car.startPiloting(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            RcCarEntity controlled = findControlledCar(serverPlayer);
            if (controlled != null) {
                controlled.stopPiloting(serverPlayer);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.beautifuldecor.rc_car_aim"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static RcCarEntity findControlledCar(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RcCarEntity car && car.isPilotedBy(player)) {
                return car;
            }
        }
        return null;
    }
}
