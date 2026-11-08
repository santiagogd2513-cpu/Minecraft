package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.LightSwitchBlock;
import com.example.beautifuldecor.block.ToggleBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Stores the ceiling lights linked to a wall switch and persists them in the world save. */
public class LightSwitchBlockEntity extends BlockEntity {
    private static final String ACTIVE_SWITCH_TAG = "beautifuldecor_active_light_switch";
    private static final int MAX_LINKED_LIGHTS = 64;
    private static final double MAX_LINK_DISTANCE_SQUARED = 64.0 * 64.0;

    private final List<BlockPos> linkedLights = new ArrayList<>();

    public LightSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_SWITCH.get(), pos, state);
    }

    public static void toggleLinkMode(Level level, BlockPos switchPos, Player player) {
        CompoundTag playerData = player.getPersistentData();
        if (isSelectedSwitch(playerData, level, switchPos)) {
            playerData.remove(ACTIVE_SWITCH_TAG);
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.link_stopped"), true);
            return;
        }

        CompoundTag selected = new CompoundTag();
        selected.putString("Dimension", level.dimension().location().toString());
        selected.putLong("Position", switchPos.asLong());
        playerData.put(ACTIVE_SWITCH_TAG, selected);
        player.displayClientMessage(Component.translatable(
                "message.beautifuldecor.light_switch.link_started"), true);
    }

    private static boolean isSelectedSwitch(CompoundTag playerData, Level level, BlockPos switchPos) {
        if (!playerData.contains(ACTIVE_SWITCH_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag selected = playerData.getCompound(ACTIVE_SWITCH_TAG);
        return selected.getString("Dimension").equals(level.dimension().location().toString())
                && BlockPos.of(selected.getLong("Position")).equals(switchPos);
    }

    /**
     * Called before a switchable light toggles. Returns true when the click was consumed
     * to link the light to the player's selected switch.
     */
    public static boolean tryLinkActiveSwitch(Level level, BlockPos lightPos, Player player) {
        if (level.isClientSide) {
            return false;
        }

        CompoundTag playerData = player.getPersistentData();
        if (!playerData.contains(ACTIVE_SWITCH_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag selected = playerData.getCompound(ACTIVE_SWITCH_TAG);
        if (!selected.getString("Dimension").equals(level.dimension().location().toString())) {
            playerData.remove(ACTIVE_SWITCH_TAG);
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.wrong_dimension"), true);
            return true;
        }

        BlockPos switchPos = BlockPos.of(selected.getLong("Position"));
        if (switchPos.distToCenterSqr(lightPos.getX() + 0.5, lightPos.getY() + 0.5,
                lightPos.getZ() + 0.5) > MAX_LINK_DISTANCE_SQUARED) {
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.too_far"), true);
            return true;
        }

        if (!level.hasChunkAt(switchPos)) {
            playerData.remove(ACTIVE_SWITCH_TAG);
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.switch_missing"), true);
            return true;
        }

        if (!(level.getBlockEntity(switchPos) instanceof LightSwitchBlockEntity lightSwitch)) {
            playerData.remove(ACTIVE_SWITCH_TAG);
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.switch_missing"), true);
            return true;
        }

        return lightSwitch.linkLight(lightPos, player);
    }

    private boolean linkLight(BlockPos lightPos, Player player) {
        if (linkedLights.contains(lightPos)) {
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.already_linked"), true);
            return true;
        }
        if (linkedLights.size() >= MAX_LINKED_LIGHTS) {
            player.displayClientMessage(Component.translatable(
                    "message.beautifuldecor.light_switch.limit_reached", MAX_LINKED_LIGHTS), true);
            return true;
        }

        BlockState lightState = level.getBlockState(lightPos);
        if (!(lightState.getBlock() instanceof ToggleBlock light) || !light.isSwitchableLight()) {
            return false;
        }

        linkedLights.add(lightPos.immutable());
        boolean turnOn = getBlockState().getValue(LightSwitchBlock.ON);
        level.setBlock(lightPos, lightState.setValue(ToggleBlock.OPEN, turnOn), 3);
        setChanged();
        player.displayClientMessage(Component.translatable(
                "message.beautifuldecor.light_switch.light_linked"), true);
        return true;
    }

    public void clearLinkedLights() {
        if (!linkedLights.isEmpty()) {
            linkedLights.clear();
            setChanged();
        }
    }

    public void setLinkedLightsOn(boolean turnOn) {
        if (level == null || level.isClientSide) {
            return;
        }

        boolean removedStaleLight = false;
        Iterator<BlockPos> iterator = linkedLights.iterator();
        while (iterator.hasNext()) {
            BlockPos lightPos = iterator.next();
            if (!level.hasChunkAt(lightPos)) {
                continue;
            }

            BlockState lightState = level.getBlockState(lightPos);
            if (lightState.getBlock() instanceof ToggleBlock light && light.isSwitchableLight()) {
                if (lightState.getValue(ToggleBlock.OPEN) != turnOn) {
                    level.setBlock(lightPos, lightState.setValue(ToggleBlock.OPEN, turnOn), 3);
                }
            } else {
                iterator.remove();
                removedStaleLight = true;
            }
        }

        if (removedStaleLight) {
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag lights = new ListTag();
        for (BlockPos lightPos : linkedLights) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Position", lightPos.asLong());
            lights.add(entry);
        }
        tag.put("LinkedLights", lights);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linkedLights.clear();
        ListTag lights = tag.getList("LinkedLights", Tag.TAG_COMPOUND);
        for (int index = 0; index < lights.size(); index++) {
            linkedLights.add(BlockPos.of(lights.getCompound(index).getLong("Position")));
        }
    }
}
