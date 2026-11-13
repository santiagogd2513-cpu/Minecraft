package com.example.beautifuldecor.network;

import com.example.beautifuldecor.client.NewsClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** V6.11.0: el servidor manda a los clientes lo que muestra el noticiero: el clima y los últimos titulares. */
public class NewsPacket {
    public final boolean raining;
    public final boolean thundering;
    public final int rainTime;
    public final List<Component> headlines;

    public NewsPacket(boolean raining, boolean thundering, int rainTime, List<Component> headlines) {
        this.raining = raining;
        this.thundering = thundering;
        this.rainTime = rainTime;
        this.headlines = headlines;
    }

    public static NewsPacket decode(FriendlyByteBuf buffer) {
        boolean raining = buffer.readBoolean();
        boolean thundering = buffer.readBoolean();
        int rainTime = buffer.readInt();
        int n = buffer.readVarInt();
        List<Component> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(buffer.readComponent());
        }
        return new NewsPacket(raining, thundering, rainTime, list);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.raining);
        buffer.writeBoolean(this.thundering);
        buffer.writeInt(this.rainTime);
        buffer.writeVarInt(this.headlines.size());
        for (Component c : this.headlines) {
            buffer.writeComponent(c);
        }
    }

    public static void handle(NewsPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NewsClient.update(packet)));
        context.setPacketHandled(true);
    }
}
