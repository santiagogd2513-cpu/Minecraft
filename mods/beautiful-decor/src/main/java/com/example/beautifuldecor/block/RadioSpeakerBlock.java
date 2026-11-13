package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.RadioSpeakerBlockEntity;
import com.example.beautifuldecor.item.RadioSpeakerItem;
import com.example.beautifuldecor.radio.RadioEntry;
import com.example.beautifuldecor.radio.RadioHub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * V4.2.0: bafle de la radio de mesa. Si el ítem venía vinculado a una radio, al ponerlo queda listo: suena lo que tenga puesto esa
 * radio, esté donde esté el bafle (mientras esté cargado y en la misma dimensión). Clic derecho con la mano vacía: dice qué radio lo
 * maneja y qué suena.
 */
public class RadioSpeakerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** V6.11.0: true si está montada en la pared (se coloca haciendo clic en el costado de un bloque); entonces queda pegada atrás. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty WALL = net.minecraft.world.level.block.state.properties.BooleanProperty.create("wall");
    private static final VoxelShape[] WALL_SHAPES = ShapeRotator.all(ModBlock.box(3, 0, 8, 13, 13, 16));
    private static final VoxelShape SHAPE_NS = Shapes.box(3.0 / 16.0, 0.0, 4.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0, 12.0 / 16.0);
    private static final VoxelShape SHAPE_EW = Shapes.box(4.0 / 16.0, 0.0, 3.0 / 16.0, 12.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0);

    public RadioSpeakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            return this.defaultBlockState().setValue(FACING, face).setValue(WALL, Boolean.TRUE);
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WALL, Boolean.FALSE);
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
        if (state.getValue(WALL)) {
            return WALL_SHAPES[state.getValue(FACING).get2DDataValue()];
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioSpeakerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Player player = placer instanceof Player p ? p : null;
        BlockPos radio = RadioSpeakerItem.getLink(stack);
        if (radio == null) {
            tell(player, Component.translatable("message.beautifuldecor.radio.speaker_unlinked"));
            return;
        }
        ResourceLocation dim = RadioSpeakerItem.getLinkDimension(stack);
        if (dim != null && !dim.equals(level.dimension().location())) {
            tell(player, Component.translatable("message.beautifuldecor.radio.other_dimension"));
            return;
        }
        if (level.getBlockEntity(pos) instanceof RadioSpeakerBlockEntity be) {
            be.setRadio(radio);
        }
        tell(player, Component.translatable("message.beautifuldecor.radio.speaker_ready", radio.getX(), radio.getY(), radio.getZ()));
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof RadioSpeakerItem) {
            return InteractionResult.PASS;           // con otro bafle en la mano: se pone el bafle de al lado
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RadioSpeakerBlockEntity be) {
            BlockPos radio = be.getRadio();
            if (radio == null) {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.radio.speaker_unlinked"), true);
            } else {
                RadioEntry entry = level instanceof ServerLevel sl ? RadioHub.get(sl).find(radio) : null;
                String now = entry == null || entry.url().isEmpty() ? "-" : entry.name() + (entry.on() ? "" : " (off)");
                player.displayClientMessage(Component.translatable("message.beautifuldecor.radio.speaker_status",
                        radio.getX(), radio.getY(), radio.getZ(), now), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
