package com.forja.arca;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Memoria USB por OTG, sin root: se habla directo con ella por USB con el protocolo
 * «Bulk-Only» y comandos SCSI, igual que lo hace una PC. Mientras está abierta, Android la
 * desmonta (no se puede usar desde otras apps). Sirve con sectores de 512 B y de 4 KB.
 */
final class UsbDisk implements BlockDevice {
    private static final int TIMEOUT = 15000;
    private static final int CBW_SIG = 0x43425355, CSW_SIG = 0x53425355;

    final UsbDevice device;
    private final UsbDeviceConnection conn;
    private final UsbInterface iface;
    private final UsbEndpoint in, out;
    private int tag = 1;
    private long blocks;
    private int blockSize;
    String vendor = "", product = "";

    /** Memorias USB (clase almacenamiento masivo, SCSI, bulk-only) conectadas. */
    static List<UsbDevice> find(UsbManager um) {
        List<UsbDevice> out = new ArrayList<>();
        for (UsbDevice d : um.getDeviceList().values()) if (storageInterface(d) != null) out.add(d);
        return out;
    }

    static UsbInterface storageInterface(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            if (f.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE && f.getInterfaceSubclass() == 6
                    && f.getInterfaceProtocol() == 0x50) return f;
        }
        return null;
    }

    UsbDisk(UsbManager um, UsbDevice d) throws IOException {
        device = d;
        iface = storageInterface(d);
        if (iface == null) throw new IOException("No es una memoria USB compatible");
        UsbEndpoint i = null, o = null;
        for (int k = 0; k < iface.getEndpointCount(); k++) {
            UsbEndpoint e = iface.getEndpoint(k);
            if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (e.getDirection() == UsbConstants.USB_DIR_IN) i = e;
            else o = e;
        }
        if (i == null || o == null) throw new IOException("La USB no tiene los canales esperados");
        in = i;
        out = o;
        conn = um.openDevice(d);
        if (conn == null) throw new IOException("Android no dejó abrir la USB (¿falta el permiso?)");
        if (!conn.claimInterface(iface, true)) {
            conn.close();
            throw new IOException("Otra app está usando la USB");
        }
        try {
            init();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void init() throws IOException {
        byte[] inq = new byte[36];
        command(new byte[]{0x12, 0, 0, 0, 36, 0}, inq, 36, true);
        vendor = new String(inq, 8, 8, StandardCharsets.US_ASCII).trim();
        product = new String(inq, 16, 16, StandardCharsets.US_ASCII).trim();
        // esperar a que esté lista (algunas tardan en arrancar)
        IOException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                command(new byte[]{0, 0, 0, 0, 0, 0}, null, 0, false);
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (last != null) throw new IOException("La USB no está lista: " + last.getMessage());
        byte[] cap = new byte[8];
        command(new byte[]{0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0}, cap, 8, true);
        ByteBuffer b = ByteBuffer.wrap(cap).order(ByteOrder.BIG_ENDIAN);
        long last32 = b.getInt(0) & 0xFFFFFFFFL;
        blockSize = b.getInt(4);
        if (last32 == 0xFFFFFFFFL) {
            byte[] c16 = new byte[32];
            byte[] cdb = new byte[16];
            cdb[0] = (byte) 0x9E;
            cdb[1] = 0x10;
            cdb[13] = 32;
            command(cdb, c16, 32, true);
            ByteBuffer bb = ByteBuffer.wrap(c16).order(ByteOrder.BIG_ENDIAN);
            blocks = bb.getLong(0) + 1;
            blockSize = bb.getInt(8);
        } else {
            blocks = last32 + 1;
        }
        if (blockSize != 512 && blockSize != 4096) throw new IOException("Sectores de " + blockSize + " bytes: tamaño no compatible");
    }

    String name() {
        String n = (vendor + " " + product).trim();
        if (n.isEmpty()) n = device.getProductName() != null ? device.getProductName() : "Memoria USB";
        return n;
    }

    @Override
    public long sectors() {
        return blocks;
    }

    @Override
    public int sectorSize() {
        return blockSize;
    }

    @Override
    public int maxTransferBytes() {
        return 128 * 1024;
    }

    @Override
    public void read(long lba, byte[] buf, int off, int count) throws IOException {
        command(rw(0x28, 0x88, lba, count), buf, off, count * blockSize, true);
    }

    @Override
    public void write(long lba, byte[] buf, int off, int count) throws IOException {
        command(rw(0x2A, 0x8A, lba, count), buf, off, count * blockSize, false);
    }

    @Override
    public void flush() throws IOException {
        try {
            command(new byte[]{0x35, 0, 0, 0, 0, 0, 0, 0, 0, 0}, null, 0, false);
        } catch (IOException ignored) {
            // algunas memorias no tienen caché que vaciar
        }
    }

    void close() {
        try {
            conn.releaseInterface(iface);
        } catch (Exception ignored) {
        }
        conn.close();
    }

    // ------------------------------------------------------------------ protocolo

    private static byte[] rw(int op10, int op16, long lba, int count) {
        if (lba + count <= 0xFFFFFFFFL) {
            byte[] c = new byte[10];
            c[0] = (byte) op10;
            ByteBuffer.wrap(c).order(ByteOrder.BIG_ENDIAN).putInt(2, (int) lba).putShort(7, (short) count);
            return c;
        }
        byte[] c = new byte[16];
        c[0] = (byte) op16;
        ByteBuffer.wrap(c).order(ByteOrder.BIG_ENDIAN).putLong(2, lba).putInt(10, count);
        return c;
    }

    private void command(byte[] cdb, byte[] data, int len, boolean dataIn) throws IOException {
        command(cdb, data, 0, len, dataIn);
    }

    private synchronized void command(byte[] cdb, byte[] data, int off, int len, boolean dataIn) throws IOException {
        int t = tag++;
        byte[] cbw = new byte[31];
        ByteBuffer b = ByteBuffer.wrap(cbw).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, CBW_SIG).putInt(4, t).putInt(8, len);
        cbw[12] = (byte) (dataIn ? 0x80 : 0);
        cbw[13] = 0;
        cbw[14] = (byte) cdb.length;
        System.arraycopy(cdb, 0, cbw, 15, cdb.length);
        if (conn.bulkTransfer(out, cbw, 31, TIMEOUT) != 31) {
            resetRecovery();
            throw new IOException("La USB no respondió (¿se desconectó?)");
        }
        if (len > 0) {
            int done = 0;
            while (done < len) {
                int n = Math.min(len - done, 64 * 1024);
                int r = conn.bulkTransfer(dataIn ? in : out, data, off + done, n, TIMEOUT);
                if (r <= 0) {
                    clearHalt(dataIn ? in : out);
                    break;
                }
                done += r;
                if (dataIn && r < n) break;   // la USB mandó menos (paquete corto)
            }
        }
        byte[] csw = new byte[13];
        int r = conn.bulkTransfer(in, csw, 13, TIMEOUT);
        if (r != 13) {
            clearHalt(in);
            r = conn.bulkTransfer(in, csw, 13, TIMEOUT);
        }
        ByteBuffer c = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN);
        if (r != 13 || c.getInt(0) != CSW_SIG) {
            resetRecovery();
            throw new IOException("Respuesta inválida de la USB");
        }
        int status = csw[12];
        if (status == 1) throw new IOException("La USB rechazó la operación (" + sense() + ")");
        if (status == 2) {
            resetRecovery();
            throw new IOException("Error de comunicación con la USB");
        }
    }

    private String sense() {
        try {
            byte[] s = new byte[18];
            command(new byte[]{0x03, 0, 0, 0, 18, 0}, s, 18, true);
            int key = s[2] & 0x0F, asc = s[12] & 0xFF;
            if (key == 7) return "la USB está protegida contra escritura";
            if (key == 2) return "no está lista";
            return String.format("código %X/%02X", key, asc);
        } catch (IOException e) {
            return "sin detalles";
        }
    }

    private void clearHalt(UsbEndpoint e) {
        conn.controlTransfer(0x02, 0x01, 0, e.getAddress(), null, 0, TIMEOUT);
    }

    private void resetRecovery() {
        conn.controlTransfer(0x21, 0xFF, 0, iface.getId(), null, 0, TIMEOUT);
        clearHalt(in);
        clearHalt(out);
    }
}
