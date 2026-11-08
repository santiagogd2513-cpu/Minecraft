package com.example.beautifuldecor.entity;

/**
 * Los números y las cuentas del manejo del carro RC (V3.1.0), sin nada de Minecraft para poder probarlas solas
 * (tools/verify_v310.py las corre en una simulación). Todas las velocidades son en bloques por tick.
 */
public final class RcCarPhysics {
    private RcCarPhysics() {}

    /** Velocidad máxima: 9 bloques por segundo (un jugador corriendo va a 5,6). */
    public static final float TOP_SPEED = 0.45f;
    public static final float REVERSE_SPEED = 0.2f;
    public static final float ACCELERATION = 0.04f;
    /** Giro máximo por tick, en grados, y agarre lateral (define lo abiertas que son las curvas rápidas). */
    public static final float MAX_TURN = 6.0f;
    public static final float LATERAL_GRIP = 0.05f;
    /** Multiplicador de velocidad por tick al soltar el gas. */
    public static final float COAST_DRAG = 0.95f;

    /** Nueva velocidad (con signo: negativo = reversa) después de un tick con esas teclas. */
    public static float speedAfter(float v, boolean grounded, boolean forward, boolean back, boolean brake) {
        if (grounded) {
            if (forward) {
                if (v < -0.02f) {
                    v += 0.08f;
                } else {
                    v += ACCELERATION * (1.0f - v / (TOP_SPEED * 1.03f));
                }
            }
            if (back) {
                if (v > 0.03f) {
                    v -= 0.06f + 0.05f * v;
                } else {
                    v = Math.max(v - 0.025f, -REVERSE_SPEED);
                }
            }
            if (!forward && !back) {
                v *= COAST_DRAG;
                if (Math.abs(v) < 0.005f) {
                    v = 0.0f;
                }
            }
            if (brake) {
                v *= 0.85f;
            }
        } else {
            v *= 0.999f;
        }
        return Math.max(-REVERSE_SPEED, Math.min(TOP_SPEED, v));
    }

    /** Giro en grados de este tick: positivo = a la derecha. Cuanto más rápido va, más abierta es la curva. */
    public static float turnAfter(float v, float steerInput, boolean brake) {
        float absV = Math.abs(v);
        if (steerInput == 0.0f || absV <= 0.01f) {
            return 0.0f;
        }
        float byGrip = (float) Math.toDegrees(LATERAL_GRIP / Math.max(absV, 0.03f));
        float turn = Math.min(MAX_TURN, byGrip) * Math.min(1.0f, absV / 0.12f);
        if (brake) {
            turn *= 1.4f;
        }
        return turn * steerInput * (v < 0.0f ? -1.0f : 1.0f);
    }
}
