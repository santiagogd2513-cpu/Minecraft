package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.StationControllerBlockEntity;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.entity.TrainRoute;
import com.example.beautifuldecor.item.TrainRemoteItem;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.StationOpenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

import javax.annotation.Nullable;

/**
 * V6.0.0: controlador de estación. Se pone junto a la vía (a cualquier lado, hasta 3 bloques). Con el Mando de trenes vinculado:
 * clic en un controlador que no está en la ruta lo agrega como siguiente parada; clic en uno que ya está abre sus ajustes. Sin el
 * mando, el clic abre los ajustes. El tren se detiene con el morro justo en el punto de la vía que le queda más cerca.
 */
public class StationControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final boolean depot;

    public boolean isDepot() {
        return this.depot;
    }

    public StationControllerBlock(BlockBehaviour.Properties properties) {
        this(properties, false);
    }

    /** V6.16.0: {@code depot} = es el depósito: solo puede ser la última parada de la ruta. */
    public StationControllerBlock(BlockBehaviour.Properties properties, boolean depot) {
        super(properties);
        this.depot = depot;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationControllerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof StationControllerBlockEntity be)) {
            return InteractionResult.CONSUME;
        }
        ItemStack remote = ItemStack.EMPTY;
        if (player.getMainHandItem().getItem() instanceof TrainRemoteItem) {
            remote = player.getMainHandItem();
        } else if (player.getOffhandItem().getItem() instanceof TrainRemoteItem) {
            remote = player.getOffhandItem();
        }
        TrainRoute route = null;
        if (!remote.isEmpty() && TrainRemoteItem.linked(remote) != null) {
            TrainCarEntity car = TrainRemoteItem.linkedCar(sl, remote);
            if (car == null) {
                return this.addPending(sl, sp, pos, be, remote);              // V6.16.2: el tren no está cargado: la parada queda en el mando
            }
            TrainRemoteItem.applyPending(sp, car, remote);
            route = car.route();
            if (route.indexOf(pos) < 0) {
                if (route.stops.size() >= TrainRoute.MAX_STOPS) {
                    sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.full"), true);
                    return InteractionResult.CONSUME;
                }
                BlockPos rail = TrainRoute.findRail(sl, pos);
                if (rail == null) {
                    sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.no_rail"), true);
                    return InteractionResult.CONSUME;
                }
                if (!route.stops.isEmpty() && sl.isLoaded(route.stops.get(route.stops.size() - 1).ctrl) && sl.getBlockState(route.stops.get(route.stops.size() - 1).ctrl).getBlock() instanceof StationControllerBlock last && last.depot) {
                    sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.after_depot"), true);
                    return InteractionResult.CONSUME;
                }
                if (this.depot && route.stops.isEmpty()) {
                    sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.depot_not_first"), true);
                    return InteractionResult.CONSUME;
                }
                TrainRoute.Stop stop = new TrainRoute.Stop(pos, rail);
                stop.name = be.getStationName();
                stop.dwell = be.getDwellSec();
                stop.depart = be.getDepartKmh();
                stop.travel = be.getTravelKmh();
                stop.open = be.getOpenHour();
                stop.close = be.getCloseHour();
                route.stops.add(stop);
                route.invalidate();
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.5f);
                sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                sp.displayClientMessage(Component.translatable(this.depot ? "message.beautifuldecor.station.depot_added" : "message.beautifuldecor.station.added",
                        this.depot ? stop.label(route.stops.size() - 1) : route.stops.size(), stop.label(route.stops.size() - 1)), true);
                return InteractionResult.CONSUME;
            }
        }
        int index = route == null ? -1 : route.indexOf(pos);
        int total = route == null ? 0 : route.stops.size();
        ModMessages.sendToPlayer(sp, new StationOpenPacket(pos, be.getStationName(), be.getDwellSec(), be.getDepartKmh(), be.getTravelKmh(),
                be.getOpenHour(), be.getCloseHour(), index, total));
        return InteractionResult.CONSUME;
    }

    /** V6.16.2: el tren vinculado no está cargado: la parada se guarda en el mando y se agrega a la ruta cuando el tren se cargue. */
    private InteractionResult addPending(ServerLevel sl, ServerPlayer sp, BlockPos pos, StationControllerBlockEntity be, ItemStack remote) {
        net.minecraft.nbt.ListTag list = TrainRemoteItem.pending(remote);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getLong("Ctrl") == pos.asLong()) {
                sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.pending_dup", i + 1), true);
                sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.8f);
                return InteractionResult.CONSUME;
            }
        }
        if (list.size() >= TrainRoute.MAX_STOPS) {
            sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.full"), true);
            return InteractionResult.CONSUME;
        }
        if (!list.isEmpty() && list.getCompound(list.size() - 1).getBoolean("Depot")) {
            sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.after_depot"), true);
            return InteractionResult.CONSUME;
        }
        BlockPos rail = TrainRoute.findRail(sl, pos);
        if (rail == null) {
            sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.no_rail"), true);
            return InteractionResult.CONSUME;
        }
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putLong("Ctrl", pos.asLong());
        t.putLong("Rail", rail.asLong());
        t.putString("Name", be.getStationName());
        t.putInt("DwellSec", be.getDwellSec());
        t.putInt("Depart", be.getDepartKmh());
        t.putInt("Travel", be.getTravelKmh());
        t.putInt("Open", be.getOpenHour());
        t.putInt("Close", be.getCloseHour());
        t.putBoolean("Depot", this.depot);
        list.add(t);
        TrainRemoteItem.setPending(remote, list);
        sl.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.9f, 1.0f);
        sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        sp.displayClientMessage(Component.translatable("message.beautifuldecor.station.pending", list.size()), true);
        return InteractionResult.CONSUME;
    }
}
