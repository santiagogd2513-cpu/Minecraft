package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.RcCarEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.RcCarControlPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 * Lado cliente del carro RC (V3.1.0), hecho como el del dron (DroneClientEvents).
 * <ul>
 *   <li>Entra solo a la cámara del carro cuando el servidor te pone como piloto.</li>
 *   <li>W acelera, S frena y da reversa, A/D giran, Espacio es el freno de mano y R sale. El jugador no camina ni salta.</li>
 *   <li>La cámara mira adonde mira el jugador (puedes mirar a los lados) y gira junto con el carro al doblar.</li>
 *   <li>Sin mano, sin resaltado de bloque y sin poder picar, poner, usar ni atacar nada.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class RcCarClientEvents {
    private static final Map<Integer, RcCarMotorSound> MOTOR_SOUNDS = new HashMap<>();
    private static final Map<Integer, Integer> MOTOR_START_DELAYS = new HashMap<>();
    private static int reenterCooldown;

    private RcCarClientEvents() {}

    /** El carro que este jugador está manejando ahora mismo, o null si no controla ninguno. */
    private static RcCarEntity controlledCar(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.getCameraEntity() instanceof RcCarEntity car
                && car.isLocalControl() && !car.isRemoved()) {
            return car;
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

    /** Si la cámara quedó en un carro que ya no es válido (apagado, borrado, de otro mundo), vuelve al jugador. */
    private static void validateCamera(Minecraft minecraft) {
        if (minecraft.getCameraEntity() instanceof RcCarEntity car
                && (car.isRemoved() || car.level() != minecraft.level || !car.isPilotedBy(minecraft.player))) {
            leaveCarCamera(minecraft, car);
        }
    }

    private static void tickStart(Minecraft minecraft) {
        RcCarEntity car = controlledCar(minecraft);
        if (car == null) {
            return;
        }

        // Sin clic izquierdo/derecho sostenido: así no se pica ni se usa nada mientras se maneja.
        minecraft.options.keyAttack.setDown(false);
        minecraft.options.keyUse.setDown(false);
        Vec3 motion = minecraft.player.getDeltaMovement();
        minecraft.player.setDeltaMovement(0.0, motion.y, 0.0);

        boolean forward = false;
        boolean back = false;
        boolean left = false;
        boolean right = false;
        boolean brake = false;
        if (minecraft.screen == null) {
            forward = minecraft.options.keyUp.isDown();
            back = minecraft.options.keyDown.isDown();
            left = minecraft.options.keyLeft.isDown();
            right = minecraft.options.keyRight.isDown();
            brake = minecraft.options.keyJump.isDown();
        }
        car.setLocalInput(forward, back, left, right, brake);
        car.setViewRotation(minecraft.player.getYRot(), Mth.clamp(minecraft.player.getXRot(), -90.0f, 90.0f));
    }

    private static void tickEnd(Minecraft minecraft) {
        if (reenterCooldown > 0) {
            reenterCooldown--;
        }

        RcCarEntity car = controlledCar(minecraft);
        if (car != null) {
            if (!minecraft.isPaused()) {
                // La vista gira junto con el carro (como en una barca): se suma al jugador lo que giró el carro en este tick.
                float delta = car.getYawDelta();
                if (delta != 0.0f) {
                    minecraft.player.setYRot(minecraft.player.getYRot() + delta);
                }
                ModMessages.CHANNEL.sendToServer(new RcCarControlPacket(car.getId(),
                        car.getX(), car.getY(), car.getZ(), car.getYRot()));
            }
        } else if (reenterCooldown == 0 && !(minecraft.getCameraEntity() instanceof RcCarEntity)) {
            // El servidor ya te puso a manejar un carro (control remoto sobre el bloque o sobre la entidad).
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof RcCarEntity piloted && !piloted.isRemoved()
                        && piloted.isPilotedBy(minecraft.player)) {
                    enterCarCamera(minecraft, piloted);
                    break;
                }
            }
        }

        updateMotorSounds(minecraft);
    }

    private static void enterCarCamera(Minecraft minecraft, RcCarEntity car) {
        Player player = minecraft.player;
        // La vista arranca mirando hacia donde mira el carro, un poco hacia abajo.
        float heading = car.getYRot();
        player.setYRot(heading);
        player.yRotO = heading;
        player.setXRot(8.0f);
        player.xRotO = 8.0f;
        minecraft.setCameraEntity(car);
        car.beginLocalControl(player.position(), heading, 8.0f);
    }

    private static void leaveCarCamera(Minecraft minecraft, RcCarEntity car) {
        if (car != null) {
            car.endLocalControl();
        }
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        reenterCooldown = 40;
    }

    @SubscribeEvent
    public static void onCarKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        RcCarEntity car = controlledCar(minecraft);
        if (car == null || minecraft.screen != null
                || event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != GLFW.GLFW_KEY_R) {
            return;
        }
        // El servidor apaga el carro y lo vuelve a poner como bloque en el suelo.
        ModMessages.CHANNEL.sendToServer(RcCarControlPacket.exit(car.getId()));
        leaveCarCamera(minecraft, car);
    }

    // ------------------------------------------------------------------------------------------------
    // Cámara
    // ------------------------------------------------------------------------------------------------

    /** Cada fotograma (no cada tick): la cámara del carro copia la vista del jugador. Es lo que quita los tirones. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RcCarEntity car = controlledCar(minecraft);
        if (car == null) {
            return;
        }
        float partialTick = event.renderTickTime;
        car.setViewRotation(minecraft.player.getViewYRot(partialTick),
                Mth.clamp(minecraft.player.getViewXRot(partialTick), -90.0f, 90.0f));
    }

    /** Sensación de velocidad: el campo de visión se abre cuanto más rápido va el carro. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        RcCarEntity car = controlledCar(Minecraft.getInstance());
        if (car == null) {
            return;
        }
        float k = Mth.clamp(Math.abs(car.getVisualSpeed()) / RcCarEntity.TOP_SPEED, 0.0f, 1.0f);
        event.setFOV(event.getFOV() + 12.0 * k * k);
    }

    // ------------------------------------------------------------------------------------------------
    // Sin mano, sin bloques, sin interacciones
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    /** Clic izquierdo (picar/atacar), derecho (usar/poner) y clic central: todo cancelado mientras se maneja. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** El jugador se queda quieto: sin caminar, saltar ni agacharse (las teclas las lee el carro). */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (controlledCar(Minecraft.getInstance()) == null) {
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
        if (event.getLevel().isClientSide && controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide && controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide && controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide && controlledCar(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Pantalla
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void renderCarHud(RenderGuiOverlayEvent.Post event) {
        // Este evento llega una vez por cada capa del HUD; se dibuja solo con la mira para no repetir el texto.
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RcCarEntity car = controlledCar(minecraft);
        if (car == null || minecraft.options.hideGui) {
            return;
        }

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        String controls = Component.translatable("hud.beautifuldecor.rc_car_controls").getString();
        event.getGuiGraphics().drawString(minecraft.font, controls,
                Math.max(6, (width - minecraft.font.width(controls)) / 2), height - 58, 0xFFFFFFFF, true);

        double distance = car.position().distanceTo(minecraft.player.position());
        String range = Component.translatable("hud.beautifuldecor.rc_car_range",
                String.valueOf((int) Math.round(distance)), String.valueOf((int) RcCarEntity.MAX_RANGE)).getString();
        int rangeColor = distance > RcCarEntity.MAX_RANGE * 0.85 ? 0xFFFFAA55 : 0xFFD0D0D0;
        event.getGuiGraphics().drawString(minecraft.font, range,
                Math.max(6, (width - minecraft.font.width(range)) / 2), height - 70, rangeColor, true);

        String speed = Component.translatable("hud.beautifuldecor.rc_car_speed",
                String.valueOf(Math.round(Math.abs(car.getVisualSpeed()) * 20.0f))).getString();
        event.getGuiGraphics().drawString(minecraft.font, speed,
                Math.max(6, (width - minecraft.font.width(speed)) / 2), height - 82, 0xFFE8F5B0, true);
    }

    // ------------------------------------------------------------------------------------------------
    // Sonido del motor
    // ------------------------------------------------------------------------------------------------

    private static void updateMotorSounds(Minecraft minecraft) {
        Set<Integer> pilotedIds = new HashSet<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof RcCarEntity car && car.isPiloted()) {
                int id = car.getId();
                pilotedIds.add(id);
                if (!MOTOR_SOUNDS.containsKey(id)) {
                    int remaining = MOTOR_START_DELAYS.getOrDefault(id, 14);
                    if (remaining <= 0) {
                        RcCarMotorSound sound = new RcCarMotorSound(car);
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

        Iterator<Map.Entry<Integer, RcCarMotorSound>> iterator = MOTOR_SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            RcCarMotorSound sound = iterator.next().getValue();
            if (sound.isFinished()) {
                minecraft.getSoundManager().stop(sound);
                iterator.remove();
            }
        }
    }

    private static void stopAllMotorSounds(Minecraft minecraft) {
        for (RcCarMotorSound sound : MOTOR_SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        MOTOR_SOUNDS.clear();
        MOTOR_START_DELAYS.clear();
    }
}
