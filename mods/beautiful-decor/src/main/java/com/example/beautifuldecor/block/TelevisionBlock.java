package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.TelevisionBlockEntity;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

public class TelevisionBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");

    private static final VoxelShape DEFAULT_SHAPE = Shapes.or(
            Shapes.box(0.0, 3.0 / 16.0, 6.9 / 16.0, 1.0, 14.2 / 16.0, 8.5 / 16.0),
            Shapes.box(4.0 / 16.0, 0.0, 6.5 / 16.0, 12.0 / 16.0, 1.5 / 16.0, 9.5 / 16.0),
            Shapes.box(7.0 / 16.0, 1.5 / 16.0, 7.2 / 16.0, 9.0 / 16.0, 3.0 / 16.0, 8.5 / 16.0)
    );

    private final VoxelShape[] shapes;

    public TelevisionBlock(BlockBehaviour.Properties properties) {
        this(properties, DEFAULT_SHAPE);
    }

    /** northShape: forma pensada para facing=north; se gira sola según hacia dónde mire el televisor. */
    public TelevisionBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
        super(properties);
        this.shapes = ShapeRotator.all(northShape);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TelevisionBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Open GUI on client; server just acknowledges
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.ClientHooks.openTelevisionScreen(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Called from server packet handler */
    public static void applyPath(Level level, BlockPos pos, String path, boolean play) {
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TelevisionBlockEntity tv)) return;

        String clean = path == null ? "" : path.trim();
        boolean shouldPlay = play && !clean.isEmpty();
        tv.apply(clean, shouldPlay);

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BigTelevisionBlock big) {
            big.setOnAllParts(level, pos, state, shouldPlay);
        } else if (state.getBlock() instanceof TelevisionBlock && state.hasProperty(ON)) {
            level.setBlock(pos, state.setValue(ON, shouldPlay), 3);
        }
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS,
                0.6f, shouldPlay ? 1.2f : 0.85f);
    }
}
