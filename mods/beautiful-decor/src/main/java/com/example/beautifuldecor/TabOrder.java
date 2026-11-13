package com.example.beautifuldecor;

import com.example.beautifuldecor.init.ModCreativeTabs;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.slf4j.Logger;

import java.util.List;

/**
 * Coloca las tres pestañas del mod al final de la lista (última página del inventario creativo).
 */
public final class TabOrder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TabOrder() {
    }

    public static void moveToEnd() {
        try {
            List<CreativeModeTab> mine = List.of(ModCreativeTabs.BEAUTIFUL_DECOR.get(), ModCreativeTabs.BEAUTIFUL_DECOR_TECH.get(),
                    ModCreativeTabs.BEAUTIFUL_DECOR_VEHICLES.get(), ModCreativeTabs.BEAUTIFUL_DECOR_PARTS.get());
            TabReorder.Result result = TabReorder.moveToEnd(CreativeModeTabRegistry.class, mine);
            if (result.found() == 0) {
                LOGGER.info("Beautiful Decor: no se encontró la lista ordenada de pestañas (Forge la arma más tarde)");
            } else if (!result.failed().isEmpty()) {
                LOGGER.warn("Beautiful Decor: las pestañas no quedaron al final en {} (solo cambia su lugar en el inventario creativo)", result.failed());
            }
        } catch (Throwable t) {
            LOGGER.warn("Beautiful Decor: no se pudieron mover las pestañas al final: {}", t.toString());
        }
    }
}
