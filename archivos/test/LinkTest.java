package com.boveda.archivos;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class LinkTest {
    static int fails;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FALLA: " + m); } else System.out.println("ok: " + m); }
    static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }

    static class SockPipe implements LinkWire.Pipe {
        final Socket s; SockPipe(Socket s) { this.s = s; }
        public InputStream in() throws IOException { return s.getInputStream(); }
        public OutputStream out() throws IOException { return s.getOutputStream(); }
        public void close() throws IOException { s.close(); }
    }

    /** Un cable que daña un byte (para comprobar que se detecta). */
    static class BadPipe extends SockPipe {
        final long flipAt; BadPipe(Socket s, long flipAt) { super(s); this.flipAt = flipAt; }
        public InputStream in() throws IOException {
            InputStream r = s.getInputStream();
            return new InputStream() {
                long n;
                public int read() throws IOException { int b = r.read(); if (b >= 0 && n++ == flipAt) b ^= 0x55; return b; }
                public int read(byte[] a, int o, int l) throws IOException { int k = r.read(a, o, l); if (k > 0) for (int i = 0; i < k; i++) if (n++ == flipAt) a[o + i] ^= 0x55; return k; }
            };
        }
    }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("lnk");
        Path descargas = Files.createDirectories(tmp.resolve("Descargas"));
        Path privado = Files.createDirectories(tmp.resolve("privado"));
        Files.writeString(privado.resolve("secreto.txt"), "no debe verse");
        Files.writeString(descargas.resolve("hola.txt"), "hola mundo");
        Files.createDirectories(descargas.resolve("sub"));
        byte[] big = new byte[6_000_000]; new Random(7).nextBytes(big);
        Files.write(descargas.resolve("sub/grande.bin"), big);
        try { Files.createSymbolicLink(descargas.resolve("escape"), privado); } catch (Exception e) { System.out.println("sin symlinks"); }
        Path solo = Files.createDirectories(tmp.resolve("Fotos"));
        Files.writeString(solo.resolve("a.jpg"), "foto");

        LinkVfs vfs = new LinkVfs();
        vfs.add(new LinkVfs.FolderSource("Descargas", descargas.toFile(), true));
        vfs.add(new LinkVfs.FolderSource("Fotos", solo.toFile(), false));

        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        List<String> eventos = Collections.synchronizedList(new ArrayList<>());
        Thread st = new Thread(() -> {
            try (Socket s = ss.accept()) {
                new LinkServer(new SockPipe(s), vfs, "Celular viejo", new LinkServer.Listener() {
                    public boolean approve(String n) { eventos.add("nombre:" + n); return true; }
                    public void onActivity(String w) { eventos.add(w); }
                    public void onClosed(String w) { eventos.add("cerrado:" + w); }
                }).run();
            } catch (IOException e) { e.printStackTrace(); }
        });
        st.start();
        Socket cs = new Socket("127.0.0.1", port);
        LinkClient c = new LinkClient(new SockPipe(cs), "Celular nuevo");
        check(c.peerName.equals("Celular viejo"), "saludo: nombre del otro celular");

        List<LinkVfs.Item> raiz = c.list("");
        check(raiz.size() == 2 && raiz.get(0).dir, "la raíz solo muestra las carpetas compartidas: " + raiz.size());
        List<LinkVfs.Item> d = c.list("Descargas");
        Set<String> nombres = new TreeSet<>(); for (LinkVfs.Item i : d) nombres.add(i.name);
        check(nombres.contains("hola.txt") && nombres.contains("sub"), "lista de una carpeta: " + nombres);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        c.read("Descargas/hola.txt", 0, -1, bo, null, 10);
        check(bo.toString().equals("hola mundo"), "leer un archivo chico");

        bo = new ByteArrayOutputStream();
        long t0 = System.nanoTime();
        long[] last = {0};
        c.read("Descargas/sub/grande.bin", 0, -1, bo, (dn, tt) -> { last[0] = dn; return true; }, big.length);
        check(sha(bo.toByteArray()).equals(sha(big)) && last[0] == big.length, "leer 6 MB: idéntico bit a bit (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");

        bo = new ByteArrayOutputStream();
        c.read("Descargas/sub/grande.bin", 1_000_000, 500, bo, null, 500);
        check(Arrays.equals(bo.toByteArray(), Arrays.copyOfRange(big, 1_000_000, 1_000_500)), "leer un pedazo con offset (para reanudar)");

        // subir
        byte[] up = new byte[3_000_000]; new Random(9).nextBytes(up);
        String nom = c.put("Descargas/subido.bin", new ByteArrayInputStream(up), up.length, 1_600_000_000_000L, null);
        check(nom.equals("subido.bin") && sha(Files.readAllBytes(descargas.resolve("subido.bin"))).equals(sha(up)), "subir 3 MB: idéntico");
        check(Files.getLastModifiedTime(descargas.resolve("subido.bin")).toMillis() == 1_600_000_000_000L, "se conserva la fecha del archivo");
        String nom2 = c.put("Descargas/subido.bin", new ByteArrayInputStream(up), up.length, 0, null);
        check(nom2.equals("subido (1).bin"), "si ya existe, no pisa: queda como " + nom2);
        check(Files.list(descargas).noneMatch(p -> p.getFileName().toString().endsWith(".enviando")), "no quedan archivos a medias");

        // seguridad
        String[] malos = {"Descargas/../privado/secreto.txt", "../privado/secreto.txt", "Descargas/sub/../../privado/secreto.txt", "privado/secreto.txt", "/etc/passwd", "Descargas/escape/secreto.txt"};
        for (String m : malos) {
            boolean bloqueado;
            try { ByteArrayOutputStream x = new ByteArrayOutputStream(); c.read(m, 0, -1, x, null, 0); bloqueado = x.size() == 0; } catch (IOException e) { bloqueado = true; }
            check(bloqueado, "bloqueado: " + m);
        }
        try { c.put("Fotos/nueva.jpg", new ByteArrayInputStream(new byte[10]), 10, 0, null); check(false, "carpeta de solo lectura no debe aceptar archivos"); } catch (IOException e) { check(true, "carpeta de solo lectura rechaza subir: " + e.getMessage()); }
        check(!Files.exists(solo.resolve("nueva.jpg")), "no se creó nada en la carpeta de solo lectura");
        try { c.put("raiz.txt", new ByteArrayInputStream(new byte[1]), 1, 0, null); check(false, "no se puede subir a la raíz"); } catch (IOException e) { check(true, "subir a la raíz rechazado"); }
        // la conexión sigue sana después de los errores
        check(c.list("Descargas").size() >= 3, "la conexión sigue funcionando tras los errores");
        // cancelar
        try { c.read("Descargas/sub/grande.bin", 0, -1, new ByteArrayOutputStream(), (dn, tt) -> false, big.length); check(false, "cancelar"); } catch (IOException e) { check(e.getMessage().equals("cancelado"), "cancelar una descarga"); }
        c.close(); st.join(3000);
        check(eventos.stream().anyMatch(e -> e.startsWith("nombre:Celular nuevo")), "el que comparte supo el nombre del otro");
        check(eventos.stream().anyMatch(e -> e.startsWith("cerrado")), "el que comparte supo que se cerró");

        // cable que daña datos
        ServerSocket ss2 = new ServerSocket(0);
        Thread st2 = new Thread(() -> { try (Socket s = ss2.accept()) { new LinkServer(new SockPipe(s), vfs, "X", null).run(); } catch (IOException e) { } });
        st2.start();
        Socket cs2 = new Socket("127.0.0.1", ss2.getLocalPort());
        LinkClient bad = new LinkClient(new BadPipe(cs2, 2_000_000), "Y");
        try { bad.read("Descargas/sub/grande.bin", 0, -1, new ByteArrayOutputStream(), null, big.length); check(false, "dato dañado no detectado"); }
        catch (IOException e) { check(e.getMessage().contains("dañado"), "un byte dañado en el cable se detecta: " + e.getMessage()); }
        // cable cortado a media descarga
        ServerSocket ss3 = new ServerSocket(0);
        Thread st3 = new Thread(() -> { try (Socket s = ss3.accept()) { new LinkServer(new SockPipe(s), vfs, "X", null).run(); } catch (IOException e) { } });
        st3.start();
        Socket cs3 = new Socket("127.0.0.1", ss3.getLocalPort());
        LinkClient cut = new LinkClient(new SockPipe(cs3), "Z");
        try { cut.read("Descargas/sub/grande.bin", 0, -1, new ByteArrayOutputStream(), (dn, tt) -> { if (dn > 1_000_000) try { cs3.close(); } catch (IOException e) {} return true; }, big.length); check(false, "corte no detectado"); }
        catch (IOException e) { check(true, "cable cortado a media descarga: error claro (" + e.getMessage() + ")"); }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
