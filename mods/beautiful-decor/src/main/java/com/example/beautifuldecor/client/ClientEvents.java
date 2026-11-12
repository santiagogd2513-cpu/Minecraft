package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.GarageColors;
import com.example.beautifuldecor.block.GarageDoorBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.init.ModBlocks;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.SEAT.get(), NoopRenderer::new);
            EntityRenderers.register(ModEntities.DRONE.get(), DroneRenderer::new);
            EntityRenderers.register(ModEntities.RC_CAR.get(), RcCarRenderer::new);
            EntityRenderers.register(ModEntities.MOTORCYCLE.get(), MotorcycleRenderer::new);
            EntityRenderers.register(ModEntities.BICYCLE.get(), BicycleRenderer::new);
            EntityRenderers.register(ModEntities.FORD_F150.get(), PickupRenderer::new);
            EntityRenderers.register(ModEntities.DELIVERY_TRUCK.get(), DeliveryTruckRenderer::new);
            EntityRenderers.register(ModEntities.BUGOTTI_CHILLON.get(), SupercarRenderer::new);
            EntityRenderers.register(ModEntities.HELICOPTER.get(), HelicopterRenderer::new);
            EntityRenderers.register(ModEntities.AIRLINER.get(), AirlinerRenderer::new);
            EntityRenderers.register(ModEntities.TRAIN_CAR.get(), TrainCarRenderer::new);          // V4.8.0
            EntityRenderers.register(ModEntities.ELEVATOR_CABIN.get(), ElevatorCabinRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MINI_FERRARI.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MINI_GOLF.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MINI_MUSTANG_CLASSIC.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.GARAGE_DOOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RC_CAR.get(), RenderType.cutout());
        });
    }

    @SubscribeEvent
    public static void registerDroneLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER, DroneModel::createBodyLayer);
        event.registerLayerDefinition(RcCarModel.LAYER, RcCarModel::createBodyLayer);
        event.registerLayerDefinition(MotorcycleModel.LAYER, MotorcycleModel::createBodyLayer);
        event.registerLayerDefinition(BicycleModel.LAYER, BicycleModel::createBodyLayer);
        event.registerLayerDefinition(PickupModel.LAYER, PickupModel::createBodyLayer);
        event.registerLayerDefinition(DeliveryTruckModel.LAYER, DeliveryTruckModel::createBodyLayer);
        event.registerLayerDefinition(SupercarModel.LAYER, SupercarModel::createBodyLayer);
        event.registerLayerDefinition(HelicopterModel.LAYER, HelicopterModel::createBodyLayer);
        event.registerLayerDefinition(AirlinerModel.LAYER, AirlinerModel::createBodyLayer);
        for (com.example.beautifuldecor.entity.TrainCarEntity.Variant tv : com.example.beautifuldecor.entity.TrainCarEntity.Variant.values()) {
            event.registerLayerDefinition(TrainCarModel.layer(tv.id), () -> TrainCarModel.create(tv.id));          // V4.8.0 / V5.0.0
        }
        event.registerLayerDefinition(SecurityCameraModel.LAYER, SecurityCameraModel::createLayer);       // V4.3.0
    }

    /** V2.11.0: el video de los televisores se dibuja sobre su pantalla. */
    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TELEVISION.get(), TelevisionRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.AD_SCREEN.get(), AdScreenRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SECURITY_CAMERA.get(), SecurityCameraRenderer::new);     // V4.3.0
        event.registerBlockEntityRenderer(ModBlockEntities.SECURITY_MONITOR.get(), SecurityMonitorRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(VehicleKeys.LIGHT);
        event.register(VehicleKeys.HORN);
        event.register(VehicleKeys.RADIO);
        event.register(GarageKeys.COLORS);
        event.register(HelicopterKeys.EXIT);
        event.register(AirlinerKeys.GEAR);
    }

    /** V3.2.0: al recargar recursos (F3+T, cambiar paquete de texturas) se sueltan las texturas repintadas de los vehículos. */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> VehiclePaintTextures.reset());
    }

    /** V2.3.0: tinte de la puerta de garaje (tintindex 0 puerta, 1 franja, 2 marco, 3 cara interior). */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            int[] colors = level != null && pos != null ? GarageDoorBlock.colorsAt(level, pos, state)
                    : GarageColors.defaults();
            return GarageColors.tint(tintIndex, colors);
        }, ModBlocks.GARAGE_DOOR.get());
    }
}
