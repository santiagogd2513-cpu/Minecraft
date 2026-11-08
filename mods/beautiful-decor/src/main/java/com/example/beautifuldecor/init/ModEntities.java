package com.example.beautifuldecor.init;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.BicycleEntity;
import com.example.beautifuldecor.entity.DeliveryTruckEntity;
import com.example.beautifuldecor.entity.DroneEntity;
import com.example.beautifuldecor.entity.ElevatorCabinEntity;
import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.entity.HelicopterEntity;
import com.example.beautifuldecor.entity.MotorcycleEntity;
import com.example.beautifuldecor.entity.PickupEntity;
import com.example.beautifuldecor.entity.SupercarEntity;
import com.example.beautifuldecor.entity.RcCarEntity;
import com.example.beautifuldecor.entity.SeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BeautifulDecor.MOD_ID);

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.0f, 0.0f)
                    .clientTrackingRange(10)
                    .build("beautifuldecor:seat"));

    public static final RegistryObject<EntityType<DroneEntity>> DRONE = ENTITIES.register("drone",
            () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.5f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("beautifuldecor:drone"));

    /** V2.0.0: moto de carreras. Se ve desde lejos (16 chunks) y se actualiza cada tick porque va muy rápido. */
    public static final RegistryObject<EntityType<MotorcycleEntity>> MOTORCYCLE = ENTITIES.register("motorcycle",
            () -> EntityType.Builder.<MotorcycleEntity>of(MotorcycleEntity::new, MobCategory.MISC)
                    .sized(1.1f, 1.1f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:motorcycle"));

    /** V2.1.0: bicicleta (40 bloques por segundo). */
    public static final RegistryObject<EntityType<BicycleEntity>> BICYCLE = ENTITIES.register("bicycle",
            () -> EntityType.Builder.<BicycleEntity>of(BicycleEntity::new, MobCategory.MISC)
                    .sized(0.8f, 1.2f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:bicycle"));

    /** V2.1.0: camioneta Furd F-150 (80 bloques por segundo, 2 asientos). La caja no gira: 2,2 x 1,9. */
    public static final RegistryObject<EntityType<PickupEntity>> FORD_F150 = ENTITIES.register("ford_f150",
            () -> EntityType.Builder.<PickupEntity>of(PickupEntity::new, MobCategory.MISC)
                    .sized(2.2f, 1.9f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:ford_f150"));

    /** V2.2.0: cabina del ascensor en viaje. Casi no manda actualizaciones: cliente y servidor calculan el mismo movimiento. */
    public static final RegistryObject<EntityType<ElevatorCabinEntity>> ELEVATOR_CABIN = ENTITIES.register("elevator_cabin",
            () -> EntityType.Builder.<ElevatorCabinEntity>of(ElevatorCabinEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .build("beautifuldecor:elevator_cabin"));

    /** V3.0.0: camión de reparto AMAZOOM (48 bloques por segundo, 2 asientos). La caja no gira: 2,4 x 2,8. */
    public static final RegistryObject<EntityType<DeliveryTruckEntity>> DELIVERY_TRUCK = ENTITIES.register("delivery_truck",
            () -> EntityType.Builder.<DeliveryTruckEntity>of(DeliveryTruckEntity::new, MobCategory.MISC)
                    .sized(2.4f, 2.8f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:delivery_truck"));

    /** V3.7.0: superdeportivo Bugotti Chillón (250 bloques por segundo, 2 asientos). La caja no gira: 2,1 x 1,3. */
    public static final RegistryObject<EntityType<SupercarEntity>> BUGOTTI_CHILLON = ENTITIES.register("bugotti_chillon",
            () -> EntityType.Builder.<SupercarEntity>of(SupercarEntity::new, MobCategory.MISC)
                    .sized(2.1f, 1.3f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:bugotti_chillon"));

    /** V3.0.0: helicóptero BOING HX-6 (28 bloques por segundo, 4 asientos). La caja no gira: 2,4 x 2,9. */
    public static final RegistryObject<EntityType<HelicopterEntity>> HELICOPTER = ENTITIES.register("helicopter",
            () -> EntityType.Builder.<HelicopterEntity>of(HelicopterEntity::new, MobCategory.MISC)
                    .sized(2.4f, 2.9f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:helicopter"));

    /** V3.8.0: avión BOING 7G7 Gigante (130 plazas, 100 bloques por segundo). La caja no gira ni cubre las alas: 7 x 7,5. */
    public static final RegistryObject<EntityType<AirlinerEntity>> AIRLINER = ENTITIES.register("airliner",
            () -> EntityType.Builder.<AirlinerEntity>of(AirlinerEntity::new, MobCategory.MISC)
                    .sized(7.0f, 7.5f)
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:airliner"));

    /** V3.1.0: carro RC Lamborgonzo Huracanito (1 bloque de largo). Cliente y servidor se actualizan cada tick. */
    public static final RegistryObject<EntityType<RcCarEntity>> RC_CAR = ENTITIES.register("rc_car",
            () -> EntityType.Builder.<RcCarEntity>of(RcCarEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.3f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .fireImmune()
                    .build("beautifuldecor:rc_car"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
