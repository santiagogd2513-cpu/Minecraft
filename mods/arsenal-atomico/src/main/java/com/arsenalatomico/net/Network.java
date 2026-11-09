package com.arsenalatomico.net;

import com.arsenalatomico.ArsenalAtomico;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ArsenalAtomico.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(StartCinematicPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartCinematicPacket::encode).decoder(StartCinematicPacket::new)
                .consumerMainThread(StartCinematicPacket::handle).add();
        CHANNEL.messageBuilder(SelectWeaponPacket.class, id++)
                .encoder(SelectWeaponPacket::encode).decoder(SelectWeaponPacket::new)
                .consumerMainThread(SelectWeaponPacket::handle).add();
        CHANNEL.messageBuilder(BasePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BasePacket::encode).decoder(BasePacket::new)
                .consumerMainThread(BasePacket::handle).add();
        CHANNEL.messageBuilder(RidePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RidePacket::encode).decoder(RidePacket::new)
                .consumerMainThread(RidePacket::handle).add();
        CHANNEL.messageBuilder(OpenConsolePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenConsolePacket::encode).decoder(OpenConsolePacket::new)
                .consumerMainThread(OpenConsolePacket::handle).add();
        CHANNEL.messageBuilder(FirePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FirePacket::encode).decoder(FirePacket::new)
                .consumerMainThread(FirePacket::handle).add();
    }

    private Network() {}
}
