package com.boveda.archivos;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un lector de NBT completo (todo el árbol en memoria): compuestos como Map, listas como List, arreglos como long[]/int[]/byte[]. Sin Android. */
final class Nbt {
    private Nbt() {}

    /** Lee un NBT ya descomprimido y devuelve el compuesto de la raíz. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> read(DataInputStream d) throws IOException {
        int t = d.readUnsignedByte();
        if (t != 10) throw new IOException("no es un archivo NBT");
        d.skipBytes(d.readUnsignedShort());
        return (Map<String, Object>) payload(d, 10, 0);
    }

    private static Object payload(DataInputStream d, int t, int depth) throws IOException {
        if (depth > 64) throw new IOException("NBT demasiado anidado");
        switch (t) {
            case 1: return d.readByte();
            case 2: return d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: {
                int n = d.readInt();
                if (n < 0 || n > 800_000_000) throw new IOException("arreglo dañado");
                byte[] b = new byte[n];
                d.readFully(b);
                return b;
            }
            case 8: return d.readUTF();
            case 9: {
                int et = d.readUnsignedByte(), n = d.readInt();
                if (n < 0 || n > 50_000_000) throw new IOException("lista dañada");
                List<Object> l = new ArrayList<>(Math.min(n, 1 << 16));
                for (int i = 0; i < n; i++) l.add(payload(d, et, depth + 1));
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    int tt = d.readUnsignedByte();
                    if (tt == 0) break;
                    String k = d.readUTF();
                    m.put(k, payload(d, tt, depth + 1));
                }
                return m;
            }
            case 11: {
                int n = d.readInt();
                if (n < 0 || n > 200_000_000) throw new IOException("arreglo dañado");
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = d.readInt();
                return a;
            }
            case 12: {
                int n = d.readInt();
                if (n < 0 || n > 200_000_000) throw new IOException("arreglo dañado");
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = d.readLong();
                return a;
            }
            default: throw new IOException("dato desconocido en el NBT (" + t + ")");
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    static int i(Object o, int def) {
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    static double dbl(Object o, double def) {
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    static String str(Object o) {
        return o instanceof String ? (String) o : "";
    }
}
