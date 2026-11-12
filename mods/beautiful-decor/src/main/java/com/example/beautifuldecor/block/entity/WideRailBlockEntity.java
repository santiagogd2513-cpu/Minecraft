package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.entity.RailPath;
import com.example.beautifuldecor.entity.WideArc;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** V6.1.0: un pedazo de curva ancha: guarda a qué arco pertenece (bloque esquina, los dos lados de las rectas y el nivel k) y calcula su tramo. */
public class WideRailBlockEntity extends BlockEntity {
    private BlockPos corner = BlockPos.ZERO;
    private int side1;
    private int side2;
    private int level;
    @Nullable
    private WideArc.Piece piece;
    private boolean pieceDone;

    public WideRailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIDE_RAIL.get(), pos, state);
    }

    public void set(BlockPos corner, RailPath.Side s1, RailPath.Side s2, int level) {
        this.corner = corner;
        this.side1 = s1.ordinal();
        this.side2 = s2.ordinal();
        this.level = level;
        this.pieceDone = false;
        this.piece = null;
        this.setChanged();
    }

    public BlockPos corner() {
        return this.corner;
    }

    public RailPath.Side side1() {
        return RailPath.Side.values()[this.side1];
    }

    public RailPath.Side side2() {
        return RailPath.Side.values()[this.side2];
    }

    public int arcLevel() {
        return this.level;
    }

    /** El tramo de este bloque (null si los datos no cuadran con este sitio). */
    @Nullable
    public WideArc.Piece piece() {
        if (!this.pieceDone) {
            this.pieceDone = true;
            this.piece = null;
            if (this.level >= 1) {
                BlockPos p = this.getBlockPos();
                for (WideArc.Cell c : WideArc.cells(this.corner.getX(), this.corner.getZ(), this.side1(), this.side2(), this.level)) {
                    if (c.x == p.getX() && c.z == p.getZ()) {
                        this.piece = c.piece;
                        break;
                    }
                }
            }
        }
        return this.piece;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Corner", this.corner.asLong());
        tag.putInt("S1", this.side1);
        tag.putInt("S2", this.side2);
        tag.putInt("K", this.level);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.corner = BlockPos.of(tag.getLong("Corner"));
        this.side1 = Math.floorMod(tag.getInt("S1"), 4);
        this.side2 = Math.floorMod(tag.getInt("S2"), 4);
        this.level = tag.getInt("K");
        this.pieceDone = false;
        this.piece = null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
