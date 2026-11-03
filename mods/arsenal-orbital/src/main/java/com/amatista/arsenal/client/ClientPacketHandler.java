package com.amatista.arsenal.client;

import com.amatista.arsenal.net.BeamPacket;
import com.amatista.arsenal.net.StartCinematicPacket;

/** Recibe los paquetes del servidor (solo existe en el cliente). */
public final class ClientPacketHandler {
    public static void startCinematic(StartCinematicPacket packet) {
        Cinematic.start(packet.target, packet.durationTicks, packet.type);
    }

    public static void beam(BeamPacket packet) {
        WorldEffects.addStrike(packet.target, packet.ticksToImpact, packet.type);
    }

    public static void selected(com.amatista.arsenal.net.SelectWeaponPacket packet) {
        ClientState.selected = packet.type;
    }

    private ClientPacketHandler() {}
}
