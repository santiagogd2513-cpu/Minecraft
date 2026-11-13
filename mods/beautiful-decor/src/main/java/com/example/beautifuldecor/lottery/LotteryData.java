package com.example.beautifuldecor.lottery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** V6.15.0: estado de la lotería del mundo: sorteo actual, próximo sorteo, último resultado, boletos vendidos y premios pendientes de jugadores desconectados. */
public class LotteryData extends SavedData {
    public static final long DRAW_TICKS = 24000L;
    public static final int MAX_TICKETS = 20;
    private static final String NAME = "beautifuldecor_lottery";

    public record Ticket(UUID owner, int number, int cls, int draw) {
    }

    public record Prize(UUID owner, int tier) {
    }

    public int draw = 1;
    public long nextDraw = -1L;
    public int lastNumber = -1;
    public int lastClass = -1;
    public final List<Ticket> tickets = new ArrayList<>();
    public final List<Prize> pending = new ArrayList<>();

    public static LotteryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LotteryData::load, LotteryData::new, NAME);
    }

    public static LotteryData load(CompoundTag tag) {
        LotteryData d = new LotteryData();
        d.draw = Math.max(1, tag.getInt("draw"));
        d.nextDraw = tag.contains("next") ? tag.getLong("next") : -1L;
        d.lastNumber = tag.contains("lastNumber") ? tag.getInt("lastNumber") : -1;
        d.lastClass = tag.contains("lastClass") ? tag.getInt("lastClass") : -1;
        ListTag t = tag.getList("tickets", Tag.TAG_COMPOUND);
        for (int i = 0; i < t.size(); i++) {
            CompoundTag c = t.getCompound(i);
            if (c.hasUUID("o")) {
                d.tickets.add(new Ticket(c.getUUID("o"), c.getInt("n"), c.getInt("c"), c.getInt("d")));
            }
        }
        ListTag p = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            CompoundTag c = p.getCompound(i);
            if (c.hasUUID("o")) {
                d.pending.add(new Prize(c.getUUID("o"), c.getInt("t")));
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("draw", this.draw);
        tag.putLong("next", this.nextDraw);
        tag.putInt("lastNumber", this.lastNumber);
        tag.putInt("lastClass", this.lastClass);
        ListTag t = new ListTag();
        for (Ticket k : this.tickets) {
            CompoundTag c = new CompoundTag();
            c.putUUID("o", k.owner());
            c.putInt("n", k.number());
            c.putInt("c", k.cls());
            c.putInt("d", k.draw());
            t.add(c);
        }
        tag.put("tickets", t);
        ListTag p = new ListTag();
        for (Prize k : this.pending) {
            CompoundTag c = new CompoundTag();
            c.putUUID("o", k.owner());
            c.putInt("t", k.tier());
            p.add(c);
        }
        tag.put("pending", p);
        return tag;
    }

    public int countFor(UUID owner) {
        int n = 0;
        for (Ticket k : this.tickets) {
            if (k.owner().equals(owner) && k.draw() == this.draw) {
                n++;
            }
        }
        return n;
    }
}
