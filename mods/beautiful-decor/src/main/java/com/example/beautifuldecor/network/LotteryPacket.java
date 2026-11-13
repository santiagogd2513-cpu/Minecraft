package com.example.beautifuldecor.network;

import com.example.beautifuldecor.client.LotteryClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V6.15.0: el servidor avisa a los clientes del sorteo actual, cuánto falta y el último resultado (lo muestran las pantallas de anuncios). */
public class LotteryPacket {
    public final int draw;
    public final int ticksLeft;
    public final int lastNumber;
    public final int lastClass;

    public LotteryPacket(int draw, int ticksLeft, int lastNumber, int lastClass) {
        this.draw = draw;
        this.ticksLeft = ticksLeft;
        this.lastNumber = lastNumber;
        this.lastClass = lastClass;
    }

    public static LotteryPacket decode(FriendlyByteBuf b) {
        return new LotteryPacket(b.readVarInt(), b.readInt(), b.readInt(), b.readInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(this.draw);
        b.writeInt(this.ticksLeft);
        b.writeInt(this.lastNumber);
        b.writeInt(this.lastClass);
    }

    public static void handle(LotteryPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LotteryClient.update(packet)));
        context.setPacketHandled(true);
    }
}
