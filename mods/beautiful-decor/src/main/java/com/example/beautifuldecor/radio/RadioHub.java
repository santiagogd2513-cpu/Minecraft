package com.example.beautifuldecor.radio;

import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.RadioStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V4.2.0: el registro, en el servidor y por dimensión, de las radios de mesa: qué emisora tiene cada una, si está encendida y a qué
 * volumen. Se guarda con el mundo. Los bafles no guardan nada de esto: consultan a su radio, por eso siguen sonando con lo que la
 * radio tenga puesto aunque estén lejos de ella.
 */
public class RadioHub extends SavedData {
    private static final String NAME = "beautifuldecor_radios";

    private final Map<Long, RadioEntry> radios = new LinkedHashMap<>();

    public static RadioHub get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RadioHub::load, RadioHub::new, NAME);
    }

    public static RadioHub load(CompoundTag tag) {
        RadioHub hub = new RadioHub();
        ListTag list = tag.getList("Radios", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            hub.radios.put(t.getLong("Pos"), new RadioEntry(t.getString("Url"), t.getString("Name"), t.getBoolean("On"),
                    t.contains("Volume", Tag.TAG_INT) ? t.getInt("Volume") : RadioStations.DEFAULT_VOLUME));
        }
        return hub;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, RadioEntry> e : this.radios.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.putString("Url", e.getValue().url());
            t.putString("Name", e.getValue().name());
            t.putBoolean("On", e.getValue().on());
            t.putInt("Volume", e.getValue().volume());
            list.add(t);
        }
        tag.put("Radios", list);
        return tag;
    }

    /** La radio de ese bloque, o null si nunca se le puso nada. */
    public RadioEntry find(BlockPos pos) {
        return this.radios.get(pos.asLong());
    }

    public RadioEntry getOrCreate(BlockPos pos) {
        return this.radios.computeIfAbsent(pos.asLong(), k -> new RadioEntry());
    }

    /** Avisa a todos los de la dimensión del estado de esa radio, y guarda. */
    public void changed(ServerLevel level, BlockPos pos) {
        setDirty();
        RadioEntry e = this.radios.get(pos.asLong());
        if (e != null) {
            ModMessages.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), RadioStatePacket.single(pos.asLong(), e));
        }
    }

    /** La radio ya no existe (se rompió el bloque). */
    public void remove(ServerLevel level, BlockPos pos) {
        if (this.radios.remove(pos.asLong()) != null) {
            setDirty();
            ModMessages.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), RadioStatePacket.removal(pos.asLong()));
        }
    }

    /** Todo el estado de esta dimensión, para quien entra o cambia de dimensión. */
    public void sendAll(ServerPlayer player) {
        ModMessages.sendToPlayer(player, RadioStatePacket.full(this.radios));
    }
}
