package com.forja.arca;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Lee la parte «de pertenencia» de la cabecera de un paquete CON (partidas y perfiles) y comprueba
 * su firma RSA con la clave pública que el propio paquete trae. No necesita ninguna llave secreta.
 * Como todavía no se conoce la receta exacta, se prueban varias formas de ordenar los bytes y se
 * anota cuál funcionó: así sabremos cómo firmar después.
 */
final class Firma {
    static final int SIG = 0x1AC, SIG_LEN = 0x80, SIGNED_FROM = 0x22C, SIGNED_TO = 0x344;

    enum Estado { NO_ES_CON, SIN_CLAVE, VALIDA, NO_COINCIDE }

    Estado estado = Estado.NO_ES_CON;
    String magic = "";
    String consolaDelCertificado = "", consolaDeLaCabecera = "", perfil = "", dispositivo = "";
    String receta = "";
    boolean sinFirma;

    static Firma analizar(byte[] h) {
        Firma f = new Firma();
        if (h == null || h.length < 0x420) return f;
        f.magic = new String(h, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1).trim();
        if (!f.magic.equals("CON")) return f;
        f.consolaDelCertificado = hex(h, 0x6, 5);
        f.consolaDeLaCabecera = hex(h, 0x36C, 5);
        f.perfil = hex(h, 0x371, 8);
        f.dispositivo = hex(h, 0x3FD, 20);
        f.sinFirma = allZero(h, SIG, SIG_LEN);
        int exp = -1;
        for (int i = 4; i < 0x60; i++) {
            if (h[i] == 0 && h[i + 1] == 1 && h[i + 2] == 0 && h[i + 3] == 1) {
                exp = i;
                break;
            }
        }
        if (exp < 0 || f.sinFirma) {
            f.estado = exp < 0 ? Estado.SIN_CLAVE : Estado.NO_COINCIDE;
            return f;
        }
        try {
            byte[] mod = Arrays.copyOfRange(h, exp + 4, exp + 4 + 0x80);
            byte[] sig = Arrays.copyOfRange(h, SIG, SIG + SIG_LEN);
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(h, SIGNED_FROM, SIGNED_TO));
            String[] nombres = {"tal cual", "invertido", "con cada grupo de 8 bytes al revés"};
            for (int sm = 0; sm < 3 && f.receta.isEmpty(); sm++) {
                for (int mm = 0; mm < 3 && f.receta.isEmpty(); mm++) {
                    BigInteger n = new BigInteger(1, orden(mod, mm)), s = new BigInteger(1, orden(sig, sm));
                    if (n.signum() == 0 || s.compareTo(n) >= 0) continue;
                    byte[] bloque = fijo(s.modPow(BigInteger.valueOf(65537), n), 0x80);
                    for (int om = 0; om < 3; om++) {
                        byte[] b = orden(bloque, om);
                        if (contiene(b, hash) || contiene(b, invertir(hash))) {
                            f.receta = "firma " + nombres[sm] + ", clave " + nombres[mm] + ", resultado " + nombres[om];
                            break;
                        }
                    }
                }
            }
            f.estado = f.receta.isEmpty() ? Estado.NO_COINCIDE : Estado.VALIDA;
        } catch (Exception e) {
            f.estado = Estado.NO_COINCIDE;
        }
        return f;
    }

    /** Texto para mostrarle a la persona. */
    String resumen() {
        if (estado == Estado.NO_ES_CON) {
            return magic.isEmpty() ? "Esto no es un paquete reconocible." : "Los paquetes «" + magic + "» vienen firmados por Microsoft: no se pueden volver a firmar, y Arca no los toca.";
        }
        StringBuilder b = new StringBuilder();
        b.append("Firma: ");
        switch (estado) {
            case VALIDA -> b.append("✅ correcta con la clave del propio paquete");
            case SIN_CLAVE -> b.append("⚠️ no encontré la clave pública dentro del certificado");
            default -> b.append(sinFirma ? "⚠️ el paquete no trae firma (está en ceros)" : "⚠️ no coincide con ninguna forma que probé");
        }
        b.append("\n• Consola que firmó (certificado): ").append(consolaDelCertificado);
        b.append("\n• Consola anotada en la cabecera: ").append(consolaDeLaCabecera);
        b.append("\n• Perfil: ").append(perfil);
        b.append("\n• Dispositivo: ").append(dispositivo);
        if (!receta.isEmpty()) b.append("\n• Receta que funcionó: ").append(receta);
        if (estado == Estado.VALIDA) {
            b.append("\n\nQue la firma sea correcta no dice que sea de TU consola: compara la consola de arriba con la de otras partidas tuyas.");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ ayudas

    static byte[] orden(byte[] b, int modo) {
        if (modo == 0) return b;
        byte[] o = b.clone();
        if (modo == 1) return invertir(o);
        for (int i = 0; i + 8 <= o.length; i += 8) {
            for (int j = 0; j < 4; j++) {
                byte t = o[i + j];
                o[i + j] = o[i + 7 - j];
                o[i + 7 - j] = t;
            }
        }
        return o;
    }

    static byte[] invertir(byte[] b) {
        byte[] o = new byte[b.length];
        for (int i = 0; i < b.length; i++) o[i] = b[b.length - 1 - i];
        return o;
    }

    static byte[] fijo(BigInteger v, int len) {
        byte[] r = v.toByteArray();
        byte[] o = new byte[len];
        int n = Math.min(r.length, len);
        System.arraycopy(r, r.length - n, o, len - n, n);
        return o;
    }

    private static boolean contiene(byte[] b, byte[] pat) {
        outer:
        for (int i = 0; i + pat.length <= b.length; i++) {
            for (int j = 0; j < pat.length; j++) if (b[i + j] != pat[j]) continue outer;
            return true;
        }
        return false;
    }

    private static boolean allZero(byte[] b, int off, int len) {
        for (int i = 0; i < len; i++) if (b[off + i] != 0) return false;
        return true;
    }

    private static String hex(byte[] b, int off, int len) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < len; i++) s.append(String.format(java.util.Locale.US, "%02X", b[off + i] & 0xFF));
        return s.toString();
    }
}
