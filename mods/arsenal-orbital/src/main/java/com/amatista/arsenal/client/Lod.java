package com.amatista.arsenal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Nivel de detalle para las cinemáticas: un planeta pequeño en pantalla se dibuja con menos
 * cuadritos, y lo que queda fuera de la vista no se calcula.
 */
final class Lod {
    private Lod() {}

    static float tanV() {
        float m11 = RenderSystem.getProjectionMatrix().m11();
        return m11 > 1e-4f ? 1f / m11 : 1f;
    }

    static float tanH() {
        float m00 = RenderSystem.getProjectionMatrix().m00();
        return m00 > 1e-4f ? 1f / m00 : 1f;
    }

    /** Cada cuántas filas/columnas de la malla dibujar una esfera de radio r a esa distancia de la cámara. */
    static int step(float radius, float dist) {
        if (dist <= radius * 1.02f) return 1;
        float tanA = radius / (float) Math.sqrt(dist * dist - radius * radius);
        float frac = tanA / tanV();   // 1 = ocupa media pantalla de alto
        if (frac > 0.45f) return 1;
        if (frac > 0.2f) return 2;
        if (frac > 0.08f) return 4;
        return 8;
    }

    /** Posición de la cámara (la matriz de vista es una rotación + traslación). */
    static Vector3f cameraPos(Matrix4f view) {
        return view.invertAffine(new Matrix4f()).getTranslation(new Vector3f());
    }

    /** Hacia dónde mira la cámara, en coordenadas del mundo. */
    static Vector3f forward(Matrix4f view) {
        return new Vector3f(-view.m02(), -view.m12(), -view.m22()).normalize();
    }

    /** Coseno del ángulo desde el centro de la vista hasta la esquina de la pantalla, menos un margen. */
    static float viewCos(float margin) {
        float th = tanH(), tv = tanV();
        double diag = Math.sqrt(th * th + tv * tv);
        return (float) (1.0 / Math.sqrt(1.0 + diag * diag)) - margin;
    }
}
