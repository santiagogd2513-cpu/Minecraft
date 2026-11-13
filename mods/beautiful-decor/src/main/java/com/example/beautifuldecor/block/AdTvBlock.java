package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * V5.2.1: televisor de anuncios de 2 de ancho x 1 de alto. Pasa los mismos anuncios que las pantallas de anuncios (mismo reproductor).
 * Se coloca como ellas (el bloque donde haces clic es la esquina inferior izquierda vista de frente y se extiende a tu derecha), en dos
 * modos: en la PARED (panel pegado atrás del bloque) o en el TECHO (clic en la cara de abajo de un techo: queda colgado de dos soportes y
 * mira hacia donde mira el jugador, o sea hacia donde se vaya a ver desde el centro del lugar). Para el techo hace falta que todo el techo
 * de arriba sea sólido.
 */
public class AdTvBlock extends AdScreenBlock {
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

    private static final VoxelShape[] CEILING_SHAPES = ShapeRotator.all(Shapes.or(
            ModBlock.box(0, 2, 6.5, 16, 14, 9.5), ModBlock.box(2, 14, 7, 4, 16, 9), ModBlock.box(12, 14, 7, 14, 16, 9)));

    private final int tvWidth;
    private final int tvHeight;

    public AdTvBlock(BlockBehaviour.Properties properties) {
        this(properties, 2, 1);
    }

    /** V6.11.0: televisores de anuncios de otros tamaños (3x2, 4x2, 6x4). Los de más de 1 de alto solo van en la pared. */
    public AdTvBlock(BlockBehaviour.Properties properties, int width, int height) {
        super(properties, Math.max(width, height));
        this.tvWidth = width;
        this.tvHeight = height;
        registerDefaultState(defaultBlockState().setValue(CEILING, false));
    }

    @Override
    public int width() {
        return this.tvWidth;
    }

    @Override
    public int height() {
        return this.tvHeight;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CEILING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(CEILING)) {
            return CEILING_SHAPES[state.getValue(FACING).get2DDataValue()];
        }
        return super.getShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = super.getStateForPlacement(context);
        if (base == null) {
            return null;
        }
        if (context.getClickedFace() != Direction.DOWN || this.tvHeight > 1) {
            return base.setValue(CEILING, false);
        }
        Direction right = base.getValue(FACING).getCounterClockWise();
        Level level = context.getLevel();
        for (int px = 0; px < width(); px++) {
            BlockPos up = context.getClickedPos().relative(right, px).above();
            if (!level.getBlockState(up).isFaceSturdy(level, up, Direction.DOWN)) {
                return null;
            }
        }
        return base.setValue(CEILING, true);
    }
}
