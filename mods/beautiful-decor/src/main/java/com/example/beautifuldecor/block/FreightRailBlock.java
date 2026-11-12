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

    /** La forma que tiene este riel ahora, en los nombres de {@link RailPath} (son los mismos que los de RailShape). */
    public static RailPath.Shape pathShape(BlockState state) {
        RailShape shape = state.getValue(BlockStateProperties.RAIL_SHAPE);
        return RailPath.Shape.valueOf(shape.name());
    }
}
