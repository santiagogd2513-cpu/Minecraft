package com.example.beautifuldecor.block;

import net.minecraft.world.level.block.state.BlockState;

/** Ayuda para saber si un bloque es un computador HS y si está encendido (V2.4.0: con la tecla R abre el navegador). */
public final class HsComputers {
    private HsComputers() {}

    public static boolean isHsComputer(BlockState state) {
        return state.getBlock() instanceof HsDesktopBlock || state.getBlock() instanceof LaptopBlock;
    }

    /** Escritorio encendido, portátil abierto o (V2.11.0) televisor encendido: el sonido del navegador y de los televisores depende de esto. */
    public static boolean isOn(BlockState state) {
        if (state.getBlock() instanceof HsDesktopBlock) {
            return state.getValue(HsDesktopBlock.ON);
        }
        if (state.getBlock() instanceof LaptopBlock) {
            return state.getValue(LaptopBlock.OPEN);
        }
        if (state.getBlock() instanceof TelevisionBlock) {
            return state.getValue(TelevisionBlock.ON);
        }
        return false;
    }
}
