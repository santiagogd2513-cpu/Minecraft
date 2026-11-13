package com.example.beautifuldecor.entity;

/** V6.9.0: un vehículo con skins (menú de la tecla R, ficha "Skins") y, en algunas, sirena. Lo implementan los vehículos que tienen skins. */
public interface SkinnableVehicle {
    /** Cuántas skins tiene (la 0 es la original). */
    int skinCount();

    /** Clave de idioma del nombre de cada skin. */
    String skinKey(int skin);

    int getSkin();

    void setSkin(int skin);

    boolean isSirenOn();
}
