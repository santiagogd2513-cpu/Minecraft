package com.example.beautifuldecor.client;

import com.example.beautifuldecor.elevator.ElevatorZone;
import com.example.beautifuldecor.network.ElevatorControllerPacket;
import com.example.beautifuldecor.network.ElevatorDisplayPacket;
import com.example.beautifuldecor.network.StationOpenPacket;
import com.example.beautifuldecor.network.TrainMenuOpenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ClientHooks {
    /** V4.0.0: abre el menú del micrófono de megafonía. */
    public static void openMicrophoneScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new com.example.beautifuldecor.megaphone.client.MicrophoneScreen(pos));
    }

    /** V4.2.0: abre el menú de la radio de mesa. */
    public static void openRadioScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new VehicleRadioScreen(new RadioBlockClient.TableSource(pos)));
    }

    /** V4.3.0: menú del monitor de cámaras (la lista) y menú de una cámara (nombre, descripción y rotación). */
    public static void openCameraListScreen(BlockPos monitor) {
        Minecraft.getInstance().setScreen(new CameraListScreen(monitor));
    }

    public static void openCameraEditScreen(BlockPos camera) {
        Minecraft.getInstance().setScreen(new CameraEditScreen(camera));
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

    /** V6.0.0: menú del tren vinculado al mando. */
    public static void openTrainMenu(TrainMenuOpenPacket packet) {
        Minecraft.getInstance().setScreen(new TrainRemoteScreen(packet));
    }

    /** V6.0.0: ajustes de una estación. */
    public static void openStation(StationOpenPacket packet) {
        Minecraft.getInstance().setScreen(new StationScreen(packet));
    }
}
