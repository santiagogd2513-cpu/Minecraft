package com.example.beautifuldecor.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.Set;

/** V6.0.0: mantiene cargados los chunks por donde va un tren automático (los suyos y la vía que tiene por delante). */
public final class TrainChunks {
    /** Se renueva cada 10 ticks; si el tren deja de pedirlo, a los 40 ticks se descarga solo. */
    public static final TicketType<ChunkPos> TYPE = TicketType.create("beautifuldecor_train", Comparator.comparingLong(ChunkPos::toLong), 40);

    private TrainChunks() {
    }

    /**
     * @param near chunks donde hay coches: se cargan con las entidades funcionando (radio 2, así el tren sigue andando)
     * @param far  chunks de la vía por delante: solo se cargan, para poder ver la vía (radio 0)
     */
    public static void keep(ServerLevel level, Set<Long> near, Set<Long> far) {
        for (long key : far) {
            ChunkPos pos = new ChunkPos(key);
            level.getChunkSource().addRegionTicket(TYPE, pos, 0, pos);
        }
        for (long key : near) {
            ChunkPos pos = new ChunkPos(key);
            level.getChunkSource().addRegionTicket(TYPE, pos, 2, pos);
        }
    }
}
