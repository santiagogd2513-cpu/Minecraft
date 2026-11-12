package com.example.beautifuldecor.block;

import com.example.beautifuldecor.block.entity.WideRailBlockEntity;
import com.example.beautifuldecor.entity.RailPath;
import com.example.beautifuldecor.entity.WideArc;
import com.example.beautifuldecor.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.1.0: ensanchar y achicar curvas. Una curva de Minecraft (k = 0) tiene dos rectas que salen de su bloque por dos lados. Cada nivel
 * más (k + 1) la cambia por un arco de círculo de radio k + 0,5 que empalma con las mismas rectas, así que se come un bloque más de cada
 * una. Hace falta que las rectas tengan rieles suficientes y que en los bloques nuevos haya dónde apoyar el riel (un bloque debajo).
 */
public final class WideCurves {
    private WideCurves() {
    }

    public static Block plain(int family) {
        return (family == 0 ? ModBlocks.FREIGHT_RAIL : family == 1 ? ModBlocks.PASSENGER_RAIL : ModBlocks.METRO_RAIL).get();
    }

    public static Block wide(int family) {
        return (family == 0 ? ModBlocks.WIDE_FREIGHT_RAIL : family == 1 ? ModBlocks.WIDE_PASSENGER_RAIL : ModBlocks.WIDE_METRO_RAIL).get();
    }

    private static RailShape shapeOf(RailPath.Side a, RailPath.Side b) {
        for (RailShape s : RailShape.values()) {
            RailPath.Shape ps = RailPath.Shape.valueOf(s.name());
            if (!ps.ascending() && ((ps.sideA == a && ps.sideB == b) || (ps.sideA == b && ps.sideB == a))) {
                return s;
            }
        }
        return RailShape.NORTH_SOUTH;
    }

    private static BlockState railState(int family, RailShape shape) {
        return plain(family).defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
    }

    private static void say(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
    }

    private static BlockPos line(BlockPos corner, RailPath.Side side, int j) {
        return corner.offset(side.dx * j, 0, side.dz * j);
    }

    /**
     * @return true si el clic era sobre una curva de esa familia (se atendió, bien o con un aviso); false si no era una curva
     */
    public static boolean use(ServerLevel level, ServerPlayer player, BlockPos pos, int family, boolean shrink) {
        BlockState state = level.getBlockState(pos);
        BlockPos corner;
        RailPath.Side s1;
        RailPath.Side s2;
        int k;
        if (state.getBlock() instanceof FreightRailBlock rail && rail.family() == family) {
            RailPath.Shape shape = FreightRailBlock.pathShape(state);
            if (!shape.curve) {
                return false;
            }
            corner = pos;
            s1 = shape.sideA;
            s2 = shape.sideB;
            k = 0;
        } else if (state.getBlock() instanceof WideRailBlock wide && wide.family() == family && level.getBlockEntity(pos) instanceof WideRailBlockEntity be) {
            corner = be.corner();
            s1 = be.side1();
            s2 = be.side2();
            k = be.arcLevel();
        } else {
            return false;
        }
        int k2 = shrink ? k - 1 : k + 1;
        if (k2 < 0) {
            say(player, "message.beautifuldecor.wide.min");
            return true;
        }
        int kMax = Math.max(k, k2);

        // ---- bloques que toca el cambio y cómo tienen que quedar
        Map<BlockPos, BlockState> want = new LinkedHashMap<>();
        Map<BlockPos, WideArc.Piece> pieces = new LinkedHashMap<>();
        BlockPos at = corner;
        int y = corner.getY();
        List<WideArc.Cell> newArc = k2 >= 1 ? WideArc.cells(corner.getX(), corner.getZ(), s1, s2, k2) : List.of();
        List<WideArc.Cell> oldArc = k >= 1 ? WideArc.cells(corner.getX(), corner.getZ(), s1, s2, k) : List.of();
        List<WideArc.Cell> maxArc = kMax >= 1 ? WideArc.cells(corner.getX(), corner.getZ(), s1, s2, kMax) : List.of();
        Map<BlockPos, Boolean> touched = new LinkedHashMap<>();
        touched.put(corner, true);
        for (int j = 1; j <= kMax; j++) {
            touched.put(line(corner, s1, j), true);
            touched.put(line(corner, s2, j), true);
        }
        for (List<WideArc.Cell> arc : List.of(newArc, oldArc, maxArc)) {
            for (WideArc.Cell c : arc) {
                touched.put(new BlockPos(c.x, y, c.z), true);
            }
        }
        BlockState wideState = wide(family).defaultBlockState();
        for (BlockPos p : touched.keySet()) {
            BlockState w = Blocks.AIR.defaultBlockState();
            WideArc.Cell inNew = null;
            for (WideArc.Cell c : newArc) {
                if (c.x == p.getX() && c.z == p.getZ()) {
                    inNew = c;
                }
            }
            if (inNew != null) {
                w = wideState;
            } else if (k2 == 0 && p.equals(corner)) {
                w = railState(family, shapeOf(s1, s2));
            } else {
                for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                    RailPath.Side sd = sideIndex == 0 ? s1 : s2;
                    for (int j = k2 + 1; j <= kMax; j++) {
                        if (p.equals(line(corner, sd, j))) {
                            w = railState(family, shapeOf(sd, sd.opposite()));
                        }
                    }
                }
            }
            want.put(p, w);
        }

