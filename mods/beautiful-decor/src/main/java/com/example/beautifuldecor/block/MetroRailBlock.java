package com.example.beautifuldecor.block;

/** V5.1.0: riel del metro eléctrico: misma geometría que los otros rieles de tren, con su propia familia de vía (2). */
public class MetroRailBlock extends FreightRailBlock {
    public MetroRailBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int family() {
        return 2;
    }
}
