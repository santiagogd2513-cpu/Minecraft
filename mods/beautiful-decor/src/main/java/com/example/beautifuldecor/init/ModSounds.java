package com.example.beautifuldecor.init;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BeautifulDecor.MOD_ID);

    public static final RegistryObject<SoundEvent> PRINTER = SOUND_EVENTS.register("printer",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "printer")));

    public static final RegistryObject<SoundEvent> DRONE_STARTUP = SOUND_EVENTS.register("drone_startup",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "drone_startup")));

    public static final RegistryObject<SoundEvent> DRONE_MOTOR = SOUND_EVENTS.register("drone_motor",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "drone_motor")));

    // ===== v2.0.0: moto y puerta de garaje =====
    public static final RegistryObject<SoundEvent> MOTORCYCLE_ENGINE = simple("motorcycle_engine");
    public static final RegistryObject<SoundEvent> MOTORCYCLE_HORN = simple("motorcycle_horn");
    public static final RegistryObject<SoundEvent> MOTORCYCLE_START = simple("motorcycle_start");
    public static final RegistryObject<SoundEvent> GARAGE_DOOR = simple("garage_door");
    /** V7.8.0: el tin del semáforo del cruce. */
    public static final RegistryObject<SoundEvent> CROSSING_BELL = simple("crossing_bell");

    // ===== v2.1.0: bicicleta y camioneta =====
    public static final RegistryObject<SoundEvent> BICYCLE_BELL = simple("bicycle_bell");
    public static final RegistryObject<SoundEvent> PICKUP_ENGINE = simple("pickup_engine");
    public static final RegistryObject<SoundEvent> PICKUP_HORN = simple("pickup_horn");
    public static final RegistryObject<SoundEvent> PICKUP_START = simple("pickup_start");

    // ===== v3.0.0: camión de reparto y helicóptero =====
    public static final RegistryObject<SoundEvent> DELIVERY_TRUCK_ENGINE = simple("delivery_truck_engine");
    public static final RegistryObject<SoundEvent> DELIVERY_TRUCK_HORN = simple("delivery_truck_horn");
    public static final RegistryObject<SoundEvent> DELIVERY_TRUCK_START = simple("delivery_truck_start");
    public static final RegistryObject<SoundEvent> HELICOPTER_ROTOR = simple("helicopter_rotor");
    public static final RegistryObject<SoundEvent> HELICOPTER_START = simple("helicopter_start");

    // ===== v3.7.0: superdeportivo Bugotti Chillón =====
    public static final RegistryObject<SoundEvent> BUGOTTI_CHILLON_ENGINE = simple("bugotti_chillon_engine");
    public static final RegistryObject<SoundEvent> BUGOTTI_CHILLON_HORN = simple("bugotti_chillon_horn");
    public static final RegistryObject<SoundEvent> BUGOTTI_CHILLON_START = simple("bugotti_chillon_start");

    // ===== v3.8.0: avión BOING 7G7 Gigante =====
    public static final RegistryObject<SoundEvent> AIRLINER_ENGINE = simple("airliner_engine");
    public static final RegistryObject<SoundEvent> AIRLINER_START = simple("airliner_start");

    // ===== v3.1.0: carro RC =====
    public static final RegistryObject<SoundEvent> RC_CAR_MOTOR = simple("rc_car_motor");
    public static final RegistryObject<SoundEvent> RC_CAR_START = simple("rc_car_start");

    // ===== v2.2.0: ascensores =====
    public static final RegistryObject<SoundEvent> ELEVATOR_HUM = simple("elevator_hum");
    public static final RegistryObject<SoundEvent> ELEVATOR_DING = simple("elevator_ding");
    public static final RegistryObject<SoundEvent> ELEVATOR_DOOR = simple("elevator_door");

    // ===== v1.4.0: timbre y piano =====
    public static final RegistryObject<SoundEvent> DOORBELL = simple("doorbell");

    /** Notas del piano en orden: do, re, mi, fa, sol, la, si. */
    public static final List<RegistryObject<SoundEvent>> PIANO_NOTES = List.of(
            simple("piano_do"), simple("piano_re"), simple("piano_mi"), simple("piano_fa"),
            simple("piano_sol"), simple("piano_la"), simple("piano_si"));

    // ===== V6.2.0: sonidos de los trenes y avisos de estación =====
    public static final RegistryObject<SoundEvent> TRAIN_ROLL_FREIGHT = simple("train_roll_freight");
    public static final RegistryObject<SoundEvent> TRAIN_ROLL_PASSENGER = simple("train_roll_passenger");
    public static final RegistryObject<SoundEvent> TRAIN_ROLL_STEAM = simple("train_roll_steam");
    public static final RegistryObject<SoundEvent> TRAIN_ROLL_METRO = simple("train_roll_metro");
    public static final RegistryObject<SoundEvent> TRAIN_BRAKE_SQUEAL = simple("train_brake_squeal");
    public static final RegistryObject<SoundEvent> TRAIN_BRAKE_METRO = simple("train_brake_metro");
    public static final RegistryObject<SoundEvent> TRAIN_EMERGENCY = simple("train_emergency");
    public static final RegistryObject<SoundEvent> TRAIN_CHIME = simple("train_chime");
    public static final RegistryObject<SoundEvent> VEHICLE_SIREN = simple("vehicle_siren");
    public static final RegistryObject<SoundEvent> TASER_ZAP = simple("taser_zap");
    public static final RegistryObject<SoundEvent> TRAIN_SAY_NEXT = simple("train_say_next");
    public static final RegistryObject<SoundEvent> TRAIN_SAY_ARRIVED = simple("train_say_arrived");
    public static final RegistryObject<SoundEvent> TRAIN_SAY_DEPARTING = simple("train_say_departing");
    public static final RegistryObject<SoundEvent> TRAIN_SAY_FINAL = simple("train_say_final");
    public static final RegistryObject<SoundEvent> TRAIN_SAY_DESTINATION = simple("train_say_destination");

    // ===== V7.2.0: Spotifly =====
    public static final RegistryObject<SoundEvent> SPOTIFLY_SONG_1 = simple("spotifly_song_1");
    public static final RegistryObject<SoundEvent> SPOTIFLY_SONG_2 = simple("spotifly_song_2");
    public static final RegistryObject<SoundEvent> SPOTIFLY_SONG_3 = simple("spotifly_song_3");

    /** Canción 1 a 3 de Spotifly. */
    public static SoundEvent song(int n) {
        return (n == 1 ? SPOTIFLY_SONG_1 : n == 2 ? SPOTIFLY_SONG_2 : SPOTIFLY_SONG_3).get();
    }

    private static RegistryObject<SoundEvent> simple(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
