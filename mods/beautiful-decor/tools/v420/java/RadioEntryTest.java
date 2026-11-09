import com.example.beautifuldecor.radio.RadioEntry;
import com.example.beautifuldecor.radio.RadioStations;

public class RadioEntryTest {
    static void check(boolean c, String m) {
        if (!c) {
            throw new AssertionError(m);
        }
    }

    public static void main(String[] a) {
        RadioEntry e = new RadioEntry();
        check(!e.on() && !e.playing() && e.url().isEmpty(), "nueva: apagada y sin emisora");
        check(e.volume() == RadioStations.DEFAULT_VOLUME, "volumen por defecto");
        check(!e.setOn(true) && !e.on(), "sin emisora no se enciende");
        String url = RadioStations.cleanUrl("http://radio.example.com:8000/stream");
        check(url != null, "url válida");
        e.select(url, "");
        check(e.on() && e.playing(), "elegir emisora enciende");
        check(!e.name().isEmpty(), "nombre sale de la url si no se da");
        check(!e.setOn(false) && !e.playing() && e.url().equals(url), "apagar conserva la emisora");
        check(e.setOn(true) && e.playing(), "volver a encender");
        e.setVolume(500);
        check(e.volume() == RadioStations.clampVolume(500) && e.volume() <= 100, "volumen acotado");
        e.setVolume(-30);
        check(e.volume() >= 0, "volumen mínimo");
        RadioEntry c = e.copy();
        check(c.url().equals(e.url()) && c.on() == e.on() && c.volume() == e.volume() && c != e, "copia igual");
        RadioEntry bad = new RadioEntry("", "x", true, 70);
        check(!bad.on(), "al cargar, encendida sin emisora queda apagada");
        RadioEntry ok = new RadioEntry(url, "Mi radio", true, 40);
        check(ok.playing() && ok.volume() == 40 && ok.name().equals("Mi radio"), "carga normal");
        System.out.println("RadioEntryTest OK");
    }
}
