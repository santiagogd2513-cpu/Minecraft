package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computador de escritorio HS: monitor con soporte, teclado, mouse y torre con el logo HS.
 * Clic derecho = enciende o apaga el equipo (la pantalla, el LED y el logo de la torre se encienden
 * y el monitor emite un poco de luz). La forma se define mirando al norte y gira con el bloque.
 */
public class HsDesktopBlock extends ShapedFacingBlock {
    public static final BooleanProperty ON = BooleanProperty.create("on");

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            ModBlock.box(0.5, 3, 7, 11.5, 13, 8.6),     // monitor
            ModBlock.box(3, 0, 5.5, 9, 3, 9.5),         // base y cuello
            ModBlock.box(1.5, 0, 1, 9.5, 1, 5),         // teclado
            ModBlock.box(10, 0, 2, 12, 1, 5),           // mouse
            ModBlock.box(12.5, 0, 3, 15.5, 12, 15));    // torre

    public HsDesktopBlock(BlockBehaviour.Properties properties) {
        this(properties, NORTH_SHAPE);
    }

    /** V2.3.0: variantes del computador HS (retro, compacto, ámbar, delgado, gamer y de doble pantalla), cada una con su forma. */
    public HsDesktopBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
        super(properties, northShape);
        registerDefaultState(defaultBlockState().setValue(ON, Boolean.FALSE));
    }

    /** Formas de colisión de las variantes nuevas (mirando al norte); deben coincidir con tools/v230/computers.py. */
    public static VoxelShape variantShape(String id) {
        return switch (id) {
            case "hs_desktop_retro" -> Shapes.or(
                    ModBlock.box(11.5, 0, 2, 15.5, 13, 14),       // torre
                    ModBlock.box(0.5, 1.5, 4, 10.5, 11.5, 7),     // monitor CRT
                    ModBlock.box(2, 1.5, 7, 9, 10, 13),           // tubo
                    ModBlock.box(3, 0, 7, 8, 1.5, 12),            // pie
                    ModBlock.box(1, 0, 0.5, 9, 1, 3.5));          // teclado
            case "hs_desktop_compact" -> Shapes.or(
                    ModBlock.box(2.5, 0, 4, 11.5, 14, 12.5),
                    ModBlock.box(3.5, 8, 12.5, 10.5, 13, 14.5),
                    ModBlock.box(1.5, 0, 0.5, 10.5, 1, 3.5));
            case "hs_desktop_amber" -> Shapes.or(
                    ModBlock.box(0.5, 0, 3.5, 15.5, 4, 13.5),
                    ModBlock.box(2, 4, 6.5, 12, 12, 13),
                    ModBlock.box(2, 0, 0.3, 13, 1.4, 3.3));
            case "hs_desktop_slim" -> Shapes.or(
                    ModBlock.box(1, 3.5, 6.8, 15, 13.5, 7.8),
                    ModBlock.box(5.5, 0, 5.5, 10.5, 3.5, 9.5),
                    ModBlock.box(3.5, 0, 1.5, 12.5, 0.6, 4.5));
            case "hs_desktop_gaming" -> Shapes.or(
                    ModBlock.box(10.5, 0, 2, 15.5, 14, 14),
                    ModBlock.box(0, 4, 7.5, 10, 12.5, 8.5),
                    ModBlock.box(2.5, 0, 6.5, 7.5, 4, 10),
                    ModBlock.box(0, 0, 0, 10.4, 1.2, 5.5));
            case "hs_desktop_dual" -> Shapes.or(
                    ModBlock.box(0.3, 4.5, 7.5, 15.7, 12.5, 8.5),
                    ModBlock.box(2, 0, 6.5, 6, 4.5, 9.5),
                    ModBlock.box(10, 0, 6.5, 14, 4.5, 9.5),
                    ModBlock.box(6.5, 0, 9.5, 9.5, 3, 12.5),
                    ModBlock.box(3, 0, 1.2, 12, 0.6, 4.2));
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean on = !state.getValue(ON);
            level.setBlock(pos, state.setValue(ON, on), 3);
            level.playSound(null, pos, on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.35f, on ? 1.6f : 1.3f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
