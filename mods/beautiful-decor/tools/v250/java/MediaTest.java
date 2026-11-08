import com.example.beautifuldecor.browser.audio.PcmSource;
import com.example.beautifuldecor.browser.video.Mp4AacSource;
import com.example.beautifuldecor.browser.video.Mp4Video;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Video (orden y colores) y audio (con recorte del retraso del codificador) de un MP4 contra lo que saca ffmpeg. Args: mp4 rgba pcm */
public class MediaTest {
    public static void main(String[] a) throws Exception {
        Path mp4 = Path.of(a[0]);
        byte[] rgba = Files.readAllBytes(Path.of(a[1]));
        byte[] refPcm = Files.readAllBytes(Path.of(a[2]));
        int bad = 0;
        // ---- video
        try (Mp4Video v = Mp4Video.open(mp4)) {
            int w = v.width();
            int h = v.height();
            int n = 0;
            long last = Long.MIN_VALUE;
            double sum = 0;
            int maxD = 0;
            int[] spare = new int[w * h];
            Mp4Video.Frame f;
            while ((f = v.next(Long.MIN_VALUE, spare)) != null) {
                if (f.ptsUs <= last) {
                    System.out.println("PTS FUERA DE ORDEN en cuadro " + n);
                    bad++;
                }
                last = f.ptsUs;
                int base = n * w * h * 4;
                if (base + w * h * 4 <= rgba.length && f.abgr != null) {
                    for (int i = 0; i < w * h; i++) {
                        int p = f.abgr[i];
                        int r = p & 0xff;
                        int g = (p >> 8) & 0xff;
                        int b = (p >> 16) & 0xff;
                        int rr = rgba[base + i * 4] & 0xff;
                        int gg = rgba[base + i * 4 + 1] & 0xff;
                        int bb = rgba[base + i * 4 + 2] & 0xff;
                        int d = Math.max(Math.abs(r - rr), Math.max(Math.abs(g - gg), Math.abs(b - bb)));
                        sum += Math.abs(r - rr) + Math.abs(g - gg) + Math.abs(b - bb);
                        maxD = Math.max(maxD, d);
                        if ((p >>> 24) != 255) {
                            bad++;
                        }
                    }
                }
                n++;
            }
            int refFrames = rgba.length / (w * h * 4);
            System.out.printf("video %dx%d cuadros=%d ffmpeg=%d dif media=%.3f max=%d%n", w, h, n, refFrames, sum / (3.0 * Math.max(1, n) * w * h), maxD);
            if (n != refFrames) {
                bad++;
            }
            if (maxD > 12) {
                bad++;
            }
        }
        // ---- audio
        PcmSource s = Mp4AacSource.open(mp4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int r;
        while ((r = s.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
        int ch = s.channels();
        s.close();
        byte[] pcm = out.toByteArray();
        int n = Math.min(pcm.length, refPcm.length) / 2;
        int maxE = 0;
        for (int i = 0; i < n; i++) {
            int x = (short) ((pcm[2 * i] & 0xff) | (pcm[2 * i + 1] << 8));
            int y = (short) ((refPcm[2 * i] & 0xff) | (refPcm[2 * i + 1] << 8));
            maxE = Math.max(maxE, Math.abs(x - y));
        }
        System.out.printf("audio canales=%d rate=%d bytes=%d ffmpeg=%d maxErr=%d%n", ch, s.sampleRate(), pcm.length, refPcm.length, maxE);
        if (maxE > 2 || Math.abs(pcm.length - refPcm.length) > 4096 * ch) {
            bad++;
        }
        System.out.println(bad == 0 ? "MEDIA OK" : "MEDIA MAL " + bad);
    }
}
