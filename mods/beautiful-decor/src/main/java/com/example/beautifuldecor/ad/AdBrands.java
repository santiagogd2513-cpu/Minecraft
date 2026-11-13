package com.example.beautifuldecor.ad;

import java.util.List;

/**
 * V4.1.0: las marcas del mod que tienen anuncio (V4.1.1: ahora también HS, la marca insignia) y el orden en que lo pasan las pantallas de anuncios. Lógica pura (sin Minecraft).
 * <p>
 * Cada marca tiene un video "ads/&lt;marca&gt;.mp4" (imagen + sonido) y un póster "textures/ads/poster_&lt;marca&gt;.png". Cada pantalla pasa los
 * anuncios en este orden, uno tras otro y en bucle; para que pantallas vecinas no pasen el mismo a la vez, cada una empieza por una
 * marca distinta según dónde está.
 */
public final class AdBrands {
    /** Una marca por anuncio, en el orden de la lista. */
    public static final List<String> BRANDS = List.of("hs", "furd", "amazoom", "lamborgonzo", "boing", "bugerati", "bugotti", "rojotoro", "burguerreino", "polisia", "aguacero", "hsnews", "loteria");
    /** Cuánto dura el póster de cada marca cuando la pantalla está lejos y no reproduce el video (milisegundos). */
    public static final long POSTER_MILLIS = 7000L;

    private AdBrands() {
    }

    public static int count() {
        return BRANDS.size();
    }

    /** La marca de ese número; el número se reduce solo al rango válido (también si es negativo). */
    public static String brand(int index) {
        return BRANDS.get(Math.floorMod(index, BRANDS.size()));
    }

    /** El número que sigue, dando la vuelta al final. */
    public static int next(int index) {
        return Math.floorMod(index + 1, BRANDS.size());
    }

    /** Por qué marca empieza la pantalla que está en esa posición (hay un valor distinto para posiciones vecinas). */
    public static int startIndex(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= (h >>> 17);
        return (int) Math.floorMod(h, (long) BRANDS.size());
    }

    /** El póster que toca mostrar ahora a una pantalla lejana: cambia cada {@link #POSTER_MILLIS} y respeta el desfase de la pantalla. */
    public static int posterIndex(int start, long millis) {
        return Math.floorMod(start + (int) Math.floorMod(millis / POSTER_MILLIS, (long) BRANDS.size()), BRANDS.size());
    }

    /** Ruta del video dentro del mod, sin el espacio de nombres ("ads/furd.mp4"). */
    public static String videoPath(String brand) {
        return "ads/" + brand + ".mp4";
    }

    /** Ruta del póster dentro del mod, sin el espacio de nombres. */
    public static String posterPath(String brand) {
        return "textures/ads/poster_" + brand + ".png";
    }
}
