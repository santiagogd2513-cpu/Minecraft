package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rampa de tobogán. FACING = hacia dónde sube (como las escaleras).
 * Cada pieza sube un bloque, así que una cadena en diagonal forma un tobogán que va subiendo.
 * Si al lado (un bloque más arriba) hay otra rampa mirando hacia la izquierda o la derecha,
 * la pieza se vuelve automáticamente una curva que conecta con ella.
 */
public class SlideRampBlock extends Block {
    public enum SlideShape implements StringRepresentable {
        STRAIGHT("straight"), LEFT("left"), RIGHT("right");

        private final String name;

        SlideShape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<SlideShape> SHAPE = EnumProperty.create("shape", SlideShape.class);

    private static final VoxelShape[][] SHAPES = new VoxelShape[3][4];

    static {
        for (SlideShape shape : SlideShape.values()) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                SHAPES[shape.ordinal()][direction.get2DDataValue()] = buildShape(shape, direction);
            }
        }
    }

    public SlideRampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(SHAPE, SlideShape.STRAIGHT));
    }

    // ---------- forma de colisión (celdas de 2x2 como una escalera suave) ----------
    private static VoxelShape buildShape(SlideShape shape, Direction facing) {
        VoxelShape result = Shapes.empty();
        int turns = (facing.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
        for (int j = 0; j < 8; j++) {
            for (int i = 0; i < 8; i++) {
                double cx = (i + 0.5) * 2.0;
                double cz = (j + 0.5) * 2.0;
                double height;
                if (shape == SlideShape.STRAIGHT) {
                    double h = 16.0 - j * 2.0;
                    boolean rail = cx < 2.0 || cx > 14.0;
                    height = rail ? Math.min(16.0, h + 3.0) : h;
                } else {
                    double x = shape == SlideShape.RIGHT ? cx : 16.0 - cx;
                    double u = 16.0 - x;
                    double w = 16.0 - cz;
                    double r = Math.hypot(u, w);
                    if (r >= 17.0) {
                        continue;
                    }
                    double h = Math.min(16.0, 1.0 + 15.0 * Math.toDegrees(Math.atan2(w, u)) / 90.0);
                    boolean floor = r >= 2.0 && r < 14.0;
                    height = floor ? h : Math.min(16.0, h + 3.0);
                }
                double x0 = i * 2.0 / 16.0, x1 = (i + 1) * 2.0 / 16.0;
                double z0 = j * 2.0 / 16.0, z1 = (j + 1) * 2.0 / 16.0;
                for (int t = 0; t < turns; t++) { // gira 90° en sentido horario
                    double nx0 = 1.0 - z1, nx1 = 1.0 - z0, nz0 = x0, nz1 = x1;
                    x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
                }
                result = Shapes.or(result, Shapes.box(x0, 0.0, z0, x1, height / 16.0, z1));
            }
        }
        return result;
    }

    // ---------- estado ----------
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return computeShape(state, context.getLevel(), context.getClickedPos());
    }

    private static boolean isSlideFacing(LevelAccessor level, BlockPos pos, Direction facing) {
        BlockState other = level.getBlockState(pos);
        return other.getBlock() instanceof SlideRampBlock && other.getValue(FACING) == facing;
    }

    private static BlockState computeShape(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        if (isSlideFacing(level, pos.relative(facing).above(), facing)) {
            return state.setValue(SHAPE, SlideShape.STRAIGHT);
        }
        Direction right = facing.getClockWise();
        Direction left = facing.getCounterClockWise();
        if (isSlideFacing(level, pos.relative(right).above(), right)) {
            return state.setValue(SHAPE, SlideShape.RIGHT);
        }
        if (isSlideFacing(level, pos.relative(left).above(), left)) {
            return state.setValue(SHAPE, SlideShape.LEFT);
        }
        return state.setValue(SHAPE, SlideShape.STRAIGHT);
    }

    /** La pieza anterior de la cadena está detrás y un bloque más abajo: hay que recalcular su curva. */
    private static void refreshPrevious(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        BlockPos previous = pos.relative(state.getValue(FACING).getOpposite()).below();
        BlockState previousState = level.getBlockState(previous);
        if (previousState.getBlock() instanceof SlideRampBlock) {
            BlockState updated = computeShape(previousState, level, previous);
            if (updated != previousState) {
                level.setBlock(previous, updated, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        refreshPrevious(level, pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!state.is(newState.getBlock())) {
            refreshPrevious(level, pos, state);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(SHAPE).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
