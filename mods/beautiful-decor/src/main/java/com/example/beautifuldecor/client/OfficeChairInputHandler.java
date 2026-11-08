package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SeatEntity;
import com.example.beautifuldecor.init.ModBlocks;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.OfficeChairTurnPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class OfficeChairInputHandler {
    private OfficeChairInputHandler() {}

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        int turn;
        if (event.getKey() == GLFW.GLFW_KEY_A) {
            turn = -1;
        } else if (event.getKey() == GLFW.GLFW_KEY_D) {
            turn = 1;
        } else {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null
                || !(minecraft.player.getVehicle() instanceof SeatEntity seat)) {
            return;
        }

        if (minecraft.level == null || seat.getAnchorPos() == null
                || !minecraft.level.getBlockState(seat.getAnchorPos()).is(ModBlocks.OFFICE_CHAIR.get())) {
            return;
        }

        ModMessages.CHANNEL.sendToServer(new OfficeChairTurnPacket(turn));
    }
}
