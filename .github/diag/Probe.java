import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Hace lo mismo que el motor de descargas de Nocturna y muestra cada paso. */
public class Probe {
    static final String UA = "Mozilla/5.0 (Linux; Android 16; SM-A556E Build/BP2A.250605.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/142.0.7444.106 Mobile Safari/537.36";

    public static void main(String[] a) throws Exception {
        for (String start : a) {
            System.out.println("=== " + start);
            String cur = start;
            for (int hop = 0; hop < 10; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Referer", "https://modrinth.com/");
                c.setRequestProperty("Accept-Encoding", "identity");
                c.setRequestProperty("Range", "bytes=0-0");
                int code = c.getResponseCode();
                System.out.println("  " + code + "  " + cur);
                System.out.println("     Location=" + c.getHeaderField("Location") + "  Content-Range=" + c.getHeaderField("Content-Range") + "  type=" + c.getContentType());
                if (code >= 300 && code < 400) {
                    cur = new URL(new URL(cur), c.getHeaderField("Location")).toString();
                    continue;
                }
                if (code >= 400) {
                    InputStream e = c.getErrorStream();
                    if (e != null) System.out.println("     cuerpo: " + new String(e.readNBytes(300)).replace('\n', ' '));
                }
                break;
            }
        }
    }
}
