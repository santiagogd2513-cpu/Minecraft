package com.example.beautifuldecor.camera;

import com.example.beautifuldecor.network.CameraListPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V4.3.0: la lista, en el servidor y por dimensión, de TODAS las cámaras de seguridad (sin límite), con su número, nombre, descripción
 * y rotación. Se guarda con el mundo, así que las cámaras de chunks descargados siguen en la lista. Los monitores la piden para
 * mostrar su menú; cada cambio se manda a todos los jugadores de la dimensión.
 */
public class SecurityCameraHub extends SavedData {
    private static final String NAME = "beautifuldecor_cameras";
    /** Cuántas cámaras caben en un solo paquete (el límite de la red es de 1 MiB y cada una ocupa unos 50 bytes). */
    public static final int BATCH = 400;

    private final Map<Long, CameraInfo> cameras = new LinkedHashMap<>();
    private int nextId = 1;

    public static SecurityCameraHub get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SecurityCameraHub::load, SecurityCameraHub::new, NAME);
    }

    public static SecurityCameraHub load(CompoundTag tag) {
        SecurityCameraHub hub = new SecurityCameraHub();
        hub.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag list = tag.getList("Cameras", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int id = t.getInt("Id");
            hub.cameras.put(t.getLong("Pos"), new CameraInfo(id, t.getString("Name"), t.getString("Desc"), t.getBoolean("Rotate")));
            hub.nextId = Math.max(hub.nextId, id + 1);
        }
        return hub;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", this.nextId);
        ListTag list = new ListTag();
        for (Map.Entry<Long, CameraInfo> e : this.cameras.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.putInt("Id", e.getValue().id());
            t.putString("Name", e.getValue().name());
            t.putString("Desc", e.getValue().description());
            t.putBoolean("Rotate", e.getValue().rotate());
            list.add(t);
        }
        tag.put("Cameras", list);
        return tag;
    }

    public CameraInfo find(BlockPos pos) {
        return this.cameras.get(pos.asLong());
    }

    public boolean contains(BlockPos pos) {
        return this.cameras.containsKey(pos.asLong());
    }

    public int count() {
        return this.cameras.size();
    }

    /** Anota una cámara nueva con el siguiente número (o devuelve la que ya estaba en esa posición). */
    public CameraInfo register(BlockPos pos, String name, String description, boolean rotate) {
        CameraInfo existing = this.cameras.get(pos.asLong());
        if (existing != null) {
            return existing;
        }
        int id = this.nextId++;
        CameraInfo info = new CameraInfo(id, name, description, rotate);
        this.cameras.put(pos.asLong(), info);
        setDirty();
        return info;
    }

    /** Avisa a todos los de la dimensión del estado de esa cámara, y guarda. */
    public void changed(ServerLevel level, BlockPos pos) {
        setDirty();
        CameraInfo info = this.cameras.get(pos.asLong());
        if (info != null) {
            ModMessages.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), CameraListPacket.single(pos.asLong(), info));
        }
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (this.cameras.remove(pos.asLong()) != null) {
            setDirty();
            ModMessages.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), CameraListPacket.removal(pos.asLong()));
        }
    }

    /** Toda la lista de esta dimensión para quien entra o cambia de dimensión (en varios paquetes si es muy larga). */
    public void sendAll(ServerPlayer player) {
        List<Map.Entry<Long, CameraInfo>> all = new ArrayList<>(this.cameras.entrySet());
        if (all.isEmpty()) {
            ModMessages.sendToPlayer(player, CameraListPacket.full(new ArrayList<>(), true));
            return;
        }
        for (int from = 0; from < all.size(); from += BATCH) {
            ModMessages.sendToPlayer(player, CameraListPacket.full(all.subList(from, Math.min(all.size(), from + BATCH)), from == 0));
        }
    }
}
