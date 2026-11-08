package com.example.beautifuldecor.client;

import com.example.beautifuldecor.radio.RadioStations;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * V3.5.0: las emisoras propias de la radio de los vehículos, guardadas en este celular/PC (config/beautifuldecor-radio.txt, una por
 * línea: nombre, tabulador, dirección). Son de cada jugador y sirven en todos los mundos y servidores.
 */
public final class RadioFavorites {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = "beautifuldecor-radio.txt";
    private static List<RadioStations.Station> cache;

    private RadioFavorites() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    /** Las emisoras propias (se leen del archivo la primera vez). */
    public static synchronized List<RadioStations.Station> list() {
        if (cache == null) {
            List<String> lines = List.of();
            try {
                Path f = file();
                if (Files.exists(f)) {
                    lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("[BeautifulDecor] radio: no se pudieron leer las emisoras propias", e);
            }
            cache = new ArrayList<>(RadioStations.parseCustom(lines));
        }
        return List.copyOf(cache);
    }

    /** Agrega una emisora (ya revisada). Falso si ya estaba o si no caben más. */
    public static synchronized boolean add(String name, String url) {
        list();
        if (RadioStations.containsUrl(cache, url) || cache.size() >= RadioStations.MAX_CUSTOM) {
            return false;
        }
        String clean = RadioStations.cleanName(name);
        cache.add(new RadioStations.Station(clean.isEmpty() ? RadioStations.nameFromUrl(url) : clean, url, ""));
        save();
        return true;
    }

    public static synchronized void remove(String url) {
        list();
        if (cache.removeIf(s -> s.url().equalsIgnoreCase(url))) {
            save();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), RadioStations.formatCustom(cache), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[BeautifulDecor] radio: no se pudieron guardar las emisoras propias", e);
        }
    }
}
