package com.arsenalatomico.registry;

import com.arsenalatomico.ArsenalAtomico;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Pestaña propia en el inventario creativo. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArsenalAtomico.MODID);

    public static final RegistryObject<CreativeModeTab> ATOMICO = TABS.register("atomico", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arsenalatomico.atomico"))
            .icon(() -> ModItems.TARGET_MARKER.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(ModItems.TARGET_MARKER.get());
                out.accept(ModItems.MILITARY_TRUCK.get());
            })
            .build());

    private ModTabs() {}
}
