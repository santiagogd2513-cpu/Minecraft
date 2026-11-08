package com.example.beautifuldecor.entity;

/**
 * Los colores de "Otros" de un vehículo (V3.6.0) viajan juntos en un solo dato sincronizado: un texto con un número por canal
 * ("-1" = color original), separados por comas. Así se agregan canales sin llenar la entidad de datos sueltos. Esta clase solo
 * convierte y recuerda el último texto que decodificó (el dibujo pregunta por los colores en cada fotograma).
 */
public final class PaintExtras {
    /** Todos en su color original. */
    public static final String EMPTY = "";

    private String lastText = EMPTY;
    private int[] last = fresh();

    private static int[] fresh() {
        int[] out = new int[VehiclePaintExtras.MAX];
        java.util.Arrays.fill(out, VehiclePaint.ORIGINAL);
        return out;
    }

    /** Valores guardados en el texto (los que faltan o no se entienden valen -1). */
    public static int[] decode(String text) {
        int[] out = fresh();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String[] parts = text.split(",", -1);
        for (int i = 0; i < out.length && i < parts.length; i++) {
            try {
                int value = Integer.parseInt(parts[i].trim());
                out[i] = value < 0 ? VehiclePaint.ORIGINAL : value & 0xFFFFFF;
            } catch (NumberFormatException e) {
                out[i] = VehiclePaint.ORIGINAL;
            }
        }
        return out;
    }

    /** El texto de esos valores; si todos son originales, el texto vacío. */
    public static String encode(int[] values) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < VehiclePaintExtras.MAX; i++) {
            int value = i < values.length ? values[i] : VehiclePaint.ORIGINAL;
            if (value >= 0) {
                any = true;
                value &= 0xFFFFFF;
            } else {
                value = VehiclePaint.ORIGINAL;
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return any ? sb.toString() : EMPTY;
    }

    /** Valor del canal de "Otros" número {@code index} en ese texto (-1 si es el original o no existe). */
    public int get(String text, int index) {
        if (index < 0 || index >= VehiclePaintExtras.MAX) {
            return VehiclePaint.ORIGINAL;
        }
        if (!text.equals(this.lastText)) {
            this.last = decode(text);
            this.lastText = text;
        }
        return this.last[index];
    }

    /** El texto nuevo al poner {@code value} en el canal de "Otros" número {@code index}. */
    public static String with(String text, int index, int value) {
        int[] values = decode(text);
        if (index >= 0 && index < values.length) {
            values[index] = value < 0 ? VehiclePaint.ORIGINAL : value & 0xFFFFFF;
        }
        return encode(values);
    }
}
