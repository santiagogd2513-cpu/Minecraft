package com.example.beautifuldecor.init;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.entity.ElevatorControllerBlockEntity;
import com.example.beautifuldecor.block.entity.MicrophoneBlockEntity;
import com.example.beautifuldecor.block.entity.PaSpeakerBlockEntity;
import com.example.beautifuldecor.block.entity.ElevatorDisplayBlockEntity;
import com.example.beautifuldecor.block.entity.ElevatorDoorBlockEntity;
import com.example.beautifuldecor.block.entity.GarageDoorBlockEntity;
import com.example.beautifuldecor.block.entity.KitchenDrawersBlockEntity;
import com.example.beautifuldecor.block.entity.TrafficLightBlockEntity;
import com.example.beautifuldecor.block.entity.PrinterBlockEntity;
import com.example.beautifuldecor.block.entity.TelevisionBlockEntity;
import com.example.beautifuldecor.block.entity.DogBedBlockEntity;
import com.example.beautifuldecor.block.entity.LightSwitchBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BeautifulDecor.MOD_ID);

    public static final RegistryObject<BlockEntityType<TrafficLightBlockEntity>> TRAFFIC_LIGHT =
            BLOCK_ENTITIES.register("traffic_light",
                    () -> BlockEntityType.Builder.of(TrafficLightBlockEntity::new, ModBlocks.TRAFFIC_LIGHT.get()).build(null));

    /** V4.0.0: megafonía. */
    public static final RegistryObject<BlockEntityType<MicrophoneBlockEntity>> MICROPHONE =
            BLOCK_ENTITIES.register("microphone",
                    () -> BlockEntityType.Builder.of(MicrophoneBlockEntity::new, ModBlocks.MICROPHONE.get()).build(null));

    public static final RegistryObject<BlockEntityType<PaSpeakerBlockEntity>> PA_SPEAKER =
            BLOCK_ENTITIES.register("pa_speaker",
                    () -> BlockEntityType.Builder.of(PaSpeakerBlockEntity::new, ModBlocks.PA_SPEAKER.get()).build(null));

    public static final RegistryObject<BlockEntityType<PrinterBlockEntity>> PRINTER =
            BLOCK_ENTITIES.register("printer",
                    () -> BlockEntityType.Builder.of(PrinterBlockEntity::new, ModBlocks.PRINTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<TelevisionBlockEntity>> TELEVISION =
            BLOCK_ENTITIES.register("television",
                    () -> BlockEntityType.Builder.of(TelevisionBlockEntity::new, televisionBlocks()).build(null));

    public static final RegistryObject<BlockEntityType<DogBedBlockEntity>> DOG_BED =
            BLOCK_ENTITIES.register("dog_bed",
                    () -> BlockEntityType.Builder.of(DogBedBlockEntity::new,
                            ModBlocks.DOG_BED_PINK.get(),
                            ModBlocks.DOG_BED_BLUE.get(),
                            ModBlocks.DOG_BED_GRAY.get()).build(null));

    public static final RegistryObject<BlockEntityType<LightSwitchBlockEntity>> LIGHT_SWITCH =
            BLOCK_ENTITIES.register("light_switch",
                    () -> BlockEntityType.Builder.of(LightSwitchBlockEntity::new,
                            ModBlocks.WALL_SWITCH.get()).build(null));

    /** V2.1.0: los cajones de cocina de las 11 maderas comparten el mismo block entity (27 espacios). */
    public static final RegistryObject<BlockEntityType<KitchenDrawersBlockEntity>> KITCHEN_DRAWERS =
            BLOCK_ENTITIES.register("kitchen_drawers",
                    () -> BlockEntityType.Builder.of(KitchenDrawersBlockEntity::new, kitchenDrawersBlocks()).build(null));

    // ===== V2.2.0: ascensores =====
    public static final RegistryObject<BlockEntityType<ElevatorControllerBlockEntity>> ELEVATOR_CONTROLLER =
            BLOCK_ENTITIES.register("elevator_controller",
                    () -> BlockEntityType.Builder.of(ElevatorControllerBlockEntity::new,
                            ModBlocks.ELEVATOR_CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<ElevatorDisplayBlockEntity>> ELEVATOR_DISPLAY =
            BLOCK_ENTITIES.register("elevator_display",
                    () -> BlockEntityType.Builder.of(ElevatorDisplayBlockEntity::new,
                            ModBlocks.ELEVATOR_DISPLAY.get()).build(null));

    public static final RegistryObject<BlockEntityType<ElevatorDoorBlockEntity>> ELEVATOR_DOOR =
            BLOCK_ENTITIES.register("elevator_door",
                    () -> BlockEntityType.Builder.of(ElevatorDoorBlockEntity::new,
                            ModBlocks.ELEVATOR_DOOR.get()).build(null));

    /** V2.3.0: colores personalizables de la puerta de garaje (solo la pieza de la esquina). */
    public static final RegistryObject<BlockEntityType<GarageDoorBlockEntity>> GARAGE_DOOR =
            BLOCK_ENTITIES.register("garage_door",
                    () -> BlockEntityType.Builder.of(GarageDoorBlockEntity::new,
                            ModBlocks.GARAGE_DOOR.get()).build(null));

    private static Block[] kitchenDrawersBlocks() {
        return ModBlocks.KITCHEN_DRAWERS.values().stream().map(RegistryObject::get).toArray(Block[]::new);
    }

    /** Todos los televisores (el original + los nuevos) comparten el mismo block entity. */
    private static Block[] televisionBlocks() {
        java.util.List<Block> list = new java.util.ArrayList<>();
        list.add(ModBlocks.TELEVISION.get());
        ModBlocks.TVS.values().forEach(tv -> list.add(tv.get()));
        return list.toArray(new Block[0]);
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
