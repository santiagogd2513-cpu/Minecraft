package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.TrafficLightBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightBlockEntity extends BlockEntity {
    // 30 seconds = 600 ticks
    private static final int CYCLE_TICKS = 600;
    private int timer = 0;

    public TrafficLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAFFIC_LIGHT.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TrafficLightBlockEntity be) {
        if (level.isClientSide) return;
        be.timer++;
        if (be.timer >= CYCLE_TICKS) {
            be.timer = 0;
            TrafficLightBlock.LightColor current = state.getValue(TrafficLightBlock.COLOR);
            TrafficLightBlock.LightColor next;
            switch (current) {
                case RED -> next = TrafficLightBlock.LightColor.GREEN;
                case GREEN -> next = TrafficLightBlock.LightColor.YELLOW;
                default -> next = TrafficLightBlock.LightColor.RED;
            }
            level.setBlock(pos, state.setValue(TrafficLightBlock.COLOR, next), 3);
            TrafficLightBlock.playChangeSound(level, pos);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Timer", timer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        timer = tag.getInt("Timer");
    }
}
