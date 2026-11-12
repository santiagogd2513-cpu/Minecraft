package com.example.beautifuldecor.block;

import com.example.beautifuldecor.entity.RailPath;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * V4.8.0: riel del tren de carga. Se comporta como el riel normal de Minecraft (se acomoda solo en curvas, subidas y bajadas según
 * los rieles que tiene al lado) pero con diseño propio; los coches del tren solo avanzan por estos. El recorrido por dentro del bloque
 * (recta, subida de 45° o curva de radio 0,5) lo calcula {@link RailPath}.
 */
public class FreightRailBlock extends RailBlock {
    public FreightRailBlock(Properties properties) {
        super(properties);
    }

    /** Familia de vía: 0 = carga, 1 = pasajeros. Cada coche solo avanza por la vía de su familia. */
    public int family() {
        return 0;
    }

    /** El tramo de vía de ese bloque para esa familia (riel de Minecraft o pedazo de curva ancha), o null si no hay. */
    public static RailPath.Piece pieceAt(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, BlockState state, int family) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof FreightRailBlock rail) {
            return rail.family() == family ? pathShape(state) : null;
        }
        if (block instanceof WideRailBlock wide && wide.family() == family
                && level.getBlockEntity(pos) instanceof com.example.beautifuldecor.block.entity.WideRailBlockEntity be) {
            return be.piece();
        }
        return null;
    }

    /** La forma que tiene este riel ahora, en los nombres de {@link RailPath} (son los mismos que los de RailShape). */
    public static RailPath.Shape pathShape(BlockState state) {
        RailShape shape = state.getValue(BlockStateProperties.RAIL_SHAPE);
        return RailPath.Shape.valueOf(shape.name());
    }
}
