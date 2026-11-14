package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.CrossingDetectorBlock;
import com.example.beautifuldecor.block.CrossingNet;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** V7.8.0: mira cada cuarto de segundo si hay un tren en movimiento a ~18 bloques; se queda encendido 3 segundos después de que pase. */
public class CrossingDetectorBlockEntity extends BlockEntity {
    public static final double RANGE = 18.0;
    private static final double SCAN = 70.0;
    private static final double LEAD_SECONDS = 3.5;
    private static final int HOLD_CHECKS = 12;          // 12 revisiones x 5 ticks = 3 s
    private int hold;
    private int clock;

    public CrossingDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROSSING_DETECTOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            CrossingNet.add(this.level, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide) {
            CrossingNet.remove(this.level, this.worldPosition);
        }
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrossingDetectorBlockEntity be) {
        if (level.isClientSide || ++be.clock % 5 != 0) {
            return;
        }
        AABB box = new AABB(pos).inflate(SCAN, 12.0, SCAN);
        boolean seen = false;
        for (TrainCarEntity car : level.getEntitiesOfClass(TrainCarEntity.class, box)) {
            double v = Math.sqrt(car.position().distanceToSqr(car.xo, car.yo, car.zo));          // bloques por tick
            if (v > 0.01) {
                // 18 bloques como mínimo; un tren rápido se avisa antes para que dé tiempo a bajar la barrera (~3,5 s de margen)
                double reach = Math.max(RANGE, v * 20.0 * LEAD_SECONDS);
                double dx = car.getX() - (pos.getX() + 0.5);
                double dz = car.getZ() - (pos.getZ() + 0.5);
                if (dx * dx + dz * dz <= reach * reach && Math.abs(car.getY() - pos.getY()) <= 12.0) {
                    seen = true;
                    break;
                }
            }
        }
        if (seen) {
            be.hold = HOLD_CHECKS;
        } else if (be.hold > 0) {
            be.hold--;
        }
        boolean on = be.hold > 0;
        if (on != state.getValue(CrossingDetectorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(CrossingDetectorBlock.POWERED, on), 3);
            level.updateNeighborsAt(pos, state.getBlock());
            level.updateNeighborsAt(pos.below(), state.getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Hold", this.hold);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.hold = tag.getInt("Hold");
    }
}
