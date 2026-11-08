package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Cámara frigorífica: estructura de 3 de ancho x 3 de alto x 3 de fondo (27 piezas) con paredes, piso y techo
 * delgados. Se coloca como el televisor grande: el bloque donde haces clic es la esquina inferior IZQUIERDA del
 * frente y el resto se extiende hacia tu derecha, hacia arriba y hacia el fondo.
 * <p>
 * PART = px + 3 * py + 9 * pz (px = columna hacia la derecha, py = piso, pz = fila hacia el fondo).
 * La única entrada es la puerta (px = 1, pz = 0, py = 0 y 1): clic derecho para abrirla o cerrarla.
 * La pieza (1, 2, 1) lleva el bombillo del techo (ver ModBlocks: luz 14 solo en esa pieza).
 * Romper cualquier pieza quita toda la cámara y suelta un solo ítem.
 */
public class ColdRoomBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);

    /** Pieza del techo, en el centro, donde está el bombillo. */
    public static final int LIGHT_PART = 16;
    private static final int DOOR_LOWER = 1;
    private static final int DOOR_UPPER = 4;
    private static final double T = 2.0;

    /** [facing][part][0 = cerrada / forma completa, 1 = puerta abierta]. */
    private static final VoxelShape[][][] SHAPES = buildAllShapes();

    public ColdRoomBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, Boolean.FALSE)
                .setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, PART);
    }

    // ------------------------------------------------------------------ formas

    private static VoxelShape px(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    /** Forma de una pieza mirando al norte (frente hacia -z, px crece hacia -x, pz crece hacia +z). */
    private static VoxelShape northShape(int part, boolean doorOpen) {
        int colX = part % 3;
        int row = (part / 3) % 3;
        int depth = part / 9;
        boolean floor = row == 0;
        boolean ceiling = row == 2;
        boolean left = colX == 0;
        boolean right = colX == 2;
        boolean front = depth == 0;
        boolean back = depth == 2;
        boolean door = colX == 1 && depth == 0 && row <= 1;
        double y0 = floor ? T : 0.0;
        double y1 = ceiling ? 16.0 - T : 16.0;
        double zf = front ? T : 0.0;
        double zb = back ? 16.0 - T : 16.0;

        VoxelShape shape = Shapes.empty();
        if (floor) {
            shape = Shapes.or(shape, px(0, 0, 0, 16, T, 16));
        }
        if (ceiling) {
            shape = Shapes.or(shape, px(0, 16.0 - T, 0, 16, 16, 16));
        }
        if (left) {
            shape = Shapes.or(shape, px(16.0 - T, y0, zf, 16, y1, zb));
        }
        if (right) {
            shape = Shapes.or(shape, px(0, y0, zf, T, y1, zb));
        }
        if (back) {
            shape = Shapes.or(shape, px(0, y0, 16.0 - T, 16, y1, 16));
        }
        if (front && !door) {
            shape = Shapes.or(shape, px(0, y0, 0, 16, y1, T));
        }
        if (door) {
            shape = Shapes.or(shape, px(0, y0, 0, 1.5, 16, T));
            shape = Shapes.or(shape, px(14.5, y0, 0, 16, 16, T));
            if (row == 1) {
                shape = Shapes.or(shape, px(1.5, 15, 0, 14.5, 16, T));
            }
            if (!doorOpen) {
                if (row == 0) {
                    shape = Shapes.or(shape, px(1.5, T, 0, 14.5, 16, 3));
                } else {
                    shape = Shapes.or(shape, px(1.5, 0, 0, 14.5, 15, 3));
                }
            }
        }
        return shape;
    }

    private static VoxelShape[][][] buildAllShapes() {
        VoxelShape[][][] all = new VoxelShape[4][27][2];
        for (int part = 0; part < 27; part++) {
            for (int open = 0; open < 2; open++) {
                VoxelShape north = northShape(part, open == 1);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    all[facing.get2DDataValue()][part][open] = ShapeRotator.rotate(north, facing);
                }
            }
        }
        return all;
    }

    private static VoxelShape shapeFor(BlockState state, boolean collision) {
        int open = collision && state.getValue(OPEN) ? 1 : 0;
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(PART)][open];
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state, false);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state, true);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shapeFor(state, true);
    }

    /** Las paredes delgadas tapan la luz del sol y la del exterior (como las losas): el interior solo se ilumina con el bombillo. */
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    // ------------------------------------------------------------------ posiciones de las piezas

    private static BlockPos partPos(BlockPos origin, Direction facing, int colX, int row, int depth) {
        return origin.relative(facing.getCounterClockWise(), colX).above(row).relative(facing.getOpposite(), depth);
    }

    /** Esquina inferior izquierda del frente (pieza 0) a partir de cualquier pieza. */
    private static BlockPos originOf(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getClockWise(), part % 3)
                .below((part / 3) % 3)
                .relative(facing, part / 9);
    }

    // ------------------------------------------------------------------ colocar y romper

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int part = 0; part < 27; part++) {
            BlockPos p = partPos(origin, facing, part % 3, (part / 3) % 3, part / 9);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(OPEN, Boolean.FALSE).setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int part = 1; part < 27; part++) {
            BlockPos p = partPos(pos, facing, part % 3, (part / 3) % 3, part / 9);
            level.setBlock(p, state.setValue(PART, part).setValue(OPEN, Boolean.FALSE), 3);
        }
    }

    /** Si rompes una pieza que no es la esquina, la cámara igual suelta su ítem (una sola vez). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) != 0) {
            BlockPos origin = originOf(pos, state);
            BlockState originState = level.getBlockState(origin);
            if (originState.is(this)) {
                Block.dropResources(originState, level, origin, null, player, player.getMainHandItem());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            Direction facing = state.getValue(FACING);
            BlockPos origin = originOf(pos, state);
            for (int part = 0; part < 27; part++) {
                BlockPos p = partPos(origin, facing, part % 3, (part / 3) % 3, part / 9);
                if (p.equals(pos)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(FACING) == facing && s.getValue(PART) == part) {
                    level.removeBlock(p, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ------------------------------------------------------------------ puerta

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        int part = state.getValue(PART);
        if (part != DOOR_LOWER && part != DOOR_UPPER) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean open = !state.getValue(OPEN);
            Direction facing = state.getValue(FACING);
            BlockPos origin = originOf(pos, state);
            for (int doorPart : new int[]{DOOR_LOWER, DOOR_UPPER}) {
                BlockPos p = partPos(origin, facing, doorPart % 3, (doorPart / 3) % 3, doorPart / 9);
                BlockState s = level.getBlockState(p);
                if (s.is(this)) {
                    level.setBlock(p, s.setValue(OPEN, open), 3);
                }
            }
            level.playSound(null, pos,
                    open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
