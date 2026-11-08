import com.example.beautifuldecor.megaphone.ImaAdpcm;
import com.example.beautifuldecor.megaphone.PaDsp;

import java.util.Random;

/** V4.0.0: prueba del códec ADPCM y de las cuentas del audio de megafonía (sin Minecraft). */
public final class PaAudioTest {
    static int fails;

    static void check(boolean cond, String msg) {
        if (!cond) {
            fails++;
            System.out.println("FALLA: " + msg);
        }
    }

    /** Señal parecida a la voz: vocal (varios armónicos que caen rápido) con vibrato, envolvente lenta y un poco de ruido. */
    static short[] voice(int n, int seed, double amp) {
        Random rng = new Random(seed);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) PaDsp.RATE;
            double f0 = 140 + 25 * Math.sin(2 * Math.PI * 3 * t);
            double v = 0;
            for (int h = 1; h <= 8; h++) {
                v += Math.sin(2 * Math.PI * f0 * h * t) / (h * h);      // armónicos que caen como 1/h^2 (voz suave, no un diente de sierra)
            }
            double env = 0.5 + 0.5 * Math.sin(2 * Math.PI * 2.2 * t);
            s[i] = (short) Math.max(-32768, Math.min(32767, amp * env * v + rng.nextGaussian() * amp * 0.01));
        }
        return s;
    }

    static double snrDb(short[] a, short[] b) {
        double sig = 0, err = 0;
        for (int i = 0; i < a.length; i++) {
            sig += (double) a[i] * a[i];
            double d = a[i] - b[i];
            err += d * d;
        }
        return 10 * Math.log10(sig / Math.max(err, 1e-9));
    }

    public static void main(String[] args) {
        // ---- tamaños
        check(PaDsp.FRAME % 2 == 0, "el bloque debe tener un número par de muestras");
        check(ImaAdpcm.blockSize(PaDsp.FRAME) == 323, "un bloque de 640 muestras ocupa 323 bytes");
        double kbps = ImaAdpcm.blockSize(PaDsp.FRAME) * (1000.0 / 40.0) / 1024.0;
        check(kbps < 9.0, "el flujo debe ser de unos 8 KB/s (es " + kbps + ")");

        // ---- ida y vuelta, con bloques seguidos y con estado
        short[] src = voice(PaDsp.RATE * 3, 7, 9000);
        ImaAdpcm.Encoder enc = new ImaAdpcm.Encoder();
        short[] out = new short[src.length];
        int blocks = src.length / PaDsp.FRAME;
        for (int b = 0; b < blocks; b++) {
            byte[] blk = enc.encode(src, b * PaDsp.FRAME, PaDsp.FRAME);
            check(blk.length == 323, "tamaño del bloque " + b);
            short[] dec = ImaAdpcm.decode(blk);
            check(dec.length == PaDsp.FRAME, "muestras decodificadas " + b);
            System.arraycopy(dec, 0, out, b * PaDsp.FRAME, dec.length);
        }
        double snr = snrDb(src, out);
        check(snr > 20.0, "la voz decodificada debe parecerse a la original (SNR " + snr + " dB)");
        System.out.printf("ADPCM: SNR %.1f dB, %.2f KB/s%n", snr, kbps);

        // ---- cada bloque se decodifica solo (sin los anteriores): igual que dentro del flujo
        ImaAdpcm.Encoder enc2 = new ImaAdpcm.Encoder();
        byte[][] all = new byte[blocks][];
        for (int b = 0; b < blocks; b++) {
            all[b] = enc2.encode(src, b * PaDsp.FRAME, PaDsp.FRAME);
        }
        short[] lone = ImaAdpcm.decode(all[20]);
        boolean same = true;
        for (int i = 0; i < PaDsp.FRAME; i++) {
            same &= lone[i] == out[20 * PaDsp.FRAME + i];
        }
        check(same, "un bloque suelto se decodifica igual que dentro del flujo");

        // ---- saltarse bloques (silencio) y volver con el codificador reiniciado no rompe nada
        ImaAdpcm.Encoder enc3 = new ImaAdpcm.Encoder();
        enc3.encode(src, 0, PaDsp.FRAME);
        enc3.reset();
        short[] resumed = ImaAdpcm.decode(enc3.encode(src, 10 * PaDsp.FRAME, PaDsp.FRAME));
        short[] orig = new short[PaDsp.FRAME];
        System.arraycopy(src, 10 * PaDsp.FRAME, orig, 0, PaDsp.FRAME);
        check(snrDb(orig, resumed) > 10.0, "tras un silencio el audio se recupera rápido (SNR " + snrDb(orig, resumed) + ")");

        // ---- extremos: silencio, volumen máximo, onda cuadrada
        short[] zero = new short[PaDsp.FRAME];
        short[] zdec = ImaAdpcm.decode(new ImaAdpcm.Encoder().encode(zero, 0, PaDsp.FRAME));
        int maxz = 0;
        for (short v : zdec) {
            maxz = Math.max(maxz, Math.abs(v));
        }
        check(maxz <= 8, "el silencio sigue siendo silencio (máx " + maxz + ")");
        short[] loud = new short[PaDsp.FRAME];
        for (int i = 0; i < loud.length; i++) {
            loud[i] = (short) ((i / 20) % 2 == 0 ? 32767 : -32768);
        }
        short[] ld = ImaAdpcm.decode(new ImaAdpcm.Encoder().encode(loud, 0, PaDsp.FRAME));
        check(ld.length == PaDsp.FRAME, "la onda cuadrada no rompe el códec");

        // ---- datos dañados no revientan
        check(ImaAdpcm.decode(null).length == 0, "decodificar null");
        check(ImaAdpcm.decode(new byte[2]).length == 0, "decodificar muy corto");
        check(ImaAdpcm.decode(new byte[]{0, 0, (byte) 200, 1, 2}).length == 0, "índice fuera de rango");
        byte[] junk = new byte[323];
        new Random(5).nextBytes(junk);
        junk[2] = 40;
        check(ImaAdpcm.decode(junk).length == PaDsp.FRAME, "bytes cualquiera se decodifican sin reventar");
        boolean threw = false;
        try {
            new ImaAdpcm.Encoder().encode(zero, 0, 3);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "un número impar de muestras se rechaza");

        // ---- volumen, medidor y sensibilidad
        short[] tone = new short[PaDsp.FRAME];
        for (int i = 0; i < tone.length; i++) {
            tone[i] = (short) (10000 * Math.sin(2 * Math.PI * 440 * i / PaDsp.RATE));
        }
        float rms = PaDsp.rms(tone, tone.length);
        check(Math.abs(rms - 7071) < 80, "RMS de una senoidal de 10000 es ~7071 (da " + rms + ")");
        check(PaDsp.rms(zero, zero.length) == 0.0f && PaDsp.rms(zero, 0) == 0.0f, "RMS del silencio");
        check(PaDsp.meter(0) == 0.0f && PaDsp.meter(32768) == 1.0f && PaDsp.meter(1000) > 0.0f && PaDsp.meter(1000) < PaDsp.meter(10000), "medidor creciente");
        check(PaDsp.threshold(0) > PaDsp.threshold(0.5f) && PaDsp.threshold(0.5f) > PaDsp.threshold(1)
                && Math.abs(PaDsp.threshold(0) - 900) < 1 && Math.abs(PaDsp.threshold(1) - 60) < 1, "la sensibilidad baja el umbral (900 a 60)");
        check(PaDsp.threshold(-5) == PaDsp.threshold(0) && PaDsp.threshold(5) == PaDsp.threshold(1), "sensibilidad fuera de rango se recorta");

        // ---- compuerta: se abre con voz, aguanta 6 bloques y se cierra
        PaDsp.Gate gate = new PaDsp.Gate();
        float th = PaDsp.threshold(0.5f);
        check(!gate.accept(10, th), "silencio: cerrada");
        check(gate.accept(2000, th), "voz: abre");
        int held = 0;
        for (int i = 0; i < 20 && gate.accept(10, th); i++) {
            held++;
        }
        check(held == PaDsp.Gate.HANGOVER, "aguanta " + PaDsp.Gate.HANGOVER + " bloques tras la voz (aguantó " + held + ")");
        check(!gate.isOpen(), "luego se cierra");

        // ---- ajuste automático de nivel: sube lo bajito, no toca lo fuerte, nunca pasa de 6
        PaDsp.Agc agc = new PaDsp.Agc();
        float g = 1;
        for (int i = 0; i < 60; i++) {
            g = agc.update(300);
        }
        check(g > 5.5f && g <= PaDsp.Agc.MAX_GAIN + 1e-3f, "voz bajita (300): la ganancia sube a ~6 (" + g + ")");
        for (int i = 0; i < 60; i++) {
            g = agc.update(3000);
        }
        check(Math.abs(g - 1.0f) < 0.05f, "voz normal (3000): ganancia ~1 (" + g + ")");
        for (int i = 0; i < 60; i++) {
            g = agc.update(20000);
        }
        check(g == 1.0f, "voz muy fuerte: ganancia 1 (nunca baja de 1)");
        agc.reset();
        check(agc.gain() == 1.0f, "reset");
        short[] loudish = {30000, -30000, 100, -100};
        PaDsp.applyGain(loudish, 4, 6.0f);
        check(loudish[0] == 32767 && loudish[1] == -32768 && loudish[2] == 600 && loudish[3] == -600, "la ganancia recorta sin dar la vuelta");

        // ---- distancia
        check(PaDsp.distanceGain(0) == 1.0f && PaDsp.distanceGain(PaDsp.FULL_RANGE) == 1.0f, "cerca: máximo");
        check(PaDsp.distanceGain(PaDsp.HEAR_RANGE) == 0.0f && PaDsp.distanceGain(500) == 0.0f, "lejos: nada");
        float prev = 1.0f;
        boolean mono = true;
        for (double d = 3; d <= 48; d += 0.5) {
            float v = PaDsp.distanceGain(d);
            mono &= v <= prev + 1e-6f;
            prev = v;
        }
        check(mono, "la distancia solo baja el volumen");
        check(PaDsp.distanceGain(25) > 0.1f && PaDsp.distanceGain(25) < 0.9f, "a media distancia se oye a medias");

        if (fails == 0) {
            System.out.println("PA AUDIO OK");
        } else {
            System.out.println(fails + " fallas");
            System.exit(1);
        }
    }
}
