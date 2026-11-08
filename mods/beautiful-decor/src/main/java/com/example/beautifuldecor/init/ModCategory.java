package com.example.beautifuldecor.init;

import java.util.Set;
import java.util.function.Predicate;

/** Categorías internas de la pestaña única del mod (botones laterales en el inventario creativo). */
public enum ModCategory {
    ALL("all", p -> true),
    FURNITURE("furniture", p -> Set.of("wooden_table", "sofa_red", "sofa_blue", "nightstand", "bar_stool",
            "kitchen_counter", "bookshelf_decor", "piano", "piano_stand").contains(p) || p.endsWith("chair")
            || p.startsWith("chair_") || p.startsWith("sofa_") || p.startsWith("nightstand_")
            || p.startsWith("shelf_")
            || p.startsWith("table_") || p.startsWith("pedestal_table_") || p.startsWith("writing_desk_")),
    BEDS("beds", p -> p.startsWith("bed_") || p.startsWith("dog_bed_")),
    ELECTRONICS("electronics", p -> Set.of("television", "wii_console", "nintendo_switch",
            "xbox_360", "kinect_sensor",
            "hs_laptop", "hs_desktop", "printer", "microphone", "pa_speaker", "drone", "drone_remote", "rc_car", "rc_car_remote").contains(p) || p.startsWith("xbox_")
            || p.startsWith("tv_") || p.startsWith("fan_") || p.startsWith("ceiling_fan_")),
    KITCHEN_BATH("kitchen_bath", p -> Set.of("sink", "shower", "bathtub", "toilet",
            "ceiling_shower", "toilet_paper", "dishwasher", "washing_machine", "microwave", "stove", "glass_red",
            "glass_blue", "glass_green", "glass_yellow").contains(p)
            || p.startsWith("dish_rack_") || p.startsWith("frying_pan_") || p.startsWith("pan_lid_")
            || p.equals("coffee_maker") || p.equals("orange_juicer")
            || p.startsWith("stove_") || p.startsWith("microwave_") || p.equals("kitchen_oven")
            || p.startsWith("kitchen_sink_") || p.startsWith("kitchen_drawers_") || p.startsWith("washbasin_")
            || p.startsWith("cutlery_")
            || p.equals("freezer_chest") || p.equals("cold_room") || p.startsWith("crate_")),
    FOOD("food", p -> p.startsWith("plate_")),
    LIGHTING("lighting", p -> Set.of("ceiling_light", "ceiling_light_round", "ceiling_light_square",
            "ceiling_bulb", "office_led_panel", "wall_switch", "wall_light", "floor_lamp", "round_bulb",
            "night_lamp", "desk_lamp", "desk_lamp_flexo", "circular_bulb").contains(p)
            || p.startsWith("neon_bar_") || p.startsWith("bugerati_")),
    OPENINGS("openings", p -> Set.of("glass_door", "glass_sliding_door", "wooden_door_metal",
            "metal_door_black", "dog_door", "garage_door").contains(p) || p.startsWith("window_")
            || p.startsWith("curtain_") || p.startsWith("doorbell_")),
    OUTDOOR("outdoor", p -> Set.of("traffic_light", "street_lamp", "oak_parquet",
            "refined_oak_planks", "empanada_cart", "diving_board", "metal_mesh").contains(p)
            || p.startsWith("pipe_") || p.equals("conveyor_belt") || p.startsWith("barrel_") || p.startsWith("concrete_ramp") || p.startsWith("fence_") || p.startsWith("sign_")),
    CARS("cars", p -> p.startsWith("mini_")),
    SLIDES("slides", p -> p.startsWith("slide_")),
    STAIRS("stairs", p -> p.startsWith("stairs_")),
    DECOR("decor", p -> false); // se calcula como "todo lo demás"

    public final String id;
    private final Predicate<String> filter;

    ModCategory(String id, Predicate<String> filter) {
        this.id = id;
        this.filter = filter;
    }

    /** Categoría actualmente elegida en los botones laterales. */
    public static ModCategory selected = ALL;

    public boolean matches(String path) {
        if (this == ALL) return true;
        if (this == DECOR) {
            for (ModCategory c : values()) {
                if (c != ALL && c != DECOR && c.filter.test(path)) return false;
            }
            return true;
        }
        return filter.test(path);
    }

    public String translationKey() {
        return "category.beautifuldecor." + id;
    }
}
