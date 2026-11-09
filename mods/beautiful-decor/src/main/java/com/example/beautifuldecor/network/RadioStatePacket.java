package com.example.beautifuldecor.network;

import com.example.beautifuldecor.client.RadioBlockClient;
import com.example.beautifuldecor.radio.RadioEntry;
import com.example.beautifuldecor.radio.RadioStations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * V4.2.0, servidor a cliente: el estado de las radios de mesa de la dimensión en que estás. Puede traer todas (al entrar o cambiar
 * de dimensión, reemplaza lo que había), una que cambió, o el aviso de que una dejó de existir.
 */
public class RadioStatePacket {
    public record Item(long pos, String url, String name, boolean on, int volume) {
    }

    public final boolean full;
    public final List<Item> sets;
    public final List<Long> removes;

    private RadioStatePacket(boolean full, List<Item> sets, List<Long> removes) {
        this.full = full;
        this.sets = sets;
        this.removes = removes;
    }

    private static Item item(long pos, RadioEntry e) {
        return new Item(pos, e.url(), e.name(), e.on(), e.volume());
    }

    public static RadioStatePacket single(long pos, RadioEntry e) {
        List<Item> l = new ArrayList<>();
        l.add(item(pos, e));
        return new RadioStatePacket(false, l, new ArrayList<>());
    }

    public static RadioStatePacket removal(long pos) {
        List<Long> r = new ArrayList<>();
        r.add(pos);
        return new RadioStatePacket(false, new ArrayList<>(), r);
    }

    public static RadioStatePacket full(Map<Long, RadioEntry> all) {
        List<Item> l = new ArrayList<>();
        for (Map.Entry<Long, RadioEntry> e : all.entrySet()) {
            l.add(item(e.getKey(), e.getValue()));
        }
        return new RadioStatePacket(true, l, new ArrayList<>());
    }

    public static RadioStatePacket decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        int n = buf.readVarInt();
        List<Item> sets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            sets.add(new Item(buf.readLong(), buf.readUtf(RadioStations.MAX_URL), buf.readUtf(RadioStations.MAX_NAME), buf.readBoolean(),
                    buf.readVarInt()));
        }
        int m = buf.readVarInt();
        List<Long> removes = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            removes.add(buf.readLong());
        }
        return new RadioStatePacket(full, sets, removes);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.full);
        buf.writeVarInt(this.sets.size());
        for (Item it : this.sets) {
            buf.writeLong(it.pos());
            buf.writeUtf(it.url(), RadioStations.MAX_URL);
            buf.writeUtf(it.name(), RadioStations.MAX_NAME);
            buf.writeBoolean(it.on());
            buf.writeVarInt(it.volume());
        }
        buf.writeVarInt(this.removes.size());
        for (long r : this.removes) {
            buf.writeLong(r);
        }
    }

    public static void handle(RadioStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RadioBlockClient.onState(packet)));
        context.setPacketHandled(true);
    }
}
