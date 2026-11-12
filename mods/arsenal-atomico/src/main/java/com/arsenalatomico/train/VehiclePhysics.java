package com.arsenalatomico.train;

// Adaptado de Beautiful Decor (santiagogd2513-cpu), con su permiso: la física de sus trenes y vehículos, tal cual la hizo él, sin dependencias de Minecraft.

/**
 * V4.6.0: física de los vehículos de tierra (Java puro, sin Minecraft, para poder probarla). 1 bloque = 1 metro, 1 tick = 1/20 s.
 * <p>
 * Todo sale de fuerzas reales: el motor empuja con F = potencia / velocidad (con un tope de fuerza al arrancar), el aire frena con 1/2·ρ·CdA·v²,
 * las ruedas con la resistencia a la rodadura, los frenos con una fuerza máxima (si el vehículo pesa más por la carga, frena peor) y la pendiente
 * suma o resta g·sen(θ). Por eso: más peso = cuesta más acelerar y frenar; más velocidad = cuesta más frenar (la distancia crece con v²);
 * cuesta arriba se frena solo y cuesta abajo se acelera solo.
 */
public final class VehiclePhysics {
    public static final double G = 9.81;
    public static final double AIR_DENSITY = 1.2;
    private static final double DT = 0.05;
    /** Bloques por tick a metros por segundo. */
    public static final double TICK_TO_MS = 20.0;

    private VehiclePhysics() {
    }

    /**
     * @param massKg        masa en vacío con el conductor
     * @param powerW        potencia del motor
     * @param maxAccel      aceleración máxima al arrancar con la masa en vacío (m/s²): fija la fuerza máxima del motor/tracción
     * @param brakeAccel    frenado máximo con la masa en vacío (m/s²)
     * @param handbrakeAccel frenado con el freno de mano (m/s²)
     * @param cdA           coeficiente de arrastre x área frontal (m²)
     * @param crr           resistencia a la rodadura
     * @param engineBrake   frenado del motor al soltar el gas (m/s²)
     * @param topSpeedMs    velocidad máxima que el motor puede mantener (m/s)
     * @param reverseMs     velocidad máxima en reversa (m/s)
     */
    public record Params(double massKg, double powerW, double maxAccel, double brakeAccel, double handbrakeAccel, double cdA, double crr,
                         double engineBrake, double topSpeedMs, double reverseMs) {
    }

    /**
     * Un tick de física.
     *
     * @param vBt     velocidad actual en bloques por tick (negativa = reversa)
     * @param massKg  masa total ahora (vacío + pasajeros + carga)
     * @param slope   pendiente: seno del ángulo, positivo = cuesta arriba
     * @param drive   1 = gas adelante, -1 = gas en reversa, 0 = sin gas
     * @param braking pedal de freno
     * @param hand    freno de mano
     * @return la velocidad nueva en bloques por tick
     */
    public static double step(Params p, double vBt, double massKg, double slope, int drive, boolean braking, boolean hand) {
        double m = Math.max(massKg, 1.0);
        double v = vBt * TICK_TO_MS;
        double sign = v > 0 ? 1.0 : v < 0 ? -1.0 : 0.0;
        double av = Math.abs(v);

        double a = 0.0;
        // ---- motor
        if (drive > 0 && v < p.topSpeedMs()) {
            double force = Math.min(p.maxAccel() * p.massKg(), p.powerW() / Math.max(v, 3.0));
            a += force / m;
        } else if (drive < 0 && v > -p.reverseMs()) {
            double force = Math.min(p.maxAccel() * 0.35 * p.massKg(), 0.3 * p.powerW() / Math.max(av, 3.0));
            a -= force / m;
        }
        // ---- resistencias (siempre contra el movimiento)
        double drag = 0.5 * AIR_DENSITY * p.cdA() * v * av / m;
        double roll = av > 0.05 ? p.crr() * G * sign : 0.0;
        double resist = drag + roll;
        if (drive == 0 && !braking) {
            resist += p.engineBrake() * sign * Math.min(1.0, av / 3.0);
        }
        a -= resist;
        // ---- frenos: la fuerza máxima está pensada para la masa en vacío +20 %; con más carga, frenan menos
        double brake = 0.0;
        if (braking) {
            brake += Math.min(p.brakeAccel(), p.brakeAccel() * p.massKg() * 1.2 / m);
        }
        if (hand) {
            brake += Math.min(p.handbrakeAccel(), p.handbrakeAccel() * p.massKg() * 1.2 / m);
        }
        // ---- pendiente
        double grade = -G * slope;

        double without = v + (a + grade) * DT;
        double result;
        if (brake > 0.0 && av > 0.0) {
            double dec = brake * DT;
            result = v + (a + grade) * DT - sign * dec;
            if (sign * result < 0.0 && Math.abs(grade) * DT < dec) {
                result = 0.0;                                   // el freno no hace dar marcha atrás
            }
        } else {
            result = without;
            // las resistencias solas no invierten la marcha ni mueven un vehículo parado en plano
            if (drive == 0 && sign != 0.0 && sign * result < 0.0 && Math.abs(slope) < 0.05) {
                result = 0.0;
            }
            if (drive == 0 && Math.abs(result) < 0.05 && Math.abs(slope) < 0.05) {
                result = 0.0;
            }
        }
        double limit = p.topSpeedMs() * 1.6;                    // cuesta abajo puede pasar de su tope, pero no sin límite
        result = Math.max(-p.reverseMs() * 1.3, Math.min(limit, result));
        return (float) (result / TICK_TO_MS);
    }

    /** Distancia (m) que necesita para parar desde v (m/s) con el freno a fondo en plano; simulada tick a tick. */
    public static double brakingDistance(Params p, double massKg, double vMs) {
        double v = vMs / TICK_TO_MS;
        double d = 0.0;
        for (int i = 0; i < 20000 && v > 0.0005; i++) {
            d += v;
            v = step(p, v, massKg, 0.0, 0, true, false);
        }
        return d;
    }

    /** Segundos que tarda en pasar de 0 a vMs a pleno gas en plano (o -1 si no llega). */
    public static double timeToSpeed(Params p, double massKg, double vMs) {
        double v = 0.0;
        for (int i = 0; i < 20 * 600; i++) {
            v = step(p, v, massKg, 0.0, 1, false, false);
            if (v * TICK_TO_MS >= vMs) {
                return (i + 1) * DT;
            }
        }
        return -1.0;
    }
}
