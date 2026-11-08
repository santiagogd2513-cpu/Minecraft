package com.amatista.arsenal.world;

import com.amatista.arsenal.ArsenalOrbital;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** El búnker militar ÉGIDA-7: dónde está y de qué piezas se arma. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ArsenalOrbital.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ArsenalOrbital.MODID);

    public static final RegistryObject<StructureType<BunkerStructure>> BUNKER = TYPES.register("bunker", () -> () -> BunkerStructure.CODEC);
    public static final RegistryObject<StructurePieceType> BUNKER_PIECE = PIECES.register("bunker_piece",
            () -> (StructurePieceType) BunkerPiece::new);

    private ModStructures() {}
}
