import com.example.beautifuldecor.browser.audio.AacDecoder;
import com.example.beautifuldecor.browser.video.Mp4;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decodifica el audio AAC de un MP4 y lo compara con un s16le crudo hecho por ffmpeg (mismo numero de canales). */
public class AacTest {
    public static void main(String[] a) throws Exception {
        Path mp4 = Path.of(a[0]);
        byte[] ref = Files.readAllBytes(Path.of(a[1]));
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int ch;
        int rate;
        long t0 = System.nanoTime();
        try (Mp4 m = Mp4.open(mp4)) {
            Mp4.Track t = m.audio;
            if (t == null) {
                System.out.println("SIN AUDIO: " + m.unsupportedAudio);
                System.exit(2);
            }
            AacDecoder d;
            try {
                d = new AacDecoder(t.audioConfig);
            } catch (AacDecoder.Unsupported e) {
                System.out.println("UNSUPPORTED: " + e.getMessage());
                return;
            }
            ch = d.channels();
            rate = d.sampleRate();
            for (int i = 0; i < t.sampleCount(); i++) {
                short[] s = d.decode(m.readSample(t, i));
                for (short v : s) {
                    pcm.write(v & 0xff);
                    pcm.write((v >> 8) & 0xff);
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1000000;
        byte[] out = pcm.toByteArray();
        int n = out.length / 2;
        int rn = ref.length / 2;
        // busca el desplazamiento (muestras entrelazadas) con menor error
        int bestOff = -1;
        double bestErr = Double.MAX_VALUE;
        for (int off = 0; off <= 4096 * ch; off += ch) {
            double err = 0;
            int cnt = 0;
            for (int i = 0; i < 4000 * ch && i + off < n && i + 20000 * ch < rn; i++) {
                int x = (short) ((out[(i + off) * 2] & 0xff) | (out[(i + off) * 2 + 1] << 8));
                int y = (short) ((ref[(i + 20000 * ch) * 2] & 0xff) | (ref[(i + 20000 * ch) * 2 + 1] << 8));
                // alinea contra el mismo tramo de la referencia (20000 muestras adentro)
                err += Math.abs(x - y);
                cnt++;
            }
            // se compara out[i+off] con ref[i+20000*ch] => off real = 20000*ch + desplazamiento; ajustamos
            err = cnt == 0 ? Double.MAX_VALUE : err / cnt;
            if (err < bestErr) {
                bestErr = err;
                bestOff = off;
            }
        }
        // out[k + shift] == ref[k]. Con k=20000*ch+i: out index = i + off => shift = off - 20000*ch; buscamos off desde 0 ⇒ shift negativo
        // Reintentar: shift = indice_out - indice_ref
        int bestShift = 0;
        bestErr = Double.MAX_VALUE;
        for (int shift = -4096 * ch; shift <= 4096 * ch; shift += ch) {
            double err = 0;
            int cnt = 0;
            int start = 20000 * ch;
            for (int i = 0; i < 4000 * ch; i++) {
                int ri = start + i;
                int oi = ri + shift;
                if (ri >= rn || oi < 0 || oi >= n) {
                    continue;
                }
                int x = (short) ((out[oi * 2] & 0xff) | (out[oi * 2 + 1] << 8));
                int y = (short) ((ref[ri * 2] & 0xff) | (ref[ri * 2 + 1] << 8));
                err += Math.abs(x - y);
                cnt++;
            }
            err = cnt == 0 ? Double.MAX_VALUE : err / cnt;
            if (err < bestErr) {
                bestErr = err;
                bestShift = shift;
            }
        }
        long sumAbs = 0;
        int maxAbs = 0;
        long cnt = 0;
        int bigCount = 0;
        for (int ri = 0; ri < rn; ri++) {
            int oi = ri + bestShift;
            if (oi < 0 || oi >= n) {
                continue;
            }
            int x = (short) ((out[oi * 2] & 0xff) | (out[oi * 2 + 1] << 8));
            int y = (short) ((ref[ri * 2] & 0xff) | (ref[ri * 2 + 1] << 8));
            int e = Math.abs(x - y);
            sumAbs += e;
            if (e > maxAbs) {
                maxAbs = e;
            }
            if (e > 2) {
                bigCount++;
            }
            cnt++;
        }
        String verdict = maxAbs <= 2 ? "IGUAL" : (maxAbs <= 8 ? "CASI" : "DIFERENTE");
        System.out.printf("%s canales=%d rate=%d shift=%d muestras=%d/%d maxErr=%d medio=%.4f >2:%d %dms%n", verdict, ch, rate, bestShift / ch, cnt / ch, rn / ch, maxAbs, cnt == 0 ? 0.0 : (double) sumAbs / cnt, bigCount, ms);
    }
}
