package com.example.beautifuldecor.item;

import com.example.beautifuldecor.block.DroneBlock;
import com.example.beautifuldecor.block.ModBlock;
import com.example.beautifuldecor.entity.DroneEntity;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class DroneRemoteItem extends Item {
    public DroneRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DroneBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // El bloque del dron se convierte en la entidad ya programada y se enciende.
        float yaw = state.getValue(ModBlock.FACING).toYRot();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        level.addFreshEntity(drone);
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            drone.startPiloting(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            DroneEntity controlled = findControlledDrone(serverPlayer);
            if (controlled != null) {
                controlled.stopPiloting(serverPlayer);
            } else {
                deployDrone(level, serverPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void deployDrone(Level level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 spawn = player.getEyePosition().add(look.scale(2.25));
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
        level.addFreshEntity(drone);
    }

    private static DroneEntity findControlledDrone(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof DroneEntity drone && drone.isPilotedBy(player)) {
                return drone;
            }
        }
        return null;
    }
}
