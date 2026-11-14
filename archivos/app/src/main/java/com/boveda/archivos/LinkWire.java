package com.boveda.archivos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

/**
 * «Celular a celular»: el idioma que hablan los dos celulares por el cable. Mensajes con encabezado, tamaño y verificación,
 * pensados para un tubo de bytes (USB) que puede cortarse en cualquier momento. No usa nada de Android, así se prueba en el computador.
 */
final class LinkWire {
    private LinkWire() {}

    static final int MAGIC = 0xB07E;
    static final int VERSION = 1;
    static final int MAX_PAYLOAD = 2 << 20;          // 2 MiB por mensaje
    static final int CHUNK = 512 * 1024;             // trozos de archivo

    // tipos de mensaje
    static final int HELLO = 1, LIST = 2, STAT = 3, READ = 4, PUT_BEGIN = 5, PUT_DATA = 6, PUT_END = 7, DELETE = 8, MKDIR = 9, BYE = 10, PUT_ABORT = 11, CANCEL = 12;
    static final int R_OK = 100, R_ERROR = 101, R_LIST = 102, R_STAT = 103, R_DATA = 104, R_END = 105, R_HELLO = 106;

    static final class Msg {
        final int type, id;
        final byte[] payload;

        Msg(int type, int id, byte[] payload) {
            this.type = type;
            this.id = id;
            this.payload = payload;
        }

        DataInputStream in() {
            return new DataInputStream(new java.io.ByteArrayInputStream(payload));
        }
    }

    /** Escribe un mensaje: magic, tipo, id, tamaño, datos y CRC32 de todo lo anterior. */
    static synchronized void write(DataOutputStream o, int type, int id, byte[] payload) throws IOException {
        if (payload.length > MAX_PAYLOAD) throw new IOException("mensaje demasiado grande");
        byte[] head = new byte[11];
        head[0] = (byte) (MAGIC >> 8);
        head[1] = (byte) MAGIC;
        head[2] = (byte) type;
        head[3] = (byte) (id >> 24); head[4] = (byte) (id >> 16); head[5] = (byte) (id >> 8); head[6] = (byte) id;
        head[7] = (byte) (payload.length >> 24); head[8] = (byte) (payload.length >> 16); head[9] = (byte) (payload.length >> 8); head[10] = (byte) payload.length;
        CRC32 c = new CRC32();
        c.update(head);
        c.update(payload);
        o.write(head);
        o.write(payload);
        o.writeInt((int) c.getValue());
        o.flush();
    }

    /** Lee un mensaje; lanza error si llega algo dañado (se corta la conexión en vez de seguir con datos malos). */
    static Msg read(DataInputStream in) throws IOException {
        byte[] head = new byte[11];
        in.readFully(head);
        if (((head[0] & 0xFF) << 8 | (head[1] & 0xFF)) != MAGIC) throw new IOException("mensaje desconocido");
        int type = head[2] & 0xFF;
        int id = (head[3] & 0xFF) << 24 | (head[4] & 0xFF) << 16 | (head[5] & 0xFF) << 8 | (head[6] & 0xFF);
        int len = (head[7] & 0xFF) << 24 | (head[8] & 0xFF) << 16 | (head[9] & 0xFF) << 8 | (head[10] & 0xFF);
        if (len < 0 || len > MAX_PAYLOAD) throw new IOException("tamaño de mensaje inválido");
        byte[] p = new byte[len];
        in.readFully(p);
        int crc = in.readInt();
        CRC32 c = new CRC32();
        c.update(head);
        c.update(p);
        if ((int) c.getValue() != crc) throw new IOException("mensaje dañado en el cable");
        return new Msg(type, id, p);
    }

    interface Writer {
        void write(DataOutputStream o) throws IOException;
    }

    static byte[] build(Writer w) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        w.write(new DataOutputStream(b));
        return b.toByteArray();
    }

    /** Las dos puntas de un cable: lo que se lee y lo que se escribe. */
    interface Pipe extends java.io.Closeable {
        InputStream in() throws IOException;

        OutputStream out() throws IOException;
    }
}
