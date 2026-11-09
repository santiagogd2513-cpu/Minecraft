package com.example.beautifuldecor.radio;

/**
 * V4.2.0: lo que recuerda una radio de mesa: emisora (dirección y nombre), si está encendida y el volumen. Sin nada de Minecraft.
 */
public final class RadioEntry {
    private String url = "";
    private String name = "";
    private boolean on;
    private int volume = RadioStations.DEFAULT_VOLUME;

    public RadioEntry() {
    }

    public RadioEntry(String url, String name, boolean on, int volume) {
        this.url = url == null ? "" : url;
        this.name = name == null ? "" : name;
        this.on = on && !this.url.isEmpty();
        this.volume = RadioStations.clampVolume(volume);
    }

    public String url() {
        return this.url;
    }

    public String name() {
        return this.name;
    }

    public boolean on() {
        return this.on;
    }

    public int volume() {
        return this.volume;
    }

    /** true si debería sonar: encendida y con emisora. */
    public boolean playing() {
        return this.on && !this.url.isEmpty();
    }

    /** Elegir una emisora la enciende. La dirección debe venir ya limpia (RadioStations.cleanUrl). */
    public void select(String cleanUrl, String cleanName) {
        this.url = cleanUrl;
        this.name = cleanName == null || cleanName.isEmpty() ? RadioStations.nameFromUrl(cleanUrl) : cleanName;
        this.on = true;
    }

    /** Enciende o apaga; sin emisora no se puede encender. Devuelve el estado final. */
    public boolean setOn(boolean on) {
        this.on = on && !this.url.isEmpty();
        return this.on;
    }

    public void setVolume(int volume) {
        this.volume = RadioStations.clampVolume(volume);
    }

    public RadioEntry copy() {
        return new RadioEntry(this.url, this.name, this.on, this.volume);
    }
}
