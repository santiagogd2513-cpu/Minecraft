package com.example.beautifuldecor.megaphone;

/**
 * V4.0.0: cuentas del audio de megafonía que no dependen de Minecraft (así se pueden probar aparte): volumen de la voz, compuerta de ruido,
 * ajuste automático de nivel, medidor y cuánto se oye una bocina según la distancia.
 */
public final class PaDsp {
    /** Muestras por segundo del audio de megafonía. */
    public static final int RATE = 16000;
    /** Muestras por bloque enviado (40 ms). Siempre par. */
    public static final int FRAME = 640;
    /** Una bocina se oye al máximo hasta esta distancia (bloques) y se pierde del todo a {@link #HEAR_RANGE}. */
    public static final double FULL_RANGE = 3.0;
    public static final double HEAR_RANGE = 48.0;

    private PaDsp() {}

    /** Volumen eficaz (RMS) de {@code n} muestras, de 0 a 32768. */
    public static float rms(short[] pcm, int n) {
        if (n <= 0) {
            return 0.0f;
        }
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return (float) Math.sqrt(sum / n);
    }

    /** Nivel para la barra del menú (0 a 1): de -60 dB a 0 dB. */
    public static float meter(float rms) {
        if (rms <= 0.0f) {
            return 0.0f;
        }
        double db = 20.0 * Math.log10(rms / 32768.0);
        return (float) Math.max(0.0, Math.min(1.0, (db + 60.0) / 60.0));
    }

    /** Sensibilidad del menú (0 = solo voces fuertes, 1 = capta casi todo) al volumen mínimo que abre la compuerta: de 900 a 60. */
    public static float threshold(float sensitivity) {
        double s = Math.max(0.0, Math.min(1.0, sensitivity));
        return (float) (900.0 * Math.pow(60.0 / 900.0, s));
    }

    /** Cuánto se oye una bocina a esa distancia (0 a 1). */
    public static float distanceGain(double distance) {
        if (distance <= FULL_RANGE) {
            return 1.0f;
        }
        if (distance >= HEAR_RANGE) {
            return 0.0f;
        }
        double t = 1.0 - (distance - FULL_RANGE) / (HEAR_RANGE - FULL_RANGE);
        return (float) Math.pow(t, 1.5);
    }

    /** Compuerta de ruido: se abre con voz y se queda abierta unos bloques más para no cortar el final de las palabras. */
    public static final class Gate {
        public static final int HANGOVER = 6;           // 6 bloques = 240 ms
        private int hold;

        /** @return true si este bloque se debe enviar. */
        public boolean accept(float rms, float threshold) {
            if (rms >= threshold) {
                this.hold = HANGOVER;
                return true;
            }
            if (this.hold > 0) {
                --this.hold;
                return true;
            }
            return false;
        }

        public boolean isOpen() {
            return this.hold > 0;
        }

        public void reset() {
            this.hold = 0;
        }
    }

    /** Ajuste automático de nivel: sube las voces bajitas (micrófonos de celular) hasta un nivel cómodo, sin pasarse de 6 veces. */
    public static final class Agc {
        public static final float TARGET = 3000.0f;
        public static final float MAX_GAIN = 6.0f;
        private float gain = 1.0f;

        public float gain() {
            return this.gain;
        }

        /** Mira el volumen del bloque y devuelve la ganancia que se le debe aplicar. */
        public float update(float rms) {
            float want = rms < 1.0f ? this.gain : Math.max(1.0f, Math.min(MAX_GAIN, TARGET / rms));
            float k = want < this.gain ? 0.5f : 0.1f;        // baja rápido (no distorsionar) y sube despacio (no subir el ruido)
            this.gain += (want - this.gain) * k;
            return this.gain;
        }

        public void reset() {
            this.gain = 1.0f;
        }
    }

    /** Aplica la ganancia a {@code n} muestras, recortando suave hacia los extremos. */
    public static void applyGain(short[] pcm, int n, float gain) {
        for (int i = 0; i < n; i++) {
            float v = pcm[i] * gain;
            if (v > 32767.0f) {
                v = 32767.0f;
            } else if (v < -32768.0f) {
                v = -32768.0f;
            }
            pcm[i] = (short) v;
        }
    }
}
