package com.example.beautifuldecor.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;

import javax.annotation.Nullable;

/** V3.9.0: vehículo con almacenamiento (maletero, caja o bodega). Se abre con la tecla del inventario yendo montado. */
public interface HasVehicleStorage {
    /** Filas de 9 casillas del almacenamiento; 0 si el vehículo no tiene (bicicleta y moto). */
    int storageRows();

    /** El contenido (null si no tiene). */
    @Nullable
    SimpleContainer getStorage();

    /** Título que se ve arriba de la ventana del almacenamiento. */
    Component storageTitle();

    default boolean hasStorage() {
        return this.storageRows() > 0 && this.getStorage() != null;
    }
}
