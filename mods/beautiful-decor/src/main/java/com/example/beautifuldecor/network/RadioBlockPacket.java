package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.RadioTableBlock;
import com.example.beautifuldecor.radio.RadioEntry;
import com.example.beautifuldecor.radio.RadioHub;
import com.example.beautifuldecor.radio.RadioStations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V4.2.0, cliente a servidor: el menú de la radio de mesa elige emisora, enciende/apaga o cambia el volumen. El servidor revisa que
 * estés cerca de una radio de verdad y limpia la dirección y el nombre.
 */
public class RadioBlockPacket {
    public static final int SELECT = 0;
    public static final int POWER = 1;
    public static final int VOLUME = 2;
    /** A cuánto como máximo (en bloques) puede estar quien maneja la radio. */
    public static final double REACH = 10.0;

    private final BlockPos pos;
    private final int action;
    private final String url;
    private final String name;
    private final int value;

    private RadioBlockPacket(BlockPos pos, int action, String url, String name, int value) {
        this.pos = pos;
        this.action = action;
        this.url = url == null ? "" : url;
        this.name = name == null ? "" : name;
        this.value = value;
    }

    public static RadioBlockPacket select(BlockPos pos, String url, String name) {
        String u = url == null ? "" : url;
        String n = name == null ? "" : name;
        return new RadioBlockPacket(pos, SELECT, u.length() > RadioStations.MAX_URL ? "" : u,
                n.length() > RadioStations.MAX_NAME ? n.substring(0, RadioStations.MAX_NAME) : n, 0);
    }

    public static RadioBlockPacket power(BlockPos pos, boolean on) {
        return new RadioBlockPacket(pos, POWER, "", "", on ? 1 : 0);
    }

    public static RadioBlockPacket volume(BlockPos pos, int volume) {
        return new RadioBlockPacket(pos, VOLUME, "", "", RadioStations.clampVolume(volume));
    }

    public static RadioBlockPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int action = buf.readByte();
        if (action == SELECT) {
            return new RadioBlockPacket(pos, action, buf.readUtf(RadioStations.MAX_URL), buf.readUtf(RadioStations.MAX_NAME), 0);
        }
        return new RadioBlockPacket(pos, action, "", "", buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.action);
        if (this.action == SELECT) {
            buf.writeUtf(this.url, RadioStations.MAX_URL);
            buf.writeUtf(this.name, RadioStations.MAX_NAME);
        } else {
            buf.writeVarInt(this.value);
        }
    }

    public static void handle(RadioBlockPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            BlockPos pos = packet.pos;
            if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RadioTableBlock)) {
                return;
            }
            RadioHub hub = RadioHub.get(level);
            RadioEntry entry = hub.getOrCreate(pos);
            switch (packet.action) {
                case SELECT -> {
                    String url = RadioStations.cleanUrl(packet.url);
                    if (url == null) {
                        return;
                    }
                    entry.select(url, RadioStations.cleanName(packet.name));
                }
                case POWER -> entry.setOn(packet.value != 0);
                case VOLUME -> entry.setVolume(packet.value);
                default -> {
                    return;
                }
            }
            hub.changed(level, pos);
            if (state.getValue(RadioTableBlock.ON) != entry.playing()) {
                level.setBlock(pos, state.setValue(RadioTableBlock.ON, entry.playing()), 3);
            }
        });
        context.setPacketHandled(true);
    }
}
