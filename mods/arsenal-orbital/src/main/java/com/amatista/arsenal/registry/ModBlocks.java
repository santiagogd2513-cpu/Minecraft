package com.amatista.arsenal.registry;

import com.amatista.arsenal.ArsenalOrbital;
import com.amatista.arsenal.block.PedestalBlock;
import com.amatista.arsenal.block.PedestalBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArsenalOrbital.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArsenalOrbital.MODID);

    /** Pedestal de vitrina: guarda un artefacto que flota encima (en la bóveda, con luz arcoíris). */
    public static final RegistryObject<Block> ARTIFACT_PEDESTAL = BLOCKS.register("artifact_pedestal",
            () -> new PedestalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6f, 1200f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(s -> 12)
                    .noOcclusion()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PedestalBlockEntity>> PEDESTAL_BE = BLOCK_ENTITIES.register("artifact_pedestal",
            () -> BlockEntityType.Builder.of(PedestalBlockEntity::new, ARTIFACT_PEDESTAL.get()).build(null));

    private ModBlocks() {}
}
