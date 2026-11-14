package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.CrossingBarrierBlock;
import com.example.beautifuldecor.block.CrossingMechanismBlock;
import com.example.beautifuldecor.block.CrossingNet;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.init.ModBlocks;
import com.example.beautifuldecor.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * V7.8.0: con un detector encendido cerca, espera 2 segundos (suena el semáforo) y baja la barrera en cuatro pasos; cuando el detector se apaga la sube.
 * Si un tren en movimiento choca con la barrera, la rompe.
 */
public class CrossingMechanismBlockEntity extends BlockEntity {
    private static final int WARN_TICKS = 40;
    private int warn;
    private int clock;
    /** 0 = arriba, 3 = abajo; se guarda aquí para no depender del bloque de la barrera. */
    private int arm;

    public CrossingMechanismBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROSSING_MECHANISM.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrossingMechanismBlockEntity be) {
        if (level.isClientSide || ++be.clock % 2 != 0) {
            return;
        }
        Direction f = state.getValue(CrossingMechanismBlock.FACING);
        if (!CrossingMechanismBlock.hasArm(level, pos, f)) {
            be.warn = 0;
            be.arm = 0;
            return;
        }
        boolean active = CrossingNet.active(level, pos, CrossingNet.LINK_RANGE);
        int target = be.arm;
        if (active) {
            if (be.warn < 1000) {
                be.warn += 2;
            }
            if (be.warn >= WARN_TICKS && be.warn % 4 == 0 && be.arm < 3) {
                target = be.arm + 1;
            }
        } else {
            be.warn = 0;
            if (be.arm > 0 && be.clock % 4 == 0) {
                target = be.arm - 1;
            }
        }
        if (target != be.arm) {
            be.arm = target;
            CrossingMechanismBlock.setArm(level, pos, target);
        } else {
            // el cliente o un comando pudo cambiar la barrera: se mantiene en la posición del mecanismo
            BlockState first = level.getBlockState(pos.relative(f, 1));
            if (first.getValue(CrossingBarrierBlock.ARM) != be.arm) {
                CrossingMechanismBlock.setArm(level, pos, be.arm);
            }
        }
        if (be.arm >= 1) {
            be.breakIfTrain(level, pos, f);
        }
    }

    /** Un tren en movimiento que toca la barrera la rompe. */
    private void breakIfTrain(Level level, BlockPos pos, Direction f) {
        BlockPos a = pos.relative(f, 1);
        BlockPos b = pos.relative(f, 3);
        AABB box = new AABB(a).minmax(new AABB(b)).deflate(0.2, 0.0, 0.2);
        for (TrainCarEntity car : level.getEntitiesOfClass(TrainCarEntity.class, box)) {
            if (car.position().distanceToSqr(car.xo, car.yo, car.zo) > 1.0e-4) {
                for (int i = 1; i <= 3; i++) {
                    BlockPos p = pos.relative(f, i);
                    BlockState s = level.getBlockState(p);
                    if (s.is(ModBlocks.CROSSING_BARRIER.get())) {
                        level.destroyBlock(p, false);
                    }
                }
                Block.popResource(level, a, new ItemStack(ModItems.CROSSING_BARRIER.get()));
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 0.7f);
                this.arm = 0;
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Arm", this.arm);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.arm = tag.getInt("Arm");
    }
}
