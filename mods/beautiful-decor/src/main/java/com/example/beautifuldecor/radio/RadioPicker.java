package com.example.beautifuldecor.radio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * V3.5.0: qué radios de vehículos suenan en este cliente, sin nada de Minecraft. Para no gastar datos ni batería (sobre todo en el
 * celular), como mucho {@link #MAX_PLAYING} a la vez: siempre la del vehículo en que vas, y luego las más cercanas.
 */
public final class RadioPicker {
    /** Una radio encendida cerca: el id del vehículo, la distancia al jugador y si es el vehículo en que va. */
    public record Candidate(int id, double distance, boolean own) {
    }

    public static final int MAX_PLAYING = 3;
    /** Hasta dónde se oye una radio de vehículo (bloques): igual que el reproductor del mod, que se calla a esa distancia. */
    public static final double RANGE = 40.0;

    private RadioPicker() {
    }

    /** Las que deben sonar, en orden (la propia primero, luego de más cerca a más lejos; a igual distancia, por id). */
    public static List<Integer> pick(List<Candidate> candidates, int max) {
        List<Candidate> sorted = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.own() || c.distance() <= RANGE) {
                sorted.add(c);
            }
        }
        sorted.sort(Comparator.comparing((Candidate c) -> !c.own()).thenComparingDouble(Candidate::distance).thenComparingInt(Candidate::id));
        List<Integer> out = new ArrayList<>();
        for (Candidate c : sorted) {
            if (out.size() >= Math.max(0, max)) {
                break;
            }
            if (!out.contains(c.id())) {
                out.add(c.id());
            }
        }
        return out;
    }
}
