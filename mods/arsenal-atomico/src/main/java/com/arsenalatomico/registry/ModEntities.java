package com.arsenalatomico.registry;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.entity.MissileEntity;
import com.arsenalatomico.entity.TrainCarEntity;
import com.arsenalatomico.entity.TruckEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ArsenalAtomico.MODID);

    public static final RegistryObject<EntityType<MissileEntity>> MISSILE = ENTITIES.register("missile",
            () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(20)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(ArsenalAtomico.MODID + ":missile"));

    public static final RegistryObject<EntityType<TruckEntity>> TRUCK = ENTITIES.register("military_truck",
            () -> EntityType.Builder.<TruckEntity>of(TruckEntity::new, MobCategory.MISC)
                    .sized(3.0f, 3.0f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build(ArsenalAtomico.MODID + ":military_truck"));

    public static final RegistryObject<EntityType<TrainCarEntity>> TRAIN_CAR = ENTITIES.register("train_car",
            () -> EntityType.Builder.<TrainCarEntity>of(TrainCarEntity::new, MobCategory.MISC)
                    .sized(3.2f, 3.6f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build(ArsenalAtomico.MODID + ":train_car"));

    private ModEntities() {}
}
