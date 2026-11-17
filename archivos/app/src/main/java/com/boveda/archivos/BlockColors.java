package com.boveda.archivos;

import java.util.HashMap;
import java.util.Map;

/**
 * Colores aproximados de los bloques (sin las texturas de Minecraft): una tabla para los más comunes y, para el resto,
 * pistas por el nombre («rojo», «madera», «cristal»…) o un color estable sacado del nombre. Sin Android, para probarlo en el computador.
 */
final class BlockColors {
    private BlockColors() {}

    static final int AIR = 0, DECOR = 1, SOLID = 2, GLASS = 3;

    private static final Map<String, Integer> KNOWN = new HashMap<>();
    private static final String[][] DYES = {
            {"white", "E6EBEB"}, {"orange", "E07A1F"}, {"magenta", "B64EB0"}, {"light_blue", "4FB0D9"}, {"yellow", "F0C535"}, {"lime", "73B918"},
            {"pink", "E88EAE"}, {"gray", "3E4448"}, {"light_gray", "8E8E86"}, {"cyan", "169B9B"}, {"purple", "7B2FB0"}, {"blue", "3A41A4"},
            {"brown", "724728"}, {"green", "546B22"}, {"red", "A12722"}, {"black", "14161C"}};

    static {
        put("stone", 0x7D7D7D); put("cobblestone", 0x797979); put("dirt", 0x866043); put("grass_block", 0x62A03C); put("coarse_dirt", 0x77553B);
        put("podzol", 0x5C4122); put("mycelium", 0x6F6265); put("tuff", 0x6C6D66); put("deepslate", 0x505053); put("granite", 0x9A6B58);
        put("diorite", 0xBDBDBD); put("andesite", 0x888888); put("gravel", 0x837F7E); put("sand", 0xDBCF9F); put("red_sand", 0xBE6620);
        put("sandstone", 0xD8CB9B); put("clay", 0xA0A6B5); put("snow_block", 0xF8FEFE); put("snow", 0xF8FEFE); put("ice", 0x91B8FE);
        put("packed_ice", 0x8BAEEF); put("bedrock", 0x2E2E2E); put("netherrack", 0x6F3535); put("obsidian", 0x14121D); put("water", 0x2F5CC8);
        put("lava", 0xE0620C); put("bricks", 0x96574A); put("stone_bricks", 0x777777); put("oak_planks", 0xAA8450); put("spruce_planks", 0x73542F);
        put("birch_planks", 0xC7B77B); put("jungle_planks", 0xA2714F); put("acacia_planks", 0xA85A32); put("dark_oak_planks", 0x432B14);
        put("oak_log", 0x6E5430); put("spruce_log", 0x3B2812); put("birch_log", 0xD7D3C8); put("oak_leaves", 0x3A7A2B); put("spruce_leaves", 0x2F5A32);
        put("birch_leaves", 0x6A9A45); put("glass", 0xC8E6EE); put("iron_block", 0xD8D8D8); put("gold_block", 0xF5D93B); put("diamond_block", 0x62EDE4);
        put("redstone_ore", 0x8F4A4A); put("diamond_ore", 0x7DA4A3); put("iron_ore", 0x927B6E); put("gold_ore", 0x92874F); put("coal_ore", 0x555555);
        put("lapis_ore", 0x58627F); put("copper_ore", 0x7E6E5E); put("coal_block", 0x14141A); put("oak_slab", 0xAA8450); put("stone_slab", 0x7D7D7D);
        put("smooth_stone", 0x9E9E9E); put("quartz_block", 0xECE6DF); put("terracotta", 0x985E43); put("bookshelf", 0x6E5430); put("glowstone", 0xF2C57C);
        put("sea_lantern", 0xC8DFD5); put("redstone_lamp", 0x6F4D30); put("moss_block", 0x596D2D); put("cobbled_deepslate", 0x4D4D51);
    }

    private static void put(String n, int rgb) {
        KNOWN.put("minecraft:" + n, 0xFF000000 | rgb);
    }

    /** ¿Qué tipo de bloque es? AIR (nada), DECOR (plantas y cosas finas que no se dibujan en 3D), GLASS (se ve a través) o SOLID. */
    static int kind(String name) {
        String n = name.startsWith("minecraft:") ? name.substring(10) : name;
        int colon = n.indexOf(':');
        if (colon >= 0) n = n.substring(colon + 1);
        if (n.equals("air") || n.endsWith("_air") || n.equals("barrier") || n.equals("structure_void") || n.equals("light")) return AIR;
        if (n.equals("water") || n.contains("glass") || n.equals("ice") || n.contains("stained")) return GLASS;
        if (n.contains("grass") && !n.equals("grass_block")) return DECOR;
        for (String k : new String[]{"flower", "tulip", "sapling", "fern", "torch", "button", "lever", "pressure_plate", "sign", "rail", "vine",
                "tripwire", "dandelion", "poppy", "orchid", "allium", "azure", "cornflower", "lily", "mushroom", "carpet", "banner", "head", "skull",
                "bush", "roots", "sprouts", "kelp", "seagrass", "coral_fan", "cobweb", "snow", "lantern", "chain", "rod", "candle", "pot", "door",
                "ladder", "bed", "pane", "bars", "fence", "wall_", "anvil", "lectern"}) {
            if (n.contains(k) && !n.equals("snow_block")) return DECOR;
        }
        return SOLID;
    }

