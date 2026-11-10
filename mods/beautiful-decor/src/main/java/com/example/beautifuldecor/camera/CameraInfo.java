package com.example.beautifuldecor.camera;

/** V4.3.0: los datos de una cámara de seguridad que recuerda el servidor (sin nada de Minecraft). */
public final class CameraInfo {
    public static final int MAX_NAME = 32;
    public static final int MAX_DESC = 120;

    private final int id;
    private String name;
    private String description;
    private boolean rotate;

    public CameraInfo(int id, String name, String description, boolean rotate) {
        this.id = id;
        this.name = clean(name, MAX_NAME);
        this.description = clean(description, MAX_DESC);
        this.rotate = rotate;
    }

    /** Sin saltos de línea ni espacios sobrantes, y no más largo que {@code max}. */
    public static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    public static String defaultName(int id) {
        return "Cámara " + id;
    }

    public int id() {
        return this.id;
    }

    public String name() {
        return this.name.isEmpty() ? defaultName(this.id) : this.name;
    }

    public String description() {
        return this.description;
    }

    public boolean rotate() {
        return this.rotate;
    }

    public void set(String name, String description, boolean rotate) {
        this.name = clean(name, MAX_NAME);
        this.description = clean(description, MAX_DESC);
        this.rotate = rotate;
    }
}
