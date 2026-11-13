package com.example.beautifuldecor.lottery;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.network.LotteryPacket;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.news.NewsEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * V6.15.0: el sorteo. Cada {@link LotteryData#DRAW_TICKS} ticks (un día de juego) sale un número de 000 a 999 y una clase de A a H
 * (8000 boletos posibles). Gordo: número y clase. Mediano: solo el número. Pequeño: las dos últimas cifras. Cada ganador recibe un camión
 * con el premio (si está desconectado, cuando entre). Los resultados salen en HS News y en el segmento de la lotería de las pantallas.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Lottery {
    public static final int CLASSES = 8;
    public static final int PRICE_IRON = 4;
    private static final Random RANDOM = new Random();
    private static int tick;

    private Lottery() {
    }

    public static int tier(int number, int cls, int winNumber, int winClass) {
        if (number == winNumber) {
            return cls == winClass ? 1 : 2;
        }
        return number % 100 == winNumber % 100 ? 3 : 0;
    }

    /** Vende un boleto al azar a este jugador. Devuelve null si ya tiene el máximo de este sorteo. */
    public static LotteryData.Ticket sell(ServerPlayer player) {
        LotteryData d = LotteryData.get(player.server);
        if (d.countFor(player.getUUID()) >= LotteryData.MAX_TICKETS) {
            return null;
        }
        LotteryData.Ticket t = new LotteryData.Ticket(player.getUUID(), RANDOM.nextInt(1000), RANDOM.nextInt(CLASSES), d.draw);
        d.tickets.add(t);
        d.setDirty();
        return t;
    }

    public static int ticksLeft(MinecraftServer server) {
        LotteryData d = LotteryData.get(server);
        return (int) Math.max(0L, d.nextDraw - server.overworld().getGameTime());
    }

    public static void broadcast(MinecraftServer server) {
        ModMessages.CHANNEL.send(PacketDistributor.ALL.noArg(), packet(server));
    }

    private static LotteryPacket packet(MinecraftServer server) {
        LotteryData d = LotteryData.get(server);
        return new LotteryPacket(d.draw, ticksLeft(server), d.lastNumber, d.lastClass);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        if (tick % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        LotteryData d = LotteryData.get(server);
        long now = server.overworld().getGameTime();
        if (d.nextDraw < 0L) {
            d.nextDraw = now + LotteryData.DRAW_TICKS;
            d.setDirty();
        }
        if (now >= d.nextDraw) {
            runDraw(server, d, now);
        }
        if (tick % 600 == 0) {
            broadcast(server);
        }
    }

    private static void runDraw(MinecraftServer server, LotteryData d, long now) {
        int number = RANDOM.nextInt(1000);
        int cls = RANDOM.nextInt(CLASSES);
        d.lastNumber = number;
        d.lastClass = cls;
        List<LotteryData.Prize> won = new ArrayList<>();
        int best = 0;
        for (Iterator<LotteryData.Ticket> it = d.tickets.iterator(); it.hasNext(); ) {
            LotteryData.Ticket t = it.next();
            if (t.draw() == d.draw) {
                int tier = tier(t.number(), t.cls(), number, cls);
                if (tier > 0) {
                    won.add(new LotteryData.Prize(t.owner(), tier));
                    best = best == 0 ? tier : Math.min(best, tier);
                }
            }
            if (t.draw() <= d.draw) {
                it.remove();
            }
        }
        NewsEvents.announce(server, Component.translatable("news.lottery_result", d.draw, String.format("%03d", number), String.valueOf((char) ('A' + cls)),
                best == 0 ? Component.translatable("news.lottery_nobody") : Component.translatable("news.lottery_tier." + best)));
        d.draw++;
        d.nextDraw = now + LotteryData.DRAW_TICKS;
        for (LotteryData.Prize p : won) {
            ServerPlayer player = server.getPlayerList().getPlayer(p.owner());
            if (player == null || !PrizeTruckDriver.dispatch(player, p.tier())) {
                d.pending.add(p);
            } else {
                player.sendSystemMessage(Component.translatable("message.beautifuldecor.lottery_won." + p.tier()));
            }
        }
        d.setDirty();
        broadcast(server);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        LotteryData d = LotteryData.get(server);
        ModMessages.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet(server));
        for (Iterator<LotteryData.Prize> it = d.pending.iterator(); it.hasNext(); ) {
            LotteryData.Prize p = it.next();
            if (p.owner().equals(player.getUUID())) {
                it.remove();
                d.setDirty();
                if (PrizeTruckDriver.dispatch(player, p.tier())) {
                    player.sendSystemMessage(Component.translatable("message.beautifuldecor.lottery_won." + p.tier()));
                } else {
                    d.pending.add(p);
                    break;
                }
            }
        }
    }
}
