package com.example.beautifuldecor.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** V6.0.0, servidor a cliente: abre el menú del tren vinculado al mando (nombre, automático, ruta). */
public class TrainMenuOpenPacket {
    public final int entityId;
    public final String name;
    public final boolean auto;
    public final List<String> stops;

    public TrainMenuOpenPacket(int entityId, String name, boolean auto, List<String> stops) {
        this.entityId = entityId;
        this.name = name;
        this.auto = auto;
        this.stops = stops;
    }

    public static TrainMenuOpenPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String name = buf.readUtf(64);
        boolean auto = buf.readBoolean();
        int n = buf.readVarInt();
        List<String> stops = new ArrayList<>();
        for (int i = 0; i < n && i < 64; i++) {
            stops.add(buf.readUtf(64));
        }
        return new TrainMenuOpenPacket(id, name, auto, stops);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.name, 64);
        buf.writeBoolean(this.auto);
        buf.writeVarInt(this.stops.size());
        for (String s : this.stops) {
            buf.writeUtf(s, 64);
        }
    }

    public static void handle(TrainMenuOpenPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.client.ClientHooks.openTrainMenu(packet)));
        context.setPacketHandled(true);
    }
}
