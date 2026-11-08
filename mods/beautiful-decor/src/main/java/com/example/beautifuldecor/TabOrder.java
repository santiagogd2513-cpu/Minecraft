package com.example.beautifuldecor;

import com.example.beautifuldecor.init.ModCreativeTabs;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coloca las tres pestañas del mod al final de la lista (última página del inventario creativo).
 */
public final class TabOrder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TabOrder() {
    }

    @SuppressWarnings("unchecked")
    public static void moveToEnd() {
        try {
            CreativeModeTab tab1 = ModCreativeTabs.BEAUTIFUL_DECOR.get();
            CreativeModeTab tab2 = ModCreativeTabs.BEAUTIFUL_DECOR_TECH.get();
            CreativeModeTab tab3 = ModCreativeTabs.BEAUTIFUL_DECOR_VEHICLES.get();
            for (Field field : CreativeModeTabRegistry.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (!(value instanceof List<?> list) || !list.contains(tab1)) {
                    continue;
                }
                List<CreativeModeTab> reordered = new ArrayList<>((List<CreativeModeTab>) list);
                reordered.remove(tab1);
                reordered.remove(tab2);
                reordered.remove(tab3);
                reordered.add(tab1);
                reordered.add(tab2);
                reordered.add(tab3);
                if (!Modifier.isFinal(field.getModifiers())) {
                    field.set(null, Collections.unmodifiableList(reordered));
                } else {
                    List<CreativeModeTab> mutable = (List<CreativeModeTab>) list;
                    mutable.clear();
                    mutable.addAll(reordered);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("No se pudieron mover las pestañas de Beautiful Decor al final", t);
        }
    }
}
