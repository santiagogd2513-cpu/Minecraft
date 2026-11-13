package com.example.beautifuldecor.entity;

/**
 * Las demás partes que se pueden pintar de cada vehículo con el botón "Otros" (V3.6.0): vidrios, asientos, luces, cromados...
 * ARCHIVO GENERADO por tools/v360/extra_regions.py: no editar a mano. Cada vehículo tiene hasta 14 regiones; el color de
 * referencia es el promedio de los píxeles de la región (con él la textura queda como siempre). La clave da el nombre:
 * gui.beautifuldecor.paint.CLAVE. Lo único que no tiene región: los neumáticos y las hélices o el rotor.
 */
public final class VehiclePaintExtras {
    /** Tope de regiones por vehículo. */
    public static final int MAX = 14;
    private static final String[] NONE_KEYS = new String[0];
    private static final int[] NONE_REFS = new int[0];

    private VehiclePaintExtras() {
    }

    /** Claves de las regiones del vehículo con esa textura (en el orden del menú y de la máscara: región 1 = la primera). */
    public static String[] keys(String texture) {
        return switch (texture) {
            case "motorcycle" -> new String[] {"black", "engine", "brakes", "chrome", "glass", "headlight", "taillight", "seat", "dash", "plate", "details"};
            case "bicycle" -> new String[] {"chrome", "black", "saddle", "chain", "headlight", "taillight", "bell", "details"};
            case "ford_f150" -> new String[] {"black", "chrome", "bed", "interior", "dash", "seat", "grille", "headlight", "taillight", "glass", "details"};
            case "delivery_truck" -> new String[] {"black", "chrome", "tank", "bumper", "interior", "dash", "seat", "grille", "headlight", "taillight", "marker", "glass", "details"};
            case "helicopter" -> new String[] {"skid", "interior", "dash", "seat", "accent", "black", "glass", "details"};
            case "bugotti_chillon" -> new String[] {"black", "carbon", "chrome", "interior", "dash", "seat", "intake", "headlight", "taillight", "glass", "details"};
            case "airliner" -> new String[] {"accent", "black", "chrome", "glass", "seat", "interior", "dash", "headlight", "taillight", "details"};
            case "freight_loco" -> new String[] {"accent", "black", "chrome", "glass", "interior", "headlight", "taillight", "details"};
            case "coal_wagon", "food_wagon", "mineral_wagon" -> new String[] {"accent", "black", "interior", "details"};
            case "steam_classic", "steam_modern", "metro_head" -> new String[] {"accent", "black", "chrome", "glass", "interior", "headlight", "taillight", "details"};
            case "coach_low", "coach_mid", "coach_high", "metro_car" -> new String[] {"accent", "black", "glass", "interior", "seat", "details"};
            default -> NONE_KEYS;
        };
    }

    /** Colores de referencia (0xRRGGBB) de las regiones, en el mismo orden que {@link #keys}. */
    public static int[] refs(String texture) {
        return switch (texture) {
            case "motorcycle" -> new int[] {0x18181D, 0x322B31, 0x757983, 0xAFB4BF, 0x1E2943, 0x4D525A, 0x8A0E0F, 0x251C20, 0x1A2020, 0x76767A, 0x1E1318};
            case "bicycle" -> new int[] {0xACB2BE, 0x1C1C20, 0x6C3F22, 0x333338, 0x3D4148, 0x8D0E12, 0xD3D8DF, 0xF0F4F8};
            case "ford_f150" -> new int[] {0x16171B, 0xB5BBC6, 0x24262B, 0x2C2E34, 0x1A1C23, 0x3A3C45, 0x3E3F44, 0x60656E, 0x670D10, 0x212F41, 0x4B4D54};
            case "delivery_truck" -> new int[] {0x15161B, 0x9A9FAB, 0x90949D, 0x7C808A, 0x35373E, 0x1E2025, 0x373A44, 0x1F1F25, 0x5B6069, 0x6D0E11, 0xE6961E, 0x223043, 0x504C5A};
            case "helicopter" -> new int[] {0x50545F, 0x2D2F35, 0x202225, 0x393D49, 0x283883, 0x17181D, 0x223042, 0x50536A};
            case "bugotti_chillon" -> new int[] {0x18161A, 0x1F2024, 0x989BA5, 0x2B2B30, 0x1F2023, 0x3B393D, 0x171618, 0x3D4149, 0x860D11, 0x213349, 0x3C3837};
            case "airliner" -> new int[] {0x313F82, 0x18191E, 0xAEB1BD, 0x1F2D3F, 0x2D407A, 0x525870, 0x222225, 0xF6F4E2, 0xA85534, 0x7B85A2};
            case "freight_loco" -> new int[] {0xF0C020, 0x1C1D22, 0xB8BCC8, 0x2A4058, 0x6A5A48, 0xF6F2D8, 0xC8222A, 0x3A3D46};
            case "coal_wagon" -> new int[] {0xC8461E, 0x1C1D22, 0x2A2C32, 0x50545E};
            case "food_wagon" -> new int[] {0xF2EFE4, 0x1C1D22, 0x9A7448, 0x6B4B2A};
            case "mineral_wagon" -> new int[] {0xE0B030, 0x1C1D22, 0x4A4E58, 0x6A3A1E};
            case "steam_classic" -> new int[] {0xD6AA38, 0x18191D, 0xB8BCC8, 0x2A4058, 0x6A5A48, 0xF6F2D8, 0xC8222A, 0x963428};
            case "steam_modern" -> new int[] {0xECECF0, 0x18191D, 0xB8BCC8, 0x2A4058, 0x6A5A48, 0xF6F2D8, 0xC8222A, 0x464A54};
            case "coach_low" -> new int[] {0xDCBE60, 0x1C1D22, 0x2A4058, 0x60503E, 0x92683E, 0x463426};
            case "coach_mid" -> new int[] {0xF0F0F4, 0x1C1D22, 0x2A4058, 0x968C7E, 0x34487C, 0x505660};
            case "metro_head" -> new int[] {0x1C78D2, 0x18191D, 0xB8BCC8, 0x2A4058, 0x606670, 0xF6F2D8, 0xC8222A, 0x787E8A};
            case "metro_car" -> new int[] {0x1C78D2, 0x1C1D22, 0x2A4058, 0x969CA6, 0x1E5AA0, 0x787E8A};
            case "coach_high" -> new int[] {0xE2BA42, 0x1C1D22, 0x2A4058, 0x745438, 0x8C2834, 0x3C281E};
            default -> NONE_REFS;
        };
    }
}
