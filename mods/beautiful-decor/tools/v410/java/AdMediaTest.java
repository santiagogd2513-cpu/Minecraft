import com.example.beautifuldecor.browser.audio.PcmSource;
import com.example.beautifuldecor.browser.video.Mp4AacSource;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decodifica el audio AAC de un anuncio con el decodificador del mod y lo compara con el PCM (s16le mono) que saca ffmpeg. */
public class AdMediaTest {
    public static void main(String[] a) throws Exception {
        byte[] ref = Files.readAllBytes(Path.of(a[1]));
        ByteArrayOutputStream mine = new ByteArrayOutputStream();
        int rate;
        int ch;
        try (PcmSource src = Mp4AacSource.open(Path.of(a[0]))) {
            rate = src.sampleRate();
            ch = src.channels();
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) > 0) {
                mine.write(buf, 0, n);
            }
        }
        byte[] m = mine.toByteArray();
        int n = Math.min(m.length, ref.length) / 2;
        // el decodificador del mod y ffmpeg pueden diferir en el retardo del códec: se busca el desfase (hasta 2200 muestras)
        double best = -1;
        int bestShift = 0;
        for (int shift = -2200; shift <= 2200; shift += 1) {
            double dot = 0, e1 = 0, e2 = 0;
            for (int i = 4000; i < Math.min(n - 4000, 60000); i += 3) {
                int j = i + shift;
                if (j < 0 || j >= n) {
                    continue;
                }
                double x = (short) ((m[2 * i] & 0xff) | (m[2 * i + 1] << 8));
                double y = (short) ((ref[2 * j] & 0xff) | (ref[2 * j + 1] << 8));
                dot += x * y;
                e1 += x * x;
                e2 += y * y;
            }
            double c = dot / Math.sqrt(Math.max(1e-9, e1 * e2));
            if (c > best) {
                best = c;
                bestShift = shift;
            }
        }
        double rms = 0;
        for (int i = 0; i < n; i++) {
            double x = (short) ((m[2 * i] & 0xff) | (m[2 * i + 1] << 8));
            rms += x * x;
        }
        rms = Math.sqrt(rms / Math.max(1, n));
        System.out.printf("rate=%d ch=%d seconds=%.2f rms=%.0f corr=%.5f shift=%d%n", rate, ch, n / (double) rate / Math.max(1, ch), rms, best, bestShift);
    }
}
