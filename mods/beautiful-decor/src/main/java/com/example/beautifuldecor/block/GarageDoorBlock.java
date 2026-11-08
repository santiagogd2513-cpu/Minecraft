package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.GarageDoorBlockEntity;
import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Puerta de garaje seccional de 3 de ancho x 3 de alto (9 piezas) que se abre HACIA ARRIBA (V2.0.0).
 * <p>
 * Se coloca como el televisor grande y la cámara frigorífica: el bloque donde haces clic es la esquina inferior
 * IZQUIERDA del frente y la puerta se extiende hacia tu derecha y hacia arriba.
 * <p>
 * PART = columna + 3 * fila (columna hacia la derecha, fila hacia arriba).
 * RAISE (0 a 12) = cuántos cuartos de bloque ha subido la hoja: 0 cerrada, 12 abierta del todo. Al abrir o cerrar,
 * RAISE cambia de a uno cada 2 ticks en las 9 piezas a la vez (así se ve subir y bajar con suavidad).
 * La puerta se abre con clic derecho o con redstone. Al cerrar espera a que no haya nadie debajo.
 * Rieles laterales y cajón superior siempre quedan; romper cualquier pieza quita toda la puerta (un solo ítem).
 * <p>
 * V2.3.0: los colores de la puerta, la franja y el marco son personalizables (tecla R apuntando a la puerta). Se guardan en
 * el block entity de la pieza 0 y los modelos los reciben como tinte (ver {@link GarageColors}).
 */
