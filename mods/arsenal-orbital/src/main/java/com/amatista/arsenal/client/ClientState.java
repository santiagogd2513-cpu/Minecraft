package com.amatista.arsenal.client;

import com.amatista.arsenal.StrikeType;

/** Estado del cliente: arma elegida (para resaltarla en el menú). */
public final class ClientState {
    public static StrikeType selected = StrikeType.SATELLITE;

    private ClientState() {}
}
