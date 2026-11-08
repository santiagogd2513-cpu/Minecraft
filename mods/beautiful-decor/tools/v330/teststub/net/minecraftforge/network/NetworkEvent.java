package net.minecraftforge.network;

import net.minecraft.server.level.ServerPlayer;

/** Pieza de mentira de NetworkEvent con su Context (el trabajo encolado se ejecuta enseguida). */
public class NetworkEvent {
    public static class Context {
        public ServerPlayer sender;
        public boolean handled;

        public ServerPlayer getSender() {
            return this.sender;
        }

        public void enqueueWork(Runnable work) {
            work.run();
        }

        public void setPacketHandled(boolean handled) {
            this.handled = handled;
        }
    }
}