public class GarageDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public static final IntegerProperty RAISE = IntegerProperty.create("raise", 0, 12);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final int MAX_RAISE = 12;
    /** Ticks entre un paso y el siguiente (12 pasos = 24 ticks = 1,2 s). */
    private static final int STEP_TICKS = 2;

    /** [facing][part][raise] */
    private static final VoxelShape[][][] SHAPES = buildAllShapes();

    public GarageDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0)
                .setValue(RAISE, 0)
                .setValue(OPEN, Boolean.FALSE)
                .setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, RAISE, OPEN, POWERED);
    }

    // ------------------------------------------------------------------ formas

    private static VoxelShape px(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    /** Forma de una pieza mirando al norte (columna 0 = lado +x, las columnas crecen hacia -x). */
    private static VoxelShape northShape(int part, int raise) {
        int col = part % 3;
        int row = part / 3;
        VoxelShape shape = Shapes.empty();

        double railTop = row == 2 ? 13.0 : 16.0;      // en la fila de arriba el cajón ocupa el resto
        if (col == 0) {
            shape = Shapes.or(shape, px(14, 0, 4, 16, railTop, 12));
        } else if (col == 2) {
            shape = Shapes.or(shape, px(0, 0, 4, 2, railTop, 12));
        }
        if (row == 2) {
            shape = Shapes.or(shape, px(0, 13, 3, 16, 16, 13));
        }

        // Hoja de la puerta: lo que queda por encima de la altura a la que ha subido el borde inferior (4 px por paso).
        if (16 * row + 16 > 4 * raise) {
            double yLow = Math.max(0, 4 * raise - 16 * row);
            double xFrom = col == 2 ? 2 : 0;
            double xTo = col == 0 ? 14 : 16;
            shape = Shapes.or(shape, px(xFrom, yLow, 7, xTo, 16, 9));
        }
        return shape;
    }

    private static VoxelShape[][][] buildAllShapes() {
        VoxelShape[][][] all = new VoxelShape[4][9][MAX_RAISE + 1];
        for (int part = 0; part < 9; part++) {
            for (int raise = 0; raise <= MAX_RAISE; raise++) {
                VoxelShape north = northShape(part, raise);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    all[facing.get2DDataValue()][part][raise] = ShapeRotator.rotate(north, facing);
                }
            }
        }
        return all;
    }

    private static VoxelShape shapeFor(BlockState state) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(PART)][state.getValue(RAISE)];
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    // ------------------------------------------------------------------ posiciones de las piezas

    public static BlockPos partPos(BlockPos origin, Direction facing, int part) {
        return origin.relative(facing.getCounterClockWise(), part % 3).above(part / 3);
    }

    /** Esquina inferior izquierda del frente (pieza 0) a partir de cualquier pieza. */
    public static BlockPos originOf(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getClockWise(), part % 3).below(part / 3);
    }

    // ------------------------------------------------------------------ colores (V2.3.0)

    /** Solo la pieza de la esquina guarda los colores. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new GarageDoorBlockEntity(pos, state) : null;
    }

    /** Colores de la puerta a la que pertenece esta pieza (los por defecto si todavía no hay datos). */
    public static int[] colorsAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof GarageDoorBlock
                && level.getBlockEntity(originOf(pos, state)) instanceof GarageDoorBlockEntity door) {
            return door.getColors();
        }
        return GarageColors.defaults();
    }

    /** Servidor: cambia los colores de la puerta a la que pertenece la pieza de esa posición. */
    public static void applyColors(Level level, BlockPos pos, int[] colors) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof GarageDoorBlock
                && level.getBlockEntity(originOf(pos, state)) instanceof GarageDoorBlockEntity door) {
            door.setColors(colors);
        }
    }

    // ------------------------------------------------------------------ colocar y romper

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(origin, facing, part);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int part = 1; part < 9; part++) {
            level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), 3);
        }
        updatePower(level, pos, facing);
    }

    /** Si rompes una pieza que no es la esquina, la puerta igual suelta su ítem (una sola vez). */
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
            for (int part = 0; part < 9; part++) {
                BlockPos p = partPos(origin, facing, part);
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

    // ------------------------------------------------------------------ abrir y cerrar

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            setOpen(level, originOf(pos, state), state.getValue(FACING), !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Cambia el objetivo (abrir/cerrar) en las 9 piezas y arranca la animación desde la pieza de la esquina. */
    private void setOpen(Level level, BlockPos origin, Direction facing, boolean open) {
        boolean changed = false;
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(origin, facing, part);
            BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(OPEN) != open) {
                level.setBlock(p, s.setValue(OPEN, open), 2);
                changed = true;
            }
        }
        if (changed) {
            BlockPos center = partPos(origin, facing, 4);
            level.playSound(null, center, ModSounds.GARAGE_DOOR.get(), SoundSource.BLOCKS, 0.9f, open ? 1.0f : 0.92f);
            level.scheduleTick(origin, this, STEP_TICKS);
        }
    }

    /** Redstone: se abre al recibir señal en cualquier pieza y se cierra cuando se va. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        updatePower(level, originOf(pos, state), state.getValue(FACING));
    }

    private void updatePower(Level level, BlockPos origin, Direction facing) {
        BlockState originState = level.getBlockState(origin);
        if (!originState.is(this)) {
            return;
        }
        boolean powered = false;
        for (int part = 0; part < 9 && !powered; part++) {
            powered = level.hasNeighborSignal(partPos(origin, facing, part));
        }
        if (powered == originState.getValue(POWERED)) {
            return;
        }
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(origin, facing, part);
            BlockState s = level.getBlockState(p);
            if (s.is(this)) {
                level.setBlock(p, s.setValue(POWERED, powered), 2);
            }
        }
        if (powered != originState.getValue(OPEN)) {
            setOpen(level, origin, facing, powered);
        }
    }

    /** Un paso de la animación (solo la pieza de la esquina tiene tick programado). */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        boolean open = state.getValue(OPEN);
        int raise = state.getValue(RAISE);
        int next = open ? Math.min(MAX_RAISE, raise + 1) : Math.max(0, raise - 1);
        if (next == raise) {
            return;
        }
        if (!open && personUnder(level, pos, facing, next, raise)) {
            level.scheduleTick(pos, this, STEP_TICKS * 2);       // espera a que se aparte
            return;
        }

        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(pos, facing, part);
            BlockState s = level.getBlockState(p);
            if (s.is(this)) {
                level.setBlock(p, s.setValue(RAISE, next), 2);
            }
        }
        if (next != (open ? MAX_RAISE : 0)) {
            level.scheduleTick(pos, this, STEP_TICKS);
        } else if (!open) {
            level.playSound(null, partPos(pos, facing, 4), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.7f);
        }
    }

    /** ¿Hay alguien (o una moto, o un carrito) justo en la franja que la puerta va a ocupar al bajar un paso? */
    private static boolean personUnder(Level level, BlockPos origin, Direction facing, int nextRaise, int raise) {
        BlockPos last = partPos(origin, facing, 2);
        double minX = Math.min(origin.getX(), last.getX());
        double maxX = Math.max(origin.getX(), last.getX()) + 1.0;
        double minZ = Math.min(origin.getZ(), last.getZ());
        double maxZ = Math.max(origin.getZ(), last.getZ()) + 1.0;
        double y0 = origin.getY() + 0.25 * nextRaise;
        double y1 = origin.getY() + 0.25 * raise;
        // La hoja es una lámina fina (7/16 a 9/16 del bloque) en el centro del fondo.
        if (facing.getAxis() == Direction.Axis.Z) {
            minZ = origin.getZ() + 0.4375;
            maxZ = origin.getZ() + 0.5625;
        } else {
            minX = origin.getX() + 0.4375;
            maxX = origin.getX() + 0.5625;
        }
        AABB band = new AABB(minX, y0, minZ, maxX, y1, maxZ);
        List<Entity> inside = level.getEntitiesOfClass(Entity.class, band,
                e -> !e.isSpectator() && (e instanceof LivingEntity || e.canBeCollidedWith()));
        return !inside.isEmpty();
    }
}
