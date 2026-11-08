package com.example.beautifuldecor.megaphone.client;

import com.example.beautifuldecor.block.MicrophoneBlock;
import com.example.beautifuldecor.megaphone.ImaAdpcm;
import com.example.beautifuldecor.megaphone.PaDsp;
import com.example.beautifuldecor.megaphone.PaServer;
import com.example.beautifuldecor.network.MicAudioPacket;
import com.example.beautifuldecor.network.MicControlPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * V4.0.0: captura de tu micrófono para la megafonía. Un hilo aparte lee el micrófono, lo procesa (compuerta de ruido, nivel automático,
 * ADPCM) y deja los bloques en una cola; el tick del cliente los manda al servidor.
 */
public final class MicCapture {
    public enum Status { OFF, STARTING, OK, SILENT, ERROR }

    /** Una sesión de captura; si se apaga ({@link #alive} = false) su hilo termina y ya no escribe nada. */
    private static final class Session {
        volatile boolean alive = true;
    }

    /** Cuántos bloques seguidos de silencio digital absoluto (0 en todas las muestras) antes de avisar que no llega sonido (3 s). */
    private static final int SILENT_FRAMES = 75;

    private static volatile Status status = Status.OFF;
    private static volatile String backendName = "";
    private static volatile String error = "";
    private static volatile float level;
    private static volatile boolean gateOpen;
    private static volatile float sensitivity = 0.55f;
    @Nullable
    private static volatile BlockPos micPos;
    @Nullable
    private static volatile Session current;
    private static final ConcurrentLinkedQueue<byte[]> OUT = new ConcurrentLinkedQueue<>();

    private MicCapture() {}

    // ------------------------------------------------------------------ estado para el menú y el HUD

    public static Status status() {
        return status;
    }

    public static String backendName() {
        return backendName;
    }

    public static String error() {
        return error;
    }

    /** Nivel del micrófono (0 a 1) para la barra del menú. */
    public static float level() {
        return level;
    }

    public static boolean gateOpen() {
        return gateOpen;
    }

    public static float sensitivity() {
        return sensitivity;
    }

    public static void setSensitivity(float value) {
        sensitivity = Math.max(0.0f, Math.min(1.0f, value));
    }

    /** El micrófono (bloque) por el que estás hablando ahora, o null. */
    @Nullable
    public static BlockPos micPos() {
        return micPos;
    }

    public static boolean isActive() {
        return micPos != null;
    }

    public static boolean isActiveOn(BlockPos pos) {
        BlockPos active = micPos;
        return active != null && active.equals(pos);
    }

    // ------------------------------------------------------------------ arrancar y parar

    /** Empieza a capturar tu micrófono para hablar por el micrófono-bloque {@code pos}. */
    public static void start(BlockPos pos) {
        stop();
        Session session = new Session();
        current = session;
        micPos = pos.immutable();
        status = Status.STARTING;
        error = "";
        backendName = "";
        level = 0.0f;
        gateOpen = false;
        OUT.clear();
        Thread thread = new Thread(() -> run(session), "BeautifulDecor-Mic");
        thread.setDaemon(true);
        thread.start();
    }

    /** Deja de capturar (no avisa al servidor; eso lo hace quien lo llama). */
    public static void stop() {
        Session session = current;
        current = null;
        if (session != null) {
            session.alive = false;
        }
        micPos = null;
        OUT.clear();
        status = Status.OFF;
        level = 0.0f;
        gateOpen = false;
    }

    // ------------------------------------------------------------------ el hilo de captura

