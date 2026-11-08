package com.example.beautifuldecor.client;

import com.example.beautifuldecor.network.ElevatorControllerPacket;

/** Pieza de mentira de ClientHooks: solo anota el paquete que recibe el cliente. */
public class ClientHooks {
    public static ElevatorControllerPacket lastController;

    public static void openElevatorController(ElevatorControllerPacket packet) {
        lastController = packet;
    }
}
