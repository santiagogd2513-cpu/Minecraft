package com.amatista.arsenal.world;

import com.amatista.arsenal.block.PedestalBlockEntity;
import com.amatista.arsenal.registry.ModBlocks;
import com.amatista.arsenal.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Una parte del búnker. Todo se dibuja en coordenadas propias (x, z relativas a la caseta, y
 * absoluta) y se gira según la rotación del búnker. Cada parte solo pone los bloques que caen en
 * el chunk que se está generando.
 *
 * <pre>
 *  superficie: caseta (x -3..3, z -1..12) con la puerta en z = -1
 *  escalera de ida y vuelta: x -2..2, z 0..11, baja 16 bloques por vuelta (3 vueltas = 48)
 *  abajo: control de acceso (z -8..-2) → pasillo (z -54..-10) con 8 salas a los lados
 *         → bóveda (z -70..-56) con el artefacto en su vitrina
 * </pre>
 */
public class BunkerPiece extends StructurePiece {
    enum Kind {ENTRANCE, HALL, CORRIDOR, ROOM, VAULT}

    static final int ROOMS = 8;
    static final String[] THEMES = {"ARMERÍA", "PUESTO DE MANDO", "LABORATORIO", "DORMITORIO",
            "COMUNICACIONES", "DEPÓSITO", "SALA DE ENERGÍA", "ENFERMERÍA"};

    private final Kind kind;
    private final int index, theme;
    private final BlockPos origin;
    private final Rotation rot;
    private final int surfaceY, floorY, cycles;
    private final long seed;

    // mientras se genera un chunk
    private WorldGenLevel lvl;
    private BoundingBox clip;

    // ------------------------------------------------------------------ creación y guardado

    static void addAll(StructurePiecesBuilder b, BlockPos origin, Rotation rot, int surface, int cycles, long seed) {
        int floor = surface - 16 * cycles;
        b.addPiece(new BunkerPiece(Kind.ENTRANCE, 0, 0, origin, rot, surface, floor, cycles, seed));
        b.addPiece(new BunkerPiece(Kind.HALL, 0, 0, origin, rot, surface, floor, cycles, seed));
        b.addPiece(new BunkerPiece(Kind.CORRIDOR, 0, 0, origin, rot, surface, floor, cycles, seed));
        // cada búnker tiene las salas en otro orden
        List<Integer> themes = new ArrayList<>();
        for (int i = 0; i < ROOMS; i++) themes.add(i);
        RandomSource r = RandomSource.create(seed);
        for (int i = themes.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = themes.get(i);
            themes.set(i, themes.get(j));
            themes.set(j, t);
        }
        for (int i = 0; i < ROOMS; i++) b.addPiece(new BunkerPiece(Kind.ROOM, i, themes.get(i), origin, rot, surface, floor, cycles, seed));
        b.addPiece(new BunkerPiece(Kind.VAULT, 0, 0, origin, rot, surface, floor, cycles, seed));
    }

    private BunkerPiece(Kind kind, int index, int theme, BlockPos origin, Rotation rot, int surfaceY, int floorY, int cycles, long seed) {
        super(ModStructures.BUNKER_PIECE.get(), 0, bounds(kind, index, origin, rot, surfaceY, floorY));
        this.kind = kind;
        this.index = index;
        this.theme = theme;
        this.origin = origin;
        this.rot = rot;
        this.surfaceY = surfaceY;
        this.floorY = floorY;
        this.cycles = cycles;
        this.seed = seed;
    }

