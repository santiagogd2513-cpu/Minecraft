package com.boveda.archivos;

import java.util.ArrayList;
import java.util.List;

/**
 * Cómo se dibuja cada entidad del plano en 2D y en 3D: unas cuantas cajas de colores (un cuerpo, una cabeza, patas, ruedas…)
 * con las medidas aproximadas de la de verdad. Sin Android, para probarlo en el computador.
 */
final class EntityModels {
    private EntityModels() {}

    /** Una caja: centro (x a la derecha, y desde los pies, z hacia el frente) y medidas, en bloques. */
    static final class Box {
        final float cx, cy, cz, sx, sy, sz;
        final int color;

        Box(float cx, float cy, float cz, float sx, float sy, float sz, int rgb) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.color = 0xFF000000 | rgb;
        }
    }

    static final class Model {
        String name;
        float w, h, l;          // ancho, alto y largo (para el mapa en 2D)
        int color;              // color principal (para el mapa en 2D)
        final List<Box> boxes = new ArrayList<>();

        Model add(float cx, float cy, float cz, float sx, float sy, float sz, int rgb) {
            boxes.add(new Box(cx, cy, cz, sx, sy, sz, rgb));
            return this;
        }
    }

    private static Model m(String name, float w, float h, float l, int color) {
        Model k = new Model();
        k.name = name;
        k.w = w;
        k.h = h;
        k.l = l;
        k.color = 0xFF000000 | color;
        return k;
    }

    /** Un animal de cuatro patas: cuerpo, cabeza y patas. */
    private static Model quadruped(String name, float bw, float bh, float bl, float legH, int body, int head, int legs) {
        Model k = m(name, bw, legH + bh + 0.2f, bl + 0.4f, body);
        k.add(0, legH + bh / 2, 0, bw, bh, bl, body);
        k.add(0, legH + bh * 0.85f, bl / 2 + 0.2f, bw * 0.75f, bh * 0.75f, bw * 0.75f, head);
        float lx = bw / 2 - 0.1f, lz = bl / 2 - 0.12f;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) k.add(sx * lx, legH / 2, sz * lz, 0.2f, legH, 0.2f, legs);
        return k;
    }

    private static Model humanoid(String name, int shirt, int pants, int skin) {
        Model k = m(name, 0.6f, 1.9f, 0.6f, shirt);
        k.add(0, 0.4f, 0, 0.5f, 0.8f, 0.25f, pants);
        k.add(0, 1.05f, 0, 0.5f, 0.7f, 0.25f, shirt);
        k.add(-0.37f, 1.05f, 0, 0.2f, 0.7f, 0.2f, shirt);
        k.add(0.37f, 1.05f, 0, 0.2f, 0.7f, 0.2f, shirt);
        k.add(0, 1.65f, 0, 0.5f, 0.5f, 0.5f, skin);
        return k;
    }

    static Model of(String id) {
        String n = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        boolean bd = id.startsWith("beautifuldecor:");
        if (bd) {
            switch (n) {
                case "motorcycle" -> {
                    Model k = m("Motocicleta", 0.9f, 1.3f, 2.1f, 0xC8281E);
                    k.add(0, 0.35f, 0.9f, 0.18f, 0.7f, 0.7f, 0x1A1A1A);
                    k.add(0, 0.35f, -0.9f, 0.2f, 0.7f, 0.7f, 0x1A1A1A);
                    k.add(0, 0.65f, 0, 0.3f, 0.4f, 1.3f, 0xC8281E);
                    k.add(0, 0.95f, -0.25f, 0.28f, 0.14f, 0.7f, 0x222222);
                    k.add(0, 1.15f, 0.6f, 0.9f, 0.08f, 0.1f, 0x9A9A9A);
                    k.add(0, 0.8f, 0.7f, 0.1f, 0.6f, 0.1f, 0x9A9A9A);
                    return k;
                }
                case "bicycle" -> {
                    Model k = m("Bicicleta", 0.6f, 1.2f, 1.8f, 0x2E7BD6);
                    k.add(0, 0.35f, 0.7f, 0.08f, 0.7f, 0.7f, 0x1A1A1A);
                    k.add(0, 0.35f, -0.7f, 0.08f, 0.7f, 0.7f, 0x1A1A1A);
                    k.add(0, 0.65f, 0, 0.08f, 0.08f, 1.3f, 0x2E7BD6);
                    k.add(0, 0.9f, -0.35f, 0.14f, 0.08f, 0.35f, 0x222222);
                    k.add(0, 1.05f, 0.65f, 0.6f, 0.06f, 0.06f, 0x9A9A9A);
                    return k;
                }
                case "bugotti_chillon" -> {
                    Model k = m("Bugatti Chiron", 2.0f, 1.3f, 4.5f, 0x1F3FA0);
                    k.add(0, 0.6f, 0, 1.9f, 0.55f, 4.4f, 0x1F3FA0);
                    k.add(0, 1.05f, -0.2f, 1.5f, 0.5f, 2.0f, 0x161C2A);
                    k.add(0, 0.62f, 0.0f, 0.3f, 0.57f, 4.2f, 0xE87A1E);
                    for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) k.add(sx * 0.95f, 0.35f, sz * 1.4f, 0.3f, 0.7f, 0.7f, 0x111111);
                    return k;
                }
                case "train_car" -> {
                    Model k = m("Vagón de tren", 3.0f, 3.9f, 19.5f, 0xD0D0D8);
                    k.add(0, 2.0f, 0, 3.0f, 3.1f, 19.5f, 0xD0D0D8);
                    k.add(0, 2.75f, 0, 3.04f, 0.9f, 17.0f, 0x2A3548);
                    k.add(0, 1.2f, 0, 3.04f, 0.5f, 19.5f, 0xBE2D2D);
                    k.add(0, 3.7f, 0, 3.2f, 0.25f, 19.7f, 0x808088);
                    k.add(0, 0.45f, 7.0f, 2.2f, 0.5f, 3.2f, 0x2B2B2B);
                    k.add(0, 0.45f, -7.0f, 2.2f, 0.5f, 3.2f, 0x2B2B2B);
                    return k;
                }
                default -> {
                    Model k = m(pretty(n), 1f, 1f, 1f, hashColor(id));
                    k.add(0, 0.5f, 0, 1f, 1f, 1f, hashColor(id));
                    return k;
                }
            }
        }
        return switch (n) {
            case "wolf" -> quadruped("Lobo", 0.6f, 0.6f, 0.9f, 0.45f, 0xD8D8D8, 0xE6E6E6, 0xBDBDBD).add(0, 0.95f, -0.65f, 0.15f, 0.15f, 0.5f, 0xC4C4C4);
            case "cat", "ocelot" -> quadruped("Gato", 0.4f, 0.4f, 0.8f, 0.3f, 0xD98A3A, 0xE8A04F, 0xC47A2E);
            case "pig" -> quadruped("Cerdo", 0.9f, 0.7f, 1.0f, 0.4f, 0xF0A8A8, 0xF4B6B6, 0xE59A9A);
            case "cow" -> quadruped("Vaca", 0.9f, 0.8f, 1.2f, 0.6f, 0x4A3A30, 0x5A4638, 0x3E3028).add(0, 1.2f, 0, 0.92f, 0.5f, 0.6f, 0xE8E8E8);
            case "sheep" -> quadruped("Oveja", 0.9f, 0.8f, 1.1f, 0.5f, 0xEDEDED, 0x4E4036, 0xCFC6BA);
            case "horse", "donkey", "mule" -> quadruped("Caballo", 0.9f, 1.0f, 1.8f, 0.8f, 0x8C5A30, 0x7A4E28, 0x6B4423);
            case "chicken" -> quadruped("Gallina", 0.35f, 0.4f, 0.5f, 0.25f, 0xF2F2F2, 0xF5F5F5, 0xE0A020);
            case "villager" -> humanoid("Aldeano", 0x6B4F2A, 0x5A4424, 0xC8957A);
            case "player" -> humanoid("Jugador", 0x2E7BD6, 0x3A3AA0, 0xC8957A);
            case "zombie" -> humanoid("Zombi", 0x3A8A3A, 0x2E4E9A, 0x5E9E4E);
            case "skeleton" -> humanoid("Esqueleto", 0xD8D8D0, 0xC8C8C0, 0xE6E6DE);
            case "iron_golem" -> {
                Model k = m("Gólem de hierro", 1.4f, 2.7f, 0.9f, 0xCFCFCF);
                k.add(0, 0.7f, 0, 0.6f, 1.4f, 0.4f, 0xBEBEBE);
                k.add(0, 1.7f, 0, 1.2f, 1.2f, 0.7f, 0xCFCFCF);
                k.add(0, 2.5f, 0.05f, 0.5f, 0.5f, 0.5f, 0xD8D8D8);
                yield k;
            }
            case "creeper" -> {
                Model k = m("Creeper", 0.6f, 1.7f, 0.6f, 0x4EB04E);
                k.add(0, 0.3f, 0, 0.5f, 0.6f, 0.5f, 0x3E9E3E);
                k.add(0, 0.9f, 0, 0.5f, 0.8f, 0.3f, 0x4EB04E);
                k.add(0, 1.45f, 0, 0.5f, 0.5f, 0.5f, 0x5EC05E);
                yield k;
            }
            case "armor_stand" -> {
                Model k = m("Soporte de armadura", 0.6f, 1.9f, 0.3f, 0x9A7B4F);
                k.add(0, 0.5f, 0, 0.4f, 0.9f, 0.1f, 0x9A7B4F);
                k.add(0, 1.2f, 0, 0.5f, 0.5f, 0.12f, 0xB89A6A);
                k.add(0, 1.7f, 0, 0.25f, 0.25f, 0.25f, 0x9A7B4F);
                yield k;
            }
            default -> {
                int c = hashColor(id);
                Model k = m(pretty(n), 0.6f, 1.2f, 0.6f, c);
                k.add(0, 0.6f, 0, 0.6f, 1.2f, 0.6f, c);
                yield k;
            }
        };
    }

    private static String pretty(String n) {
        String s = n.replace('_', ' ');
        return s.isEmpty() ? "Entidad" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int hashColor(String id) {
        int h = id.hashCode();
        return (90 + ((h >>> 16) & 0x7F)) << 16 | (90 + ((h >>> 8) & 0x7F)) << 8 | (90 + (h & 0x7F));
    }

    /** El nombre para mostrar: el nombre que le puso el jugador, o el del tipo de entidad. */
    static String label(BdPlan.Ent e) {
        Model k = of(e.id);
        return e.name.isEmpty() ? k.name : k.name + " «" + e.name + "»";
    }

    /** Esquinas de una caja ya girada (yaw de Minecraft: 0 = mira a +Z, 90 = mira a -X) y puesta en el plano. out: 8 esquinas × (x,y,z). */
    static void corners(BdPlan.Ent e, Box b, float[] out) {
        double yr = Math.toRadians(e.yaw), c = Math.cos(yr), s = Math.sin(yr);
        int k = 0;
        for (int iy = 0; iy < 2; iy++) for (int iz = 0; iz < 2; iz++) for (int ix = 0; ix < 2; ix++) {
            double lx = b.cx + (ix == 0 ? -b.sx : b.sx) / 2, lz = b.cz + (iz == 0 ? -b.sz : b.sz) / 2, ly = b.cy + (iy == 0 ? -b.sy : b.sy) / 2;
            out[k++] = (float) (e.x + lx * c - lz * s);
            out[k++] = (float) (e.y + ly);
            out[k++] = (float) (e.z + lx * s + lz * c);
        }
    }
}
