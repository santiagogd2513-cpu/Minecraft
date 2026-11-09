package com.example.beautifuldecor.megaphone.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.network.MicControlPacket;
import com.example.beautifuldecor.network.MicStatePacket;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.SpeakerAudioPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * V4.0.0: la parte del cliente de la megafonía: recibe el estado del micrófono y la voz del servidor, mueve la captura y la reproducción
 * cada tick, hace las notas musicales sobre las bocinas que suenan y dibuja el aviso «EN VIVO» cuando el micrófono está abierto.
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PaClient {
    /** Cuánto dura en el menú un aviso del servidor («ocupado», «sin bocinas cerca»...). */
    private static final long MESSAGE_MS = 5000L;
    private static final long ACTIVE_MS = 200L;

    /** Lo último que se oyó de un micrófono: cuándo y por qué bocinas (para las notas musicales). */
    private record Activity(long timeMs, long[] speakers) {}

    @Nullable
    private static MicStatePacket lastState;
    private static long lastStateMs;
    private static final Map<Long, Activity> ACTIVITY = new HashMap<>();
    private static int tickCounter;
    /** V4.0.3: cuántos bloques de voz han llegado del servidor desde que abriste el micrófono (para el aviso del menú). */
    private static int received;
    /**
     * Oírte a ti mismo por las bocinas. Encendido por defecto: lo normal es probarlo solo y querer oírlo; si hace eco (bocina cerca del
     * micrófono y sin audífonos) se apaga desde el menú.
     */
    private static boolean listenSelf = true;

    private PaClient() {}

    // ------------------------------------------------------------------ lo que llega del servidor

    public static void onMicState(MicStatePacket packet) {
        lastState = packet;
        lastStateMs = System.currentTimeMillis();
        String message = packet.message;
        boolean cut = "message.beautifuldecor.pa.busy".equals(message) || "message.beautifuldecor.pa.cut".equals(message)
                || "message.beautifuldecor.pa.too_far".equals(message) || "message.beautifuldecor.pa.mic_missing".equals(message);
        if (cut && MicCapture.isActiveOn(packet.pos)) {
            MicCapture.stop();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!message.isEmpty() && minecraft.player != null && !(minecraft.screen instanceof MicrophoneScreen)) {
            minecraft.player.displayClientMessage(Component.translatable(message, packet.arg), true);
        }
    }

    public static void onSpeakerAudio(SpeakerAudioPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || packet.speakers.length == 0) {
            return;
        }
        received++;
        ACTIVITY.put(packet.mic.asLong(), new Activity(System.currentTimeMillis(), packet.speakers));
        if (!listenSelf && MicCapture.isActiveOn(packet.mic)) {
            return;
        }
        PaPlayback.play(packet, minecraft.gameRenderer.getMainCamera().getPosition(), PaPlayback.blocksVolume());
    }

    // ------------------------------------------------------------------ para el menú

    /** El último estado recibido de ese micrófono, o null si no ha llegado. */
    @Nullable
    public static MicStatePacket stateOf(BlockPos pos) {
        MicStatePacket state = lastState;
        return state != null && state.pos.equals(pos) ? state : null;
    }

    /** El aviso del servidor (ya traducido) si es reciente, o null. */
    @Nullable
    public static Component recentMessage(BlockPos pos) {
        MicStatePacket state = stateOf(pos);
        if (state == null || state.message.isEmpty() || System.currentTimeMillis() - lastStateMs > MESSAGE_MS) {
            return null;
        }
        return Component.translatable(state.message, state.arg);
    }

    public static int received() {
        return received;
    }

    /** Pone en cero los contadores del aviso de audio (al empezar a hablar). */
    public static void resetStats() {
        received = 0;
        PaPlayback.resetStats();
    }

    public static boolean listenSelf() {
        return listenSelf;
    }

    public static void setListenSelf(boolean value) {
        listenSelf = value;
    }

    public static void sendControl(BlockPos pos, byte action) {
        ModMessages.sendToServer(new MicControlPacket(pos, action));
    }

    // ------------------------------------------------------------------ eventos del cliente

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MicCapture.tick();
        PaPlayback.tick();
        tickNotes();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MicCapture.stop();
        PaPlayback.shutdown();
        ACTIVITY.clear();
        lastState = null;
        resetStats();
    }

    /** Notas musicales sobre las bocinas por las que está sonando la voz. */
    private static void tickNotes() {
        if (ACTIVITY.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        boolean emit = (++tickCounter & 3) == 0;
        RandomSource random = minecraft.level == null ? null : minecraft.level.random;
        for (Iterator<Activity> it = ACTIVITY.values().iterator(); it.hasNext(); ) {
            Activity activity = it.next();
            long age = now - activity.timeMs();
            if (age > 1000L) {
                it.remove();
            } else if (emit && age < ACTIVE_MS && random != null) {
                int count = Math.min(3, activity.speakers().length);
                for (int i = 0; i < count; i++) {
                    BlockPos speaker = BlockPos.of(activity.speakers()[random.nextInt(activity.speakers().length)]);
                    minecraft.level.addParticle(ParticleTypes.NOTE, speaker.getX() + 0.5, speaker.getY() + 1.1, speaker.getZ() + 0.5,
                            random.nextInt(24) / 24.0, 0.0, 0.0);
                }
            }
        }
    }

    /** Aviso «EN VIVO» arriba de la pantalla mientras tu micrófono está abierto, con una barrita de nivel. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!MicCapture.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Component text = Component.translatable("hud.beautifuldecor.pa.live");
        int textWidth = minecraft.font.width(text);
        int x = (graphics.guiWidth() - textWidth) / 2;
        int y = 6;
        graphics.fill(x - 14, y - 3, x + textWidth + 6, y + 18, 0x99000000);
        boolean blink = (System.currentTimeMillis() / 500L) % 2L == 0L;
        graphics.fill(x - 10, y + 1, x - 4, y + 7, blink ? 0xFFFF2020 : 0xFF801010);
        graphics.drawString(minecraft.font, text, x, y, 0xFFFFFFFF, false);
        int barWidth = textWidth;
        graphics.fill(x, y + 11, x + barWidth, y + 14, 0xFF303030);
        graphics.fill(x, y + 11, x + Math.round(barWidth * MicCapture.level()), y + 14, MicCapture.gateOpen() ? 0xFF40E060 : 0xFF808080);
    }
}
