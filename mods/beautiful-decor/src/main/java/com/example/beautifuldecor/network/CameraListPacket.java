package com.example.beautifuldecor.network;

import com.example.beautifuldecor.camera.CameraInfo;
import com.example.beautifuldecor.client.CameraClientRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * V4.3.0, servidor a cliente: la lista de cámaras de seguridad de la dimensión en que estás. Puede traer un tramo de la lista completa
 * ({@code reset} = vaciar lo que había antes de aplicar), una cámara que cambió, o el aviso de que una dejó de existir.
 */
public class CameraListPacket {
    public record Item(long pos, int id, String name) {
    }

    public final boolean reset;
    public final List<Item> sets;
    public final List<Long> removes;

    private CameraListPacket(boolean reset, List<Item> sets, List<Long> removes) {
        this.reset = reset;
        this.sets = sets;
        this.removes = removes;
    }

    public static CameraListPacket single(long pos, CameraInfo info) {
        List<Item> l = new ArrayList<>();
        l.add(new Item(pos, info.id(), info.name()));
        return new CameraListPacket(false, l, new ArrayList<>());
    }

    public static CameraListPacket removal(long pos) {
        List<Long> r = new ArrayList<>();
        r.add(pos);
        return new CameraListPacket(false, new ArrayList<>(), r);
    }

    public static CameraListPacket full(List<Map.Entry<Long, CameraInfo>> part, boolean reset) {
        List<Item> l = new ArrayList<>();
        for (Map.Entry<Long, CameraInfo> e : part) {
            l.add(new Item(e.getKey(), e.getValue().id(), e.getValue().name()));
        }
        return new CameraListPacket(reset, l, new ArrayList<>());
    }

    public static CameraListPacket decode(FriendlyByteBuf buf) {
        boolean reset = buf.readBoolean();
        int n = buf.readVarInt();
        List<Item> sets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            sets.add(new Item(buf.readLong(), buf.readVarInt(), buf.readUtf(CameraInfo.MAX_NAME)));
        }
        int m = buf.readVarInt();
        List<Long> removes = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            removes.add(buf.readLong());
        }
        return new CameraListPacket(reset, sets, removes);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.reset);
        buf.writeVarInt(this.sets.size());
        for (Item it : this.sets) {
            buf.writeLong(it.pos());
            buf.writeVarInt(it.id());
            buf.writeUtf(it.name(), CameraInfo.MAX_NAME);
        }
        buf.writeVarInt(this.removes.size());
        for (long r : this.removes) {
            buf.writeLong(r);
        }
    }

    public static void handle(CameraListPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CameraClientRegistry.onList(packet)));
        context.setPacketHandled(true);
    }
}
