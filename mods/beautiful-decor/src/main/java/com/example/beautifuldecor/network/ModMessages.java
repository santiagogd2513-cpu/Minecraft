package com.example.beautifuldecor.network;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static final String PROTOCOL = "21";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(TelevisionPathPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TelevisionPathPacket::new)
                .encoder(TelevisionPathPacket::encode)
                .consumerMainThread(TelevisionPathPacket::handle)
                .add();
        CHANNEL.messageBuilder(OfficeChairTurnPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(OfficeChairTurnPacket::decode)
                .encoder(OfficeChairTurnPacket::encode)
                .consumerMainThread(OfficeChairTurnPacket::handle)
                .add();
        CHANNEL.messageBuilder(DroneControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(DroneControlPacket::decode)
                .encoder(DroneControlPacket::encode)
                .consumerMainThread(DroneControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(VehicleActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(VehicleActionPacket::decode)
                .encoder(VehicleActionPacket::encode)
                .consumerMainThread(VehicleActionPacket::handle)
                .add();
        // ---- V2.2.0: ascensores
        CHANNEL.messageBuilder(ElevatorControllerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ElevatorControllerPacket::decode)
                .encoder(ElevatorControllerPacket::encode)
                .consumerMainThread(ElevatorControllerPacket::handle)
                .add();
        CHANNEL.messageBuilder(ElevatorDisplayPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ElevatorDisplayPacket::decode)
                .encoder(ElevatorDisplayPacket::encode)
                .consumerMainThread(ElevatorDisplayPacket::handle)
                .add();
        CHANNEL.messageBuilder(ElevatorSavePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ElevatorSavePacket::decode)
                .encoder(ElevatorSavePacket::encode)
                .consumerMainThread(ElevatorSavePacket::handle)
                .add();
        CHANNEL.messageBuilder(ElevatorCallPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ElevatorCallPacket::decode)
                .encoder(ElevatorCallPacket::encode)
                .consumerMainThread(ElevatorCallPacket::handle)
                .add();
        // ---- V2.3.0: colores de la puerta de garaje
        CHANNEL.messageBuilder(GarageColorPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(GarageColorPacket::decode)
                .encoder(GarageColorPacket::encode)
                .consumerMainThread(GarageColorPacket::handle)
                .add();
        // ---- V3.0.0: bajarse del helicóptero con Z
        CHANNEL.messageBuilder(HelicopterActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(HelicopterActionPacket::decode)
                .encoder(HelicopterActionPacket::encode)
                .consumerMainThread(HelicopterActionPacket::handle)
                .add();
        // ---- V3.8.0: bajarse del avión con Z y recoger o bajar el tren de aterrizaje con F
        CHANNEL.messageBuilder(AirlinerActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(AirlinerActionPacket::decode)
                .encoder(AirlinerActionPacket::encode)
                .consumerMainThread(AirlinerActionPacket::handle)
                .add();
        // ---- V4.0.0: megafonía
        CHANNEL.messageBuilder(MicControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(MicControlPacket::decode)
                .encoder(MicControlPacket::encode)
                .consumerMainThread(MicControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(MicAudioPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(MicAudioPacket::decode)
                .encoder(MicAudioPacket::encode)
                .consumerMainThread(MicAudioPacket::handle)
                .add();
        CHANNEL.messageBuilder(MicStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(MicStatePacket::decode)
                .encoder(MicStatePacket::encode)
                .consumerMainThread(MicStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(SpeakerAudioPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SpeakerAudioPacket::decode)
                .encoder(SpeakerAudioPacket::encode)
                .consumerMainThread(SpeakerAudioPacket::handle)
                .add();
        // ---- V3.9.0: la tecla del inventario, yendo montado en un vehículo con almacenamiento, abre el almacenamiento
        CHANNEL.messageBuilder(OpenVehicleStoragePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(OpenVehicleStoragePacket::decode)
                .encoder(OpenVehicleStoragePacket::encode)
                .consumerMainThread(OpenVehicleStoragePacket::handle)
                .add();
        // ---- V3.1.0: el piloto del carro RC manda su posición cada tick
        CHANNEL.messageBuilder(RcCarControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RcCarControlPacket::decode)
                .encoder(RcCarControlPacket::encode)
                .consumerMainThread(RcCarControlPacket::handle)
                .add();
        // ---- V3.2.0: el menú de colores del vehículo (tecla R) manda pintura, manijas y rines
        CHANNEL.messageBuilder(VehiclePaintPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(VehiclePaintPacket::decode)
                .encoder(VehiclePaintPacket::encode)
                .consumerMainThread(VehiclePaintPacket::handle)
                .add();
        // ---- V3.5.0: la radio del vehículo (tecla X): emisora, encendido y volumen
        CHANNEL.messageBuilder(VehicleRadioPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(VehicleRadioPacket::decode)
                .encoder(VehicleRadioPacket::encode)
                .consumerMainThread(VehicleRadioPacket::handle)
                .add();
        // ---- V4.2.0: radio de mesa y sus bafles
        CHANNEL.messageBuilder(RadioBlockPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RadioBlockPacket::decode)
                .encoder(RadioBlockPacket::encode)
                .consumerMainThread(RadioBlockPacket::handle)
                .add();
        CHANNEL.messageBuilder(RadioStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RadioStatePacket::decode)
                .encoder(RadioStatePacket::encode)
                .consumerMainThread(RadioStatePacket::handle)
                .add();
        // ---- V4.3.0: cámaras de seguridad
        CHANNEL.messageBuilder(CameraEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CameraEditPacket::decode)
                .encoder(CameraEditPacket::encode)
                .consumerMainThread(CameraEditPacket::handle)
                .add();
        CHANNEL.messageBuilder(MonitorSelectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(MonitorSelectPacket::decode)
                .encoder(MonitorSelectPacket::encode)
                .consumerMainThread(MonitorSelectPacket::handle)
                .add();
        CHANNEL.messageBuilder(CameraListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CameraListPacket::decode)
                .encoder(CameraListPacket::encode)
                .consumerMainThread(CameraListPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenCameraEditPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenCameraEditPacket::decode)
                .encoder(OpenCameraEditPacket::encode)
                .consumerMainThread(OpenCameraEditPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
