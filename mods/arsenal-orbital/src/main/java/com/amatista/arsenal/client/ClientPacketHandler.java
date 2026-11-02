package com.amatista.arsenal.client;

import com.amatista.arsenal.net.BeamPacket;
import com.amatista.arsenal.net.StartCinematicPacket;

/** Recibe los paquetes del servidor (solo existe en el cliente). */
public final class ClientPacketHandler {
    public static void startCinematic(StartCinematicPacket packet) {
        Cinematic.start(packet.target, packet.durationTicks);
    }

    public static void beam(BeamPacket packet) {
        WorldEffects.addBeam(packet.target, packet.ticksToImpact);
    }

    private ClientPacketHandler() {}
}
