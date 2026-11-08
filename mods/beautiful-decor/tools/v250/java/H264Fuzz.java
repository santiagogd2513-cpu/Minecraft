import com.example.beautifuldecor.browser.video.H264Decoder;
import com.example.beautifuldecor.browser.video.H264Pic;
import com.example.beautifuldecor.browser.video.Mp4;
import java.nio.file.Path;
import java.util.Random;

/** Estropea muestras al azar y comprueba que el decodificador no se cuelga ni lanza errores inesperados. */
public class H264Fuzz {
    public static void main(String[] a) throws Exception {
        int rounds = Integer.parseInt(a[1]);
        Random rnd = new Random(12345);
        int bad = 0;
        long worst = 0;
        try (Mp4 m = Mp4.open(Path.of(a[0]))) {
            Mp4.Track v = m.video;
            byte[][] samples = new byte[v.sampleCount()][];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = m.readSample(v, i);
            }
            for (int r = 0; r < rounds; r++) {
                H264Decoder dec = new H264Decoder();
                dec.setParameterSets(v.sps, v.pps, v.nalLengthSize);
                long t0 = System.nanoTime();
                try {
                    for (int i = 0; i < samples.length; i++) {
                        byte[] s = samples[i].clone();
                        int mode = rnd.nextInt(4);
                        if (mode == 0 || mode == 1) {
                            int n = 1 + rnd.nextInt(6);
                            for (int k = 0; k < n; k++) {
                                int p = rnd.nextInt(s.length);
                                s[p] = (byte) rnd.nextInt(256);
                            }
                        } else if (mode == 2 && s.length > 8) {
                            s = java.util.Arrays.copyOf(s, 4 + rnd.nextInt(s.length - 4));
                        }
                        H264Pic p = dec.decode(s);
                        if (p != null) {
                            dec.release(p);
                        }
                    }
                } catch (H264Decoder.Unsupported u) {
                    // aceptable
                } catch (Throwable t) {
                    bad++;
                    System.out.println("ERROR inesperado en ronda " + r + ": " + t);
                    StackTraceElement[] st = t.getStackTrace();
                    for (int k = 0; k < Math.min(4, st.length); k++) {
                        System.out.println("   " + st[k]);
                    }
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                worst = Math.max(worst, ms);
            }
        }
        System.out.println((bad == 0 ? "FUZZ OK" : "FUZZ MAL") + " rondas=" + rounds + " errores=" + bad + " peor=" + worst + "ms");
        System.exit(bad == 0 ? 0 : 1);
    }
}
