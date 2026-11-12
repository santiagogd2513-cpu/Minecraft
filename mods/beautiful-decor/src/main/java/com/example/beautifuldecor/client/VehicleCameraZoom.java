package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.entity.BicycleEntity;
import com.example.beautifuldecor.entity.DeliveryTruckEntity;
import com.example.beautifuldecor.entity.HelicopterEntity;
import com.example.beautifuldecor.entity.MotorcycleEntity;
import com.example.beautifuldecor.entity.PickupEntity;
import com.example.beautifuldecor.entity.SupercarEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * V4.4.0: cámara en tercera persona que se aleja según el tamaño del vehículo (con el avión, la cámara se quedaba dentro del avión).
 * Minecraft deja la cámara a 4 bloques; aquí se la retira lo que falte hasta {@link #distanceFor}, sin atravesar bloques.
 * Nunca lanza errores: si no se puede mover la cámara en esta instalación, queda como siempre.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VehicleCameraZoom {
    private static final double VANILLA = 4.0;
    private static boolean looked;
    private static Method move;
    private static boolean broken;

    private VehicleCameraZoom() {
    }

    /** Distancia de la cámara (bloques) para que se vea todo el vehículo. */
    static double distanceFor(Entity vehicle) {
        if (vehicle instanceof com.example.beautifuldecor.entity.TrainCarEntity) {
            return 32.0;                    // V4.9.0: locomotora de 20 m y vagones de 14 m
        }
        if (vehicle instanceof AirlinerEntity) {
            return 75.0;                    // 62 de largo y 58 de envergadura
        }
        if (vehicle instanceof HelicopterEntity) {
            return 14.0;
        }
        if (vehicle instanceof DeliveryTruckEntity) {
            return 10.5;
        }
        if (vehicle instanceof SupercarEntity) {
            return 8.5;
        }
        if (vehicle instanceof PickupEntity) {
            return 8.0;
        }
        if (vehicle instanceof MotorcycleEntity) {
            return 5.5;
        }
        if (vehicle instanceof BicycleEntity) {
            return 5.0;
        }
        return VANILLA;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (broken) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = event.getCamera();
            if (mc.player == null || mc.level == null || !camera.isDetached() || mc.player.getVehicle() == null) {
                return;
            }
            Entity vehicle = mc.player.getVehicle();
            double extra = distanceFor(vehicle) - VANILLA;
            if (extra <= 0.01) {
                return;
            }
            Method m = moveMethod();
            if (m == null) {
                return;
            }
            // sin atravesar bloques: se corta donde choca con algo
            Vector3f look = camera.getLookVector();
            Vec3 start = camera.getPosition();
            Vec3 end = start.add(-look.x() * extra, -look.y() * extra, -look.z() * extra);
            HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera.getEntity()));
            if (hit.getType() != HitResult.Type.MISS) {
                extra = Math.max(0.0, hit.getLocation().distanceTo(start) - 0.2);
            }
            if (extra > 0.01) {
                m.invoke(camera, -Mth.clamp(extra, 0.0, 200.0), 0.0, 0.0);
            }
        } catch (Throwable t) {
            broken = true;
            System.err.println("[BeautifulDecor] El alejamiento de la cámara en tercera persona se apagó por un error: " + t);
        }
    }

    /**
     * Camera.move(adelante, arriba, izquierda) es protegido. Se busca por forma, entre los métodos (double, double, double) sin resultado, y se distingue de
     * setPosition(x, y, z) probándolo en una cámara nueva: move(1,0,0) avanza por su vector de mirada y setPosition(1,0,0) pone x = 1.
     */
    private static Method moveMethod() {
        if (looked) {
            return move;
        }
        looked = true;
        try {
            for (Method m : Camera.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class || p.length != 3
                        || p[0] != double.class || p[1] != double.class || p[2] != double.class) {
                    continue;
                }
                m.setAccessible(true);
                Camera probe = new Camera();
                m.invoke(probe, 1.0, 0.0, 0.0);
                boolean isSetPosition = Math.abs(probe.getPosition().x - 1.0) < 1.0e-6;
                Camera probe2 = new Camera();
                m.invoke(probe2, 0.0, 0.0, 1.0);
                boolean moved = probe2.getPosition().lengthSqr() > 0.5 && !isSetPosition;
                if (moved) {
                    move = m;
                    break;
                }
            }
        } catch (Throwable t) {
            move = null;
        }
        return move;
    }
}
