package com.arsenalatomico.registry;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.item.TargetMarkerItem;
import com.arsenalatomico.item.TruckItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArsenalAtomico.MODID);

    public static final RegistryObject<Item> TARGET_MARKER = ITEMS.register("target_marker",
            () -> new TargetMarkerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> MILITARY_TRUCK = ITEMS.register("military_truck",
            () -> new TruckItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {}
}
