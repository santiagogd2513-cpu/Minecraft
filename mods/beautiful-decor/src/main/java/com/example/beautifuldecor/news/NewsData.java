package com.example.beautifuldecor.news;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** V6.11.0: las últimas noticias de HS News del mundo (se guardan con el mundo; las pantallas de anuncios las muestran en el noticiero). */
public class NewsData extends SavedData {
    public static final int MAX = 20;
    private static final String NAME = "beautifuldecor_news";
    private final List<Component> items = new ArrayList<>();

    public static NewsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NewsData::load, NewsData::new, NAME);
    }

    public static NewsData load(CompoundTag tag) {
        NewsData d = new NewsData();
        ListTag list = tag.getList("items", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                Component c = Component.Serializer.fromJson(list.getString(i));
                if (c != null) {
                    d.items.add(c);
                }
            } catch (RuntimeException ignored) {
                // una noticia dañada simplemente se pierde
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Component c : this.items) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        tag.put("items", list);
        return tag;
    }

    public void add(Component text) {
        this.items.add(text);
        while (this.items.size() > MAX) {
            this.items.remove(0);
        }
        this.setDirty();
    }

    /** Las últimas n noticias, la más reciente al final. */
    public List<Component> latest(int n) {
        int from = Math.max(0, this.items.size() - n);
        return new ArrayList<>(this.items.subList(from, this.items.size()));
    }
}
