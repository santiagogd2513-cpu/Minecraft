package com.example.beautifuldecor.block;

import com.example.beautifuldecor.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * V7.8.0: barrera del cruce. Son 3 bloques seguidos (parte 0 pegada al mecanismo, 1 y 2 hacia afuera) que se ponen de un solo clic.
 * {@code arm}: 3 = abajo (cerrada), 2 = casi abajo (10 grados), 1 = subiendo (solo queda el primer tramo inclinado), 0 = arriba (solo un poste en la parte 0).
 */
public class CrossingBarrierBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public static final IntegerProperty ARM = IntegerProperty.create("arm", 0, 3);

    private static final Direction[] DIRS = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    /** [dirección][arm][part] */
    private static final VoxelShape[][][] COLLISION = new VoxelShape[4][4][3];
    private static final VoxelShape[][][] OUTLINE = new VoxelShape[4][4][3];

    static {
        for (int d = 0; d < 4; d++) {
            for (int arm = 0; arm < 4; arm++) {
                for (int part = 0; part < 3; part++) {
                    Direction f = DIRS[d];
                    VoxelShape small = rot(f, 6, 6, 6, 10, 10, 10);
                    VoxelShape coll;
                    VoxelShape out;
                    if (arm >= 2) {
                        coll = rot(f, 0, 0, 6, 16, 16, 10);
                        out = coll;
                    } else if (arm == 1 && part == 0) {
                        coll = rot(f, 0, 0, 6, 8, 16, 10);
                        out = coll;
                    } else if (arm == 0 && part == 0) {
                        coll = rot(f, 0, 0, 6, 3, 16, 10);
                        out = coll;
                    } else {
                        coll = Shapes.empty();
                        out = small;
                    }
                    COLLISION[d][arm][part] = coll;
                    OUTLINE[d][arm][part] = out;
                }
            }
        }
    }

    public CrossingBarrierBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.EAST).setValue(PART, 0).setValue(ARM, 0));
    }

    /** Caja escrita para una barrera que sale hacia el este (x = a lo largo del brazo); se gira hacia {@code f}. */
    private static VoxelShape rot(Direction f, double x1, double y1, double z1, double x2, double y2, double z2) {
        int n = f == Direction.SOUTH ? 1 : f == Direction.WEST ? 2 : f == Direction.NORTH ? 3 : 0;
        double a1 = x1, b1 = z1, a2 = x2, b2 = z2;
        for (int i = 0; i < n; i++) {
            double na1 = 16.0 - b2;
            double na2 = 16.0 - b1;
            double nb1 = a1;
            double nb2 = a2;
            a1 = na1;
            a2 = na2;
            b1 = nb1;
            b2 = nb2;
        }
        return Shapes.box(a1 / 16.0, y1 / 16.0, b1 / 16.0, a2 / 16.0, y2 / 16.0, b2 / 16.0);
    }

    private static int dirIndex(Direction f) {
        return f == Direction.SOUTH ? 1 : f == Direction.WEST ? 2 : f == Direction.NORTH ? 3 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, ARM);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE[dirIndex(state.getValue(FACING))][state.getValue(ARM)][state.getValue(PART)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION[dirIndex(state.getValue(FACING))][state.getValue(ARM)][state.getValue(PART)];
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            Block.popResource(level, pos, new ItemStack(ModItems.CROSSING_BARRIER.get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Si se quita un tramo se van los otros dos (no sueltan nada: el objeto se suelta una sola vez). */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            Direction f = state.getValue(FACING);
            BlockPos start = pos.relative(f.getOpposite(), state.getValue(PART));
            for (int i = 0; i < 3; i++) {
                BlockPos p = start.relative(f, i);
                if (p.equals(pos)) {
                    continue;
                }
                BlockState other = level.getBlockState(p);
                if (other.getBlock() == this && other.getValue(FACING) == f && other.getValue(PART) == i) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
