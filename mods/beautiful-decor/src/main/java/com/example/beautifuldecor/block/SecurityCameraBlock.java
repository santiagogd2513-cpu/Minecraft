package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import com.example.beautifuldecor.camera.CameraInfo;
import com.example.beautifuldecor.camera.SecurityCameraHub;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.OpenCameraEditPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * V4.3.0: cámara de seguridad (negra o blanca), en pared o en techo (no se puede poner en el suelo). Al ponerla se abre su menú
 * (nombre, descripción y rotación); con clic derecho se vuelve a abrir. La cabeza que gira y la imagen se dibujan aparte
 * (SecurityCameraRenderer); el bloque solo trae el soporte.
 */
public class SecurityCameraBlock extends BaseEntityBlock {
    public enum Mount implements StringRepresentable {
        WALL("wall"),
        CEILING("ceiling");

        private final String id;

        Mount(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);

    /** Pared al sur (para facing=norte): soporte y cabeza. */
    private static final VoxelShape[] WALL_SHAPES = ShapeRotator.all(ModBlock.box(4.5, 4, 3.5, 11.5, 11.5, 16));
    private static final VoxelShape CEILING_SHAPE = ModBlock.box(4, 6, 4, 12, 16, 12);

    private final boolean white;

    public SecurityCameraBlock(BlockBehaviour.Properties properties, boolean white) {
        super(properties);
        this.white = white;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.WALL));
    }

    public boolean isWhite() {
        return this.white;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state;
        if (clicked == Direction.UP) {
            return null;                                        // en el suelo no: solo pared o techo
        } else if (clicked == Direction.DOWN) {
            state = this.defaultBlockState().setValue(MOUNT, Mount.CEILING).setValue(FACING, context.getHorizontalDirection());
        } else {
            state = this.defaultBlockState().setValue(MOUNT, Mount.WALL).setValue(FACING, clicked);
        }
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(MOUNT) == Mount.CEILING) {
            BlockPos above = pos.above();
            return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
        }
        Direction behind = state.getValue(FACING).getOpposite();
        BlockPos support = pos.relative(behind);
        return level.getBlockState(support).isFaceSturdy(level, support, state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? super.updateShape(state, direction, neighbor, level, pos, neighborPos) : Blocks.AIR.defaultBlockState();
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
        return state.getValue(MOUNT) == Mount.CEILING ? CEILING_SHAPE : WALL_SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityCameraBlockEntity(pos, state);
    }

    /** Al ponerla: se anota en la lista de cámaras con el siguiente número y se abre su menú a quien la puso. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl) {
            SecurityCameraHub hub = SecurityCameraHub.get(sl);
            CameraInfo info = hub.register(pos, "", "", false);
            if (level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity be) {
                be.applyEdit(sl, info.name(), info.description(), info.rotate());
            }
            if (placer instanceof ServerPlayer player) {
                ModMessages.sendToPlayer(player, new OpenCameraEditPacket(pos));
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.ClientHooks.openCameraEditScreen(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Al quitarla, sale de la lista de cámaras y los monitores que la mostraban se quedan sin señal. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            SecurityCameraHub.get(sl).remove(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
