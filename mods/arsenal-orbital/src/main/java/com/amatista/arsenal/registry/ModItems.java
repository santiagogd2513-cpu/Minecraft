package com.amatista.arsenal.registry;

import com.amatista.arsenal.ArsenalOrbital;
import com.amatista.arsenal.item.OrbitalDesignatorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArsenalOrbital.MODID);

    public static final RegistryObject<Item> ORBITAL_DESIGNATOR = ITEMS.register("orbital_designator",
            () -> new OrbitalDesignatorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> ARTIFACT_PEDESTAL = ITEMS.register("artifact_pedestal",
            () -> new BlockItem(ModBlocks.ARTIFACT_PEDESTAL.get(), new Item.Properties().rarity(Rarity.RARE)));

    private ModItems() {}
}
