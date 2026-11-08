package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.init.ModCategory;
import com.example.beautifuldecor.init.ModCreativeTabs;
import com.example.beautifuldecor.init.ModItems;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Botones de categoría a la izquierda del inventario creativo, visibles solo en la pestaña del mod. */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CreativeSidebar {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE = 20;
    private static final int GAP = 2;
    private static final int ROWS = 6;

    private static ItemStack icon(ModCategory c) {
        return switch (c) {
            case ALL -> new ItemStack(Items.COMPASS);
            case FURNITURE -> new ItemStack(ModItems.WOODEN_TABLE.get());
            case BEDS -> new ItemStack(ModItems.BED_BLUE.get());
            case ELECTRONICS -> new ItemStack(ModItems.TELEVISION.get());
            case KITCHEN_BATH -> new ItemStack(ModItems.SINK.get());
            case FOOD -> new ItemStack(ModItems.PLATE_BURGER.get());
            case LIGHTING -> new ItemStack(ModItems.CEILING_LIGHT.get());
            case OPENINGS -> new ItemStack(ModItems.GLASS_DOOR.get());
            case OUTDOOR -> new ItemStack(ModItems.STREET_LAMP.get());
            case CARS -> new ItemStack(ModItems.MINI_FERRARI.get());
            case SLIDES -> new ItemStack(ModItems.SLIDES.get("red").get());
            case STAIRS -> new ItemStack(ModItems.EXTRA_ITEMS.get("stairs_oak").get());
            case DECOR -> new ItemStack(ModItems.FLOWER_VASE.get());
        };
    }

    // El juego normal (no el entorno de desarrollo) usa nombres internos distintos a "selectedTab" y "selectTab", así que buscarlos por
    // nombre fallaba en cada cuadro y llenaba latest.log de errores (y la barra de categorías nunca aparecía). Se buscan por su forma, una sola vez.
    private static volatile Field selectedField;
    private static volatile Method selectMethod;
    private static volatile boolean fieldTried;
    private static volatile boolean methodTried;

    private static synchronized Field selectedField() {
        if (!fieldTried) {
            fieldTried = true;
            selectedField = ReflectFind.staticField(CreativeModeInventoryScreen.class, CreativeModeTab.class, "selectedTab");
            if (selectedField == null) {
                LOGGER.warn("[BeautifulDecor] no se encontró la pestaña creativa seleccionada; no habrá barra de categorías");
            }
        }
        return selectedField;
    }

    private static synchronized Method selectMethod() {
        if (!methodTried) {
            methodTried = true;
            selectMethod = ReflectFind.voidMethod(CreativeModeInventoryScreen.class, CreativeModeTab.class, "selectTab");
            if (selectMethod == null) {
                LOGGER.warn("[BeautifulDecor] no se encontró el cambio de pestaña creativa; las categorías no recargarán la lista");
            }
        }
        return selectMethod;
    }

    /** La pestaña creativa seleccionada ahora, o null si no se pudo leer (sin errores en el registro: se avisa una sola vez al buscarla). */
    private static Object selectedTab() {
        Field f = fieldTried ? selectedField : selectedField();
        if (f == null) {
            return null;
        }
        try {
            return f.get(null);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isOurTab() {
        // Barra lateral de categorías solo en la primera pestaña (decoración)
        Object tab = selectedTab();
        return tab != null && tab == ModCreativeTabs.BEAUTIFUL_DECOR.get();
    }

    /** True si la pestaña seleccionada es cualquiera de las tres del mod. */
    private static boolean isAnyModTab() {
        Object tab = selectedTab();
        return tab != null && (tab == ModCreativeTabs.BEAUTIFUL_DECOR.get()
                || tab == ModCreativeTabs.BEAUTIFUL_DECOR_TECH.get()
                || tab == ModCreativeTabs.BEAUTIFUL_DECOR_VEHICLES.get());
    }

    private static int buttonX(CreativeModeInventoryScreen screen, int index) {
        int column = index / ROWS;
        return screen.getGuiLeft() - (SIZE + GAP) * (column + 1) - 4;
    }

    private static int buttonY(CreativeModeInventoryScreen screen, int index) {
        return screen.getGuiTop() + 4 + (index % ROWS) * (SIZE + GAP);
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Pre event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen) {
            com.example.beautifuldecor.TabOrder.moveToEnd();
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || !isOurTab()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        ModCategory hovered = null;
        ModCategory[] all = ModCategory.values();
        for (int i = 0; i < all.length; i++) {
            int x = buttonX(screen, i), y = buttonY(screen, i);
            boolean hover = event.getMouseX() >= x && event.getMouseX() < x + SIZE
                    && event.getMouseY() >= y && event.getMouseY() < y + SIZE;
            boolean selected = all[i] == ModCategory.selected;
            g.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, selected ? 0xFFC77AFF : 0xFF000000);
            g.fill(x, y, x + SIZE, y + SIZE, hover ? 0xFF70448D : 0xFF34263F);
            g.renderItem(icon(all[i]), x + 2, y + 2);
            if (hover) hovered = all[i];
        }
        if (hovered != null) {
            g.renderTooltip(mc.font, Component.translatable(hovered.translationKey()),
                    event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof CreativeModeInventoryScreen screen)
                || !isOurTab()) {
            return;
        }
        ModCategory[] all = ModCategory.values();
        for (int i = 0; i < all.length; i++) {
            int x = buttonX(screen, i), y = buttonY(screen, i);
            if (event.getMouseX() >= x && event.getMouseX() < x + SIZE
                    && event.getMouseY() >= y && event.getMouseY() < y + SIZE) {
                event.setCanceled(true);
                if (all[i] != ModCategory.selected) {
                    ModCategory.selected = all[i];
                    refresh(screen);
                }
                return;
            }
        }
    }

    /** Reconstruye el contenido de la pestaña con la categoría elegida y vuelve a cargarla en pantalla. */
    private static void refresh(CreativeModeInventoryScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        try {
            CreativeModeTab tab = ModCreativeTabs.BEAUTIFUL_DECOR.get();
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),
                    mc.player.canUseGameMasterBlocks(), mc.level.registryAccess()));
            Method select = selectMethod();
            if (select != null) {
                select.invoke(screen, tab);
            }
        } catch (Exception e) {
            LOGGER.error("No se pudo actualizar la categoría del inventario creativo", e);
        }
    }
}
