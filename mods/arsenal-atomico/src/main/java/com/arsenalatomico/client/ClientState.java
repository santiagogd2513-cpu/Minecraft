package com.arsenalatomico.client;

import com.arsenalatomico.WeaponType;

/** Lo que el cliente recuerda: el arma elegida. */
public final class ClientState {
    public static WeaponType selected = WeaponType.SALVO;

    private ClientState() {}
}
