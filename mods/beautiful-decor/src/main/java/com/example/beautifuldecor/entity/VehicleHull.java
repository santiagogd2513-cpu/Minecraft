package com.example.beautifuldecor.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * V6.13.0: caja de colisión que sigue al modelo. Una entidad de Minecraft solo tiene una caja alineada con los ejes (no gira), así que aquí se
 * calcula, en cada momento, la caja más chica que encierra el rectángulo (ancho x largo) del vehículo girado hacia donde mira. Medidas sacadas
 * de los propios modelos (tools/v6130/hulls.py). {@code front} = cuánto está el centro del rectángulo hacia delante del punto de la entidad (negativo: atrás).
 */
public final class VehicleHull {
    private VehicleHull() {
    }

    /** Medidas del casco en metros: ancho, largo, desfase del centro hacia delante, alto. */
    public record Spec(double width, double length, double front, double height) {
    }

    /** Caja que encierra el rectángulo girado (un poco recortada en las diagonales, donde sobra más aire). */
    public static AABB box(Vec3 pos, float yawDeg, Spec s) {
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double k = 1.0 - 0.07 * Math.abs(Math.sin(2.0 * yaw));
        double ex = (Math.abs(sin) * s.length() + Math.abs(cos) * s.width()) * 0.5 * k;
        double ez = (Math.abs(cos) * s.length() + Math.abs(sin) * s.width()) * 0.5 * k;
        double cx = pos.x - sin * s.front();
        double cz = pos.z + cos * s.front();
        return new AABB(cx - ex, pos.y, cz - ez, cx + ex, pos.y + s.height(), cz + ez);
    }

    /** Pone la caja de la entidad según su posición y giro actuales. */
    public static void refresh(Entity e, Spec s) {
        e.setBoundingBox(box(e.position(), e.getYRot(), s));
    }

    /** Después de moverla: la posición de la entidad es el punto del eje, no el centro de la caja. */
    public static void fixPosition(Entity e, Spec s) {
        AABB b = e.getBoundingBox();
        double yaw = Math.toRadians(e.getYRot());
        double cx = (b.minX + b.maxX) * 0.5;
        double cz = (b.minZ + b.maxZ) * 0.5;
        e.setPosRaw(cx + Math.sin(yaw) * s.front(), b.minY, cz - Math.cos(yaw) * s.front());
    }
}
