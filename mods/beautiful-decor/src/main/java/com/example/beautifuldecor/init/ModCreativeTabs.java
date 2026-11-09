package com.example.beautifuldecor.init;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventario creativo propio del mod: dos pestañas nativas de Minecraft.
 * <ul>
 *   <li>{@link #BEAUTIFUL_DECOR} — muebles, camas, cocina/baño, luces, puertas, exterior, decoración
 *       (con barra lateral de categorías vía {@link com.example.beautifuldecor.client.CreativeSidebar})</li>
 *   <li>{@link #BEAUTIFUL_DECOR_TECH} — computadores y consolas HS, pantallas, megafonía y anuncios, ventiladores, ascensores, dron y carro RC (V4.1.2: los autos en miniatura, toboganes y platos pasaron a decoración)</li>
 *   <li>{@link #BEAUTIFUL_DECOR_VEHICLES} — (V2.0.1) vehículos en los que uno se monta y conduce (el dron no cuenta)</li>
 * </ul>
 * Construido desde cero con barra de búsqueda y orden de registro estable (sin shuffle).
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeautifulDecor.MOD_ID);

    /** Primera pestaña: decoración y mobiliario. */
    public static final RegistryObject<CreativeModeTab> BEAUTIFUL_DECOR = CREATIVE_MODE_TABS.register(
            "beautifuldecor",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.beautifuldecor"))
                    .icon(() -> new ItemStack(ModItems.TAB_ICON.get()))
                    .withSearchBar()
                    .withTabsAfter(CreativeModeTabs.OP_BLOCKS)
                    .displayItems((parameters, output) -> {
                        ModCategory category = ModCategory.selected;
                        List<RegistryObject<Item>> items = new ArrayList<>();
                        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
                            String path = entry.getId().getPath();
                            if (isDecorTabItem(path) && category.matches(path)) {
                                items.add(entry);
                            }
                        }
                        // V1.8.4: mesas, sillas y sofás, camas, resto de la casa, luces y al final la cocina
                        items.sort((a, b) -> ModCreativeOrder.compareDecor(a.getId().getPath(), b.getId().getPath()));
                        for (RegistryObject<Item> entry : items) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    /** Segunda pestaña: solo tecnología. */
    public static final RegistryObject<CreativeModeTab> BEAUTIFUL_DECOR_TECH = CREATIVE_MODE_TABS.register(
            "beautifuldecor_tech",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.beautifuldecor_tech"))
                    .icon(() -> iconOrFallback(ModItems.TELEVISION, ModItems.TAB_ICON))
                    .withSearchBar()
                    .withTabsAfter(BEAUTIFUL_DECOR.getKey())
                    .displayItems((parameters, output) -> {
                        List<RegistryObject<Item>> items = new ArrayList<>();
                        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
                            if (isTechTabItem(entry.getId().getPath())) {
                                items.add(entry);
                            }
                        }
                        // V1.8.4: marca HS, carros en miniatura, ventiladores, extras y al final pantallas y consolas
                        items.sort((a, b) -> ModCreativeOrder.compareTech(a.getId().getPath(), b.getId().getPath()));
                        for (RegistryObject<Item> entry : items) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    /**
     * Tercera pestaña (V2.0.1): vehículos de transporte que se conducen montado en ellos. El dron y su control remoto
     * NO van aquí (se pilotan a distancia). Los próximos vehículos se agregan en {@link #VEHICLE_IDS}.
     */
    public static final RegistryObject<CreativeModeTab> BEAUTIFUL_DECOR_VEHICLES = CREATIVE_MODE_TABS.register(
            "beautifuldecor_vehicles",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.beautifuldecor_vehicles"))
                    .icon(() -> iconOrFallback(ModItems.MOTORCYCLE, ModItems.TAB_ICON))
                    .withSearchBar()
                    .withTabsAfter(BEAUTIFUL_DECOR_TECH.getKey())
                    .displayItems((parameters, output) -> {
                        // el orden de VEHICLE_IDS es el orden en la pestaña
                        for (String id : ModCreativeTabs.VEHICLE_IDS) {
                            for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
                                if (entry.getId().getPath().equals(id)) {
                                    output.accept(entry.get());
                                }
                            }
                        }
                    })
                    .build());

    /** Ítems de la pestaña de vehículos, en orden. Agrega aquí cada vehículo conducible nuevo. */
    public static final List<String> VEHICLE_IDS = List.of("bicycle", "motorcycle", "ford_f150", "delivery_truck", "helicopter", "bugotti_chillon", "airliner");

    private ModCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }

    /** Ítems de la primera pestaña (decoración / casa). */
    public static boolean isDecorTabItem(String path) {
        if (path.equals("tab_icon")) {
            return false;
        }
        return !isTechTabItem(path) && !isVehicleTabItem(path);
    }

    /** Ítems de la tercera pestaña (vehículos conducibles). */
    public static boolean isVehicleTabItem(String path) {
        return VEHICLE_IDS.contains(path);
    }

    /** Ítems de la segunda pestaña (tecnología / diversión). */
    public static boolean isTechTabItem(String path) {
        if (path.equals("tab_icon")) {
            return false;
        }
        return path.equals("television")
                || path.equals("wii_console")
                || path.equals("nintendo_switch")
                || path.equals("xbox_360")
                || path.equals("xbox_one")
                || path.equals("kinect_sensor")
                || path.equals("hs_laptop")
                || path.equals("hs_desktop")
                || path.startsWith("hs_desktop_")
                || path.equals("printer")
                || path.equals("microphone")                // V4.1.2: megafonía y pantallas de anuncios son tecnología
                || path.equals("pa_speaker")
                || path.equals("radio_table")               // V4.2.0
                || path.equals("radio_speaker")
                || path.startsWith("ad_screen_")
                || path.equals("drone")
                || path.equals("drone_remote")
                || path.equals("rc_car")
                || path.equals("rc_car_remote")
                || path.startsWith("xbox_")
                || path.startsWith("tv_")
                || path.startsWith("fan_")
                || path.startsWith("pedestal_fan_")
                || path.startsWith("ceiling_fan_")
                || path.startsWith("elevator_");
        // V4.1.2: los carros en miniatura (mini_*), toboganes (slide_*), platos (plate_*) y el carrito de empanadas son decoración
        // y pasaron a la pestaña de decoración.
    }

    private static ItemStack iconOrFallback(RegistryObject<Item> preferred, RegistryObject<Item> fallback) {
        try {
            return new ItemStack(preferred.get());
        } catch (Exception e) {
            return new ItemStack(fallback.get());
        }
    }
}
