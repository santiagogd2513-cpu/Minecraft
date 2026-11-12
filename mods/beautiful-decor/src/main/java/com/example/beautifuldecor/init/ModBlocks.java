package com.example.beautifuldecor.init;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.AdScreenBlock;
import com.example.beautifuldecor.block.FreightRailBlock;
import com.example.beautifuldecor.block.MetroRailBlock;
import com.example.beautifuldecor.block.PassengerRailBlock;
import com.example.beautifuldecor.block.RadioSpeakerBlock;
import com.example.beautifuldecor.block.SecurityCameraBlock;
import com.example.beautifuldecor.block.SecurityMonitorBlock;
import com.example.beautifuldecor.block.RadioTableBlock;
import com.example.beautifuldecor.block.ApplianceDoorBlock;
import com.example.beautifuldecor.block.BigTelevisionBlock;
import com.example.beautifuldecor.block.BugeratiLampBlock;
import com.example.beautifuldecor.block.ColdRoomBlock;
import com.example.beautifuldecor.block.ElevatorControllerBlock;
import com.example.beautifuldecor.block.MicrophoneBlock;
import com.example.beautifuldecor.block.PaSpeakerBlock;
import com.example.beautifuldecor.block.ElevatorDisplayBlock;
import com.example.beautifuldecor.block.ElevatorDoorBlock;
import com.example.beautifuldecor.block.GarageDoorBlock;
import com.example.beautifuldecor.block.KitchenDrawersBlock;
import com.example.beautifuldecor.block.NeonPanelBlock;
import com.example.beautifuldecor.block.CeilingFanBlock;
import com.example.beautifuldecor.block.DoorbellBlock;
import com.example.beautifuldecor.block.DoubleBedBlock;
import com.example.beautifuldecor.block.PianoBlock;
import com.example.beautifuldecor.block.PianoStandBlock;
import com.example.beautifuldecor.block.ShapedFacingBlock;
import com.example.beautifuldecor.block.FanBlock;
import com.example.beautifuldecor.block.ConveyorBeltBlock;
import com.example.beautifuldecor.block.PipeBlock;
import com.example.beautifuldecor.block.LightSwitchBlock;
import com.example.beautifuldecor.block.DivingBoardBlock;
import com.example.beautifuldecor.block.MetalMeshBlock;
import com.example.beautifuldecor.block.NightstandBlock;
import com.example.beautifuldecor.block.ModBlock;
import com.example.beautifuldecor.block.RotatingDecorativeBlock;
import com.example.beautifuldecor.block.TelevisionBlock;
import com.example.beautifuldecor.block.ToggleBlock;
import com.example.beautifuldecor.block.TableBlock;
import com.example.beautifuldecor.block.TrafficLightBlock;
import com.example.beautifuldecor.block.WritingDeskBlock;
import com.example.beautifuldecor.block.WiiConsoleBlock;
import com.example.beautifuldecor.block.HorizontalConsoleBlock;
import com.example.beautifuldecor.block.ConcreteRampBlock;
import com.example.beautifuldecor.block.CeilingShowerBlock;
import com.example.beautifuldecor.block.PrinterBlock;
import com.example.beautifuldecor.block.DogBedBlock;
import com.example.beautifuldecor.block.DroneBlock;
import com.example.beautifuldecor.block.RcCarBlock;
import com.example.beautifuldecor.block.HingedDoorBlock;
import com.example.beautifuldecor.block.HsDesktopBlock;
import com.example.beautifuldecor.block.LaptopBlock;
import com.example.beautifuldecor.block.PedestalFanBlock;
import com.example.beautifuldecor.block.PictureBlock;
import com.example.beautifuldecor.block.NeonLightBlock;
import com.example.beautifuldecor.block.SlideRampBlock;
import com.example.beautifuldecor.block.SofaBlock;
import com.example.beautifuldecor.block.ToiletPaperBlock;
import com.example.beautifuldecor.block.XboxSeriesSBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BeautifulDecor.MOD_ID);

    // ===== MUEBLES =====
    public static final RegistryObject<Block> WOODEN_TABLE = BLOCKS.register("wooden_table",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> WOODEN_CHAIR = BLOCKS.register("wooden_chair",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion(),
                    Shapes.box(0.1, 0.0, 0.1, 0.9, 0.6, 0.9), true, 0.45));

    public static final RegistryObject<Block> SOFA_RED = BLOCKS.register("sofa_red",
            () -> new SofaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.5f)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    public static final RegistryObject<Block> SOFA_BLUE = BLOCKS.register("sofa_blue",
            () -> new SofaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.5f)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    // ===== LUCES =====
    public static final RegistryObject<Block> CEILING_LIGHT = BLOCKS.register("ceiling_light",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15)
                    .noOcclusion(),
                    Shapes.box(2.0 / 16.0, 10.0 / 16.0, 2.0 / 16.0,
                            14.0 / 16.0, 1.0, 14.0 / 16.0)));

    public static final RegistryObject<Block> WALL_LIGHT = BLOCKS.register("wall_light",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 12)
                    .noOcclusion()));

    public static final RegistryObject<Block> FLOOR_LAMP = BLOCKS.register("floor_lamp",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 14)
                    .noOcclusion(),
                    Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0,
                            13.0 / 16.0, 15.25 / 16.0, 13.0 / 16.0)));

    // ===== ELECTRODOMESTICOS / DECORACION =====
    public static final RegistryObject<Block> TELEVISION = BLOCKS.register("television",
            () -> new TelevisionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(TelevisionBlock.ON) ? 4 : 0)
                    .noOcclusion()));

    public static final RegistryObject<Block> WII_CONSOLE = BLOCKS.register("wii_console",
            () -> new WiiConsoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(WiiConsoleBlock.PHASE) == 2 ? 8
                            : state.getValue(WiiConsoleBlock.PHASE) == 1 ? 3 : 0)
                    .noOcclusion()));

    public static final RegistryObject<Block> KITCHEN_COUNTER = BLOCKS.register("kitchen_counter",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(3.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    public static final RegistryObject<Block> SINK = BLOCKS.register("sink",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(2.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    Shapes.or(
                            Shapes.box(4.0 / 16.0, 0.0, 4.0 / 16.0, 12.0 / 16.0, 8.0 / 16.0, 12.0 / 16.0),
                            Shapes.box(1.0 / 16.0, 7.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0, 10.0 / 16.0, 15.0 / 16.0),
                            Shapes.box(6.0 / 16.0, 10.0 / 16.0, 11.0 / 16.0, 10.0 / 16.0, 14.0 / 16.0, 15.0 / 16.0))));

    public static final RegistryObject<Block> SHOWER = BLOCKS.register("shower",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion(),
                    Shapes.box(0.1, 0.0, 0.1, 0.9, 1.5, 0.9), true, 0.05));

    public static final RegistryObject<Block> CEILING_SHOWER = BLOCKS.register("ceiling_shower",
            () -> new CeilingShowerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> TOILET_PAPER = BLOCKS.register("toilet_paper",
            () -> new ToiletPaperBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion()));

    // ===== V3.0.0: cuadros de pared =====
    public static final RegistryObject<Block> PAINTING_VALLEY_DAWN = BLOCKS.register("painting_valley_dawn",
            () -> new PictureBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(0.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> PAINTING_POLICE_CHASE = BLOCKS.register("painting_police_chase",
            () -> new PictureBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(0.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> PAINTING_CIRCUIT_AERIAL = BLOCKS.register("painting_circuit_aerial",
            () -> new PictureBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(0.5f).sound(SoundType.WOOD).noOcclusion()));

    // ===== PLANTAS Y DECORACION =====
    public static final RegistryObject<Block> PLANT_POT = BLOCKS.register("plant_pot",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(1.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    public static final RegistryObject<Block> DECORATIVE_VASE = BLOCKS.register("decorative_vase",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(1.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    public static final RegistryObject<Block> BOOKSHELF_DECOR = BLOCKS.register("bookshelf_decor",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));


    // ===== NUEVAS DECORACIONES =====
    public static final RegistryObject<Block> ROUND_BULB = BLOCKS.register("round_bulb",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.5f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion(),
                    Shapes.box(4.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0,
                            12.0 / 16.0, 15.5 / 16.0, 12.0 / 16.0)));

    public static final RegistryObject<Block> NIGHT_LAMP = BLOCKS.register("night_lamp",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 12)
                    .noOcclusion(),
                    Shapes.box(3.0 / 16.0, 0.0, 3.0 / 16.0,
                            13.0 / 16.0, 14.0 / 16.0, 13.0 / 16.0)));

    public static final RegistryObject<Block> DESK_LAMP = BLOCKS.register("desk_lamp",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.METAL).lightLevel(state -> 13).noOcclusion(),
                    Shapes.box(2.0 / 16.0, 0.0, 2.0 / 16.0,
                            14.0 / 16.0, 14.0 / 16.0, 14.0 / 16.0)));

    public static final RegistryObject<Block> NIGHTSTAND = BLOCKS.register("nightstand",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> BED_WHITE = BLOCKS.register("bed_white",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.5f)
                    .sound(SoundType.WOOL)
                    .noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5625, 2.0), true, 0.5));

    public static final RegistryObject<Block> BED_BLUE = BLOCKS.register("bed_blue",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.5f)
                    .sound(SoundType.WOOL)
                    .noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5625, 2.0), true, 0.5));

    public static final RegistryObject<Block> BATHTUB = BLOCKS.register("bathtub",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(2.5f)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    Shapes.or(
                        ModBlock.box(0.5, 0, 1, 15.5, 1.5, 31),
                        ModBlock.box(0.5, 1.5, 1, 15.5, 8, 2.5),
                        ModBlock.box(0.5, 1.5, 29.5, 15.5, 8, 31),
                        ModBlock.box(0.5, 1.5, 2.5, 2.5, 8, 29.5),
                        ModBlock.box(13.5, 1.5, 2.5, 15.5, 8, 29.5)
                    )));

    public static final RegistryObject<Block> MODERN_CHAIR = BLOCKS.register("modern_chair",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));


    public static final RegistryObject<Block> BED_PINK = BLOCKS.register("bed_pink",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.5f).sound(SoundType.WOOL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5625, 2.0), true, 0.5));

    public static final RegistryObject<Block> BED_GREEN = BLOCKS.register("bed_green",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(1.5f).sound(SoundType.WOOL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5625, 2.0), true, 0.5));

    public static final RegistryObject<Block> PICNIC_RUG = BLOCKS.register("picnic_rug",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.5f).sound(SoundType.WOOL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0/16.0, 1.0)));

    public static final RegistryObject<Block> DISHWASHER = BLOCKS.register("dishwasher",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> WASHING_MACHINE = BLOCKS.register("washing_machine",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0,
                            15.0 / 16.0, 1.0, 15.0 / 16.0)));

    public static final RegistryObject<Block> MICROWAVE = BLOCKS.register("microwave",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(2.0/16.0, 0.0, 2.0/16.0, 14.0/16.0, 10.0/16.0, 14.0/16.0)));

    public static final RegistryObject<Block> STOVE = BLOCKS.register("stove",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)));

    public static final RegistryObject<Block> PICNIC_BASKET = BLOCKS.register("picnic_basket",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(1.0f).sound(SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> GLASS_RED = BLOCKS.register("glass_red",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.3f).sound(SoundType.GLASS).noOcclusion()));

    public static final RegistryObject<Block> GLASS_BLUE = BLOCKS.register("glass_blue",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.3f).sound(SoundType.GLASS).noOcclusion()));

    public static final RegistryObject<Block> GLASS_GREEN = BLOCKS.register("glass_green",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.3f).sound(SoundType.GLASS).noOcclusion()));

    public static final RegistryObject<Block> GLASS_YELLOW = BLOCKS.register("glass_yellow",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.3f).sound(SoundType.GLASS).noOcclusion()));


    // ===== PUERTAS =====
    public static final RegistryObject<Block> GLASS_SLIDING_DOOR = BLOCKS.register("glass_sliding_door",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(1.5f).sound(SoundType.GLASS).noOcclusion()));

    public static final RegistryObject<Block> WOODEN_DOOR_METAL = BLOCKS.register("wooden_door_metal",
            () -> new HingedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD).noOcclusion(), false));

    public static final RegistryObject<Block> GLASS_DOOR = BLOCKS.register("glass_door",
            () -> new HingedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(1.5f).sound(SoundType.GLASS).noOcclusion(), false));

    public static final RegistryObject<Block> METAL_DOOR_BLACK = BLOCKS.register("metal_door_black",
            () -> new HingedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(), true));
    // ===== NUEVAS LUCES =====
    public static final RegistryObject<Block> CIRCULAR_BULB = BLOCKS.register("circular_bulb",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.5f).sound(SoundType.GLASS).lightLevel(s -> s.getValue(ToggleBlock.OPEN) ? 15 : 0)
                    .noOcclusion(),
                    Shapes.box(4.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0,
                            12.0 / 16.0, 1.0, 12.0 / 16.0), true));

    public static final RegistryObject<Block> CEILING_LIGHT_SQUARE = BLOCKS.register("ceiling_light_square",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.METAL).lightLevel(s -> s.getValue(ToggleBlock.OPEN) ? 14 : 0)
                    .noOcclusion(),
                    Shapes.box(2.0 / 16.0, 10.0 / 16.0, 2.0 / 16.0,
                            14.0 / 16.0, 1.0, 14.0 / 16.0), true));

    public static final RegistryObject<Block> CEILING_LIGHT_ROUND = BLOCKS.register("ceiling_light_round",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.METAL).lightLevel(s -> s.getValue(ToggleBlock.OPEN) ? 14 : 0)
                    .noOcclusion(),
                    Shapes.box(2.0 / 16.0, 10.0 / 16.0, 2.0 / 16.0,
                            14.0 / 16.0, 1.0, 14.0 / 16.0), true));

    public static final RegistryObject<Block> CEILING_BULB = BLOCKS.register("ceiling_bulb",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                    .strength(0.5f).sound(SoundType.GLASS)
                    .lightLevel(state -> state.getValue(ToggleBlock.OPEN) ? 15 : 0).noOcclusion(),
                    Shapes.box(4.0 / 16.0, 4.0 / 16.0, 4.0 / 16.0,
                            12.0 / 16.0, 1.0, 12.0 / 16.0), true));

    public static final RegistryObject<Block> OFFICE_LED_PANEL = BLOCKS.register("office_led_panel",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ToggleBlock.OPEN) ? 15 : 0).noOcclusion(),
                    Shapes.box(1.0 / 16.0, 11.0 / 16.0, 1.0 / 16.0,
                            15.0 / 16.0, 1.0, 15.0 / 16.0), true));

    public static final RegistryObject<Block> WALL_SWITCH = BLOCKS.register("wall_switch",
            () -> new LightSwitchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(0.4f).sound(SoundType.STONE).noOcclusion()));


    // ===== SEMAFORO Y FAROLA =====
    public static final RegistryObject<Block> TRAFFIC_LIGHT = BLOCKS.register("traffic_light",
            () -> new TrafficLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> {
                        var c = state.getValue(TrafficLightBlock.COLOR);
                        return c == TrafficLightBlock.LightColor.RED ? 8 :
                               c == TrafficLightBlock.LightColor.YELLOW ? 10 : 12;
                    })));

    public static final RegistryObject<Block> STREET_LAMP = BLOCKS.register("street_lamp",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> 15)  // maximo de Minecraft (como glowstone)
                    ));

    public static final RegistryObject<Block> DIVING_BOARD = BLOCKS.register("diving_board",
            () -> new DivingBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> METAL_MESH = BLOCKS.register("metal_mesh",
            () -> new MetalMeshBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));


    public static final RegistryObject<Block> EMPANADA_CART = BLOCKS.register("empanada_cart",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD).noOcclusion(),
                    Shapes.box(-10.0/16.0, 0.0, 1.5/16.0, 26.0/16.0, 30.0/16.0, 22.5/16.0)));


    public static final RegistryObject<Block> FLOWER_VASE = BLOCKS.register("flower_vase",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).strength(1.0f).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<Block> TOILET = BLOCKS.register("toilet",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<Block> CURTAIN_RED = BLOCKS.register("curtain_red",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> CURTAIN_BLUE = BLOCKS.register("curtain_blue",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> CURTAIN_WHITE = BLOCKS.register("curtain_white",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> CURTAIN_GREEN = BLOCKS.register("curtain_green",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> WINDOW_CLEAR = BLOCKS.register("window_clear",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f).sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> WINDOW_BLUE = BLOCKS.register("window_blue",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f).sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> WINDOW_TINTED = BLOCKS.register("window_tinted",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f).sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> WINDOW_GREEN = BLOCKS.register("window_green",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f).sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> WINDOW_DARK = BLOCKS.register("window_dark",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f).sound(SoundType.GLASS).noOcclusion()));

    // ===== ACABADOS DE ROBLE =====
    public static final RegistryObject<Block> REFINED_OAK_PLANKS = BLOCKS.register("refined_oak_planks",
            () -> new RotatingDecorativeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD)));

    public static final RegistryObject<Block> OAK_PARQUET = BLOCKS.register("oak_parquet",
            () -> new RotatingDecorativeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD)));

    
    public static final RegistryObject<Block> MINI_FIESTA = BLOCKS.register("mini_fiesta",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(2.5/16.0, 0.0, 1.7/16.0, 13.5/16.0, 6.5/16.0, 14.3/16.0)));

    
    public static final RegistryObject<Block> DOG_DOOR = BLOCKS.register("dog_door",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(1.5f).sound(SoundType.WOOD).noOcclusion(),
                    ToggleBlock.DOG_DOOR_SHAPE, false));

        public static final RegistryObject<Block> CONCRETE_RAMP_WHITE = BLOCKS.register("concrete_ramp_white",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_ORANGE = BLOCKS.register("concrete_ramp_orange",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_MAGENTA = BLOCKS.register("concrete_ramp_magenta",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_LIGHT_BLUE = BLOCKS.register("concrete_ramp_light_blue",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_YELLOW = BLOCKS.register("concrete_ramp_yellow",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_LIME = BLOCKS.register("concrete_ramp_lime",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_PINK = BLOCKS.register("concrete_ramp_pink",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_GRAY = BLOCKS.register("concrete_ramp_gray",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_LIGHT_GRAY = BLOCKS.register("concrete_ramp_light_gray",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_CYAN = BLOCKS.register("concrete_ramp_cyan",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_PURPLE = BLOCKS.register("concrete_ramp_purple",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_BLUE = BLOCKS.register("concrete_ramp_blue",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_BROWN = BLOCKS.register("concrete_ramp_brown",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_GREEN = BLOCKS.register("concrete_ramp_green",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_RED = BLOCKS.register("concrete_ramp_red",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RAMP_BLACK = BLOCKS.register("concrete_ramp_black",
            () -> new ConcreteRampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

        public static final RegistryObject<Block> FENCE_METAL = BLOCKS.register("fence_metal",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.5/16.0, 0.0, 6.5/16.0, 15.5/16.0, 18.5/16.0, 9.5/16.0)));
    public static final RegistryObject<Block> FENCE_ALUMINUM = BLOCKS.register("fence_aluminum",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.5/16.0, 0.0, 6.5/16.0, 15.5/16.0, 18.5/16.0, 9.5/16.0)));
    public static final RegistryObject<Block> FENCE_CARBON = BLOCKS.register("fence_carbon",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.5/16.0, 0.0, 6.5/16.0, 15.5/16.0, 18.5/16.0, 9.5/16.0)));
    public static final RegistryObject<Block> FENCE_STEEL = BLOCKS.register("fence_steel",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.5/16.0, 0.0, 6.5/16.0, 15.5/16.0, 18.5/16.0, 9.5/16.0)));
    public static final RegistryObject<Block> FENCE_GOLD = BLOCKS.register("fence_gold",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.5/16.0, 0.0, 6.5/16.0, 15.5/16.0, 18.5/16.0, 9.5/16.0)));
    public static final RegistryObject<Block> SIGN_STOP = BLOCKS.register("sign_stop",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(Shapes.box(7.0/16.0, 0.0, 7.0/16.0, 9.0/16.0, 14.0/16.0, 9.0/16.0), Shapes.box(1.0/16.0, 8.0/16.0, 6.5/16.0, 15.0/16.0, 15.5/16.0, 7.0/16.0))));
    public static final RegistryObject<Block> SIGN_NO_PARKING = BLOCKS.register("sign_no_parking",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(Shapes.box(7.0/16.0, 0.0, 7.0/16.0, 9.0/16.0, 14.0/16.0, 9.0/16.0), Shapes.box(1.0/16.0, 8.0/16.0, 6.5/16.0, 15.0/16.0, 15.5/16.0, 7.0/16.0))));
    public static final RegistryObject<Block> SIGN_TURN_LEFT = BLOCKS.register("sign_turn_left",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(Shapes.box(7.0/16.0, 0.0, 7.0/16.0, 9.0/16.0, 14.0/16.0, 9.0/16.0), Shapes.box(1.0/16.0, 8.0/16.0, 6.5/16.0, 15.0/16.0, 15.5/16.0, 7.0/16.0))));
    public static final RegistryObject<Block> SIGN_TURN_RIGHT = BLOCKS.register("sign_turn_right",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(Shapes.box(7.0/16.0, 0.0, 7.0/16.0, 9.0/16.0, 14.0/16.0, 9.0/16.0), Shapes.box(1.0/16.0, 8.0/16.0, 6.5/16.0, 15.0/16.0, 15.5/16.0, 7.0/16.0))));
    public static final RegistryObject<Block> SIGN_PEDESTRIAN = BLOCKS.register("sign_pedestrian",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(Shapes.box(7.0/16.0, 0.0, 7.0/16.0, 9.0/16.0, 14.0/16.0, 9.0/16.0), Shapes.box(1.0/16.0, 8.0/16.0, 6.5/16.0, 15.0/16.0, 15.5/16.0, 7.0/16.0))));

    
    public static final RegistryObject<Block> PLATE_BURGER = BLOCKS.register("plate_burger",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));
    public static final RegistryObject<Block> PLATE_HOTDOG = BLOCKS.register("plate_hotdog",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));
    public static final RegistryObject<Block> PLATE_PIZZA = BLOCKS.register("plate_pizza",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));
    public static final RegistryObject<Block> PLATE_RICE = BLOCKS.register("plate_rice",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));
    public static final RegistryObject<Block> PLATE_SUSHI = BLOCKS.register("plate_sushi",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));
    public static final RegistryObject<Block> PLATE_CHICKEN = BLOCKS.register("plate_chicken",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));
    public static final RegistryObject<Block> PLATE_FRIES = BLOCKS.register("plate_fries",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
                    .strength(0.5f).sound(SoundType.STONE).noOcclusion(),
                    Shapes.box(1.5/16.0, 0.0, 1.5/16.0, 14.5/16.0, 4.0/16.0, 14.5/16.0)));

    
    public static final RegistryObject<Block> PRINTER = BLOCKS.register("printer",
            () -> new PrinterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5f).sound(SoundType.METAL).noOcclusion()));

    
    public static final RegistryObject<Block> DOG_BED_PINK = BLOCKS.register("dog_bed_pink",
            () -> new DogBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.8f).sound(SoundType.WOOL).noOcclusion()));

    public static final RegistryObject<Block> DOG_BED_BLUE = BLOCKS.register("dog_bed_blue",
            () -> new DogBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.8f).sound(SoundType.WOOL).noOcclusion()));

    public static final RegistryObject<Block> DOG_BED_GRAY = BLOCKS.register("dog_bed_gray",
            () -> new DogBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.8f).sound(SoundType.WOOL).noOcclusion()));

    // ===== NUEVA COLECCIÓN =====
    public static final RegistryObject<Block> MINI_F150 = BLOCKS.register("mini_f150",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.2f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)));

    public static final RegistryObject<Block> MINI_MUSTANG = BLOCKS.register("mini_mustang",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.2f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)));

    public static final RegistryObject<Block> XBOX_360 = BLOCKS.register("xbox_360",
            () -> new HorizontalConsoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0,
                            15.0 / 16.0, 7.0 / 16.0, 15.0 / 16.0),
                    Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0,
                            15.0 / 16.0, 7.0 / 16.0, 15.0 / 16.0)));

    public static final RegistryObject<Block> NINTENDO_SWITCH = BLOCKS.register("nintendo_switch",
            () -> new HorizontalConsoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.4 / 16.0, 0.0, 5.5 / 16.0,
                            15.6 / 16.0, 6.5 / 16.0, 10.5 / 16.0),
                    Shapes.box(5.5 / 16.0, 0.0, 0.4 / 16.0,
                            10.5 / 16.0, 6.5 / 16.0, 15.6 / 16.0)));

    public static final RegistryObject<Block> BARREL_GASOLINE = BLOCKS.register("barrel_gasoline",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(1.4 / 16.0, 0.0, 1.4 / 16.0, 14.6 / 16.0, 1.0, 14.6 / 16.0)));

    public static final RegistryObject<Block> BARREL_WATER = BLOCKS.register("barrel_water",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(1.4 / 16.0, 0.0, 1.4 / 16.0, 14.6 / 16.0, 1.0, 14.6 / 16.0)));

    public static final RegistryObject<Block> BARREL_DIESEL = BLOCKS.register("barrel_diesel",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(1.4 / 16.0, 0.0, 1.4 / 16.0, 14.6 / 16.0, 1.0, 14.6 / 16.0)));

    public static final RegistryObject<Block> BARREL_OIL = BLOCKS.register("barrel_oil",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(1.4 / 16.0, 0.0, 1.4 / 16.0, 14.6 / 16.0, 1.0, 14.6 / 16.0)));

    public static final RegistryObject<Block> PIPE_IRON = BLOCKS.register("pipe_iron",
            () -> new PipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> PIPE_STEEL = BLOCKS.register("pipe_steel",
            () -> new PipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> PIPE_ALUMINUM = BLOCKS.register("pipe_aluminum",
            () -> new PipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> CONVEYOR_BELT = BLOCKS.register("conveyor_belt",
            () -> new ConveyorBeltBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> XBOX_ONE = BLOCKS.register("xbox_one",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3, 1.0)));

    public static final RegistryObject<Block> KINECT_SENSOR = BLOCKS.register("kinect_sensor",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.2f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.0, 0.2, 0.2, 1.0, 0.8, 0.8)));

    public static final RegistryObject<Block> HS_LAPTOP = BLOCKS.register("hs_laptop",
            () -> new LaptopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> HS_DESKTOP = BLOCKS.register("hs_desktop",
            () -> new HsDesktopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(HsDesktopBlock.ON) ? 5 : 0)));

    // ===== COMPUTADORES HS NUEVOS (V2.3.0): 3 antiguos y 3 modernos =====
    public static final Map<String, RegistryObject<Block>> HS_DESKTOPS = new LinkedHashMap<>();

    static {
        for (String id : new String[]{"hs_desktop_retro", "hs_desktop_compact", "hs_desktop_amber", "hs_desktop_slim",
                "hs_desktop_gaming", "hs_desktop_dual"}) {
            HS_DESKTOPS.put(id, BLOCKS.register(id,
                    () -> new HsDesktopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                            .strength(1.5f).sound(SoundType.METAL).noOcclusion()
                            .lightLevel(state -> state.getValue(HsDesktopBlock.ON) ? 5 : 0),
                            HsDesktopBlock.variantShape(id))));
        }
    }

    public static final RegistryObject<Block> DRONE = BLOCKS.register("drone", DroneBlock::new);

    /** V3.1.0: carro RC Lamborgonzo Huracanito "apagado" (bloque); el control remoto lo vuelve entidad. */
    public static final RegistryObject<Block> RC_CAR = BLOCKS.register("rc_car", RcCarBlock::new);

    public static final RegistryObject<Block> OFFICE_CHAIR = BLOCKS.register("office_chair",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 15.0 / 16.0, 14.0 / 16.0),
                    true, 0.48));

    public static final RegistryObject<Block> BAR_STOOL = BLOCKS.register("bar_stool",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(
                            ModBlock.box(2, 12, 2, 14, 15, 14),
                            ModBlock.box(3, 11, 3, 13, 13, 13),
                            ModBlock.box(7, 5, 7, 9, 12, 9),
                            ModBlock.box(2, 1, 2, 4, 12, 4),
                            ModBlock.box(12, 1, 2, 14, 12, 4),
                            ModBlock.box(2, 1, 12, 4, 12, 14),
                            ModBlock.box(12, 1, 12, 14, 12, 14),
                            ModBlock.box(3, 4, 2, 13, 5, 4),
                            ModBlock.box(3, 4, 12, 13, 5, 14),
                            ModBlock.box(2, 4, 3, 4, 5, 13),
                            ModBlock.box(12, 4, 3, 14, 5, 13)),
                    true, 0.76));

    public static final Map<String, RegistryObject<Block>> NEON_BARS = new LinkedHashMap<>();

    static {
        for (String color : NeonLightBlock.COLORS) {
            NEON_BARS.put(color, BLOCKS.register("neon_bar_" + color,
                    () -> new NeonLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(0.5f).sound(SoundType.GLASS)
                            .lightLevel(state -> 14).noOcclusion())));
        }
    }

    // ===== AUTOS =====
    public static final RegistryObject<Block> MINI_FERRARI = BLOCKS.register("mini_ferrari",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.2f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.03, 0.0, 0.0, 0.97, 0.4, 1.0)));

    public static final RegistryObject<Block> MINI_GOLF = BLOCKS.register("mini_golf",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.2f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.03, 0.0, 0.0, 0.97, 0.5, 1.0)));

    public static final RegistryObject<Block> MINI_MUSTANG_CLASSIC = BLOCKS.register("mini_mustang_classic",
            () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.2f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.box(0.03, 0.0, 0.0, 0.97, 0.45, 1.0)));

    public static final RegistryObject<Block> XBOX_SERIES_S = BLOCKS.register("xbox_series_s",
            () -> new XboxSeriesSBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    public static final String[] SLIDE_COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final Map<String, RegistryObject<Block>> SLIDES = new LinkedHashMap<>();

    static {
        for (String color : SLIDE_COLORS) {
            SLIDES.put(color, BLOCKS.register("slide_" + color,
                    () -> new SlideRampBlock(BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.item.DyeColor.byName(color,
                                    net.minecraft.world.item.DyeColor.WHITE).getMapColor())
                            .strength(1.5f).sound(SoundType.STONE).friction(0.98f).noOcclusion())));
        }
    }

    // ===== SILLAS, SOFÁS Y MESITAS DE NOCHE DE TODAS LAS MADERAS =====
    public static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped"};
    public static final Map<String, RegistryObject<Block>> CHAIRS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> SOFAS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> NIGHTSTANDS = new LinkedHashMap<>();

    static {
        for (String wood : WOODS) {
            CHAIRS.put("chair_" + wood, BLOCKS.register("chair_" + wood,
                    () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion(),
                            Shapes.box(0.1, 0.0, 0.1, 0.9, 0.6, 0.9), true, 0.52)));
            SOFAS.put("sofa_" + wood, BLOCKS.register("sofa_" + wood,
                    () -> new SofaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(1.5f).sound(SoundType.WOOD).noOcclusion())));
            NIGHTSTANDS.put("nightstand_" + wood, BLOCKS.register("nightstand_" + wood,
                    () -> new NightstandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion())));
        }
    }

    // ===== MESAS DE TODAS LAS MADERAS =====
    public static final Map<String, RegistryObject<Block>> PEDESTAL_TABLES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> TABLES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> DESKS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> SHELF_TABLES = new LinkedHashMap<>();

    static {
        for (String wood : WOODS) {
            PEDESTAL_TABLES.put("pedestal_table_" + wood, BLOCKS.register("pedestal_table_" + wood,
                    () -> new ModBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion(),
                            Shapes.or(ModBlock.box(0, 13, 1, 16, 16, 15), ModBlock.box(6.5, 1.5, 6.5, 9.5, 13, 9.5),
                                    ModBlock.box(3, 0, 3, 13, 1.5, 13)))));
            TABLES.put("table_" + wood, BLOCKS.register("table_" + wood,
                    () -> new TableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion())));
            DESKS.put("writing_desk_" + wood, BLOCKS.register("writing_desk_" + wood,
                    () -> new WritingDeskBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion())));
            SHELF_TABLES.put("shelf_table_" + wood, BLOCKS.register("shelf_table_" + wood,
                    () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion(),
                            Shapes.block())));
        }
    }

    // ===== VENTILADORES =====
    public static final Map<String, RegistryObject<Block>> FANS = new LinkedHashMap<>();

    static {
        FANS.put("fan_small", BLOCKS.register("fan_small",
                () -> new FanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(1.0f).sound(SoundType.METAL).noOcclusion(),
                        ModBlock.box(4, 0, 4, 12, 11.4, 11), 1.6f)));
        FANS.put("fan_medium", BLOCKS.register("fan_medium",
                () -> new FanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                        ModBlock.box(3.5, 0, 3.5, 12.5, 16, 12.5), 1.3f)));
        FANS.put("fan_large", BLOCKS.register("fan_large",
                () -> new FanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(2.0f).sound(SoundType.METAL).noOcclusion(),
                        ModBlock.box(0.4, 0, 2.5, 15.6, 16, 13.5), 0.9f)));
    }

    // ===== VENTILADORES DE PIE (V2.3.0): dos bloques de alto =====
    public static final Map<String, RegistryObject<Block>> PEDESTAL_FANS = new LinkedHashMap<>();

    static {
        for (String id : new String[]{"pedestal_fan_white", "pedestal_fan_black", "pedestal_fan_retro"}) {
            PEDESTAL_FANS.put(id, BLOCKS.register(id,
                    () -> new PedestalFanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(1.5f).sound(SoundType.METAL).noOcclusion())));
        }
    }

    // ===== TELEVISORES (modernos, antiguos y grandes) =====
    public static final Map<String, RegistryObject<Block>> TVS = new LinkedHashMap<>();

    static {
        TVS.put("tv_modern", BLOCKS.register("tv_modern",
                () -> new TelevisionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(2.5f).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(TelevisionBlock.ON) ? 5 : 0).noOcclusion(),
                        Shapes.or(ModBlock.box(0, 4.5, 7, 16, 13.5, 9.2), ModBlock.box(3, 0, 6, 13, 4.5, 10)))));
        TVS.put("tv_crt", BLOCKS.register("tv_crt",
                () -> new TelevisionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(2.5f).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(TelevisionBlock.ON) ? 4 : 0).noOcclusion(),
                        Shapes.or(ModBlock.box(1, 1.5, 3, 15, 13.5, 10), ModBlock.box(3, 3, 10, 13, 12, 15),
                                ModBlock.box(2, 0, 4, 14, 1.5, 12)))));
        TVS.put("tv_retro", BLOCKS.register("tv_retro",
                () -> new TelevisionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                        .strength(2.5f).sound(SoundType.WOOD)
                        .lightLevel(state -> state.getValue(TelevisionBlock.ON) ? 3 : 0).noOcclusion(),
                        Shapes.or(ModBlock.box(1, 3, 3, 15, 14, 12), ModBlock.box(1.5, 0, 4, 14.5, 3, 11.5)))));
        TVS.put("tv_big", BLOCKS.register("tv_big",
                () -> new BigTelevisionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(3.0f).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(TelevisionBlock.ON) ? 6 : 0).noOcclusion(), 3, 2)));
        TVS.put("tv_cinema", BLOCKS.register("tv_cinema",
                () -> new BigTelevisionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(3.0f).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(TelevisionBlock.ON) ? 6 : 0).noOcclusion(), 4, 2)));
    }

    // ===== LÁMPARA DE ESCRITORIO ARTICULADA (flexo) =====
    public static final RegistryObject<Block> DESK_LAMP_FLEXO = BLOCKS.register("desk_lamp_flexo",
            () -> new ToggleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.0f).sound(SoundType.METAL)
                    .lightLevel(s -> s.getValue(ToggleBlock.OPEN) ? 14 : 0).noOcclusion(),
                    ModBlock.box(3, 0, 3, 12, 14, 11), true));

    // ===== VENTILADORES DE TECHO (clic = encender/apagar) =====
    public static final Map<String, RegistryObject<Block>> CEILING_FANS = new LinkedHashMap<>();

    static {
        for (String type : new String[]{"wood", "white", "black"}) {
            CEILING_FANS.put("ceiling_fan_" + type, BLOCKS.register("ceiling_fan_" + type,
                    () -> new CeilingFanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(1.0f).sound(SoundType.METAL).noOcclusion())));
        }
    }

    // ===== CAMAS MATRIMONIALES 2x2 (un color y combinaciones manta + sábanas) =====
    /** {manta, sábanas y almohadas}. Debe coincidir con BED_COMBOS de tools/generate_home_and_street_pack.py. */
    public static final String[][] BED_COMBOS = {
            {"red", "white"}, {"blue", "white"}, {"green", "white"}, {"black", "white"},
            {"gray", "white"}, {"pink", "white"}, {"purple", "white"}, {"light_blue", "white"},
            {"black", "red"}, {"blue", "yellow"}, {"red", "black"}, {"gray", "light_gray"},
            {"lime", "green"}, {"orange", "brown"}, {"cyan", "blue"}, {"magenta", "purple"}};
    public static final Map<String, RegistryObject<Block>> DOUBLE_BEDS = new LinkedHashMap<>();

    private static void registerDoubleBed(String name) {
        DOUBLE_BEDS.put(name, BLOCKS.register(name,
                () -> new DoubleBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                        .strength(1.5f).sound(SoundType.WOOL).noOcclusion())));
    }

    static {
        for (String color : SLIDE_COLORS) {
            registerDoubleBed("bed_double_" + color);
        }
        for (String[] combo : BED_COMBOS) {
            registerDoubleBed("bed_double_" + combo[0] + "_" + combo[1]);
        }
    }

    // ===== ESTANTERÍAS: madera y metal =====
    public static final String[] METAL_SHELF_TYPES = {"iron", "black", "gold", "copper"};
    public static final Map<String, RegistryObject<Block>> SHELVES = new LinkedHashMap<>();

    private static void registerShelf(String name, SoundType sound, MapColor color, float strength) {
        SHELVES.put(name, BLOCKS.register(name,
                () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(color)
                        .strength(strength, 3.0f).sound(sound).noOcclusion(),
                        Shapes.box(0.0, 0.0, 4.0 / 16.0, 1.0, 1.0, 1.0))));
    }

    static {
        for (String wood : WOODS) {
            registerShelf("shelf_" + wood, SoundType.WOOD, MapColor.WOOD, 2.0f);
        }
        for (String metal : METAL_SHELF_TYPES) {
            registerShelf("shelf_metal_" + metal, SoundType.METAL, MapColor.METAL, 2.5f);
        }
    }

    // ===== SEÑAL "PROHIBIDO CAMIONES" =====
    public static final RegistryObject<Block> SIGN_NO_TRUCKS = BLOCKS.register("sign_no_trucks",
            () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(ModBlock.box(7, 0, 7, 9, 13, 9), ModBlock.box(2, 3.5, 6.4, 14, 15.5, 7))));

    // ===== ALFOMBRAS DECORATIVAS (se camina encima; sonido de lana) =====
    public static final String[] CARPET_STRIPES = {"blue", "pink", "mono"};
    public static final String[] CARPET_CHECKERS = {"white_black", "lightblue_darkblue", "red_white", "red_black",
            "pink_purple", "green_lime", "yellow_orange", "gray_lightgray", "cyan_blue", "brown_beige"};
    public static final Map<String, RegistryObject<Block>> CARPETS = new LinkedHashMap<>();

    private static void registerCarpet(String name) {
        CARPETS.put(name, BLOCKS.register(name,
                () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                        .strength(0.1f).sound(SoundType.WOOL).noOcclusion(),
                        Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0))));
    }

    static {
        for (String key : CARPET_STRIPES) {
            registerCarpet("carpet_stripes_" + key);
        }
        for (String key : CARPET_CHECKERS) {
            registerCarpet("carpet_checker_" + key);
        }
    }

    // ===== TIMBRES (v1.4.0): clic derecho = suena el timbre =====
    public static final String[] DOORBELL_COLORS = {"white", "light_gray", "dark_gray"};
    public static final Map<String, RegistryObject<Block>> DOORBELLS = new LinkedHashMap<>();

    static {
        for (String color : DOORBELL_COLORS) {
            DOORBELLS.put("doorbell_" + color, BLOCKS.register("doorbell_" + color,
                    () -> new DoorbellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(0.8f).sound(SoundType.METAL).noOcclusion(),
                            ModBlock.box(5.5, 4, 14.5, 10.5, 12, 16))));
        }
    }

    // ===== COCINA (v1.4.0): escurridores, cafetera, exprimidor, sartenes y tapas =====
    public static final Map<String, RegistryObject<Block>> KITCHENWARE = new LinkedHashMap<>();

    private static void registerKitchenware(String name, VoxelShape northShape, SoundType sound) {
        KITCHENWARE.put(name, BLOCKS.register(name,
                () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(1.0f, 2.0f).sound(sound).noOcclusion(), northShape)));
    }

    static {
        registerKitchenware("dish_rack_empty", ModBlock.box(1, 0, 3, 15, 5.6, 13), SoundType.METAL);
        registerKitchenware("dish_rack_plates", ModBlock.box(1, 0, 3, 15, 8.5, 13), SoundType.METAL);
        registerKitchenware("coffee_maker", ModBlock.box(2.5, 0, 3, 13.5, 13.5, 14.5), SoundType.METAL);
        registerKitchenware("orange_juicer", ModBlock.box(3, 0, 2.2, 13, 9.6, 13), SoundType.METAL);
        for (String pan : new String[]{"cast_iron", "stainless", "copper", "nonstick", "red"}) {
            registerKitchenware("frying_pan_" + pan, ModBlock.box(2, 0, 3, 16, 2.4, 13), SoundType.METAL);
            registerKitchenware("pan_lid_" + pan.replace("nonstick", "glass"),
                    ModBlock.box(2.5, 0, 2.5, 13.5, 4.3, 13.5),
                    pan.equals("nonstick") ? SoundType.GLASS : SoundType.METAL);
        }
    }

    // ===== COCINA 2: estufas con horno, muebles de estufa, horno, microondas y cubiertos =====
    public static final String[] STOVE_COLORS = {"light_gray", "dark_gray", "black", "white"};
    public static final String[] MICROWAVE_COLORS = {"white", "gray", "dark_gray", "black"};
    public static final String[] KITCHEN_SINK_COLORS = {"white", "light_gray", "dark_gray", "black"};
    public static final Map<String, RegistryObject<Block>> KITCHEN_APPLIANCES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> CUTLERY = new LinkedHashMap<>();

    private static BlockBehaviour.Properties applianceProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(3.0f, 4.0f).sound(SoundType.METAL).noOcclusion();
    }

    /** Electrodoméstico con puerta que se abre y cierra con clic derecho. */
    private static void registerOpenable(String name, VoxelShape northShape, float pitch) {
        KITCHEN_APPLIANCES.put(name, BLOCKS.register(name,
                () -> new ApplianceDoorBlock(applianceProps(), northShape, pitch)));
    }

    private static void registerCutlery(String name, VoxelShape northShape) {
        CUTLERY.put(name, BLOCKS.register(name,
                () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(0.2f).sound(SoundType.METAL).noOcclusion(), northShape)));
    }

    static {
        for (String color : STOVE_COLORS) {
            registerOpenable("stove_oven_" + color, ModBlock.box(0, 0, 0, 16, 14, 16), 0.9f);
            KITCHEN_APPLIANCES.put("stove_cabinet_" + color, BLOCKS.register("stove_cabinet_" + color,
                    () -> new ShapedFacingBlock(applianceProps(), ModBlock.box(0, 0, 0, 16, 14, 16))));
        }
        // Lavaplatos de cocina (v1.5.0): mueble con tina, escurridor y grifo
        for (String color : KITCHEN_SINK_COLORS) {
            KITCHEN_APPLIANCES.put("kitchen_sink_" + color, BLOCKS.register("kitchen_sink_" + color,
                    () -> new ShapedFacingBlock(applianceProps(), ModBlock.box(0, 0, 0, 16, 13, 16))));
        }
        registerOpenable("kitchen_oven", ModBlock.box(0, 0, 0, 16, 16, 16), 0.8f);
        for (String color : MICROWAVE_COLORS) {
            registerOpenable("microwave_" + color, ModBlock.box(1, 0, 2, 15, 9, 14), 1.4f);
        }
        registerCutlery("cutlery_fork", ModBlock.box(6, 0, 1, 10, 1, 13));
        registerCutlery("cutlery_knife", ModBlock.box(6.5, 0, 1.5, 9.5, 1, 13));
        registerCutlery("cutlery_spoon", ModBlock.box(6, 0, 1.5, 10, 1, 13));
        registerCutlery("cutlery_set", ModBlock.box(2.5, 0, 1, 13.5, 1, 13));
        registerCutlery("cutlery_pack", ModBlock.box(5, 0, 2.4, 11, 2, 14));
    }

    // ===== PIANO (v1.4.0): 2 de ancho x 1 de alto; cada clic toca la nota siguiente =====
    public static final Map<String, RegistryObject<Block>> PIANOS = new LinkedHashMap<>();

    static {
        PIANOS.put("piano", BLOCKS.register("piano",
                () -> new PianoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                        .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion())));
        PIANOS.put("piano_stand", BLOCKS.register("piano_stand",
                () -> new PianoStandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                        .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion())));
    }

    // ===== ESCALERAS BONITAS (v1.6.0): 11 maderas, 16 colores de concreto y cuarzo liso =====
    public static final String[] CONCRETE_COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final Map<String, RegistryObject<Block>> STAIRS = new LinkedHashMap<>();

    private static Block vanillaBlock(String path) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", path));
    }

    private static void registerStairs(String name, String vanillaBase) {
        STAIRS.put(name, BLOCKS.register(name,
                () -> new StairBlock(() -> vanillaBlock(vanillaBase).defaultBlockState(),
                        BlockBehaviour.Properties.copy(vanillaBlock(vanillaBase)))));
    }

    static {
        for (String wood : WOODS) {
            registerStairs("stairs_" + wood, wood + "_planks");
        }
        for (String color : CONCRETE_COLORS) {
            registerStairs("stairs_concrete_" + color, color + "_concrete");
        }
        registerStairs("stairs_smooth_quartz", "smooth_quartz");
    }

    // ===== LAVAMANOS (v1.6.0): 3 diseños x 4 colores =====
    public static final String[] WASHBASIN_COLORS = {"white", "light_gray", "dark_gray", "black"};
    public static final String[] WASHBASIN_DESIGNS = {"pedestal", "wall", "vanity"};
    public static final Map<String, RegistryObject<Block>> WASHBASINS = new LinkedHashMap<>();

    private static VoxelShape washbasinShape(String design) {
        return switch (design) {
            case "pedestal" -> Shapes.or(ModBlock.box(4.5, 0, 5.5, 11.5, 9, 12.5),
                    ModBlock.box(2, 8.5, 2.5, 14, 15.8, 15));
            case "wall" -> ModBlock.box(2, 4.5, 5, 14, 16, 16);
            default -> Shapes.or(ModBlock.box(0, 0, 2, 16, 13, 16), ModBlock.box(4, 13, 5, 12, 16, 15.5));
        };
    }

    static {
        for (String design : WASHBASIN_DESIGNS) {
            for (String color : WASHBASIN_COLORS) {
                String name = "washbasin_" + design + "_" + color;
                WASHBASINS.put(name, BLOCKS.register(name,
                        () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                                .strength(2.0f).sound(SoundType.STONE).noOcclusion(), washbasinShape(design))));
            }
        }
    }


    // ===== V1.8.0: congelador, cámara frigorífica, cajas y la marca Bugerati =====
    public static final Map<String, RegistryObject<Block>> CRATES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> BUGERATI = new LinkedHashMap<>();
    public static final String[] CRATE_IDS = {"crate_empty", "crate_fries", "crate_ribs", "crate_balls"};
    public static final String[] BUGERATI_NEON_SHAPES = {"ring", "heart", "star", "bolt", "moon", "arrow"};
    public static final String[] BUGERATI_NEON_COLORS = {"pink", "cyan", "yellow", "red"};
    public static final String[] BUGERATI_CHANDELIER_FINISHES = {"gold", "silver", "black"};

    /** Cámara frigorífica 3x3x3 (27 piezas); la luz 14 solo la da la pieza del bombillo del techo. */
    public static final RegistryObject<Block> COLD_ROOM = BLOCKS.register("cold_room",
            () -> new ColdRoomBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f, 4.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(ColdRoomBlock.PART) == ColdRoomBlock.LIGHT_PART ? 14 : 0)));

    /** V2.0.0: puerta de garaje 3x3 que se abre hacia arriba. */
    public static final RegistryObject<Block> GARAGE_DOOR = BLOCKS.register("garage_door",
            () -> new GarageDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f, 5.0f).sound(SoundType.METAL).noOcclusion()));

    // ===== V2.1.0: cajones de cocina (almacenamiento, 11 maderas) y caja de herramientas abierta =====
    public static final Map<String, RegistryObject<Block>> KITCHEN_DRAWERS = new LinkedHashMap<>();

    static {
        for (String wood : WOODS) {
            KITCHEN_DRAWERS.put("kitchen_drawers_" + wood, BLOCKS.register("kitchen_drawers_" + wood,
                    () -> new KitchenDrawersBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(2.0f, 3.0f).sound(SoundType.WOOD))));
        }
    }

    /** Caja de herramientas roja con la tapa abierta y herramientas adentro (decorativa). Forma mirando al norte. */
    public static final RegistryObject<Block> TOOLBOX_OPEN = BLOCKS.register("toolbox_open",
            () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
                    .strength(1.0f, 2.0f).sound(SoundType.METAL).noOcclusion(),
                    Shapes.or(ModBlock.box(1, 0, 3, 15, 6.5, 12), ModBlock.box(1, 6.5, 11, 15, 15, 16))));

    // ===== V4.1.0: pantallas de anuncios (cuadradas: 4x4, 8x8 y 12x12 bloques) =====
    public static final Map<String, RegistryObject<Block>> AD_SCREENS = new LinkedHashMap<>();

    static {
        int[] sizes = {4, 8, 12};
        String[] names = {"ad_screen_small", "ad_screen_medium", "ad_screen_large"};
        for (int i = 0; i < sizes.length; i++) {
            final int size = sizes[i];
            AD_SCREENS.put(names[i], BLOCKS.register(names[i],
                    () -> new AdScreenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                            .strength(2.0f).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7), size)));
        }
    }

    // ===== V4.0.0: megafonía =====
    /** Micrófono de mesa (base negra): se pone encima de un bloque; su menú permite hablar por las bocinas vinculadas. */
    public static final RegistryObject<Block> MICROPHONE = BLOCKS.register("microphone",
            () -> new MicrophoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(0.8f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(MicrophoneBlock.TALKING) ? 4 : 0)));

    /** Bocina de megafonía: suena lo que se habla por el micrófono al que está vinculada. */
    public static final RegistryObject<Block> PA_SPEAKER = BLOCKS.register("pa_speaker",
            () -> new PaSpeakerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    /** V4.2.0: radio de mesa y su bafle. */
    public static final RegistryObject<Block> RADIO_TABLE = BLOCKS.register("radio_table",
            () -> new RadioTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(1.0f).sound(SoundType.WOOD).noOcclusion()
                    .lightLevel(state -> state.getValue(RadioTableBlock.ON) ? 3 : 0)));
    public static final RegistryObject<Block> RADIO_SPEAKER = BLOCKS.register("radio_speaker",
            () -> new RadioSpeakerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(1.2f).sound(SoundType.WOOD).noOcclusion()));

    /** V4.3.0: cámaras de seguridad (negra y blanca), monitor de mesa y pantalla de pared. */
    public static final RegistryObject<Block> SECURITY_CAMERA_BLACK = BLOCKS.register("security_camera_black",
            () -> new SecurityCameraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(), false));
    public static final RegistryObject<Block> SECURITY_CAMERA_WHITE = BLOCKS.register("security_camera_white",
            () -> new SecurityCameraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(), true));
    public static final RegistryObject<Block> SECURITY_MONITOR = BLOCKS.register("security_monitor",
            () -> new SecurityMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(), false));
    public static final RegistryObject<Block> SECURITY_SCREEN = BLOCKS.register("security_screen",
            () -> new SecurityMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion(), true));

    /** V4.8.0: riel del tren de carga (recto, curvas, subidas y bajadas). */
    public static final RegistryObject<Block> FREIGHT_RAIL = BLOCKS.register("freight_rail",
            () -> new FreightRailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .noCollission().strength(0.7f).sound(SoundType.METAL)));

    /** V5.1.0: riel del metro. */
    public static final RegistryObject<Block> METRO_RAIL = BLOCKS.register("metro_rail",
            () -> new MetroRailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .noCollission().strength(0.7f).sound(SoundType.METAL)));

    /** V5.0.0: riel del tren de pasajeros. */
    public static final RegistryObject<Block> PASSENGER_RAIL = BLOCKS.register("passenger_rail",
            () -> new PassengerRailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .noCollission().strength(0.7f).sound(SoundType.METAL)));

    // ===== V2.2.0: sistema de ascensores =====
    /** Controlador: define la zona, el tamaño y la velocidad de la cabina. Un controlador por piso. */
    public static final RegistryObject<Block> ELEVATOR_CONTROLLER = BLOCKS.register("elevator_controller",
            () -> new ElevatorControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0f, 6.0f).sound(SoundType.METAL)));

    /** Pantalla de pisos: se pega en cualquier cara y se vincula a un controlador. */
    public static final RegistryObject<Block> ELEVATOR_DISPLAY = BLOCKS.register("elevator_display",
            () -> new ElevatorDisplayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .strength(1.0f, 2.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> 6)));

    /** Puerta corrediza de un bloque que se abre cuando la cabina llega a su piso. */
    public static final RegistryObject<Block> ELEVATOR_DOOR = BLOCKS.register("elevator_door",
            () -> new ElevatorDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

    private static BlockBehaviour.Properties bugeratiLampProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(1.0f).sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(ToggleBlock.OPEN) ? 14 : 0).noOcclusion();
    }

    private static void registerBugeratiLamp(String name, VoxelShape northShape) {
        BUGERATI.put(name, BLOCKS.register(name,
                () -> new BugeratiLampBlock(bugeratiLampProps(), northShape)));
    }

    private static void registerBugeratiNeon(String name) {
        BUGERATI.put(name, BLOCKS.register(name,
                () -> new NeonPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(0.5f).sound(SoundType.GLASS).lightLevel(state -> 13).noOcclusion())));
    }

    static {
        // Congelador horizontal: la tapa se levanta con clic derecho (igual que el horno o el microondas)
        registerOpenable("freezer_chest", ModBlock.box(0, 0, 1, 16, 14, 15), 0.7f);

        for (String id : CRATE_IDS) {
            CRATES.put(id, BLOCKS.register(id,
                    () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(0.8f).sound(SoundType.WOOD).noOcclusion(),
                            ModBlock.box(1, 0, 1, 15, 12, 15))));
        }

        // Lámparas Bugerati (clic derecho = encender/apagar; también obedecen a los interruptores de pared)
        registerBugeratiLamp("bugerati_floor_lamp", ModBlock.box(4, 0, 4, 12, 15.6, 12));
        registerBugeratiLamp("bugerati_arc_lamp", ModBlock.box(3, 0, 3, 13, 13.5, 13));
        registerBugeratiLamp("bugerati_table_lamp", ModBlock.box(4, 0, 4, 12, 15.6, 12));
        registerBugeratiLamp("bugerati_orb_lamp", ModBlock.box(4, 0, 4, 12, 13.3, 12));
        registerBugeratiLamp("bugerati_pendant_lamp", ModBlock.box(4, 1, 4, 12, 16, 12));
        registerBugeratiLamp("bugerati_wall_sconce", ModBlock.box(5.6, 3.2, 9.4, 10.4, 12.8, 16));
        for (String finish : BUGERATI_CHANDELIER_FINISHES) {
            registerBugeratiLamp("bugerati_chandelier_" + finish, ModBlock.box(1, 1.4, 1, 15, 16, 15));
        }

        // Neones Bugerati de pared: 6 formas x 4 colores + logo dorado
        for (String shape : BUGERATI_NEON_SHAPES) {
            for (String color : BUGERATI_NEON_COLORS) {
                registerBugeratiNeon("bugerati_neon_" + shape + "_" + color);
            }
        }
        registerBugeratiNeon("bugerati_neon_logo");
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
