package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cinta transportadora (V2.6.0). Lleva hacia donde mira ({@link #FACING}) todo lo que esté encima: ítems tirados, esferas de
 * experiencia, mobs y jugadores (agacharse con Shift sirve para quedarse quieto).
 * <p>
 * Las cintas se unen solas, como los rieles: una cinta que recibe a otra por un costado (y nada por detrás) se vuelve esquina y
 * gira el recorrido. Basta poner una cinta mirando hacia otro lado al final de la anterior, o poner la anterior apuntando a ella,
 * en cualquier orden. Si recibe cinta por detrás es recta, aunque también entre cinta por los costados (se mezclan).
 */
public class ConveyorBeltBlock extends Block {
    /** Forma de la cinta según quién la alimenta. */
    public enum Shape implements StringRepresentable {
        STRAIGHT("straight"),
        CURVE_LEFT("curve_left"),     // la cinta que la alimenta está a su izquierda
        CURVE_RIGHT("curve_right");   // ... o a su derecha

        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    /** Altura de la superficie, en bloques (4 de 16). */
    public static final double TOP = 4.0 / 16.0;
    private static final VoxelShape BASE = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);
    /** Velocidad de ítems y esferas (se pierde un 40 % por la fricción: unos 1,3 bloques por segundo). */
    private static final double ITEM_SPEED = 0.11;
    /** Empuje por tick a seres vivos. */
    private static final double LIVING_PUSH = 0.05;

    public ConveyorBeltBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    /** Cierto si en esa posición hay una cinta que empuja hacia la dirección dada. */
    private static boolean feeds(BlockGetter level, BlockPos from, Direction towards) {
        BlockState s = level.getBlockState(from);
        return s.getBlock() instanceof ConveyorBeltBlock && s.getValue(FACING) == towards;
    }

    /** La forma que le toca a una cinta en esa posición mirando hacia {@code facing}, según las cintas de alrededor. */
    public static Shape shapeFor(BlockGetter level, BlockPos pos, Direction facing) {
        if (feeds(level, pos.relative(facing.getOpposite()), facing)) {
            return Shape.STRAIGHT;
        }
        boolean left = feeds(level, pos.relative(facing.getCounterClockWise()), facing.getClockWise());
        boolean right = feeds(level, pos.relative(facing.getClockWise()), facing.getCounterClockWise());
        if (left && !right) {
            return Shape.CURVE_LEFT;
        }
        if (right && !left) {
            return Shape.CURVE_RIGHT;
        }
        return Shape.STRAIGHT;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        return this.defaultBlockState().setValue(FACING, facing).setValue(SHAPE, shapeFor(context.getLevel(), context.getClickedPos(), facing));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(SHAPE, shapeFor(level, pos, state.getValue(FACING)));
        }
        return state;
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
        return BASE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BASE;
    }

    /**
     * Hacia dónde empuja la cinta a algo que está en ese punto: en una esquina, primero hacia el centro por donde entra y
     * después hacia donde mira.
     */
    static Direction directionAt(BlockState state, BlockPos pos, double x, double z) {
        Direction facing = state.getValue(FACING);
        Shape shape = state.getValue(SHAPE);
        if (shape == Shape.STRAIGHT) {
            return facing;
        }
        Direction entry = shape == Shape.CURVE_LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        double along = (x - (pos.getX() + 0.5)) * entry.getStepX() + (z - (pos.getZ() + 0.5)) * entry.getStepZ();
        return along < 0.0 ? entry : facing;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        boolean player = entity instanceof Player;
        if (level.isClientSide != player) {
            return;                         // los jugadores se mueven en su cliente; todo lo demás lo mueve el servidor
        }
        boolean living = entity instanceof LivingEntity;
        if (!living && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            return;
        }
        if (!entity.onGround() || entity.isShiftKeyDown() || !pos.equals(entity.blockPosition())) {
            return;
        }
        double above = entity.getY() - pos.getY();
        if (above < TOP - 0.05 || above > TOP + 0.5) {
            return;
        }
        Direction dir = directionAt(state, pos, entity.getX(), entity.getZ());
        Direction side = dir.getClockWise();
        var motion = entity.getDeltaMovement();
        if (living) {
            entity.setDeltaMovement(motion.x + dir.getStepX() * LIVING_PUSH, motion.y, motion.z + dir.getStepZ() * LIVING_PUSH);
        } else {
            // se va acercando al centro de la cinta para no caerse por un costado
            double lateral = (entity.getX() - (pos.getX() + 0.5)) * side.getStepX() + (entity.getZ() - (pos.getZ() + 0.5)) * side.getStepZ();
            double pull = Mth.clamp(-lateral * 0.3, -0.04, 0.04);
            entity.setDeltaMovement(dir.getStepX() * ITEM_SPEED + side.getStepX() * pull, motion.y,
                    dir.getStepZ() * ITEM_SPEED + side.getStepZ() * pull);
        }
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
