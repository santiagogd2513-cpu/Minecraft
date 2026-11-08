import java.net.HttpURLConnection;
import java.net.URL;

/** La misma lógica nueva del motor de Nocturna (probe con reintento sin pedazo). */
public class Probe {
    static final String UA = "Mozilla/5.0 (Linux; Android 16; SM-A556E; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/142.0.0.0 Mobile Safari/537.36";

    static HttpURLConnection open(String url, String range) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", "https://www.curseforge.com/");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (range != null) c.setRequestProperty("Range", range);
        return c;
    }

    public static void main(String[] a) throws Exception {
        boolean allOk = true;
        for (String start : a) {
            System.out.println("=== " + start);
            String cur = start;
            String result = "FALLO";
            for (int hop = 0; hop < 10; hop++) {
                HttpURLConnection c = open(cur, "bytes=0-0");
                int code = c.getResponseCode();
                if (code >= 400) {
                    System.out.println("  " + code + " con pedazo, reintento entero: " + cur);
                    c.disconnect();
                    c = open(cur, null);
                    code = c.getResponseCode();
                }
                System.out.println("  " + code + "  " + cur + "  Content-Range=" + c.getHeaderField("Content-Range"));
                if (code >= 300 && code < 400) {
                    cur = new URL(new URL(cur), c.getHeaderField("Location")).toString();
                    c.disconnect();
                    continue;
                }
                if (code == 206 || code == 200) {
                    // como el motor: bajar el primer mega por pedazo desde la dirección final
                    HttpURLConnection s = (HttpURLConnection) new URL(cur).openConnection();
                    s.setRequestProperty("User-Agent", UA);
                    s.setRequestProperty("Range", "bytes=0-1048575");
                    int sc = s.getResponseCode();
                    long n = s.getInputStream().readAllBytes().length;
                    System.out.println("  pedazo: " + sc + ", " + n + " bytes");
                    result = (code == 206 ? sc == 206 : sc == 200) && n > 0 ? "OK" : "FALLO";
                }
                c.disconnect();
                break;
            }
            System.out.println("  => " + result);
            if (!result.equals("OK")) allOk = false;
        }
        System.out.println(allOk ? "TODO OK" : "HAY FALLOS");
        if (!allOk) System.exit(1);
    }
}
