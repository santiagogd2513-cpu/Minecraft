import com.example.beautifuldecor.ad.AdBrands;
import java.util.HashSet;
import java.util.Set;

/** Prueba la lógica pura de los anuncios: orden, vuelta al final, marcas de arranque distintas y póster que rota. */
public class AdBrandsTest {
    static int fails;

    static void check(boolean ok, String msg) {
        if (!ok) {
            fails++;
            System.out.println("FALLO: " + msg);
        }
    }

    public static void main(String[] a) {
        int n = AdBrands.count();
        check(n == 7, "debe haber 7 marcas, hay " + n);
        check(AdBrands.brand(-1).equals(AdBrands.BRANDS.get(n - 1)), "brand(-1) es la última");
        check(AdBrands.brand(n).equals(AdBrands.BRANDS.get(0)), "brand(n) da la vuelta");
        Set<String> seen = new HashSet<>();
        int idx = 3;
        for (int i = 0; i < n; i++) {
            seen.add(AdBrands.brand(idx));
            idx = AdBrands.next(idx);
        }
        check(seen.size() == n && idx == 3, "next recorre las 7 marcas y vuelve al principio");
        Set<Integer> starts = new HashSet<>();
        for (int x = -50; x < 50; x += 3) {
            for (int z = -50; z < 50; z += 5) {
                int s = AdBrands.startIndex(x, 64 + x % 7, z);
                check(s >= 0 && s < n, "startIndex fuera de rango: " + s);
                starts.add(s);
            }
        }
        check(starts.size() == n, "las posiciones deben repartirse entre las 7 marcas, salen " + starts.size());
        int different = 0;
        for (int x = 0; x < 20; x++) {
            if (AdBrands.startIndex(x * 4, 70, 10) != AdBrands.startIndex(x * 4 + 4, 70, 10)) {
                different++;
            }
        }
        check(different >= 10, "pantallas vecinas deben empezar casi siempre por marcas distintas (" + different + "/20)");
        int p0 = AdBrands.posterIndex(2, 0);
        int p1 = AdBrands.posterIndex(2, AdBrands.POSTER_MILLIS);
        check(p0 == 2 && p1 == 3, "el póster cambia cada " + AdBrands.POSTER_MILLIS + " ms");
        check(AdBrands.posterIndex(n - 1, AdBrands.POSTER_MILLIS) == 0, "el póster da la vuelta");
        check(AdBrands.posterIndex(0, Long.MAX_VALUE) >= 0, "póster con un tiempo enorme");
        check(AdBrands.videoPath("furd").equals("ads/furd.mp4") && AdBrands.posterPath("furd").equals("textures/ads/poster_furd.png"), "rutas");
        if (fails > 0) {
            System.exit(1);
        }
        System.out.println("AdBrandsTest OK");
    }
}
