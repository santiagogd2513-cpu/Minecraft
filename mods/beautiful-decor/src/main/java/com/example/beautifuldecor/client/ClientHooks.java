package com.example.beautifuldecor.client;

import com.example.beautifuldecor.elevator.ElevatorZone;
import com.example.beautifuldecor.network.ElevatorControllerPacket;
import com.example.beautifuldecor.network.ElevatorDisplayPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ClientHooks {
    /** V4.0.0: abre el menú del micrófono de megafonía. */
    public static void openMicrophoneScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new com.example.beautifuldecor.megaphone.client.MicrophoneScreen(pos));
    }

    public static void openTelevisionScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TelevisionScreen(pos));
    }

    /** V2.2.0: marca la zona del ascensor y, si el servidor lo pide, abre el menú del controlador. */
    public static void openElevatorController(ElevatorControllerPacket packet) {
        ElevatorOverlay.show(ElevatorZone.of(packet.pos, packet.facing, packet.width, packet.depth, packet.height,
                packet.offX, packet.offY, packet.offZ));
        if (packet.openMenu) {
            Minecraft.getInstance().setScreen(new ElevatorControllerScreen(packet));
        }
    }

    /** V2.2.0: abre la lista de pisos de la pantalla de ascensor. */
    public static void openElevatorDisplay(ElevatorDisplayPacket packet) {
        Minecraft.getInstance().setScreen(new ElevatorDisplayScreen(packet));
    }
}
