package com.example.beautifuldecor.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * V4.2.0: lo que el menú de la radio necesita de «la radio» que maneja, sea la de un vehículo o la radio de mesa. Así los dos usan
 * el mismo menú y la misma lista de emisoras guardadas (RadioFavorites), que por eso queda sincronizada entre autos y radio de mesa.
 */
public interface RadioSource {
    /** Segunda línea del título (nombre del vehículo o de la radio). */
    Component label();

    /** false si el menú ya no debe seguir abierto (te bajaste, te alejaste, la radio ya no existe). */
    boolean valid(LocalPlayer player);

    boolean isOn();

    String url();

    String stationName();

    int volume();

    /** true si debería estar sonando ahora (para el indicador verde). */
    boolean shouldPlay();

    /** El reproductor que suena en este cliente, o null. */
    HsAudioPlayer player();

    void select(String url, String name);

    void power(boolean on);

    void setVolume(int volume);
}