        // ---- ¿se puede?
        BlockPos below;
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) {
            BlockPos p = e.getKey();
            if (!level.isLoaded(p)) {
                say(player, "message.beautifuldecor.wide.no_room");
                return true;
            }
            BlockState cur = level.getBlockState(p);
            boolean mine = cur.getBlock() instanceof FreightRailBlock fr && fr.family() == family
                    || cur.getBlock() instanceof WideRailBlock wr && wr.family() == family && level.getBlockEntity(p) instanceof WideRailBlockEntity b
                    && b.corner().equals(corner);
            boolean free = cur.isAir() || cur.canBeReplaced();
            if (!mine && !free) {
                say(player, "message.beautifuldecor.wide.no_room");
                return true;
            }
            if (mine && !(cur.getBlock() instanceof WideRailBlock) && !onLines(corner, s1, s2, kMax, p)) {
                say(player, "message.beautifuldecor.wide.no_room");        // un riel de otra vía en medio
                return true;
            }
            if (!e.getValue().isAir() && cur.isAir() || !e.getValue().isAir() && free && !mine) {
                below = p.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    say(player, "message.beautifuldecor.wide.no_room");
                    return true;
                }
            }
        }
        if (!shrink) {
            for (RailPath.Side sd : new RailPath.Side[] {s1, s2}) {
                BlockPos feed = line(corner, sd, k2 + 1);
                BlockState fs = level.getBlockState(feed);
                RailShape need = shapeOf(sd, sd.opposite());
                if (!(fs.getBlock() instanceof FreightRailBlock fr && fr.family() == family && fs.getValue(BlockStateProperties.RAIL_SHAPE) == need)) {
                    say(player, "message.beautifuldecor.wide.need_straight");
                    return true;
                }
            }
        }

        // ---- se hace (sin avisar a los vecinos, para que Minecraft no reacomode los rieles de al lado)
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) {
            BlockPos p = e.getKey();
            level.setBlock(p, e.getValue(), 2);
            if (e.getValue().is(wideState.getBlock()) && level.getBlockEntity(p) instanceof WideRailBlockEntity be) {
                be.set(corner, s1, s2, k2);
                level.sendBlockUpdated(p, e.getValue(), e.getValue(), 3);
            }
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.4f);
        player.displayClientMessage(Component.translatable("message.beautifuldecor.wide.level", k2), true);
        return true;
    }

    private static boolean onLines(BlockPos corner, RailPath.Side s1, RailPath.Side s2, int kMax, BlockPos p) {
        if (p.equals(corner)) {
            return true;
        }
        for (int j = 1; j <= kMax; j++) {
            if (p.equals(line(corner, s1, j)) || p.equals(line(corner, s2, j))) {
                return true;
            }
        }
        return false;
    }
}