    public BunkerPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.BUNKER_PIECE.get(), tag);
        kind = Kind.values()[tag.getInt("Kind")];
        index = tag.getInt("Index");
        theme = tag.getInt("Theme");
        origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        rot = Rotation.values()[tag.getInt("Rot")];
        surfaceY = tag.getInt("Surface");
        floorY = tag.getInt("Floor");
        cycles = tag.getInt("Cycles");
        seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("Kind", kind.ordinal());
        tag.putInt("Index", index);
        tag.putInt("Theme", theme);
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        tag.putInt("Rot", rot.ordinal());
        tag.putInt("Surface", surfaceY);
        tag.putInt("Floor", floorY);
        tag.putInt("Cycles", cycles);
        tag.putLong("Seed", seed);
    }

    private static int roomZTop(int index) {
        return -11 - 11 * (index / 2);
    }

    private static int roomSide(int index) {
        return index % 2 == 0 ? -1 : 1;
    }

    private static BoundingBox bounds(Kind kind, int index, BlockPos o, Rotation rot, int s, int f) {
        int[] b = switch (kind) {
            case ENTRANCE -> new int[]{-6, f - 2, -6, 6, s + 8, 14};
            case HALL -> new int[]{-4, f - 1, -9, 4, f + 5, -1};
            case CORRIDOR -> new int[]{-3, f - 1, -55, 3, f + 5, -9};
            case ROOM -> {
                int zt = roomZTop(index), side = roomSide(index);
                yield new int[]{side > 0 ? 3 : -13, f - 1, zt - 9, side > 0 ? 13 : -3, f + 5, zt + 1};
            }
            case VAULT -> new int[]{-8, f - 1, -71, 8, f + 8, -55};
        };
        BlockPos a = at(o, rot, b[0], b[1], b[2]), c = at(o, rot, b[3], b[4], b[5]);
        return BoundingBox.fromCorners(a, c);
    }

    /** De coordenadas del búnker al mundo. */
    private static BlockPos at(BlockPos o, Rotation rot, int x, int y, int z) {
        return switch (rot) {
            case NONE -> new BlockPos(o.getX() + x, y, o.getZ() + z);
            case CLOCKWISE_90 -> new BlockPos(o.getX() - z, y, o.getZ() + x);
            case CLOCKWISE_180 -> new BlockPos(o.getX() - x, y, o.getZ() - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(o.getX() + z, y, o.getZ() - x);
        };
    }

    // ------------------------------------------------------------------ herramientas

    private BlockPos w(int x, int y, int z) {
        return at(origin, rot, x, y, z);
    }

    private void set(int x, int y, int z, BlockState s) {
        BlockPos p = w(x, y, z);
        if (clip.isInside(p)) lvl.setBlock(p, s.rotate(rot), 2);
    }

    private void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    private BlockState get(int x, int y, int z) {
        return lvl.getBlockState(w(x, y, z));
    }

    private boolean inside(int x, int y, int z) {
        return clip.isInside(w(x, y, z));
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
    }

    private void air(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, Blocks.AIR);
    }

    /** Caja con piso, techo y paredes; adentro aire. */
    private void room(int x0, int y0, int z0, int x1, int y1, int z1, Block floor, Block wallLow, Block wallHigh, Block ceiling) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    Block b;
                    if (y == y0) b = floor;
                    else if (y == y1) b = ceiling;
                    else if (edge) b = y <= y0 + 2 ? wallLow : wallHigh;
                    else b = Blocks.AIR;
                    set(x, y, z, b);
                }
    }

    private BlockState facing(Block b, Direction d) {
        return b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
    }

    private void chest(int x, int y, int z, Direction d, ResourceLocation loot, RandomSource r) {
        set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, d));
        BlockPos p = w(x, y, z);
        if (clip.isInside(p)) RandomizableContainerBlockEntity.setLootTable(lvl, r, p, loot);
    }

    private void bed(int x, int y, int z, Direction head, Block bed) {
        set(x, y, z, bed.defaultBlockState().setValue(BedBlock.FACING, head).setValue(BedBlock.PART, BedPart.FOOT));
        set(x + head.getStepX(), y, z + head.getStepZ(), bed.defaultBlockState().setValue(BedBlock.FACING, head).setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void sign(int x, int y, int z, Direction face, DyeColor color, String... lines) {
        set(x, y, z, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, face));
        BlockPos p = w(x, y, z);
        if (clip.isInside(p) && lvl.getBlockEntity(p) instanceof SignBlockEntity s) {
            SignText t = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, Component.literal(lines[i]));
            // durante la generación el letrero todavía no está en un mundo: el texto se carga como datos
            CompoundTag tag = s.saveWithoutMetadata();
            final SignText text = t.setColor(color).setHasGlowingText(true);
            SignText.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, text).result().ifPresent(n -> tag.put("front_text", n));
            s.load(tag);
        }
    }

    /** Vitrina de vidrio con el pedestal en el centro (x, z); el pedestal queda en y. */
    private void showcase(int x, int y, int z, ItemStack item, boolean rainbow, Block base) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                set(x + dx, y, z + dz, base);
                set(x + dx, y + 1, z + dz, Blocks.GLASS);
                set(x + dx, y + 2, z + dz, Blocks.GLASS);
            }
        air(x, y + 1, z, x, y + 2, z);
        fill(x - 1, y + 3, z - 1, x + 1, y + 3, z + 1, Blocks.GLASS);
        set(x, y, z, ModBlocks.ARTIFACT_PEDESTAL.get());
        BlockPos p = w(x, y, z);
        if (clip.isInside(p) && lvl.getBlockEntity(p) instanceof PedestalBlockEntity be) be.setup(item, rainbow);
    }

    // ------------------------------------------------------------------ generación

    @Override
    public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator gen, RandomSource ignored,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        lvl = level;
        clip = box;
        RandomSource r = RandomSource.create(seed ^ (kind.ordinal() * 7919L + index * 104729L));
        try {
            switch (kind) {
                case ENTRANCE -> entrance(r);
                case HALL -> hall(r);
                case CORRIDOR -> corridor();
                case ROOM -> roomPiece(r);
                case VAULT -> vault();
            }
        } finally {
            lvl = null;
            clip = null;
        }
    }

    // ---- caseta y escalera

    private void entrance(RandomSource r) {
        int s = surfaceY, f = floorY;
        // pozo: paredes de piedra desde el fondo hasta la superficie
        for (int y = f - 1; y < s; y++)
            for (int x = -3; x <= 3; x++)
                for (int z = -1; z <= 12; z++) {
                    boolean wall = x == -3 || x == 3 || z == -1 || z == 12;
                    if (!wall) {
                        set(x, y, z, y == f - 1 ? Blocks.POLISHED_ANDESITE : Blocks.AIR);
                        continue;
                    }
                    // luces en las paredes cada 4 bloques
                    boolean lamp = (y - f) % 4 == 2 && ((x == -3 || x == 3) && (z == 3 || z == 8) || (z == 12 && x == 0));
                    set(x, y, z, lamp ? Blocks.SEA_LANTERN : r.nextInt(9) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
        // caseta de concreto sobre el pozo
        for (int y = s; y <= s + 4; y++)
            for (int x = -3; x <= 3; x++)
                for (int z = -1; z <= 12; z++) {
                    boolean wall = x == -3 || x == 3 || z == -1 || z == 12;
                    if (y == s + 4) set(x, y, z, (x == 0 && (z == 3 || z == 8)) ? Blocks.SEA_LANTERN : wall ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
                    else if (wall) set(x, y, z, y == s + 3 ? Blocks.POLISHED_ANDESITE : Blocks.GRAY_CONCRETE);
                    else set(x, y, z, Blocks.AIR);
                }
        // ventanitas
        for (int z : new int[]{3, 8}) {
            set(-3, s + 2, z, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true));
            set(3, s + 2, z, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true));
        }
        // camino a la puerta y adornos afuera, sobre el terreno
        for (int z = -5; z <= -2; z++)
            for (int x = -1; x <= 1; x++) {
                air(x, s, z, x, s + 3, z);
                set(x, s - 1, z, Blocks.POLISHED_ANDESITE);
                foundation(x, s - 2, z, 8);
            }
        for (int x : new int[]{-2, 2}) {
            set(x, s - 1, -3, Blocks.POLISHED_ANDESITE);
            foundation(x, s - 2, -3, 8);
            set(x, s, -3, Blocks.ANDESITE_WALL);
            set(x, s + 1, -3, Blocks.LANTERN);
        }
        // franja amarilla y negra de peligro alrededor de la puerta
        for (int x = -2; x <= 2; x++) if (x != 0) set(x, s + 3, -1, Math.abs(x) % 2 == 1 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
        // puerta de hierro con botones afuera y adentro
        set(0, s, -1, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        set(0, s + 1, -1, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        set(1, s + 1, -2, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
        set(-1, s + 1, 0, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH));
        sign(0, s + 2, -2, Direction.NORTH, DyeColor.YELLOW, "BÚNKER", "ÉGIDA-7", "ZONA MILITAR", "PROHIBIDO PASAR");
        sign(1, s + 2, 0, Direction.SOUTH, DyeColor.WHITE, "PROFUNDIDAD", "-" + (16 * cycles) + " m", "SALAS 1-8", "BÓVEDA AL FONDO");

        // escalera de ida y vuelta
        for (int c = 0; c < cycles; c++) {
            int h = s - 16 * c;
            // descanso de arriba (en la primera vuelta es el piso de la caseta)
            fill(-2, h - 1, 0, 2, h - 1, 1, Blocks.POLISHED_ANDESITE);
            // bajada A (lado izquierdo, hacia +z)
            for (int i = 1; i <= 8; i++) {
                int y = h - 1 - i;
                for (int x = -2; x <= -1; x++) {
                    set(x, y, 1 + i, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM));
                    set(x, y - 1, 1 + i, Blocks.STONE_BRICKS);
                }
            }
            // descanso de abajo
            fill(-2, h - 9, 10, 2, h - 9, 11, Blocks.POLISHED_ANDESITE);
            fill(-2, h - 10, 10, 2, h - 10, 11, Blocks.STONE_BRICKS);
            // bajada B (lado derecho, de vuelta hacia -z)
            for (int j = 1; j <= 8; j++) {
                int y = h - 9 - j;
                for (int x = 1; x <= 2; x++) {
                    set(x, y, 10 - j, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM));
                    set(x, y - 1, 10 - j, Blocks.STONE_BRICKS);
                }
            }
            // farol colgando sobre cada descanso
            set(0, h + 2, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            set(0, h + 3, 0, Blocks.CHAIN);
        }
        // muro del medio entre las dos bajadas, con luces
        for (int y = f - 1; y <= s + 3; y++)
            for (int z = 2; z <= 9; z++) set(0, y, z, (y - f) % 4 == 0 && (z == 4 || z == 7) ? Blocks.SEA_LANTERN : Blocks.POLISHED_ANDESITE);
        // baranda arriba para no caer al hueco de la bajada B
        for (int x = 1; x <= 2; x++)
            set(x, s, 2, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
        // descanso del fondo
        fill(-2, f - 1, 0, 2, f - 1, 1, Blocks.POLISHED_ANDESITE);
        set(0, f + 4, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(0, f + 5, 1, Blocks.CHAIN);
        set(0, f + 6, 1, Blocks.CHAIN);

        // sacos de arena (barriles) y antena
        for (int[] p : new int[][]{{-4, 1}, {-4, 2}, {4, 5}, {4, 6}}) {
            if (!inside(p[0], s, p[1])) continue;
            int y = ground(p[0], p[1], s);
            set(p[0], y, p[1], Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        }
        for (int y = s + 5; y <= s + 7; y++) set(2, y, 10, Blocks.IRON_BARS);
        set(2, s + 8, 10, Blocks.LIGHTNING_ROD);
    }

    /** Rellena hacia abajo hasta tocar terreno sólido (para que nada quede flotando). */
    private void foundation(int x, int y, int z, int max) {
        for (int i = 0; i < max; i++) {
            if (!inside(x, y - i, z)) return;
            BlockState b = get(x, y - i, z);
            if (b.isSolid() && b.getFluidState().isEmpty()) return;
            set(x, y - i, z, Blocks.STONE_BRICKS);
        }
    }

    /** Altura donde se puede poner algo sobre el terreno, cerca de y. */
    private int ground(int x, int z, int y) {
        for (int i = 0; i < 8 && get(x, y, z).isSolid(); i++) y++;
        for (int i = 0; i < 8 && !get(x, y - 1, z).isSolid(); i++) y--;
        return y;
    }

    // ---- control de acceso

    private void hall(RandomSource r) {
        int f = floorY;
        room(-4, f - 1, -9, 4, f + 5, -1, Blocks.POLISHED_DEEPSLATE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE);
        air(-1, f, -1, 1, f + 2, -1);       // desde la escalera
        air(-1, f, -9, 1, f + 2, -9);       // hacia el pasillo
        for (int z : new int[]{-7, -3}) for (int x : new int[]{-2, 2}) set(x, f + 5, z, Blocks.SEA_LANTERN);
        set(0, f + 5, -5, Blocks.SEA_LANTERN);
        // mostrador del guardia
        for (int z = -6; z <= -4; z++) set(-2, f, z, Blocks.POLISHED_BLACKSTONE);
        set(-2, f + 1, -5, Blocks.DAYLIGHT_DETECTOR);
        set(-2, f + 1, -6, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.WEST));
        set(-3, f, -5, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        // casilleros
        for (int z = -7; z <= -3; z += 2) {
            set(3, f, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.WEST));
            set(3, f + 1, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.WEST));
        }
        // marco de seguridad (detector) en la salida
        for (int y = f; y <= f + 2; y++) {
            set(-2, y, -8, Blocks.IRON_BLOCK);
            set(2, y, -8, Blocks.IRON_BLOCK);
        }
        fill(-2, f + 3, -8, 2, f + 3, -8, Blocks.IRON_BLOCK);
        set(0, f + 3, -8, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        set(0, f + 4, -8, Blocks.REDSTONE_BLOCK);
        sign(-3, f + 2, -7, Direction.EAST, DyeColor.YELLOW, "CONTROL", "DE ACCESO", "Identifíquese", "");
        sign(3, f + 2, -5, Direction.WEST, DyeColor.WHITE, "SALAS 1 - 8", "a los lados", "BÓVEDA", "al fondo");
    }

    // ---- pasillo

    private void corridor() {
        int f = floorY;
        room(-3, f - 1, -55, 3, f + 5, -9, Blocks.POLISHED_DEEPSLATE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE);
        air(-1, f, -9, 1, f + 2, -9);
        // línea guía amarilla en el piso y luces en el techo
        for (int z = -54; z <= -10; z++) {
            set(0, f - 1, z, z % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.POLISHED_DEEPSLATE);
            if (z % 3 == 0) set(0, f + 5, z, Blocks.SEA_LANTERN);
            // tuberías en las esquinas del techo
            set(-2, f + 4, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z));
            set(2, f + 4, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z));
        }
    }

    // ---- salas

    private int side, zBase;

    private void r(int rx, int ry, int rz, BlockState s) {
        set(side * (4 + rx), floorY + ry, zBase + rz, s);
    }

    private void r(int rx, int ry, int rz, Block b) {
        r(rx, ry, rz, b.defaultBlockState());
    }

    /** Hacia el fondo de la sala (lejos del pasillo). */
    private Direction in() {
        return side > 0 ? Direction.EAST : Direction.WEST;
    }

    private void roomPiece(RandomSource r) {
        side = roomSide(index);
        int zt = roomZTop(index);
        zBase = zt - 8;
        int f = floorY;
        int x0 = side > 0 ? 3 : -13, x1 = side > 0 ? 13 : -3;
        Block floor = theme == 2 || theme == 7 ? Blocks.WHITE_CONCRETE : Blocks.POLISHED_ANDESITE;
        Block low = theme == 2 || theme == 7 ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE;
        room(x0, f - 1, zt - 9, x1, f + 5, zt + 1, floor, low, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE);
        // puerta desde el pasillo con marco oscuro
        for (int rz = 3; rz <= 5; rz++) for (int ry = 0; ry <= 2; ry++) r(-1, ry, rz, Blocks.AIR);
        for (int ry = 0; ry <= 3; ry++) {
            r(-1, ry, 2, Blocks.POLISHED_BLACKSTONE_BRICKS);
            r(-1, ry, 6, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int rz = 3; rz <= 5; rz++) r(-1, 3, rz, Blocks.POLISHED_BLACKSTONE_BRICKS);
        // franja de peligro en el piso del pasillo frente a la puerta
        for (int rz = 3; rz <= 5; rz++)
            for (int k = 1; k <= 2; k++)
                set(side * k, f - 1, zBase + rz, (rz + k) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
        sign(side * 2, f + 3, zBase + 4, in().getOpposite(), DyeColor.YELLOW,
                "SALA " + (index + 1), THEMES[theme], "ARMA:", "— reservada —");
        // luces del techo
        for (int rx : new int[]{1, 5}) for (int rz : new int[]{1, 7}) r(rx, 5, rz, Blocks.SEA_LANTERN);
        r(3, 5, 4, Blocks.SEA_LANTERN);
        // vitrina reservada para un arma futura, al fondo
        int px = side * (4 + 7), pz = zBase + 4;
        showcase(px, f, pz, ItemStack.EMPTY, false, Blocks.POLISHED_BLACKSTONE_BRICKS);
        r(7, 5, 4, Blocks.SEA_LANTERN);
        switch (theme) {
            case 0 -> armory(r);
            case 1 -> command();
            case 2 -> lab();
            case 3 -> barracks(r);
            case 4 -> comms();
            case 5 -> munitions(r);
            case 6 -> generator();
            default -> infirmary();
        }
    }

    private void armory(RandomSource r) {
        Direction along = Direction.SOUTH;
        r(1, 0, 0, Blocks.SMITHING_TABLE);
        r(2, 0, 0, facing(Blocks.ANVIL, in()));
        r(3, 0, 0, Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR).setValue(GrindstoneBlock.FACING, along));
        r(4, 0, 0, Blocks.FLETCHING_TABLE);
        r(5, 0, 0, Blocks.CRAFTING_TABLE);
        chest(side * (4 + 1), floorY, zBase + 8, Direction.NORTH, BuiltInLootTables.VILLAGE_WEAPONSMITH, r);
        for (int rx = 2; rx <= 5; rx++) {
            r(rx, 0, 8, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            if (rx % 2 == 0) r(rx, 1, 8, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        }
        r(3, 0, 4, Blocks.IRON_BLOCK);
        r(3, 1, 4, Blocks.STONE_PRESSURE_PLATE);
    }

    private void command() {
        for (int rx = 0; rx <= 5; rx++) {
            r(rx, 0, 0, Blocks.POLISHED_BLACKSTONE);
            r(rx, 1, 0, rx % 2 == 0 ? Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, in())
                    : Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, in()));
            r(rx, 2, 0, Blocks.BLACK_STAINED_GLASS);   // pantallas
            r(rx, 0, 8, Blocks.POLISHED_BLACKSTONE);
            r(rx, 1, 8, rx % 2 == 1 ? Blocks.DAYLIGHT_DETECTOR : Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);
            r(rx, 2, 8, Blocks.CYAN_STAINED_GLASS);
        }
        // mesa de mapas en el centro con sillas
        r(2, 0, 4, Blocks.CARTOGRAPHY_TABLE);
        r(3, 0, 4, Blocks.CARTOGRAPHY_TABLE);
        r(2, 0, 3, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        r(3, 0, 5, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        r(1, 0, 4, facing(Blocks.LECTERN, in()));
    }

    private void lab() {
        for (int rx = 0; rx <= 5; rx++) r(rx, 0, 0, Blocks.QUARTZ_BLOCK);
        r(1, 1, 0, Blocks.BREWING_STAND);
        r(3, 1, 0, Blocks.BREWING_STAND);
        r(5, 1, 0, Blocks.POTTED_FERN);
        r(4, 1, 0, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        // tanques de vidrio
        for (int rx = 2; rx <= 4; rx++)
            for (int ry = 0; ry <= 2; ry++) r(rx, ry, 8, Blocks.LIGHT_BLUE_STAINED_GLASS);
        r(3, 1, 8, Blocks.SEA_LANTERN);
        r(1, 0, 8, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        r(5, 0, 8, Blocks.CAULDRON);
        r(0, 0, 8, Blocks.POTTED_RED_MUSHROOM);
        // mesa central
        r(2, 0, 4, Blocks.QUARTZ_SLAB);
        r(3, 0, 4, Blocks.QUARTZ_SLAB);
        r(2, 1, 4, Blocks.AMETHYST_CLUSTER);
    }

    private void barracks(RandomSource r) {
        for (int rx = 0; rx <= 4; rx += 2) {
            bed(side * (4 + rx), floorY, zBase + 1, Direction.NORTH, Blocks.GREEN_BED);
            bed(side * (4 + rx), floorY, zBase + 7, Direction.SOUTH, Blocks.GREEN_BED);
        }
        for (int rx = 1; rx <= 5; rx += 2) {
            r(rx, 0, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            r(rx, 0, 8, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        }
        for (int rx = 1; rx <= 4; rx++) for (int rz = 3; rz <= 5; rz++) r(rx, 0, rz, Blocks.BROWN_CARPET);
        chest(side * (4 + 5), floorY, zBase + 2, in().getOpposite(), BuiltInLootTables.SIMPLE_DUNGEON, r);
    }

    private void comms() {
        for (int rx = 0; rx <= 5; rx++) {
            r(rx, 0, 0, rx % 2 == 0 ? Blocks.NOTE_BLOCK : Blocks.WAXED_EXPOSED_COPPER);
            if (rx % 2 == 1) r(rx, 1, 0, Blocks.LIGHTNING_ROD);
            r(rx, 0, 8, Blocks.WAXED_COPPER_BLOCK);
            r(rx, 1, 8, rx == 2 ? Blocks.JUKEBOX : Blocks.TARGET);
            r(rx, 2, 9, Blocks.BLACK_STAINED_GLASS);   // pantallas en la pared
        }
        // antena de radio en el centro
        r(2, 0, 4, Blocks.WAXED_CUT_COPPER);
        for (int ry = 1; ry <= 3; ry++) r(2, ry, 4, Blocks.IRON_BARS);
        r(2, 4, 4, Blocks.LIGHTNING_ROD);
        r(4, 0, 4, Blocks.DAYLIGHT_DETECTOR);
    }

    private void munitions(RandomSource r) {
        for (int rx = 0; rx <= 5; rx++) {
            r(rx, 0, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            if (rx < 4) r(rx, 1, 0, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            r(rx, 0, 8, rx % 3 == 0 ? Blocks.IRON_BLOCK : Blocks.STRIPPED_SPRUCE_WOOD);
        }
        chest(side * (4 + 2), floorY + 1, zBase + 8, Direction.NORTH, BuiltInLootTables.SIMPLE_DUNGEON, r);
        chest(side * (4 + 4), floorY + 1, zBase + 8, Direction.NORTH, BuiltInLootTables.ABANDONED_MINESHAFT, r);
        r(2, 0, 4, Blocks.STRIPPED_SPRUCE_WOOD);
        r(2, 1, 4, Blocks.SPRUCE_TRAPDOOR);
        r(3, 0, 3, Blocks.STRIPPED_SPRUCE_WOOD);
        r(0, 4, 0, Blocks.COBWEB);
    }

    private void generator() {
        // reactor: base de hierro, núcleo brillante dentro de vidrio naranja
        for (int rx = 1; rx <= 3; rx++)
            for (int rz = 3; rz <= 5; rz++) {
                r(rx, 0, rz, Blocks.IRON_BLOCK);
                boolean core = rx == 2 && rz == 4;
                r(rx, 1, rz, core ? Blocks.SHROOMLIGHT : Blocks.ORANGE_STAINED_GLASS);
                r(rx, 2, rz, core ? Blocks.SHROOMLIGHT : Blocks.ORANGE_STAINED_GLASS);
                r(rx, 3, rz, Blocks.IRON_BLOCK);
            }
        r(2, 4, 4, Blocks.CHAIN);
        for (int rx = 0; rx <= 5; rx++) {
            r(rx, 0, 0, rx % 2 == 0 ? Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.UP)
                    : Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
            r(rx, 0, 8, Blocks.IRON_BLOCK);
            r(rx, 1, 8, rx % 2 == 0 ? Blocks.LIGHTNING_ROD : Blocks.AIR);
        }
    }

    private void infirmary() {
        bed(side * 4, floorY, zBase + 1, Direction.NORTH, Blocks.WHITE_BED);
        bed(side * 6, floorY, zBase + 1, Direction.NORTH, Blocks.WHITE_BED);
        bed(side * 4, floorY, zBase + 7, Direction.SOUTH, Blocks.WHITE_BED);
        bed(side * 6, floorY, zBase + 7, Direction.SOUTH, Blocks.WHITE_BED);
        r(3, 0, 0, Blocks.QUARTZ_BLOCK);
        r(3, 1, 0, Blocks.BREWING_STAND);
        r(5, 0, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        r(3, 0, 8, Blocks.POTTED_AZURE_BLUET);
        r(5, 0, 8, Blocks.POTTED_POPPY);
        // cruz roja en la pared lateral
        for (int rx = 2; rx <= 4; rx++) r(rx, 2, -1, Blocks.RED_CONCRETE);
        r(3, 1, -1, Blocks.RED_CONCRETE);
        r(3, 3, -1, Blocks.RED_CONCRETE);
    }

    // ---- bóveda

    private void vault() {
        int f = floorY;
        room(-8, f - 1, -71, 8, f + 8, -55, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE);
        // piso a cuadros y techo con cuadrícula de luces
        for (int x = -7; x <= 7; x++)
            for (int z = -70; z <= -56; z++) {
                set(x, f - 1, z, (x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
                if (Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0) set(x, f + 8, z, Blocks.SEA_LANTERN);
            }
        // puerta blindada
        air(-1, f, -55, 1, f + 3, -55);
        for (int y = f; y <= f + 4; y++) {
            set(-2, y, -55, Blocks.IRON_BLOCK);
            set(2, y, -55, Blocks.IRON_BLOCK);
        }
        fill(-2, f + 4, -55, 2, f + 4, -55, Blocks.IRON_BLOCK);
        sign(0, f + 4, -54, Direction.SOUTH, DyeColor.LIGHT_BLUE, "BÓVEDA", "DESIGNADOR", "ORBITAL", "ÉGIDA-7");
        // columnas de cuarzo en las paredes
        for (int z = -68; z <= -58; z += 5)
            for (int x : new int[]{-7, 7}) {
                for (int y = f; y <= f + 7; y++) set(x, y, z, y == f + 4 ? Blocks.SEA_LANTERN : Blocks.QUARTZ_PILLAR);
            }
        // plataforma con anillo de cuarzo
        for (int x = -3; x <= 3; x++)
            for (int z = -66; z <= -60; z++) {
                boolean ring = Math.abs(x) == 3 || z == -66 || z == -60;
                set(x, f - 1, z, ring ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
        for (int x = -2; x <= 2; x++) for (int z = -65; z <= -61; z++) set(x, f, z, Blocks.POLISHED_BLACKSTONE);
        // escalones para subir a la plataforma
        for (int x = -1; x <= 1; x++)
            set(x, f, -60, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        // cuatro columnas con luz alrededor de la vitrina
        for (int[] c : new int[][]{{-3, -66}, {3, -66}, {-3, -60}, {3, -60}}) {
            for (int y = f; y <= f + 4; y++) set(c[0], y, c[1], Blocks.QUARTZ_PILLAR);
            set(c[0], f + 5, c[1], Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        }
        // la vitrina con el artefacto y la luz arcoíris
        showcase(0, f + 1, -63, ModItems.ORBITAL_DESIGNATOR.get().getDefaultInstance(), true, Blocks.POLISHED_BLACKSTONE_BRICKS);
        set(0, f + 7, -63, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN));
    }
}
