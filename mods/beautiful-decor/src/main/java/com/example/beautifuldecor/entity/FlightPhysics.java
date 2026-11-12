package com.example.beautifuldecor.entity;

/**
 * V4.7.0: física de despegue, vuelo y aterrizaje del avión y el helicóptero (Java puro, con prueba).
 * <ul>
 *   <li>La sustentación del avión crece con v² y debe igualar el peso: con carga necesita más velocidad para despegar (y se cae antes de lo normal
 *       si baja de la velocidad de pérdida). Su velocidad de despegue y de pérdida suben con la raíz del peso.</li>
 *   <li>Al pasar la velocidad de despegue no sale de golpe: el ascenso crece poco a poco (el avión se va separando del suelo).</li>
 *   <li>Al helicóptero lo levanta el rotor: con carga le sobra menos empuje (puede no levantarlo del todo) y necesita los rotores a tope.</li>
 *   <li>Aterrizaje: lo que importa es la velocidad de caída al tocar, multiplicada por la raíz del peso (energía que hay que absorber).</li>
 * </ul>
 */
public final class FlightPhysics {
    private FlightPhysics() {
    }

    /** Peso relativo del avión (1 = vacío; con la bodega de 6 filas llena, casi 2). */
    public static float airlinerLoad(int items) {
        return 1.0f + Math.max(0, items) / 3500.0f;
    }

    /** Peso relativo del helicóptero (1 = vacío; con sus 3 filas llenas, ~2,15). */
    public static float heliLoad(int items) {
        return 1.0f + Math.max(0, items) / 1500.0f;
    }

    /** Velocidad a la que puede empezar a separarse del suelo (bloques por tick). */
    public static float takeoffSpeed(float base, float load) {
        return base * (float) Math.sqrt(load);
    }

    /** Por debajo de esta velocidad, en el aire, ya no se sostiene (bloques por tick). */
    public static float stallSpeed(float base, float load) {
        return base * (float) Math.sqrt(load);
    }

    /** Fracción (0 a 1) del ascenso permitido en la carrera: 0 al llegar a la velocidad de despegue y 1 un 40 % por encima. */
    public static float liftFraction(float speed, float rotateSpeed) {
        return clamp((speed - rotateSpeed) / (0.4f * rotateSpeed), 0.0f, 1.0f);
    }

    /** Cuánto de su velocidad vertical normal puede usar el avión con este peso. */
    public static float climbScale(float load) {
        return (float) Math.pow(load, -0.8);
    }

    /** Velocidad con la que se cae (bloques por tick, positiva) si va por debajo de la velocidad de pérdida. 0 si está por encima. */
    public static float stallSink(float speed, float stallSpeed) {
        if (speed >= stallSpeed) {
            return 0.0f;
        }
        return Math.min(0.7f, (1.0f - speed / stallSpeed) * 1.2f);
    }

    /** Empuje de sobra del helicóptero (0 a 1): con 1 sube normal; con 0 el rotor no puede levantar esa carga. */
    public static float heliLiftMargin(float load) {
        return clamp((2.4f - load) / 1.4f, 0.0f, 1.0f);
    }

    /** Régimen del rotor que hace falta para usar el empuje: con carga hay que esperar a que los rotores estén casi a tope. */
    public static float heliRpmFactor(float rpm, float takeoffRpm, float margin) {
        float needed = 0.65f + 0.30f * (1.0f - margin);
        return clamp((rpm - takeoffRpm) / (needed - takeoffRpm), 0.0f, 1.0f);
    }

    public enum Kind {
        SOFT, HARD, CRASH
    }

    /** Resultado de tocar el suelo. {@code damage} en puntos de vida (2 = un corazón). */
    public record Landing(Kind kind, float severity, float damage) {
    }

    /** Aterrizaje con velocidad de caída {@code sink} (bloques por tick, positiva) y peso relativo {@code load}. */
    public static Landing landing(float sink, float load) {
        float s = Math.max(0.0f, sink) * (float) Math.sqrt(load);
        if (s < 0.20f) {
            return new Landing(Kind.SOFT, s, 0.0f);
        }
        if (s < 0.40f) {
            return new Landing(Kind.HARD, s, 3.0f + (s - 0.20f) * 25.0f);        // de 1,5 a 4 corazones
        }
        return new Landing(Kind.CRASH, s, Math.min(40.0f, 8.0f + (s - 0.40f) * 70.0f));   // de 4 corazones hasta morir
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
