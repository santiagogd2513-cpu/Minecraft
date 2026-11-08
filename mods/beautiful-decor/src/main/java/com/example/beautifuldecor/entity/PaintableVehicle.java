package com.example.beautifuldecor.entity;

import javax.annotation.Nullable;

/**
 * Un vehículo que se puede repintar con la tecla R (V3.2.0): moto, bicicleta, camioneta, camión de reparto y helicóptero.
 * El carro RC y el dron no lo implementan.
 */
public interface PaintableVehicle {
    VehiclePaint.Kind paintKind();

    /** Color guardado del canal (0xRRGGBB), o -1 si es el original. */
    int getPaintRaw(int channel);

    /** Guarda el color de un canal (-1 = original). Se sincroniza solo con todos los jugadores. */
    void setPaintRaw(int channel, int rgbOrOriginal);

    /** Solo cliente: colores de vista previa mientras el menú está abierto (null = no hay). */
    @Nullable
    int[] getPaintPreview();

    void setPaintPreview(@Nullable int[] colors);
}
