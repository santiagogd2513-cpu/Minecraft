package com.boveda.archivos;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente FTP propio, pensado también para servidores viejos o raros como los de la Xbox 360
 * (Aurora, FSD, XeXMenu): modo pasivo o activo, listados MLSD, Unix o DOS, y sin depender de
 * comandos modernos (si FEAT, OPTS, SIZE o MFMT no existen, sigue igual).
 * Una conexión hace una cosa a la vez.
 */
final class FtpClient {
    static final class Reply {
        final int code;
        final String text;

        Reply(int code, String text) {
            this.code = code;
            this.text = text;
        }

        boolean ok() {
            return code >= 200 && code < 400;
        }
    }

    static final class FtpException extends IOException {
        final int code;

        FtpException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    /** Una fila del listado. */
    static final class Item {
        final String name;
        final boolean dir;
        final long size;
        final long time;

        Item(String name, boolean dir, long size, long time) {
            this.name = name;
            this.dir = dir;
            this.size = size;
            this.time = time;
        }
    }

    private final String host;
    private final int port;
    private Charset cs;
    private boolean passive;
    private Socket ctrl;
    private BufferedReader in;
    private OutputStream out;
    private boolean mlsd, utf8;
    private volatile boolean busy;      // hay una transferencia en curso
    long lastUsed;

    FtpClient(String host, int port, boolean passive, Charset charset) {
        this.host = host;
        this.port = port;
        this.passive = passive;
        this.cs = charset;
    }

    boolean busy() {
        return busy;
    }

    // ------------------------------------------------------------------ conexión

    void connect(String user, String pass) throws IOException {
        ctrl = new Socket();
        try {
            ctrl.connect(new InetSocketAddress(host, port), 12000);
        } catch (SocketTimeoutException e) {
            throw new IOException(L.t("El equipo ") + host + L.t(" no respondió. Revisa que la IP sea la correcta y que el celular ")
                    + L.t("esté en la misma red Wi-Fi (no en datos móviles)."));
        } catch (java.net.ConnectException e) {
            throw new IOException(L.t("El equipo ") + host + L.t(" rechazó la conexión en el puerto ") + port
                    + L.t(". El servidor FTP no está encendido (en la Xbox: abre Aurora o FreeStyle Dash) o el puerto no es ese."));
        } catch (java.net.UnknownHostException e) {
            throw new IOException(L.t("No se encontró la dirección «") + host + L.t("». Escribe la IP tal como aparece en el equipo, por ejemplo 192.168.1.20."));
        } catch (java.net.NoRouteToHostException e) {
            throw new IOException(L.t("No hay camino hasta ") + host + L.t(". El celular y el equipo deben estar en la misma red Wi-Fi."));
        }
        ctrl.setSoTimeout(30000);
        ctrl.setKeepAlive(true);
        open();
        Reply hello = reply();
        if (hello.code != 220) throw new FtpException(hello.code, L.t("El servidor no saludó bien: ") + hello.text);
        Reply r = cmd("USER " + (user == null || user.isEmpty() ? "anonymous" : user));
        if (r.code == 331 || r.code == 332) r = cmd("PASS " + (pass == null ? "" : pass));
        if (r.code != 230 && r.code != 202) throw new FtpException(r.code, r.code == 530 ? L.t("Usuario o contraseña incorrectos (van en minúsculas tal cual). En la Xbox 360 con Aurora son xboxftp / xboxftp; con FreeStyle Dash, xbox / xbox.") : r.text);
        // lo moderno es opcional: servidores viejos (Xbox) no lo tienen
        try {
            Reply feat = cmd("FEAT");
            if (feat.code == 211) {
                String f = feat.text.toUpperCase(Locale.ROOT);
                mlsd = f.contains("MLSD") || f.contains("MLST");
                if (f.contains("UTF8")) {
                    Reply u = cmd("OPTS UTF8 ON");
                    if (u.ok() && cs.equals(StandardCharsets.UTF_8)) utf8 = true;
                }
            }
        } catch (FtpException ignored) {
        }
        cmd("TYPE I");
        lastUsed = System.currentTimeMillis();
    }

