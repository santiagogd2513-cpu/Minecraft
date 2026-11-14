package com.boveda.archivos;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * El cable entre dos celulares. Uno hace de «anfitrión» (el que tiene el adaptador OTG o el que Android elige) y el otro de
 * «accesorio» (modo accesorio de Android, AOA). El anfitrión le manda al otro la orden de pasar a modo accesorio y después
 * los dos hablan por dos tubos de bytes. Aquí está todo lo de USB; lo demás (LinkWire…) no sabe que hay un cable.
 */
final class LinkUsb {
    private LinkUsb() {}

    static final String MANUFACTURER = "Forja Estudios", MODEL = "Boveda", DESCRIPTION = "Celular a celular", VERSION = "1.0",
            URI = "https://github.com/santiagogd2513-cpu/Minecraft", SERIAL = "1";
    private static final int ACCESSORY_VID = 0x18D1;
    private static final int[] ACCESSORY_PIDS = {0x2D00, 0x2D01};
    static final String ACTION_PERMISSION = "com.boveda.archivos.USB_PERMISSION";

    static boolean isAccessoryMode(UsbDevice d) {
        if (d.getVendorId() != ACCESSORY_VID) return false;
        for (int p : ACCESSORY_PIDS) if (d.getProductId() == p) return true;
        return false;
    }

    /** Pide a un celular conectado que pase a modo accesorio (protocolo AOA). Devuelve false si no lo soporta. */
    static boolean switchToAccessory(UsbManager um, UsbDevice dev) {
        UsbDeviceConnection c = um.openDevice(dev);
        if (c == null) return false;
        try {
            byte[] ver = new byte[2];
            int n = c.controlTransfer(0xC0, 51, 0, 0, ver, 2, 2000);       // GET_PROTOCOL
            int protocol = n == 2 ? (ver[1] << 8) | (ver[0] & 0xFF) : 0;
            if (protocol < 1) return false;
            String[] s = {MANUFACTURER, MODEL, DESCRIPTION, VERSION, URI, SERIAL};
            for (int i = 0; i < s.length; i++) {
                byte[] b = (s[i] + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (c.controlTransfer(0x40, 52, 0, i, b, b.length, 2000) < 0) return false;   // SEND_STRING
            }
            return c.controlTransfer(0x40, 53, 0, 0, null, 0, 2000) >= 0;                 // START
        } finally {
            c.close();
        }
    }

    /** El celular que recibió el cable como «anfitrión»: habla por los dos extremos «bulk» de la interfaz de accesorio. */
    static final class HostPipe implements LinkWire.Pipe {
        private final UsbDeviceConnection conn;
        private final UsbInterface iface;
        private final UsbEndpoint epIn, epOut;
        private volatile boolean closed;

        private HostPipe(UsbDeviceConnection c, UsbInterface i, UsbEndpoint in, UsbEndpoint out) {
            conn = c;
            iface = i;
            epIn = in;
            epOut = out;
        }

        static HostPipe open(UsbManager um, UsbDevice dev) throws IOException {
            UsbDeviceConnection c = um.openDevice(dev);
            if (c == null) throw new IOException("Android no dejó abrir el cable (falta el permiso)");
            for (int k = 0; k < dev.getInterfaceCount(); k++) {
                UsbInterface i = dev.getInterface(k);
                UsbEndpoint in = null, out = null;
                for (int e = 0; e < i.getEndpointCount(); e++) {
                    UsbEndpoint ep = i.getEndpoint(e);
                    if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) in = ep;
                    else out = ep;
                }
                if (in != null && out != null && c.claimInterface(i, true)) return new HostPipe(c, i, in, out);
            }
            c.close();
            throw new IOException("el otro celular no abrió el canal de datos");
        }

        private InputStream inStream;
        private OutputStream outStream;

        public synchronized InputStream in() {
            if (inStream == null) inStream = newIn();
            return inStream;
        }

        public synchronized OutputStream out() {
            if (outStream == null) outStream = newOut();
            return outStream;
        }

        private InputStream newIn() {
            return new InputStream() {
                private final byte[] buf = new byte[16384];
                private int pos, len;

                @Override
                public int read() throws IOException {
                    byte[] one = new byte[1];
                    return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int n) throws IOException {
                    if (n == 0) return 0;
                    if (pos >= len) {
                        while (true) {
                            if (closed) throw new IOException("cable desconectado");
                            int r = conn.bulkTransfer(epIn, buf, buf.length, 500);
                            if (r > 0) {
                                pos = 0;
                                len = r;
                                break;
                            }
                            if (r == 0) continue;
                            // -1: tiempo de espera, o error. Si el cable sigue ahí, se espera; si no, se corta.
                        }
                    }
                    int k = Math.min(n, len - pos);
                    System.arraycopy(buf, pos, b, off, k);
                    pos += k;
                    return k;
                }
            };
        }

        private OutputStream newOut() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    write(new byte[]{(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int n) throws IOException {
                    while (n > 0) {
                        if (closed) throw new IOException("cable desconectado");
                        int k = Math.min(n, 16384);
                        byte[] part = (off == 0 && k == b.length) ? b : java.util.Arrays.copyOfRange(b, off, off + k);
                        int r = conn.bulkTransfer(epOut, part, k, 5000);
                        if (r < 0) throw new IOException("se cortó el cable");
                        off += r;
                        n -= r;
                    }
                }
            };
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                conn.releaseInterface(iface);
            } catch (Exception ignored) {
            }
            conn.close();
        }
    }

    /** El celular que quedó como «accesorio»: Android le da un archivo especial por donde leer y escribir. */
    static final class AccessoryPipe implements LinkWire.Pipe {
        private final ParcelFileDescriptor pfd;
        private final FileInputStream in;
        private final FileOutputStream out;

        AccessoryPipe(UsbManager um, UsbAccessory acc) throws IOException {
            pfd = um.openAccessory(acc);
            if (pfd == null) throw new IOException("Android no dejó abrir el accesorio (falta el permiso)");
            FileDescriptor fd = pfd.getFileDescriptor();
            in = new FileInputStream(fd);
            out = new FileOutputStream(fd);
        }

        public InputStream in() {
            return in;
        }

        public OutputStream out() {
            return out;
        }

        @Override
        public void close() throws IOException {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
        }
    }

    static PendingIntent permissionIntent(Context c) {
        Intent i = new Intent(ACTION_PERMISSION).setPackage(c.getPackageName());
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }
}