    /** Qué tipo de superficie dibujar: 0 genérica, 1 piedra, 2 tierra y pasto, 3 tablones, 4 troncos, 5 ladrillo, 6 liso (concreto, lana), 7 cristal, 8 metal, 9 hojas, 10 arena, 11 agua. */
    static int material(String name) {
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        boolean mc = name.startsWith("minecraft:");
        if (n.equals("water")) return 11;
        if (n.contains("glass") || n.equals("ice") || n.contains("stained")) return 7;
        if (n.contains("leaves") || n.contains("moss") || n.contains("azalea")) return 9;
        if (n.contains("planks") || (n.contains("oak") || n.contains("birch") || n.contains("spruce") || n.contains("jungle") || n.contains("acacia") || n.contains("cherry") || n.contains("mangrove")) && (n.contains("slab") || n.contains("stairs") || n.contains("fence") || n.contains("door"))) return 3;
        if (n.endsWith("_log") || n.endsWith("_wood") || n.contains("stem") || n.contains("hyphae")) return 4;
        if (n.contains("brick")) return 5;
        if (n.contains("concrete") || n.contains("wool") || n.contains("terracotta") || n.contains("quartz") || n.contains("smooth") || n.contains("plaster")) return 6;
        if (n.contains("iron") || n.contains("gold") || n.contains("copper") || n.contains("metal") || n.contains("steel") || n.contains("netherite") || n.contains("diamond_block") || n.contains("anvil")) return 8;
        if (n.contains("sand") || n.contains("gravel")) return 10;
        if (n.contains("dirt") || n.contains("grass") || n.contains("podzol") || n.contains("mycelium") || n.contains("clay") || n.contains("mud") || n.contains("farmland") || n.contains("path")) return 2;
        if (n.contains("stone") || n.contains("cobble") || n.contains("andesite") || n.contains("granite") || n.contains("diorite") || n.contains("deepslate") || n.contains("tuff") || n.contains("ore")
                || n.contains("bedrock") || n.contains("netherrack") || n.contains("obsidian") || n.contains("basalt") || n.contains("calcite") || n.contains("asphalt")) return 1;
        return 0;
    }

    /** Color ARGB del bloque. */
    static int color(String name) {
        Integer k = KNOWN.get(name.startsWith("minecraft:") || name.contains(":") ? name : "minecraft:" + name);
        if (k != null) return k;
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        // colores de tinte: «red_concrete», «light_blue_wool»…
        String best = null;
        int bestLen = -1;
        for (String[] d : DYES) {
            if ((n.startsWith(d[0] + "_") || n.contains("_" + d[0] + "_")) && d[0].length() > bestLen) {
                best = d[1];
                bestLen = d[0].length();
            }
        }
        if (best != null) {
            int rgb = Integer.parseInt(best, 16);
            if (n.contains("terracotta") && !n.contains("glazed")) rgb = mix(rgb, 0x985E43, 0.45);
            return 0xFF000000 | rgb;
        }
        String[][] hints = {{"planks", "AA8450"}, {"log", "6E5430"}, {"wood", "6E5430"}, {"leaves", "3A7A2B"}, {"stone", "7D7D7D"}, {"brick", "96574A"},
                {"sand", "DBCF9F"}, {"dirt", "866043"}, {"grass", "62A03C"}, {"iron", "D8D8D8"}, {"gold", "F5D93B"}, {"copper", "C0704F"},
                {"diamond", "62EDE4"}, {"emerald", "2FBF5A"}, {"redstone", "A12722"}, {"lapis", "2A4DB0"}, {"quartz", "ECE6DF"}, {"prismarine", "5BA89A"},
                {"concrete", "8E8E86"}, {"wool", "E6EBEB"}, {"slab", "9E9E9E"}, {"stairs", "9E9E9E"}, {"deepslate", "505053"}, {"asphalt", "2A2C30"},
                {"road", "2A2C30"}, {"metal", "B0B4B8"}, {"steel", "B0B4B8"}, {"wall", "BDBDBD"}, {"roof", "8A3B2E"}, {"tile", "A8765A"}};
        for (String[] h : hints) if (n.contains(h[0])) return 0xFF000000 | Integer.parseInt(h[1], 16);
        // un color estable sacado del nombre (para los bloques de mods)
        int h = name.hashCode();
        int r = 90 + ((h >>> 16) & 0x7F), g = 90 + ((h >>> 8) & 0x7F), b = 90 + (h & 0x7F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
