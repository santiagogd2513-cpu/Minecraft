package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.TrainInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * V4.8.0: teclas de la locomotora. W acelera, S frena (y da marcha atrás si ya está parado), Espacio es el freno de emergencia.
 * Shift te baja de la cabina (como en cualquier vehículo). Se manda al servidor cuando cambia y, mientras tanto, cada 5 ticks.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TrainClientEvents {
    private static int lastFlags = -1;
    private static int sinceSend;

    private TrainClientEvents() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player
                || !(minecraft.player.getVehicle() instanceof TrainCarEntity car) || !car.variant().isLoco()
                || car.getPassengers().indexOf(minecraft.player) != 0) {
            lastFlags = -1;
            return;
        }
        Input input = event.getInput();
        int flags = 0;
        if (input.up) {
            flags |= TrainCarEntity.INPUT_FORWARD;
        }
        if (input.down) {
            flags |= TrainCarEntity.INPUT_BACK;
        }
        if (input.jumping) {
            flags |= TrainCarEntity.INPUT_BRAKE;
        }
        if (flags != lastFlags || ++sinceSend >= 5) {
            ModMessages.CHANNEL.sendToServer(new TrainInputPacket(car.getId(), flags));
            lastFlags = flags;
            sinceSend = 0;
        }
        // lo que se quita para que el jugador no camine ni salte dentro de la cabina
        input.up = false;
        input.down = false;
        input.jumping = false;
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
    }
}
