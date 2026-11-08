package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.HasVehicleStorage;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.OpenVehicleStoragePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * V3.9.0: yendo montado en un vehículo con almacenamiento, la tecla del inventario abre el almacenamiento en vez del inventario.
 * Se intercepta la apertura de la pantalla del inventario (da igual qué tecla tenga puesta el jugador para abrirlo).
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class VehicleStorageClientEvents {
    private VehicleStorageClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Screen next = event.getNewScreen();
        boolean inventory = next instanceof InventoryScreen || next instanceof CreativeModeInventoryScreen;
        if (inventory && minecraft.player.getVehicle() instanceof HasVehicleStorage vehicle && vehicle.hasStorage()) {
            event.setCanceled(true);
            ModMessages.CHANNEL.sendToServer(new OpenVehicleStoragePacket());
        }
    }
}
