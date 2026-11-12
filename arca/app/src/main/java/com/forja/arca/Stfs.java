package com.forja.arca;

import java.nio.charset.StandardCharsets;

/**
 * La cabecera de un paquete de la Xbox 360 (STFS: partidas guardadas, perfiles, descargas, temas…).
 * Los archivos empiezan con «CON », «LIVE» o «PIRS» y traen adentro el nombre del juego y su ícono,
 * así que no hace falta internet para saber de qué juego es cada cosa.
 */
final class Stfs {
    /** Cuántos bytes de arriba del archivo hay que leer para tener todo (cabecera + los dos íconos). */
    static final int HEADER = 0x971A;

    String magic = "";
    int contentType;
    long titleId;
    String displayName = "", titleName = "";
    byte[] thumbnail = new byte[0], titleThumbnail = new byte[0];

    /** Lee la cabecera, o devuelve null si el archivo no es un paquete. */
    static Stfs parse(byte[] h) {
        if (h == null || h.length < 0x1716 + 4) return null;
        String m = new String(h, 0, 4, StandardCharsets.ISO_8859_1);
        if (!m.equals("CON ") && !m.equals("LIVE") && !m.equals("PIRS")) return null;
        Stfs s = new Stfs();
        s.magic = m.trim();
        s.contentType = be32(h, 0x344);
        s.titleId = be32(h, 0x360) & 0xFFFFFFFFL;
        s.displayName = utf16(h, 0x411, 0x80);
        s.titleName = utf16(h, 0x1691, 0x80);
        int t1 = be32(h, 0x1712), t2 = be32(h, 0x1716);
        if (t1 > 0 && t1 <= 0x4000 && 0x171A + t1 <= h.length) s.thumbnail = java.util.Arrays.copyOfRange(h, 0x171A, 0x171A + t1);
        if (t2 > 0 && t2 <= 0x4000 && 0x571A + t2 <= h.length) s.titleThumbnail = java.util.Arrays.copyOfRange(h, 0x571A, 0x571A + t2);
        return s;
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    /** Texto UTF-16 de mayor a menor, hasta el primer carácter vacío. */
    private static String utf16(byte[] b, int off, int len) {
        int n = 0;
        while (n + 1 < len && (b[off + n] != 0 || b[off + n + 1] != 0)) n += 2;
        return new String(b, off, n, StandardCharsets.UTF_16BE).trim();
    }

    /** El nombre del juego si lo trae; si no, el nombre que se muestra del paquete. */
    String bestTitle() {
        return !titleName.isEmpty() ? titleName : displayName;
    }

    static String hex8(long v) {
        return String.format(java.util.Locale.US, "%08X", v & 0xFFFFFFFFL);
    }

    /** Nombre del tipo de contenido, en español. */
    static String typeName(int t) {
        return switch (t) {
            case 0x1 -> "Partida guardada";
            case 0x2 -> "Contenido del Marketplace";
            case 0x3 -> "Contenido de editor";
            case 0x1000 -> "Juego descargado";
            case 0x2000 -> "Buffer de TV";
            case 0x4000 -> "Juego instalado";
            case 0x5000 -> "Juego de Xbox original";
            case 0x9000 -> "Objeto de avatar";
            case 0x10000 -> "Perfil";
            case 0x20000 -> "Imagen de jugador";
            case 0x30000 -> "Tema";
            case 0x40000 -> "Archivo de caché";
            case 0x50000 -> "Descarga de almacenamiento";
            case 0x60000 -> "Partida de Xbox original";
            case 0x70000 -> "Descarga de Xbox original";
            case 0x80000 -> "Demo";
            case 0x90000 -> "Video";
            case 0xA0000 -> "Aplicación";
            case 0xB0000 -> "Instalador";
            case 0xC0000 -> "Tráiler";
            case 0xD0000 -> "Juego de Arcade";
            case 0xE0000 -> "Juego de XNA";
            case 0xF0000 -> "Licencias";
            case 0x100000 -> "Película";
            case 0x200000 -> "Serie de TV";
            case 0x300000 -> "Video musical";
            case 0x400000 -> "Video de juego";
            case 0x500000 -> "Podcast";
            case 0x600000 -> "Video viral";
            case 0x2000000 -> "Juego de la comunidad";
            default -> "Contenido (" + Integer.toHexString(t).toUpperCase(java.util.Locale.US) + ")";
        };
    }
}
