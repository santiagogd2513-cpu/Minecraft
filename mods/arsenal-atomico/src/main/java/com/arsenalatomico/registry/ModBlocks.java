package com.arsenalatomico.registry;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.block.LaunchConsoleBlock;
import com.arsenalatomico.block.SiloPanelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArsenalAtomico.MODID);

    public static final RegistryObject<Block> LAUNCH_CONSOLE = BLOCKS.register("launch_console",
            () -> new LaunchConsoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 7)));

    public static final RegistryObject<Block> SILO_PANEL = BLOCKS.register("silo_panel",
            () -> new SiloPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 9)));

    private ModBlocks() {}
}