    private void open() throws IOException {
        in = new BufferedReader(new InputStreamReader(ctrl.getInputStream(), cs));
        out = ctrl.getOutputStream();
    }

    void close() {
        // se llama también cuando la conexión nunca llegó a abrirse: no debe fallar nunca
        if (ctrl == null) return;
        try {
            if (out != null && ctrl.isConnected() && !ctrl.isClosed()) send("QUIT");
        } catch (Exception ignored) {
        }
        try {
            ctrl.close();
        } catch (Exception ignored) {
        }
    }

    boolean alive() {
        if (ctrl == null || ctrl.isClosed() || busy) return false;
        if (System.currentTimeMillis() - lastUsed < 15000) return true;
        try {
            return cmd("NOOP").ok();
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ comandos

    private void send(String line) throws IOException {
        out.write((line + "\r\n").getBytes(cs));
        out.flush();
    }

    private Reply reply() throws IOException {
        String line = in.readLine();
        if (line == null) throw new IOException(L.t("El servidor cerró la conexión"));
        if (line.length() < 3) return reply();
        int code;
        try {
            code = Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException e) {
            return reply();
        }
        StringBuilder text = new StringBuilder(line.length() > 4 ? line.substring(4) : "");
        if (line.length() > 3 && line.charAt(3) == '-') {
            // respuesta de varias líneas: termina con "código " al inicio
            String end = line.substring(0, 3) + " ";
            while (true) {
                String l = in.readLine();
                if (l == null) break;
                text.append('\n').append(l);
                if (l.startsWith(end)) break;
            }
        }
        lastUsed = System.currentTimeMillis();
        return new Reply(code, text.toString());
    }

    Reply cmd(String line) throws IOException {
        send(line);
        Reply r = reply();
        if (r.code >= 400) throw new FtpException(r.code, friendly(r));
        return r;
    }

    private static String friendly(Reply r) {
        return switch (r.code) {
            case 421 -> "El servidor no acepta más conexiones (" + r.text + ")";
            case 425, 426 -> "Se cortó la transferencia (" + r.text + ")";
            case 450, 550 -> "No existe o no se puede usar (" + r.text + ")";
            case 452, 552 -> "No hay espacio en el servidor";
            case 553 -> "Nombre no permitido por el servidor";
            default -> r.text + " (" + r.code + ")";
        };
    }

    // ------------------------------------------------------------------ conexión de datos

    private interface Data {
        Socket accept() throws IOException;

        void close();
    }

    private Data openData() throws IOException {
        if (passive) {
            try {
                return pasv();
            } catch (FtpException e) {
                // algunos servidores no tienen pasivo: se intenta el modo activo
                passive = false;
            }
        }
        return active();
    }

    private Data pasv() throws IOException {
        Reply r;
        String dataHost;
        int dataPort;
        try {
            r = cmd("EPSV");
            Matcher m = Pattern.compile("\\(\\|\\|\\|(\\d+)\\|\\)").matcher(r.text);
            if (!m.find()) throw new FtpException(500, L.t("EPSV raro"));
            dataHost = host;
            dataPort = Integer.parseInt(m.group(1));
        } catch (FtpException e) {
            r = cmd("PASV");
            Matcher m = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").matcher(r.text);
            if (!m.find()) throw new FtpException(500, L.t("El servidor no dio dirección para los datos"));
            dataHost = m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4);
            dataPort = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
            // servidores detrás de un router a veces dan su IP interna: se usa la del control
            if (dataHost.startsWith("0.") || dataHost.equals("127.0.0.1")) dataHost = host;
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(dataHost, dataPort), 12000);
        s.setSoTimeout(60000);
        return new Data() {
            public Socket accept() {
                return s;
            }

            public void close() {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        };
    }

    private Data active() throws IOException {
        InetAddress local = ctrl.getLocalAddress();
        ServerSocket ss = new ServerSocket(0, 1, local);
        ss.setSoTimeout(20000);
        byte[] a = local.getAddress();
        int p = ss.getLocalPort();
        cmd("PORT " + (a[0] & 0xFF) + "," + (a[1] & 0xFF) + "," + (a[2] & 0xFF) + "," + (a[3] & 0xFF) + "," + (p >> 8) + "," + (p & 0xFF));
        return new Data() {
            public Socket accept() throws IOException {
                try {
                    Socket s = ss.accept();
                    s.setSoTimeout(60000);
                    return s;
                } catch (SocketTimeoutException e) {
                    throw new IOException(L.t("El servidor no se conectó para enviar los datos"));
                } finally {
                    ss.close();
                }
            }

            public void close() {
                try {
                    ss.close();
                } catch (IOException ignored) {
                }
            }
        };
    }

    /** Inicia una transferencia: abre datos, manda el comando y espera el 150/125. */
    private Socket transfer(String command) throws IOException {
        Data d = openData();
        send(command);
        Reply r = reply();
        if (r.code != 150 && r.code != 125) {
            d.close();
            throw new FtpException(r.code, friendly(r));
        }
        return d.accept();
    }

    /** Lee el "226 listo" al terminar una transferencia. */
    private void finish() throws IOException {
        Reply r = reply();
        if (r.code >= 400) throw new FtpException(r.code, friendly(r));
    }

    // ------------------------------------------------------------------ operaciones

    List<Item> list(String dir) throws IOException {
        String path = dir.isEmpty() ? "/" : dir;
        if (mlsd) {
            try {
                return parseMlsd(readAll("MLSD " + path));
            } catch (FtpException e) {
                mlsd = false;
            }
        }
        // sin MLSD: se entra a la carpeta y se pide LIST (lo más compatible)
        cmd("CWD " + path);
        return parseList(readAll("LIST"));
    }

    private List<String> readAll(String command) throws IOException {
        busy = true;
        List<String> lines = new ArrayList<>();
        try (Socket s = transfer(command)) {
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), cs));
            String l;
            while ((l = r.readLine()) != null) if (!l.isEmpty()) lines.add(l);
        } finally {
            busy = false;
        }
        finish();
        return lines;
    }