    private static MicBackend open(Session session) {
        StringBuilder problems = new StringBuilder();
        try {
            MicBackend openAl = new OpenAlMicBackend();
            openAl.open();
            return openAl;
        } catch (Throwable t) {
            problems.append("OpenAL: ").append(describe(t));
        }
        if (session.alive) {
            try {
                MicBackend javaSound = new JavaSoundMicBackend();
                javaSound.open();
                return javaSound;
            } catch (Throwable t) {
                problems.append(" | JavaSound: ").append(describe(t));
            }
        }
        throw new IllegalStateException(problems.toString());
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : " (" + message + ")");
    }

    private static void run(Session session) {
        MicBackend backend = null;
        try {
            backend = open(session);
            if (!session.alive) {
                return;
            }
            if (current == session) {
                backendName = backend.name();
                status = Status.OK;
            }

            ImaAdpcm.Encoder encoder = new ImaAdpcm.Encoder();
            PaDsp.Gate gate = new PaDsp.Gate();
            PaDsp.Agc agc = new PaDsp.Agc();
            short[] chunk = new short[PaDsp.FRAME * 4];
            short[] frame = new short[PaDsp.FRAME];
            int filled = 0;
            int zeroFrames = 0;

            while (session.alive) {
                int n = backend.read(chunk, chunk.length);
                if (n <= 0) {
                    Thread.sleep(8L);
                    continue;
                }
                int at = 0;
                while (at < n) {
                    int take = Math.min(n - at, PaDsp.FRAME - filled);
                    System.arraycopy(chunk, at, frame, filled, take);
                    filled += take;
                    at += take;
                    if (filled < PaDsp.FRAME) {
                        continue;
                    }
                    filled = 0;

                    boolean allZero = true;
                    for (int i = 0; i < PaDsp.FRAME; i++) {
                        if (frame[i] != 0) {
                            allZero = false;
                            break;
                        }
                    }
                    zeroFrames = allZero ? zeroFrames + 1 : 0;
                    if (session.alive && current == session) {
                        status = zeroFrames >= SILENT_FRAMES ? Status.SILENT : Status.OK;
                    }

                    float rms = PaDsp.rms(frame, PaDsp.FRAME);
                    boolean send = gate.accept(rms, PaDsp.threshold(sensitivity));
                    if (session.alive) {
                        level = Math.max(PaDsp.meter(rms), level * 0.85f);
                        gateOpen = send;
                    }
                    if (send && session.alive) {
                        PaDsp.applyGain(frame, PaDsp.FRAME, agc.update(rms));
                        OUT.add(encoder.encode(frame, 0, PaDsp.FRAME));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            if (session.alive && current == session) {
                error = describe(t);
                status = Status.ERROR;
            }
        } finally {
            if (backend != null) {
                try {
                    backend.close();
                } catch (Throwable ignored) {
                    // al cerrar ya no hay nada que hacer
                }
            }
        }
    }

    // ------------------------------------------------------------------ cada tick del cliente

    /** Manda al servidor lo capturado, y apaga el micrófono si algo falló o te alejaste. */
    public static void tick() {
        BlockPos pos = micPos;
        if (pos == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) {
            stop();
            return;
        }
        if (status == Status.ERROR) {
            ModMessages.sendToServer(new MicControlPacket(pos, MicControlPacket.STOP_TALK));
            Session session = current;
            current = null;
            if (session != null) {
                session.alive = false;
            }
            micPos = null;                       // el estado ERROR y su texto se quedan para que el menú los muestre
            OUT.clear();
            return;
        }
        double limit = PaServer.KEEP_RANGE - 0.5;
        Vec3 center = Vec3.atCenterOf(pos);
        boolean gone = !(minecraft.level.getBlockState(pos).getBlock() instanceof MicrophoneBlock);
        if (gone || minecraft.player.distanceToSqr(center) > limit * limit) {
            ModMessages.sendToServer(new MicControlPacket(pos, MicControlPacket.STOP_TALK));
            stop();
            return;
        }
        int excess = OUT.size() - 12;               // si el juego se trabó, lo viejo ya no sirve
        while (excess-- > 0) {
            OUT.poll();
        }
        int sent = 0;
        byte[] block;
        while (sent < 6 && (block = OUT.poll()) != null) {
            ModMessages.sendToServer(new MicAudioPacket(pos, block));
            sent++;
        }
    }
}
