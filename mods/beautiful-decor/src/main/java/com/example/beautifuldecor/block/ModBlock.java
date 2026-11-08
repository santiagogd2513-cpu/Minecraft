package com.example.beautifuldecor.block;

import com.example.beautifuldecor.entity.SeatEntity;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ModBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final VoxelShape shape;
    private final boolean canSit;
    private final double sitYOffset;

    private static final VoxelShape DEFAULT_SHAPE = Shapes.box(0.5/16.0, 0.0, 0.5/16.0, 15.5/16.0, 1.0, 15.5/16.0);

    public ModBlock(BlockBehaviour.Properties properties) {
        this(properties, DEFAULT_SHAPE, false, 0.4);
    }

    public ModBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        this(properties, shape, false, 0.4);
    }

    public ModBlock(BlockBehaviour.Properties properties, VoxelShape shape, boolean canSit) {
        this(properties, shape, canSit, 0.4);
    }

    public ModBlock(BlockBehaviour.Properties properties, VoxelShape shape, boolean canSit, double sitYOffset) {
        super(properties);
        this.shape = shape;
        this.canSit = canSit;
        this.sitYOffset = sitYOffset;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Faces the player (opposite of look direction) so front of item faces you
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (canSit && !player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                SeatEntity seat = new SeatEntity(level, pos, sitYOffset);
                level.addFreshEntity(seat);
                player.startRiding(seat);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1/16.0, y1/16.0, z1/16.0, x2/16.0, y2/16.0, z2/16.0);
    }
}