    InputStream retrieve(String path, long offset) throws IOException {
        if (offset > 0) cmd("REST " + offset);
        busy = true;
        Socket s;
        try {
            s = transfer("RETR " + path);
        } catch (IOException e) {
            busy = false;
            throw e;
        }
        return new FilterInputStream(s.getInputStream()) {
            boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    s.close();
                    finishQuiet();
                } finally {
                    busy = false;
                }
            }
        };
    }

    /** Si se cerró antes de terminar, el servidor responde 426: no es un error nuestro. */
    private void finishQuiet() throws IOException {
        Reply r = reply();
        if (r.code >= 400 && r.code != 426 && r.code != 451) throw new FtpException(r.code, friendly(r));
    }

    OutputStream store(String path) throws IOException {
        busy = true;
        Socket s;
        try {
            s = transfer("STOR " + path);
        } catch (IOException e) {
            busy = false;
            throw e;
        }
        return new FilterOutputStream(s.getOutputStream()) {
            boolean closed;

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    out.flush();
                    s.shutdownOutput();
                    s.close();
                    finish();       // el servidor confirma que lo guardó completo
                } finally {
                    busy = false;
                }
            }
        };
    }

    void mkdir(String path) throws IOException {
        cmd("MKD " + path);
    }

    void deleteFile(String path) throws IOException {
        cmd("DELE " + path);
    }

    void rmdir(String path) throws IOException {
        cmd("RMD " + path);
    }

    void rename(String from, String to) throws IOException {
        Reply r = cmd("RNFR " + from);
        if (r.code != 350) throw new FtpException(r.code, friendly(r));
        cmd("RNTO " + to);
    }

    long size(String path) {
        try {
            Reply r = cmd("SIZE " + path);
            return Long.parseLong(r.text.trim().split("\\s+")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    void setModified(String path, long time) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            cmd("MFMT " + f.format(time) + " " + path);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ listados

    static List<Item> parseMlsd(List<String> lines) {
        List<Item> out = new ArrayList<>();
        for (String l : lines) {
            int sp = l.indexOf(' ');
            if (sp < 0) continue;
            String facts = l.substring(0, sp).toLowerCase(Locale.ROOT), name = l.substring(sp + 1);
            if (name.equals(".") || name.equals("..")) continue;
            String type = fact(facts, "type");
            if (type.equals("cdir") || type.equals("pdir")) continue;
            boolean dir = type.equals("dir");
            long size = 0, time = 0;
            try {
                size = Long.parseLong(fact(facts, "size"));
            } catch (Exception ignored) {
            }
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                String m = fact(facts, "modify");
                if (m.length() >= 14) time = f.parse(m.substring(0, 14)).getTime();
            } catch (Exception ignored) {
            }
            out.add(new Item(name, dir, dir ? 0 : size, time));
        }
        return out;
    }

    private static String fact(String facts, String key) {
        for (String f : facts.split(";")) {
            int eq = f.indexOf('=');
            if (eq > 0 && f.substring(0, eq).equals(key)) return f.substring(eq + 1);
        }
        return "";
    }

    private static final Pattern UNIX = Pattern.compile(
            "^([-dlcbps])[-rwxsStTl+.@]{9}\\S*\\s+\\d+\\s+\\S+\\s+(?:\\S+\\s+)?(\\d+)\\s+(\\w{3})\\s+(\\d{1,2})\\s+(\\d{1,2}:\\d{2}|\\d{4})\\s(.+)$");
    private static final Pattern DOS = Pattern.compile(
            "^(\\d{2})-(\\d{2})-(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?\\s+(<DIR>|\\d+)\\s+(.+)$");
    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};

    static List<Item> parseList(List<String> lines) {
        List<Item> out = new ArrayList<>();
        for (String l : lines) {
            if (l.startsWith("total ")) continue;
            Matcher m = UNIX.matcher(l);
            if (m.matches()) {
                char t = m.group(1).charAt(0);
                String name = m.group(6);
                if (t == 'l') {
                    int arrow = name.indexOf(" -> ");
                    if (arrow > 0) name = name.substring(0, arrow);
                }
                if (name.equals(".") || name.equals("..")) continue;
                boolean dir = t == 'd';
                out.add(new Item(name, dir, dir ? 0 : parseLong(m.group(2)), unixDate(m.group(3), m.group(4), m.group(5))));
                continue;
            }
            m = DOS.matcher(l);
            if (m.matches()) {
                String name = m.group(8);
                if (name.equals(".") || name.equals("..")) continue;
                boolean dir = m.group(7).equalsIgnoreCase("<DIR>");
                out.add(new Item(name, dir, dir ? 0 : parseLong(m.group(7)), dosDate(m)));
            }
        }
        return out;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long unixDate(String mon, String day, String timeOrYear) {
        try {
            Calendar now = Calendar.getInstance();
            Calendar c = Calendar.getInstance();
            c.clear();
            int month = 0;
            for (int i = 0; i < 12; i++) if (MONTHS[i].equalsIgnoreCase(mon)) month = i;
            if (timeOrYear.contains(":")) {
                String[] hm = timeOrYear.split(":");
                c.set(now.get(Calendar.YEAR), month, Integer.parseInt(day), Integer.parseInt(hm[0]), Integer.parseInt(hm[1]));
                if (c.after(now)) c.add(Calendar.YEAR, -1);
            } else {
                c.set(Integer.parseInt(timeOrYear), month, Integer.parseInt(day));
            }
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long dosDate(Matcher m) {
        try {
            int mo = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2)), y = Integer.parseInt(m.group(3));
            if (y < 100) y += y < 70 ? 2000 : 1900;
            int h = Integer.parseInt(m.group(4)), mi = Integer.parseInt(m.group(5));
            String ap = m.group(6);
            if (ap != null) {
                if (ap.equalsIgnoreCase("PM") && h < 12) h += 12;
                if (ap.equalsIgnoreCase("AM") && h == 12) h = 0;
            }
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(y, mo - 1, d, h, mi);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
