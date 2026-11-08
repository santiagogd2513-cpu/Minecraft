package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.PaintableVehicle;
import com.example.beautifuldecor.entity.VehiclePaint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V3.2.0, cliente a servidor: el menú de colores del vehículo (tecla R) manda la pintura, las manijas y los rines.
 * -1 en un canal = color original. El servidor revisa que el vehículo exista, esté cerca y tenga ese canal.
 */
public class VehiclePaintPacket {
    /** Distancia máxima (al cuadrado) entre el jugador y el vehículo: el menú se abre mirándolo desde hasta 8 bloques. */
    private static final double MAX_DISTANCE_SQR = 20.0 * 20.0;

    private final int entityId;
    private final int[] colors;

    public VehiclePaintPacket(int entityId, int[] colors) {
        this.entityId = entityId;
        this.colors = colors.clone();
    }

    public static VehiclePaintPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int[] colors = new int[VehiclePaint.CHANNELS];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = buf.readInt();
        }
        return new VehiclePaintPacket(id, colors);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        for (int i = 0; i < VehiclePaint.CHANNELS; i++) {
            buf.writeInt(i < this.colors.length ? this.colors[i] : VehiclePaint.ORIGINAL);
        }
    }

    public static void handle(VehiclePaintPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.level().getEntity(packet.entityId);
            if (entity == null || entity.isRemoved() || !(entity instanceof PaintableVehicle vehicle)) {
                return;
            }
            if (player.distanceToSqr(entity) > MAX_DISTANCE_SQR && player.getVehicle() != entity) {
                return;
            }
            boolean changed = false;
            for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
                int value = VehiclePaint.clean(vehicle.paintKind(), ch, ch < packet.colors.length ? packet.colors[ch] : -1);
                if (value != vehicle.getPaintRaw(ch)) {
                    vehicle.setPaintRaw(ch, value);
                    changed = true;
                }
            }
            if (changed) {
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AXE_WAX_OFF,
                        SoundSource.NEUTRAL, 0.8f, 1.2f);
            }
        });
        context.setPacketHandled(true);
    }
}
