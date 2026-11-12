package com.example.beautifuldecor.block;

/** V5.0.0: riel del tren de pasajeros: igual que el de carga (curvas, subidas, bajadas) pero con otro diseño y su propia familia de vía. */
public class PassengerRailBlock extends FreightRailBlock {
    public PassengerRailBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int family() {
        return 1;
    }
}
