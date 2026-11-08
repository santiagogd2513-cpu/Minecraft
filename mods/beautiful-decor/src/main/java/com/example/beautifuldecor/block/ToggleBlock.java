package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ToggleBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final VoxelShape DOOR_SHAPE = Shapes.box(0.0, 0.0, 6.0/16.0, 1.0, 2.0, 10.0/16.0);
    public static final VoxelShape SMALL_SHAPE = Shapes.box(0.2, 0.0, 0.2, 0.8, 0.6, 0.8);
    public static final VoxelShape DOG_DOOR_SHAPE = Shapes.box(2.0/16.0, 0.0, 6.0/16.0, 14.0/16.0, 12.0/16.0, 10.0/16.0);

    private final VoxelShape shape;
    /** Formas giradas según FACING (solo para puertas); null en las luces, que siguen usando {@link #shape} tal cual. */
    private final VoxelShape[] rotatedShapes;
    private final boolean isLight;

    public ToggleBlock(BlockBehaviour.Properties properties, VoxelShape shape, boolean isLight) {
        super(properties);
        this.shape = shape;
        this.isLight = isLight;
        // V1.8.1: las puertas (no son luces) giran su colisión igual que su modelo. Antes la forma era siempre
        // una losa norte-sur, y al mirar al este/oeste quedaba un hueco por donde se pasaba entre dos hojas.
        this.rotatedShapes = isLight ? null : ShapeRotator.all(shape);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(OPEN, Boolean.FALSE)
                .setValue(FACING, Direction.NORTH));
    }

    public ToggleBlock(BlockBehaviour.Properties properties) {
        this(properties, DOOR_SHAPE, false);
    }

    public ToggleBlock(BlockBehaviour.Properties properties, boolean unusedGarageFlag) {
        // kept for compatibility; always use door shape now
        this(properties, DOOR_SHAPE, false);
    }

    public boolean isSwitchableLight() {
        return isLight;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPEN, false);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private VoxelShape shapeFor(BlockState state) {
        return rotatedShapes == null ? shape : rotatedShapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        }
        return shapeFor(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (isLight && com.example.beautifuldecor.block.entity.LightSwitchBlockEntity
                    .tryLinkActiveSwitch(level, pos, player)) {
                return InteractionResult.SUCCESS;
            }
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), 3);
            if (isLight) {
                level.playSound(null, pos,
                        open ? SoundEvents.BOTTLE_FILL_DRAGONBREATH : SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.5f, open ? 1.2f : 0.8f);
            } else {
                level.playSound(null, pos,
                        open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                        SoundSource.BLOCKS, 0.8f, 1.0f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
