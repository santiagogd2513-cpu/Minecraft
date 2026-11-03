package com.amatista.arsenal.registry;

import com.amatista.arsenal.ArsenalOrbital;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Pestaña propia del mod en el inventario creativo. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArsenalOrbital.MODID);

    public static final RegistryObject<CreativeModeTab> ARSENAL = TABS.register("arsenal", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.arsenalorbital.arsenal"))
            .icon(() -> ModItems.ORBITAL_DESIGNATOR.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // aquí se irán sumando las nuevas armas
                output.accept(ModItems.ORBITAL_DESIGNATOR.get());
            })
            .build());

    private ModTabs() {}
}
