package com.example.beautifuldecor.init;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.item.DroneRemoteItem;
import com.example.beautifuldecor.item.RcCarRemoteItem;
import com.example.beautifuldecor.item.RadioSpeakerItem;
import com.example.beautifuldecor.item.ElevatorLinkBlockItem;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.item.AirlinerItem;
import com.example.beautifuldecor.item.TrainCarItem;
import com.example.beautifuldecor.item.PaSpeakerItem;
import com.example.beautifuldecor.item.HelicopterItem;
import com.example.beautifuldecor.item.VehicleItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BeautifulDecor.MOD_ID);

    // Block Items
    public static final RegistryObject<Item> WOODEN_TABLE = ITEMS.register("wooden_table",
            () -> new BlockItem(ModBlocks.WOODEN_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> WOODEN_CHAIR = ITEMS.register("wooden_chair",
            () -> new BlockItem(ModBlocks.WOODEN_CHAIR.get(), new Item.Properties()));

    public static final RegistryObject<Item> SOFA_RED = ITEMS.register("sofa_red",
            () -> new BlockItem(ModBlocks.SOFA_RED.get(), new Item.Properties()));

    public static final RegistryObject<Item> SOFA_BLUE = ITEMS.register("sofa_blue",
            () -> new BlockItem(ModBlocks.SOFA_BLUE.get(), new Item.Properties()));

    public static final RegistryObject<Item> CEILING_LIGHT = ITEMS.register("ceiling_light",
            () -> new BlockItem(ModBlocks.CEILING_LIGHT.get(), new Item.Properties()));

    public static final RegistryObject<Item> WALL_LIGHT = ITEMS.register("wall_light",
            () -> new BlockItem(ModBlocks.WALL_LIGHT.get(), new Item.Properties()));

    public static final RegistryObject<Item> FLOOR_LAMP = ITEMS.register("floor_lamp",
            () -> new BlockItem(ModBlocks.FLOOR_LAMP.get(), new Item.Properties()));

    public static final RegistryObject<Item> TELEVISION = ITEMS.register("television",
            () -> new BlockItem(ModBlocks.TELEVISION.get(), new Item.Properties()));

    public static final RegistryObject<Item> WII_CONSOLE = ITEMS.register("wii_console",
            () -> new BlockItem(ModBlocks.WII_CONSOLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> KITCHEN_COUNTER = ITEMS.register("kitchen_counter",
            () -> new BlockItem(ModBlocks.KITCHEN_COUNTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> SINK = ITEMS.register("sink",
            () -> new BlockItem(ModBlocks.SINK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SHOWER = ITEMS.register("shower",
            () -> new BlockItem(ModBlocks.SHOWER.get(), new Item.Properties()));
    public static final RegistryObject<Item> CEILING_SHOWER = ITEMS.register("ceiling_shower",
            () -> new BlockItem(ModBlocks.CEILING_SHOWER.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOILET_PAPER = ITEMS.register("toilet_paper",
            () -> new BlockItem(ModBlocks.TOILET_PAPER.get(), new Item.Properties()));

    // V3.0.0: cuadros de pared
    public static final RegistryObject<Item> PAINTING_VALLEY_DAWN = ITEMS.register("painting_valley_dawn",
            () -> new BlockItem(ModBlocks.PAINTING_VALLEY_DAWN.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAINTING_POLICE_CHASE = ITEMS.register("painting_police_chase",
            () -> new BlockItem(ModBlocks.PAINTING_POLICE_CHASE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAINTING_CIRCUIT_AERIAL = ITEMS.register("painting_circuit_aerial",
            () -> new BlockItem(ModBlocks.PAINTING_CIRCUIT_AERIAL.get(), new Item.Properties()));

    public static final RegistryObject<Item> PLANT_POT = ITEMS.register("plant_pot",
            () -> new BlockItem(ModBlocks.PLANT_POT.get(), new Item.Properties()));

    public static final RegistryObject<Item> DECORATIVE_VASE = ITEMS.register("decorative_vase",
            () -> new BlockItem(ModBlocks.DECORATIVE_VASE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BOOKSHELF_DECOR = ITEMS.register("bookshelf_decor",
            () -> new BlockItem(ModBlocks.BOOKSHELF_DECOR.get(), new Item.Properties()));


    public static final RegistryObject<Item> ROUND_BULB = ITEMS.register("round_bulb",
            () -> new BlockItem(ModBlocks.ROUND_BULB.get(), new Item.Properties()));

    public static final RegistryObject<Item> NIGHT_LAMP = ITEMS.register("night_lamp",
            () -> new BlockItem(ModBlocks.NIGHT_LAMP.get(), new Item.Properties()));
    public static final RegistryObject<Item> DESK_LAMP = ITEMS.register("desk_lamp",
            () -> new BlockItem(ModBlocks.DESK_LAMP.get(), new Item.Properties()));

    public static final RegistryObject<Item> NIGHTSTAND = ITEMS.register("nightstand",
            () -> new BlockItem(ModBlocks.NIGHTSTAND.get(), new Item.Properties()));

    public static final RegistryObject<Item> BED_WHITE = ITEMS.register("bed_white",
            () -> new BlockItem(ModBlocks.BED_WHITE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BED_BLUE = ITEMS.register("bed_blue",
            () -> new BlockItem(ModBlocks.BED_BLUE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BATHTUB = ITEMS.register("bathtub",
            () -> new BlockItem(ModBlocks.BATHTUB.get(), new Item.Properties()));

    public static final RegistryObject<Item> MODERN_CHAIR = ITEMS.register("modern_chair",
            () -> new BlockItem(ModBlocks.MODERN_CHAIR.get(), new Item.Properties()));


    public static final RegistryObject<Item> BED_PINK = ITEMS.register("bed_pink",
            () -> new BlockItem(ModBlocks.BED_PINK.get(), new Item.Properties()));
    public static final RegistryObject<Item> BED_GREEN = ITEMS.register("bed_green",
            () -> new BlockItem(ModBlocks.BED_GREEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> PICNIC_RUG = ITEMS.register("picnic_rug",
            () -> new BlockItem(ModBlocks.PICNIC_RUG.get(), new Item.Properties()));
    public static final RegistryObject<Item> DISHWASHER = ITEMS.register("dishwasher",
            () -> new BlockItem(ModBlocks.DISHWASHER.get(), new Item.Properties()));
    public static final RegistryObject<Item> WASHING_MACHINE = ITEMS.register("washing_machine",
            () -> new BlockItem(ModBlocks.WASHING_MACHINE.get(), new Item.Properties()));
    public static final RegistryObject<Item> MICROWAVE = ITEMS.register("microwave",
            () -> new BlockItem(ModBlocks.MICROWAVE.get(), new Item.Properties()));

    public static final RegistryObject<Item> STOVE = ITEMS.register("stove",
            () -> new BlockItem(ModBlocks.STOVE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PICNIC_BASKET = ITEMS.register("picnic_basket",
            () -> new BlockItem(ModBlocks.PICNIC_BASKET.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLASS_RED = ITEMS.register("glass_red",
            () -> new BlockItem(ModBlocks.GLASS_RED.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLASS_BLUE = ITEMS.register("glass_blue",
            () -> new BlockItem(ModBlocks.GLASS_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLASS_GREEN = ITEMS.register("glass_green",
            () -> new BlockItem(ModBlocks.GLASS_GREEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLASS_YELLOW = ITEMS.register("glass_yellow",
            () -> new BlockItem(ModBlocks.GLASS_YELLOW.get(), new Item.Properties()));


    public static final RegistryObject<Item> GLASS_SLIDING_DOOR = ITEMS.register("glass_sliding_door",
            () -> new BlockItem(ModBlocks.GLASS_SLIDING_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> WOODEN_DOOR_METAL = ITEMS.register("wooden_door_metal",
            () -> new BlockItem(ModBlocks.WOODEN_DOOR_METAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLASS_DOOR = ITEMS.register("glass_door",
            () -> new BlockItem(ModBlocks.GLASS_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_DOOR_BLACK = ITEMS.register("metal_door_black",
            () -> new BlockItem(ModBlocks.METAL_DOOR_BLACK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CIRCULAR_BULB = ITEMS.register("circular_bulb",
            () -> new BlockItem(ModBlocks.CIRCULAR_BULB.get(), new Item.Properties()));
    public static final RegistryObject<Item> CEILING_LIGHT_SQUARE = ITEMS.register("ceiling_light_square",
            () -> new BlockItem(ModBlocks.CEILING_LIGHT_SQUARE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CEILING_LIGHT_ROUND = ITEMS.register("ceiling_light_round",
            () -> new BlockItem(ModBlocks.CEILING_LIGHT_ROUND.get(), new Item.Properties()));
    public static final RegistryObject<Item> CEILING_BULB = ITEMS.register("ceiling_bulb",
            () -> new BlockItem(ModBlocks.CEILING_BULB.get(), new Item.Properties()));
    public static final RegistryObject<Item> OFFICE_LED_PANEL = ITEMS.register("office_led_panel",
            () -> new BlockItem(ModBlocks.OFFICE_LED_PANEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> WALL_SWITCH = ITEMS.register("wall_switch",
            () -> new BlockItem(ModBlocks.WALL_SWITCH.get(), new Item.Properties()));


    public static final RegistryObject<Item> TRAFFIC_LIGHT = ITEMS.register("traffic_light",
            () -> new BlockItem(ModBlocks.TRAFFIC_LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> STREET_LAMP = ITEMS.register("street_lamp",
            () -> new BlockItem(ModBlocks.STREET_LAMP.get(), new Item.Properties()));
    public static final RegistryObject<Item> DIVING_BOARD = ITEMS.register("diving_board",
            () -> new BlockItem(ModBlocks.DIVING_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_MESH = ITEMS.register("metal_mesh",
            () -> new BlockItem(ModBlocks.METAL_MESH.get(), new Item.Properties()));


    public static final RegistryObject<Item> EMPANADA_CART = ITEMS.register("empanada_cart",
            () -> new BlockItem(ModBlocks.EMPANADA_CART.get(), new Item.Properties()));

    public static final RegistryObject<Item> FLOWER_VASE = ITEMS.register("flower_vase",
            () -> new BlockItem(ModBlocks.FLOWER_VASE.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOILET = ITEMS.register("toilet",
            () -> new BlockItem(ModBlocks.TOILET.get(), new Item.Properties()));
    public static final RegistryObject<Item> CURTAIN_RED = ITEMS.register("curtain_red",
            () -> new BlockItem(ModBlocks.CURTAIN_RED.get(), new Item.Properties()));
    public static final RegistryObject<Item> CURTAIN_BLUE = ITEMS.register("curtain_blue",
            () -> new BlockItem(ModBlocks.CURTAIN_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CURTAIN_WHITE = ITEMS.register("curtain_white",
            () -> new BlockItem(ModBlocks.CURTAIN_WHITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CURTAIN_GREEN = ITEMS.register("curtain_green",
            () -> new BlockItem(ModBlocks.CURTAIN_GREEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> WINDOW_CLEAR = ITEMS.register("window_clear",
            () -> new BlockItem(ModBlocks.WINDOW_CLEAR.get(), new Item.Properties()));
    public static final RegistryObject<Item> WINDOW_BLUE = ITEMS.register("window_blue",
            () -> new BlockItem(ModBlocks.WINDOW_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WINDOW_TINTED = ITEMS.register("window_tinted",
            () -> new BlockItem(ModBlocks.WINDOW_TINTED.get(), new Item.Properties()));
    public static final RegistryObject<Item> WINDOW_GREEN = ITEMS.register("window_green",
            () -> new BlockItem(ModBlocks.WINDOW_GREEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> WINDOW_DARK = ITEMS.register("window_dark",
            () -> new BlockItem(ModBlocks.WINDOW_DARK.get(), new Item.Properties()));

    public static final RegistryObject<Item> REFINED_OAK_PLANKS = ITEMS.register("refined_oak_planks",
            () -> new BlockItem(ModBlocks.REFINED_OAK_PLANKS.get(), new Item.Properties()));
    public static final RegistryObject<Item> OAK_PARQUET = ITEMS.register("oak_parquet",
            () -> new BlockItem(ModBlocks.OAK_PARQUET.get(), new Item.Properties()));

    
    public static final RegistryObject<Item> MINI_FIESTA = ITEMS.register("mini_fiesta",
            () -> new BlockItem(ModBlocks.MINI_FIESTA.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOG_BED_PINK = ITEMS.register("dog_bed_pink",
            () -> new BlockItem(ModBlocks.DOG_BED_PINK.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOG_BED_BLUE = ITEMS.register("dog_bed_blue",
            () -> new BlockItem(ModBlocks.DOG_BED_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOG_BED_GRAY = ITEMS.register("dog_bed_gray",
            () -> new BlockItem(ModBlocks.DOG_BED_GRAY.get(), new Item.Properties()));

    
    public static final RegistryObject<Item> DOG_DOOR = ITEMS.register("dog_door",
            () -> new BlockItem(ModBlocks.DOG_DOOR.get(), new Item.Properties()));

        public static final RegistryObject<Item> CONCRETE_RAMP_WHITE = ITEMS.register("concrete_ramp_white",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_WHITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_ORANGE = ITEMS.register("concrete_ramp_orange",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_ORANGE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_MAGENTA = ITEMS.register("concrete_ramp_magenta",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_MAGENTA.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_LIGHT_BLUE = ITEMS.register("concrete_ramp_light_blue",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_LIGHT_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_YELLOW = ITEMS.register("concrete_ramp_yellow",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_YELLOW.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_LIME = ITEMS.register("concrete_ramp_lime",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_LIME.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_PINK = ITEMS.register("concrete_ramp_pink",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_PINK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_GRAY = ITEMS.register("concrete_ramp_gray",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_GRAY.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_LIGHT_GRAY = ITEMS.register("concrete_ramp_light_gray",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_LIGHT_GRAY.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_CYAN = ITEMS.register("concrete_ramp_cyan",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_CYAN.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_PURPLE = ITEMS.register("concrete_ramp_purple",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_PURPLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_BLUE = ITEMS.register("concrete_ramp_blue",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_BROWN = ITEMS.register("concrete_ramp_brown",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_BROWN.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_GREEN = ITEMS.register("concrete_ramp_green",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_GREEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_RED = ITEMS.register("concrete_ramp_red",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_RED.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RAMP_BLACK = ITEMS.register("concrete_ramp_black",
            () -> new BlockItem(ModBlocks.CONCRETE_RAMP_BLACK.get(), new Item.Properties()));

        public static final RegistryObject<Item> FENCE_METAL = ITEMS.register("fence_metal",
            () -> new BlockItem(ModBlocks.FENCE_METAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> FENCE_ALUMINUM = ITEMS.register("fence_aluminum",
            () -> new BlockItem(ModBlocks.FENCE_ALUMINUM.get(), new Item.Properties()));
    public static final RegistryObject<Item> FENCE_CARBON = ITEMS.register("fence_carbon",
            () -> new BlockItem(ModBlocks.FENCE_CARBON.get(), new Item.Properties()));
    public static final RegistryObject<Item> FENCE_STEEL = ITEMS.register("fence_steel",
            () -> new BlockItem(ModBlocks.FENCE_STEEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> FENCE_GOLD = ITEMS.register("fence_gold",
            () -> new BlockItem(ModBlocks.FENCE_GOLD.get(), new Item.Properties()));
    public static final RegistryObject<Item> SIGN_STOP = ITEMS.register("sign_stop",
            () -> new BlockItem(ModBlocks.SIGN_STOP.get(), new Item.Properties()));
    public static final RegistryObject<Item> SIGN_NO_PARKING = ITEMS.register("sign_no_parking",
            () -> new BlockItem(ModBlocks.SIGN_NO_PARKING.get(), new Item.Properties()));
    public static final RegistryObject<Item> SIGN_TURN_LEFT = ITEMS.register("sign_turn_left",
            () -> new BlockItem(ModBlocks.SIGN_TURN_LEFT.get(), new Item.Properties()));
    public static final RegistryObject<Item> SIGN_TURN_RIGHT = ITEMS.register("sign_turn_right",
            () -> new BlockItem(ModBlocks.SIGN_TURN_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> SIGN_PEDESTRIAN = ITEMS.register("sign_pedestrian",
            () -> new BlockItem(ModBlocks.SIGN_PEDESTRIAN.get(), new Item.Properties()));

    
    public static final RegistryObject<Item> PLATE_BURGER = ITEMS.register("plate_burger",
            () -> new BlockItem(ModBlocks.PLATE_BURGER.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLATE_HOTDOG = ITEMS.register("plate_hotdog",
            () -> new BlockItem(ModBlocks.PLATE_HOTDOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLATE_PIZZA = ITEMS.register("plate_pizza",
            () -> new BlockItem(ModBlocks.PLATE_PIZZA.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLATE_RICE = ITEMS.register("plate_rice",
            () -> new BlockItem(ModBlocks.PLATE_RICE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLATE_SUSHI = ITEMS.register("plate_sushi",
            () -> new BlockItem(ModBlocks.PLATE_SUSHI.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLATE_CHICKEN = ITEMS.register("plate_chicken",
            () -> new BlockItem(ModBlocks.PLATE_CHICKEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLATE_FRIES = ITEMS.register("plate_fries",
            () -> new BlockItem(ModBlocks.PLATE_FRIES.get(), new Item.Properties()));

    
    public static final RegistryObject<Item> PRINTER = ITEMS.register("printer",
            () -> new BlockItem(ModBlocks.PRINTER.get(), new Item.Properties()));

    public static final RegistryObject<Item> MINI_F150 = ITEMS.register("mini_f150",
            () -> new BlockItem(ModBlocks.MINI_F150.get(), new Item.Properties()));
    public static final RegistryObject<Item> MINI_MUSTANG = ITEMS.register("mini_mustang",
            () -> new BlockItem(ModBlocks.MINI_MUSTANG.get(), new Item.Properties()));
    public static final RegistryObject<Item> BARREL_GASOLINE = ITEMS.register("barrel_gasoline",
            () -> new BlockItem(ModBlocks.BARREL_GASOLINE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BARREL_WATER = ITEMS.register("barrel_water",
            () -> new BlockItem(ModBlocks.BARREL_WATER.get(), new Item.Properties()));
    public static final RegistryObject<Item> BARREL_DIESEL = ITEMS.register("barrel_diesel",
            () -> new BlockItem(ModBlocks.BARREL_DIESEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> BARREL_OIL = ITEMS.register("barrel_oil",
            () -> new BlockItem(ModBlocks.BARREL_OIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> PIPE_IRON = ITEMS.register("pipe_iron",
            () -> new BlockItem(ModBlocks.PIPE_IRON.get(), new Item.Properties()));
    public static final RegistryObject<Item> PIPE_STEEL = ITEMS.register("pipe_steel",
            () -> new BlockItem(ModBlocks.PIPE_STEEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> PIPE_ALUMINUM = ITEMS.register("pipe_aluminum",
            () -> new BlockItem(ModBlocks.PIPE_ALUMINUM.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONVEYOR_BELT = ITEMS.register("conveyor_belt",
            () -> new BlockItem(ModBlocks.CONVEYOR_BELT.get(), new Item.Properties()));
    public static final RegistryObject<Item> XBOX_ONE = ITEMS.register("xbox_one",
            () -> new BlockItem(ModBlocks.XBOX_ONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> XBOX_360 = ITEMS.register("xbox_360",
            () -> new BlockItem(ModBlocks.XBOX_360.get(), new Item.Properties()));
    public static final RegistryObject<Item> NINTENDO_SWITCH = ITEMS.register("nintendo_switch",
            () -> new BlockItem(ModBlocks.NINTENDO_SWITCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> KINECT_SENSOR = ITEMS.register("kinect_sensor",
            () -> new BlockItem(ModBlocks.KINECT_SENSOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> HS_LAPTOP = ITEMS.register("hs_laptop",
            () -> new BlockItem(ModBlocks.HS_LAPTOP.get(), new Item.Properties()));
    public static final RegistryObject<Item> HS_DESKTOP = ITEMS.register("hs_desktop",
            () -> new BlockItem(ModBlocks.HS_DESKTOP.get(), new Item.Properties()));
    public static final RegistryObject<Item> OFFICE_CHAIR = ITEMS.register("office_chair",
            () -> new BlockItem(ModBlocks.OFFICE_CHAIR.get(), new Item.Properties()));
    public static final RegistryObject<Item> BAR_STOOL = ITEMS.register("bar_stool",
            () -> new BlockItem(ModBlocks.BAR_STOOL.get(), new Item.Properties()));

    public static final RegistryObject<Item> DRONE_REMOTE = ITEMS.register("drone_remote",
            () -> new DroneRemoteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DRONE = ITEMS.register("drone",
            () -> new BlockItem(ModBlocks.DRONE.get(), new Item.Properties().stacksTo(16)));

    /** V3.1.0: carro RC y su control remoto (distinto al del dron). */
    public static final RegistryObject<Item> RC_CAR_REMOTE = ITEMS.register("rc_car_remote",
            () -> new RcCarRemoteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RC_CAR = ITEMS.register("rc_car",
            () -> new BlockItem(ModBlocks.RC_CAR.get(), new Item.Properties().stacksTo(16)));

    /** V2.0.0 */
    public static final RegistryObject<Item> MOTORCYCLE = ITEMS.register("motorcycle",
            () -> new VehicleItem(ModEntities.MOTORCYCLE::get, new Item.Properties().stacksTo(1)));
    /** V2.1.0: bicicleta y camioneta. */
    public static final RegistryObject<Item> BICYCLE = ITEMS.register("bicycle",
            () -> new VehicleItem(ModEntities.BICYCLE::get, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FORD_F150 = ITEMS.register("ford_f150",
            () -> new VehicleItem(ModEntities.FORD_F150::get, new Item.Properties().stacksTo(1)));
    /** V3.0.0: camión de reparto y helicóptero. */
    public static final RegistryObject<Item> DELIVERY_TRUCK = ITEMS.register("delivery_truck",
            () -> new VehicleItem(ModEntities.DELIVERY_TRUCK::get, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HELICOPTER = ITEMS.register("helicopter",
            () -> new HelicopterItem(ModEntities.HELICOPTER::get, new Item.Properties().stacksTo(1)));
    /** V3.7.0: superdeportivo Bugotti Chillón. */
    public static final RegistryObject<Item> BUGOTTI_CHILLON = ITEMS.register("bugotti_chillon",
            () -> new VehicleItem(ModEntities.BUGOTTI_CHILLON::get, new Item.Properties().stacksTo(1)));
    /** V3.8.0: avión BOING 7G7 Gigante. */
    // ---- V4.8.0: tren de carga
    public static final RegistryObject<Item> FREIGHT_RAIL = ITEMS.register("freight_rail",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.FREIGHT_RAIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> METRO_RAIL = ITEMS.register("metro_rail",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.METRO_RAIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> METRO_HEAD = ITEMS.register("metro_head",
            () -> new TrainCarItem(TrainCarEntity.Variant.METRO_HEAD, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> METRO_CAR = ITEMS.register("metro_car",
            () -> new TrainCarItem(TrainCarEntity.Variant.METRO_CAR, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PASSENGER_RAIL = ITEMS.register("passenger_rail",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.PASSENGER_RAIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEAM_CLASSIC = ITEMS.register("steam_classic",
            () -> new TrainCarItem(TrainCarEntity.Variant.STEAM_CLASSIC, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> STEAM_MODERN = ITEMS.register("steam_modern",
            () -> new TrainCarItem(TrainCarEntity.Variant.STEAM_MODERN, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COACH_LOW = ITEMS.register("coach_low",
            () -> new TrainCarItem(TrainCarEntity.Variant.COACH_LOW, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COACH_MID = ITEMS.register("coach_mid",
            () -> new TrainCarItem(TrainCarEntity.Variant.COACH_MID, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COACH_HIGH = ITEMS.register("coach_high",
            () -> new TrainCarItem(TrainCarEntity.Variant.COACH_HIGH, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FREIGHT_LOCO = ITEMS.register("freight_loco",
            () -> new TrainCarItem(TrainCarEntity.Variant.FREIGHT_LOCO, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COAL_WAGON = ITEMS.register("coal_wagon",
            () -> new TrainCarItem(TrainCarEntity.Variant.COAL_WAGON, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FOOD_WAGON = ITEMS.register("food_wagon",
            () -> new TrainCarItem(TrainCarEntity.Variant.FOOD_WAGON, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MINERAL_WAGON = ITEMS.register("mineral_wagon",
            () -> new TrainCarItem(TrainCarEntity.Variant.MINERAL_WAGON, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> AIRLINER = ITEMS.register("airliner",
            () -> new AirlinerItem(ModEntities.AIRLINER::get, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GARAGE_DOOR = ITEMS.register("garage_door",
            () -> new BlockItem(ModBlocks.GARAGE_DOOR.get(), new Item.Properties()));

    public static final Map<String, RegistryObject<Item>> NEON_BARS = new LinkedHashMap<>();

    static {
        for (var entry : ModBlocks.NEON_BARS.entrySet()) {
            NEON_BARS.put(entry.getKey(), ITEMS.register("neon_bar_" + entry.getKey(),
                    () -> new BlockItem(entry.getValue().get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<Item> MINI_FERRARI = ITEMS.register("mini_ferrari",
            () -> new BlockItem(ModBlocks.MINI_FERRARI.get(), new Item.Properties()));
    public static final RegistryObject<Item> MINI_GOLF = ITEMS.register("mini_golf",
            () -> new BlockItem(ModBlocks.MINI_GOLF.get(), new Item.Properties()));
    public static final RegistryObject<Item> MINI_MUSTANG_CLASSIC = ITEMS.register("mini_mustang_classic",
            () -> new BlockItem(ModBlocks.MINI_MUSTANG_CLASSIC.get(), new Item.Properties()));

    public static final RegistryObject<Item> XBOX_SERIES_S = ITEMS.register("xbox_series_s",
            () -> new BlockItem(ModBlocks.XBOX_SERIES_S.get(), new Item.Properties()));

    /** Solo sirve como ícono de la pestaña creativa; no se muestra en ningún inventario. */
    public static final RegistryObject<Item> TAB_ICON = ITEMS.register("tab_icon",
            () -> new Item(new Item.Properties()));

    public static final Map<String, RegistryObject<Item>> SLIDES = new LinkedHashMap<>();

    static {
        for (var entry : ModBlocks.SLIDES.entrySet()) {
            SLIDES.put(entry.getKey(), ITEMS.register("slide_" + entry.getKey(),
                    () -> new BlockItem(entry.getValue().get(), new Item.Properties())));
        }
    }

    // ===== Ítems de los muebles por madera, ventiladores, televisores y lámpara articulada =====
    public static final Map<String, RegistryObject<Item>> EXTRA_ITEMS = new LinkedHashMap<>();

    private static void registerGroup(Map<String, RegistryObject<Block>> group) {
        for (var entry : group.entrySet()) {
            EXTRA_ITEMS.put(entry.getKey(), ITEMS.register(entry.getKey(),
                    () -> new BlockItem(entry.getValue().get(), new Item.Properties())));
        }
    }

    static {
        registerGroup(ModBlocks.CHAIRS);
        registerGroup(ModBlocks.SOFAS);
        registerGroup(ModBlocks.NIGHTSTANDS);
        registerGroup(ModBlocks.PEDESTAL_TABLES);
        registerGroup(ModBlocks.TABLES);
        registerGroup(ModBlocks.DESKS);
        registerGroup(ModBlocks.SHELF_TABLES);
        registerGroup(ModBlocks.FANS);
        registerGroup(ModBlocks.PEDESTAL_FANS);
        registerGroup(ModBlocks.HS_DESKTOPS);
        registerGroup(ModBlocks.TVS);
        registerGroup(ModBlocks.AD_SCREENS);
        registerGroup(ModBlocks.CEILING_FANS);
        registerGroup(ModBlocks.DOUBLE_BEDS);
        registerGroup(ModBlocks.SHELVES);
        registerGroup(ModBlocks.CARPETS);
        registerGroup(ModBlocks.DOORBELLS);
        registerGroup(ModBlocks.KITCHENWARE);
        registerGroup(ModBlocks.PIANOS);
        registerGroup(ModBlocks.KITCHEN_APPLIANCES);
        registerGroup(ModBlocks.CUTLERY);
        registerGroup(ModBlocks.STAIRS);
        registerGroup(ModBlocks.WASHBASINS);
        registerGroup(ModBlocks.CRATES);
        registerGroup(ModBlocks.KITCHEN_DRAWERS);
        registerGroup(ModBlocks.BUGERATI);
    }

    /** V1.8.0: cámara frigorífica (estructura 3x3x3 de varias piezas). */
    public static final RegistryObject<Item> COLD_ROOM = ITEMS.register("cold_room",
            () -> new BlockItem(ModBlocks.COLD_ROOM.get(), new Item.Properties()));

    public static final RegistryObject<Item> SIGN_NO_TRUCKS = ITEMS.register("sign_no_trucks",
            () -> new BlockItem(ModBlocks.SIGN_NO_TRUCKS.get(), new Item.Properties()));

    /** V2.1.0: caja de herramientas abierta. */
    // ===== V2.2.0: ascensores =====
    /** V4.0.0: megafonía. */
    public static final RegistryObject<Item> MICROPHONE = ITEMS.register("microphone",
            () -> new BlockItem(ModBlocks.MICROPHONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PA_SPEAKER = ITEMS.register("pa_speaker",
            () -> new PaSpeakerItem(ModBlocks.PA_SPEAKER.get(), new Item.Properties()));

    /** V4.2.0: radio de mesa y su bafle. */
    public static final RegistryObject<Item> RADIO_TABLE = ITEMS.register("radio_table",
            () -> new BlockItem(ModBlocks.RADIO_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> RADIO_SPEAKER = ITEMS.register("radio_speaker",
            () -> new RadioSpeakerItem(ModBlocks.RADIO_SPEAKER.get(), new Item.Properties()));

    /** V4.3.0: cámaras de seguridad, monitor y pantalla de pared. */
    public static final RegistryObject<Item> SECURITY_CAMERA_BLACK = ITEMS.register("security_camera_black",
            () -> new BlockItem(ModBlocks.SECURITY_CAMERA_BLACK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SECURITY_CAMERA_WHITE = ITEMS.register("security_camera_white",
            () -> new BlockItem(ModBlocks.SECURITY_CAMERA_WHITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SECURITY_MONITOR = ITEMS.register("security_monitor",
            () -> new BlockItem(ModBlocks.SECURITY_MONITOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> SECURITY_SCREEN = ITEMS.register("security_screen",
            () -> new BlockItem(ModBlocks.SECURITY_SCREEN.get(), new Item.Properties()));

    public static final RegistryObject<Item> ELEVATOR_CONTROLLER = ITEMS.register("elevator_controller",
            () -> new BlockItem(ModBlocks.ELEVATOR_CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<Item> ELEVATOR_DISPLAY = ITEMS.register("elevator_display",
            () -> new ElevatorLinkBlockItem(ModBlocks.ELEVATOR_DISPLAY.get(), new Item.Properties()));
    public static final RegistryObject<Item> ELEVATOR_DOOR = ITEMS.register("elevator_door",
            () -> new ElevatorLinkBlockItem(ModBlocks.ELEVATOR_DOOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> TOOLBOX_OPEN = ITEMS.register("toolbox_open",
            () -> new BlockItem(ModBlocks.TOOLBOX_OPEN.get(), new Item.Properties()));

    public static final RegistryObject<Item> DESK_LAMP_FLEXO = ITEMS.register("desk_lamp_flexo",
            () -> new BlockItem(ModBlocks.DESK_LAMP_FLEXO.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
