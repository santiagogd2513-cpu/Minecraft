package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.GarageDoorBlock;
import com.example.beautifuldecor.block.HsComputers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tecla R apuntando a un bloque: en una puerta de garaje abre el menú de colores (V2.3.0) y en un computador HS encendido
 * abre el navegador (V2.4.0). Apuntando a un vehículo (V3.2.0) abre el menú para repintarlo. También mantiene el audio del navegador atado al computador y lo calla al salir del mundo.
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class GarageClientEvents {
    private GarageClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        HsAudioPlayer.get().tick(minecraft);
        TvPlayback.tick(minecraft);
        AdPlayback.tick(minecraft);                         // V4.1.0: pantallas de anuncios
        VehicleRadioClient.tick(minecraft);                 // V3.5.0: las radios de los vehículos cercanos
        while (VehicleKeys.RADIO.consumeClick()) {
            VehicleRadioScreen.tryOpen(minecraft);          // V3.5.0: X yendo montado en un vehículo terrestre
        }
        while (GarageKeys.COLORS.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
                continue;
            }
            HitResult hit = minecraft.hitResult;
            // V3.2.0: R mirando un vehículo (o yendo montado en él) abre su menú de colores, salvo que la puerta de garaje
            // o el computador que apuntas estén más cerca que el vehículo.
            double blockDistance = Double.POSITIVE_INFINITY;
            if (hit instanceof BlockHitResult near && hit.getType() == HitResult.Type.BLOCK) {
                BlockState nearState = minecraft.level.getBlockState(near.getBlockPos());
                if (nearState.getBlock() instanceof GarageDoorBlock || HsComputers.isHsComputer(nearState)) {
                    blockDistance = minecraft.player.getEyePosition(1.0f).distanceTo(near.getLocation());
                }
            }
            if (VehicleColorScreen.tryOpen(minecraft, blockDistance)) {
                continue;
            }
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
                if (state.getBlock() instanceof GarageDoorBlock) {
                    BlockPos origin = GarageDoorBlock.originOf(blockHit.getBlockPos(), state);
                    minecraft.setScreen(new GarageColorScreen(origin,
                            GarageDoorBlock.colorsAt(minecraft.level, blockHit.getBlockPos(), state)));
                } else if (HsComputers.isHsComputer(state)) {
                    if (HsComputers.isOn(state)) {
                        HsBrowserScreen.open(minecraft, blockHit.getBlockPos());
                    } else {
                        minecraft.player.displayClientMessage(Component.translatable("message.beautifuldecor.browser.off"), true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HsBrowserScreen.reset();
        TvPlayback.reset();
        AdPlayback.reset();
        VehiclePaintTextures.reset();
        VehicleRadioClient.reset();
    }
}
