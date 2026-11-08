package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.DroneEntity;
import com.example.beautifuldecor.network.DroneControlPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Lado cliente del dron (V1.8.5).
 * <ul>
 *   <li>Entra solo a la cámara del dron cuando el servidor te pone como piloto.</li>
 *   <li>WASD mueve, Espacio sube, Shift baja, R sale. El jugador no se mueve ni salta ni se agacha.</li>
 *   <li>La cámara mira exactamente a donde mira el jugador (cada fotograma), así no hay tirones.</li>
 *   <li>Sin mano, sin resaltado de bloque y sin poder picar, poner, usar ni atacar nada.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class DroneClientEvents {
    private static final Map<Integer, DroneMotorSound> MOTOR_SOUNDS = new HashMap<>();
    private static final Map<Integer, Integer> MOTOR_START_DELAYS = new HashMap<>();
    private static int reenterCooldown;

    private DroneClientEvents() {}

    /** El dron que este jugador está volando ahora mismo, o null si no controla ninguno. */
    private static DroneEntity controlledDrone(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.getCameraEntity() instanceof DroneEntity drone
                && drone.isLocalControl() && !drone.isRemoved()) {
            return drone;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // Entrar / salir del control
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (event.phase == TickEvent.Phase.END) {
                stopAllMotorSounds(minecraft);
                reenterCooldown = 0;
            }
            return;
        }

        validateCamera(minecraft);
        if (event.phase == TickEvent.Phase.START) {
            tickStart(minecraft);
        } else {
            tickEnd(minecraft);
        }
    }

    /** Si la cámara quedó en un dron que ya no es válido (apagado, borrado, de otro mundo), vuelve al jugador. */
    private static void validateCamera(Minecraft minecraft) {
        if (minecraft.getCameraEntity() instanceof DroneEntity drone
                && (drone.isRemoved() || drone.level() != minecraft.level
                || !drone.isPilotedBy(minecraft.player))) {
            leaveDroneCamera(minecraft, drone);
        }
    }

    private static void tickStart(Minecraft minecraft) {
        DroneEntity drone = controlledDrone(minecraft);
        if (drone == null) {
            return;
        }

        // Sin clic izquierdo/derecho sostenido: así no se pica ni se usa nada mientras se vuela.
        minecraft.options.keyAttack.setDown(false);
        minecraft.options.keyUse.setDown(false);
        minecraft.player.setDeltaMovement(Vec3.ZERO);

        float forward = 0.0f;
        float strafe = 0.0f;
        float vertical = 0.0f;
        if (minecraft.screen == null) {
            if (minecraft.options.keyUp.isDown()) forward += 1.0f;
            if (minecraft.options.keyDown.isDown()) forward -= 1.0f;
            if (minecraft.options.keyRight.isDown()) strafe += 1.0f;
            if (minecraft.options.keyLeft.isDown()) strafe -= 1.0f;
            if (minecraft.options.keyJump.isDown()) vertical += 1.0f;
            if (minecraft.options.keyShift.isDown()) vertical -= 1.0f;
        }
        drone.setLocalInput(forward, strafe, vertical);
        drone.setViewRotation(minecraft.player.getYRot(), Mth.clamp(minecraft.player.getXRot(), -90.0f, 90.0f));
    }

    private static void tickEnd(Minecraft minecraft) {
        if (reenterCooldown > 0) {
            reenterCooldown--;
        }

        DroneEntity drone = controlledDrone(minecraft);
        if (drone != null) {
            if (!minecraft.isPaused()) {
                ModMessages.CHANNEL.sendToServer(new DroneControlPacket(drone.getId(),
                        drone.getX(), drone.getY(), drone.getZ(),
                        minecraft.player.getYRot(), Mth.clamp(minecraft.player.getXRot(), -90.0f, 90.0f)));
            }
        } else if (reenterCooldown == 0 && !(minecraft.getCameraEntity() instanceof DroneEntity)) {
            // El servidor ya te puso a pilotar un dron (control remoto sobre el bloque o sobre la entidad).
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof DroneEntity piloted && !piloted.isRemoved()
                        && piloted.isPilotedBy(minecraft.player)) {
                    enterDroneCamera(minecraft, piloted);
                    break;
                }
            }
        }

        updateMotorSounds(minecraft);
    }

    private static void enterDroneCamera(Minecraft minecraft, DroneEntity drone) {
        minecraft.setCameraEntity(drone);
        drone.beginLocalControl(minecraft.player.position(), minecraft.player.getYRot(),
                Mth.clamp(minecraft.player.getXRot(), -90.0f, 90.0f));
    }

    private static void leaveDroneCamera(Minecraft minecraft, DroneEntity drone) {
        if (drone != null) {
            drone.endLocalControl();
        }
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
            minecraft.player.setDeltaMovement(Vec3.ZERO);
        }
        reenterCooldown = 40;
    }

    @SubscribeEvent
    public static void onDroneKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = controlledDrone(minecraft);
        if (drone == null || minecraft.screen != null
                || event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != GLFW.GLFW_KEY_R) {
            return;
        }
        // El servidor apaga el dron y lo vuelve a poner como bloque en el suelo.
        ModMessages.CHANNEL.sendToServer(DroneControlPacket.exit(drone.getId()));
        leaveDroneCamera(minecraft, drone);
    }

    // ------------------------------------------------------------------------------------------------
    // Cámara
    // ------------------------------------------------------------------------------------------------

    /** Cada fotograma (no cada tick): la cámara del dron copia la vista del jugador. Es lo que quita los tirones. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = controlledDrone(minecraft);
        if (drone == null) {
            return;
        }
        float partialTick = event.renderTickTime;
        drone.setViewRotation(minecraft.player.getViewYRot(partialTick),
                Mth.clamp(minecraft.player.getViewXRot(partialTick), -90.0f, 90.0f));
    }

    /** Pequeña inclinación al acelerar, suavizada entre ticks. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!(Minecraft.getInstance().getCameraEntity() instanceof DroneEntity drone)) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        event.setPitch(event.getPitch() + drone.getLeanForward(partialTick) * 3.5f);
        event.setRoll(event.getRoll() - drone.getLeanSide(partialTick) * 4.8f);
    }

    // ------------------------------------------------------------------------------------------------
    // Sin mano, sin bloques, sin interacciones
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    /** Clic izquierdo (picar/atacar), derecho (usar/poner) y clic central: todo cancelado mientras se vuela. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** El jugador se queda quieto: sin caminar, saltar ni agacharse (las teclas las lee el dron). */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (controlledDrone(Minecraft.getInstance()) == null) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0f;
        input.forwardImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide && controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide && controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide && controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide && controlledDrone(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Pantalla
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void renderDroneControls(RenderGuiOverlayEvent.Post event) {
        // Este evento llega una vez por cada capa del HUD; se dibuja solo con la mira para no repetir el texto.
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = controlledDrone(minecraft);
        if (drone == null) {
            return;
        }

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        String controls = Component.translatable("hud.beautifuldecor.drone_controls").getString();
        int controlsX = Math.max(6, (width - minecraft.font.width(controls)) / 2);
        event.getGuiGraphics().drawString(minecraft.font, controls, controlsX, height - 16, 0xFFFFFFFF, true);

        double distance = drone.position().distanceTo(minecraft.player.position());
        int maxRange = (int) DroneEntity.MAX_RANGE;
        String range = Component.translatable("hud.beautifuldecor.drone_range",
                String.valueOf((int) Math.round(distance)), String.valueOf(maxRange)).getString();
        int rangeX = Math.max(6, (width - minecraft.font.width(range)) / 2);
        int rangeColor = distance > DroneEntity.MAX_RANGE * 0.85 ? 0xFFFFAA55 : 0xFFD0D0D0;
        event.getGuiGraphics().drawString(minecraft.font, range, rangeX, height - 28, rangeColor, true);
    }

    // ------------------------------------------------------------------------------------------------
    // Sonido del motor
    // ------------------------------------------------------------------------------------------------

    private static void updateMotorSounds(Minecraft minecraft) {
        Set<Integer> pilotedIds = new HashSet<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof DroneEntity drone && drone.isPiloted()) {
                int id = drone.getId();
                pilotedIds.add(id);
                if (!MOTOR_SOUNDS.containsKey(id)) {
                    int remaining = MOTOR_START_DELAYS.getOrDefault(id, 76);
                    if (remaining <= 0) {
                        DroneMotorSound sound = new DroneMotorSound(drone);
                        MOTOR_SOUNDS.put(id, sound);
                        MOTOR_START_DELAYS.remove(id);
                        minecraft.getSoundManager().play(sound);
                    } else {
                        MOTOR_START_DELAYS.put(id, remaining - 1);
                    }
                }
            }
        }
        MOTOR_START_DELAYS.keySet().removeIf(id -> !pilotedIds.contains(id));

        Iterator<Map.Entry<Integer, DroneMotorSound>> iterator = MOTOR_SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            DroneMotorSound sound = iterator.next().getValue();
            if (sound.isFinished()) {
                minecraft.getSoundManager().stop(sound);
                iterator.remove();
            }
        }
    }

    private static void stopAllMotorSounds(Minecraft minecraft) {
        for (DroneMotorSound sound : MOTOR_SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        MOTOR_SOUNDS.clear();
        MOTOR_START_DELAYS.clear();
    }
}
