package com.example.beautifuldecor.client;

import com.example.beautifuldecor.network.NewsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** V6.11.0: lo último que el servidor mandó para el noticiero (clima y titulares) y los textos que se arman con eso. */
public final class NewsClient {
    private static boolean raining;
    private static boolean thundering;
    private static int rainTime = -1;
    private static List<Component> headlines = new ArrayList<>();
    private static int version;

    private NewsClient() {
    }

    public static void update(NewsPacket packet) {
        String before = headlines.isEmpty() ? "" : headlines.get(headlines.size() - 1).getString();
        raining = packet.raining;
        thundering = packet.thundering;
        rainTime = packet.rainTime;
        headlines = new ArrayList<>(packet.headlines);
        String after = headlines.isEmpty() ? "" : headlines.get(headlines.size() - 1).getString();
        if (!after.equals(before)) {
            version++;                                       // hay una noticia nueva: las pantallas adelantan el noticiero
        }
    }

    public static void reset() {
        headlines = new ArrayList<>();
        raining = false;
        thundering = false;
        rainTime = -1;
        version = 0;
    }

    public static int version() {
        return version;
    }

    /** Titulares, el más reciente al final. Si no hay ninguno, una frase tranquila. */
    public static List<Component> headlines() {
        if (headlines.isEmpty()) {
            return List.of(Component.translatable("news.empty"));
        }
        return headlines;
    }

    public static boolean isRaining() {
        return raining;
    }

    public static boolean isThundering() {
        return thundering;
    }

    public static Component weather() {
        int minutes = Math.max(1, rainTime / 1200);
        if (thundering) {
            return Component.translatable("news.weather.storm");
        }
        if (raining) {
            return Component.translatable("news.weather.rain", minutes);
        }
        if (rainTime > 0 && rainTime < 24000) {
            return Component.translatable("news.weather.rain_soon", minutes);
        }
        return Component.translatable("news.weather.clear");
    }

    /** La hora del juego como "07:30" (el tick 0 son las 6:00). */
    public static String clock() {
        Minecraft mc = Minecraft.getInstance();
        long t = mc.level == null ? 0L : (mc.level.getDayTime() + 6000L) % 24000L;
        return String.format("%02d:%02d", t / 1000L, (t % 1000L) * 60L / 1000L);
    }
}
