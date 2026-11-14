package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.CrossingLightBlock;
import com.example.beautifuldecor.block.CrossingNet;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** V7.8.0: con un detector encendido cerca, alterna las dos luces rojas cada medio segundo y hace tin. */
public class CrossingLightBlockEntity extends BlockEntity {
    private int timer;

    public CrossingLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROSSING_LIGHT.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrossingLightBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        int lamp = state.getValue(CrossingLightBlock.LAMP);
        if (CrossingNet.active(level, pos, CrossingNet.LINK_RANGE)) {
            if (be.timer++ % 10 == 0) {
                int next = lamp == 1 ? 2 : 1;
                level.setBlock(pos, state.setValue(CrossingLightBlock.LAMP, next), 3);
                level.playSound(null, pos, ModSounds.CROSSING_BELL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        } else {
            be.timer = 0;
            if (lamp != 0) {
                level.setBlock(pos, state.setValue(CrossingLightBlock.LAMP, 0), 3);
            }
        }
    }
}
