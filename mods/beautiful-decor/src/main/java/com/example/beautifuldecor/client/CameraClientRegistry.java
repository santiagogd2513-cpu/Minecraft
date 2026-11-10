package com.example.beautifuldecor.client;

import com.example.beautifuldecor.network.CameraListPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** V4.3.0: la lista de cámaras de seguridad de la dimensión actual, tal como la manda el servidor (todas, sin límite). */
public final class CameraClientRegistry {
    public record Entry(long pos, int id, String name) {
    }

    private static final Map<Long, Entry> CAMERAS = new HashMap<>();

    private CameraClientRegistry() {
    }

    public static void onList(CameraListPacket packet) {
        if (packet.reset) {
            CAMERAS.clear();
        }
        for (CameraListPacket.Item it : packet.sets) {
            CAMERAS.put(it.pos(), new Entry(it.pos(), it.id(), it.name()));
        }
        for (long r : packet.removes) {
            CAMERAS.remove(r);
        }
    }

    public static Entry get(long pos) {
        return CAMERAS.get(pos);
    }

    public static int count() {
        return CAMERAS.size();
    }

    /** Todas, por número (1, 2, 3...). */
    public static List<Entry> sorted() {
        List<Entry> list = new ArrayList<>(CAMERAS.values());
        list.sort(Comparator.comparingInt(Entry::id).thenComparingLong(Entry::pos));
        return list;
    }

    public static void reset() {
        CAMERAS.clear();
    }
}
