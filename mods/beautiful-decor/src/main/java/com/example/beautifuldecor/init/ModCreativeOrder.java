package com.example.beautifuldecor.init;

/**
 * Orden de los ítems dentro de las dos pestañas del inventario creativo (V1.8.4).
 * <p>
 * Cada lista se lee de arriba hacia abajo: un ítem queda en el lugar de la PRIMERA entrada que le corresponde
 * (nombre exacto, o prefijo si termina en "*"). Los que comparten entrada se ordenan por nombre (así las
 * variantes de madera o de color quedan juntas). Lo que no aparezca en la lista va al final de la pestaña.
 * Esto también aplica cuando se elige una categoría en los botones laterales: siempre se respeta el mismo orden.
 * <p>
 * Para mover algo basta con cambiar de sitio su entrada; no hay que tocar nada más.
 */
public final class ModCreativeOrder {

    /** Pestaña "Beautiful Decor": mesas, sillas y sofás, camas, resto de la casa, luces y por último la cocina. */
    private static final String[] DECOR_ORDER = {
            // ---- 1. Mesas
            "wooden_table", "table_*", "pedestal_table_*", "shelf_table_*", "writing_desk_*",
            // ---- 2. Sillas y sofás
            "wooden_chair", "chair_*", "modern_chair", "office_chair", "bar_stool",
            "sofa_red", "sofa_blue", "sofa_*",
            // ---- 3. Camas y camas para perros
            "bed_blue", "bed_green", "bed_pink", "bed_white", "bed_double_*", "dog_bed_*",
            "nightstand", "nightstand_*",
            // ---- 4. Muebles de guardar e instrumentos
            "bookshelf_decor", "shelf_*", "piano", "piano_stand",
            // ---- 5. Tapetes
            "carpet_checker_*", "carpet_stripes_*", "picnic_rug",
            // ---- 6. Puertas, ventanas y cortinas
            "wooden_door_metal", "metal_door_black", "glass_door", "glass_sliding_door", "dog_door", "garage_door",
            "window_*", "curtain_*", "doorbell_*",
            // ---- 7. Decoración
            "plant_pot", "flower_vase", "decorative_vase", "picnic_basket", "toolbox_open", "painting_*",
            "mini_*",                                       // V4.1.2: carros en miniatura (antes en tecnología)
            // ---- 8. Baño
            "bathtub", "shower", "ceiling_shower", "toilet", "toilet_paper", "sink",
            "washbasin_pedestal_*", "washbasin_vanity_*", "washbasin_wall_*",
            // ---- 9. Construcción y exterior
            "oak_parquet", "refined_oak_planks", "stairs_*", "concrete_ramp_*", "metal_mesh", "fence_*",
            "pipe_*", "conveyor_belt", "barrel_*", "sign_*", "traffic_light", "diving_board",
            "slide_*", "empanada_cart",                     // V4.1.2: toboganes y carrito de empanadas (antes en tecnología)
            // ---- 10. Luces: primero las normales (techo, bombillos, lámparas) y luego la marca Bugerati y los neones
            "ceiling_light", "ceiling_light_round", "ceiling_light_square", "ceiling_bulb", "round_bulb",
            "circular_bulb", "office_led_panel", "wall_light", "floor_lamp", "night_lamp", "desk_lamp",
            "desk_lamp_flexo", "street_lamp",
            "bugerati_floor_lamp", "bugerati_arc_lamp", "bugerati_table_lamp", "bugerati_orb_lamp",
            "bugerati_pendant_lamp", "bugerati_wall_sconce",
            "bugerati_chandelier_gold", "bugerati_chandelier_silver", "bugerati_chandelier_black",
            "neon_bar_*", "bugerati_neon_*", "wall_switch",
            // ---- 11. Cocina (siempre al final de la pestaña)
            "kitchen_counter", "kitchen_drawers_*", "kitchen_sink_*",
            "stove", "stove_cabinet_*", "stove_oven_*", "kitchen_oven", "microwave", "microwave_*",
            "dishwasher", "washing_machine", "coffee_maker", "orange_juicer",
            "freezer_chest", "cold_room", "crate_empty", "crate_fries", "crate_ribs", "crate_balls",
            "dish_rack_*", "frying_pan_*", "pan_lid_*",
            "cutlery_set", "cutlery_pack", "cutlery_fork", "cutlery_knife", "cutlery_spoon",
            "glass_red", "glass_blue", "glass_green", "glass_yellow", "plate_*",
    };

    /**
     * Pestaña "Beautiful Tech": primero todo lo de la marca HS (con megafonía y anuncios), luego los ventiladores,
     * los extras (dron, carro RC, ascensores) y al final las pantallas y consolas.
     */
    private static final String[] TECH_ORDER = {
            // ---- 1. Marca HS: computadores e impresoras (V6.11.0)
            "hs_laptop", "hs_desktop", "hs_desktop_*", "printer",
            // ---- 2. Sistemas de audio: la radio y su bocina, el micrófono y su bocina
            "radio_table", "radio_speaker", "microphone", "pa_speaker",
            // ---- 3. Pantallas de anuncios y televisores de anuncios (de menor a mayor), con el micrófono de HS News
            "ad_screen_small", "ad_screen_medium", "ad_screen_large",
            "ad_tv", "ad_tv_3x2", "ad_tv_4x2", "ad_tv_6x4", "hsnews_mic",
            // ---- 4. Carro RC y dron, cada uno con su control
            "rc_car", "rc_car_remote", "drone", "drone_remote",
            // ---- 5. Televisores normales
            "television", "tv_*",
            // ---- 6. Lo demás: consolas, seguridad, ventiladores y ascensores
            "wii_console", "nintendo_switch", "xbox_360", "xbox_one", "xbox_series_s", "kinect_sensor",
            "security_camera_black", "security_camera_white", "security_monitor", "security_screen",
            "fan_small", "fan_medium", "fan_large", "pedestal_fan_*", "ceiling_fan_*",
            "elevator_controller", "elevator_display", "elevator_door",
    };

    private ModCreativeOrder() {
    }

    private static int rank(String[] order, String path) {
        for (int i = 0; i < order.length; i++) {
            String entry = order[i];
            boolean match = entry.endsWith("*")
                    ? path.startsWith(entry.substring(0, entry.length() - 1))
                    : path.equals(entry);
            if (match) {
                return i;
            }
        }
        return order.length;
    }

    private static int compare(String[] order, String a, String b) {
        int byRank = Integer.compare(rank(order, a), rank(order, b));
        return byRank != 0 ? byRank : a.compareTo(b);
    }

    /** Orden de dos ítems de la pestaña de decoración (negativo = {@code a} va antes). */
    public static int compareDecor(String a, String b) {
        return compare(DECOR_ORDER, a, b);
    }

    /** Orden de dos ítems de la pestaña de tecnología (negativo = {@code a} va antes). */
    public static int compareTech(String a, String b) {
        return compare(TECH_ORDER, a, b);
    }
}
