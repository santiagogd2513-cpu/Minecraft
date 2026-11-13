package com.example.beautifuldecor.client;

import com.example.beautifuldecor.network.LotteryPacket;

/** V6.15.0: lo último que el servidor dijo de la lotería. La cuenta regresiva corre sola en el cliente entre paquetes. */
public final class LotteryClient {
    private static int draw = 1;
    private static int lastNumber = -1;
    private static int lastClass = -1;
    private static long endMs = -1L;

    private LotteryClient() {
    }

    public static void update(LotteryPacket p) {
        draw = p.draw;
        lastNumber = p.lastNumber;
        lastClass = p.lastClass;
        endMs = System.currentTimeMillis() + p.ticksLeft * 50L;
    }

    public static void reset() {
        draw = 1;
        lastNumber = -1;
        lastClass = -1;
        endMs = -1L;
    }

    public static int draw() {
        return draw;
    }

    public static int lastNumber() {
        return lastNumber;
    }

    public static int lastClass() {
        return lastClass;
    }

    /** Minutos de juego que faltan (1 min de juego = 1200 ticks = 60 s reales). */
    public static int minutesLeft() {
        if (endMs < 0) {
            return -1;
        }
        return (int) Math.max(0L, (endMs - System.currentTimeMillis()) / 60000L);
    }

    public static String classLetter(int c) {
        return c < 0 ? "?" : String.valueOf((char) ('A' + c));
    }

    public static String numberText(int n) {
        return n < 0 ? "???" : String.format("%03d", n);
    }
}
