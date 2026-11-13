package com.example.beautifuldecor.block;

import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * V7.2.0: radiola de Spotifly. Clic derecho: apagada → canción 1 → 2 → 3 → apagada. Suena para los que están cerca (hasta ~48 bloques)
 * y al apagarla o romperla se corta la música.
 */
public class RadiolaBlock extends ModBlock {
    public static final IntegerProperty SONG = IntegerProperty.create("song", 0, 3);

    public RadiolaBlock(BlockBehaviour.Properties properties) {
        super(properties, box(1, 0, 4, 15, 11, 12));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(SONG, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SONG);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel sl) {
            stop(sl, pos);
            int next = (state.getValue(SONG) + 1) % 4;
            sl.setBlock(pos, state.setValue(SONG, next), 3);
            if (next > 0) {
                sl.playSound(null, pos, ModSounds.song(next), SoundSource.RECORDS, 3.0f, 1.0f);
                sl.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 1.0);
                player.displayClientMessage(Component.translatable("message.beautifuldecor.radiola_song", next), true);
            } else {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.radiola_off"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel sl && !state.is(newState.getBlock())) {
            stop(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Corta la música de esta radiola a los jugadores cercanos. */
    private static void stop(ServerLevel level, BlockPos pos) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 96.0 * 96.0) {
                for (int i = 1; i <= 3; i++) {
                    sp.connection.send(new ClientboundStopSoundPacket(ModSounds.song(i).getLocation(), SoundSource.RECORDS));
                }
            }
        }
    }
}
