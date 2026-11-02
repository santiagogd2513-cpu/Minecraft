package com.amatista.arsenal.net;

import com.amatista.arsenal.ArsenalOrbital;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ArsenalOrbital.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(StartCinematicPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartCinematicPacket::encode).decoder(StartCinematicPacket::new)
                .consumerMainThread(StartCinematicPacket::handle).add();
        CHANNEL.messageBuilder(BeamPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BeamPacket::encode).decoder(BeamPacket::new)
                .consumerMainThread(BeamPacket::handle).add();
        CHANNEL.messageBuilder(SkipPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SkipPacket::encode).decoder(SkipPacket::new)
                .consumerMainThread(SkipPacket::handle).add();
    }

    private Network() {}
}
