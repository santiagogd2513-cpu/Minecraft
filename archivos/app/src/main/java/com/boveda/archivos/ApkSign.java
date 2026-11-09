package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Firmador de APK: firma con los esquemas que pide Android (v1, v2 y v3) y alinea el APK para que se
 * pueda instalar. La primera vez Bóveda crea su propia llave (RSA de 2048 bits, válida 30 años); también se
 * puede usar una llave propia en un archivo .p12, .pfx o .bks.
 */
final class ApkSign {
    private static final String OWN = "firma_boveda.p12";
    private static final String CUSTOM = "firma_propia";
    private static final char[] OWN_PASS = "boveda-firma".toCharArray();

    static final class Signer {
        final PrivateKey key;
        final List<X509Certificate> chain;
        final String label;

        Signer(PrivateKey key, List<X509Certificate> chain, String label) {
            this.key = key;
            this.chain = chain;
            this.label = label;
        }
    }

    private ApkSign() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("firma_apk", Context.MODE_PRIVATE);
    }

    static boolean usingCustom(Context c) {
        return prefs(c).getBoolean("propia", false) && new File(c.getFilesDir(), CUSTOM).isFile();
    }

    /** La llave con la que se va a firmar ahora (la propia si se eligió una; si no, la de Bóveda). */
    static Signer signer(Context c) throws Exception {
        if (usingCustom(c)) {
            SharedPreferences p = prefs(c);
            Signer s = load(new File(c.getFilesDir(), CUSTOM), p.getString("tipo", "PKCS12"),
                    Secure.decrypt(p.getString("clave", "")).toCharArray());
            return new Signer(s.key, s.chain, L.t("Tu llave") + " · " + p.getString("nombre", ""));
        }
        Signer s = load(ownFile(c), "PKCS12", OWN_PASS);
        return new Signer(s.key, s.chain, L.t("Llave de Bóveda"));
    }

    private static File ownFile(Context c) throws Exception {
        File f = new File(c.getFilesDir(), OWN);
        if (!f.isFile()) createOwn(f);
        return f;
    }

    private static Signer load(File f, String type, char[] pass) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream in = new FileInputStream(f)) {
            ks.load(in, pass);
        }
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            if (!ks.isKeyEntry(a)) continue;
            Key k = ks.getKey(a, pass);
            Certificate[] chain = ks.getCertificateChain(a);
            if (!(k instanceof PrivateKey) || chain == null || chain.length == 0) continue;
            List<X509Certificate> certs = new ArrayList<>();
            for (Certificate cc : chain) certs.add((X509Certificate) cc);
            return new Signer((PrivateKey) k, certs, a);
        }
        throw new Exception(L.t("Ese archivo no tiene ninguna llave para firmar"));
    }

    /** Prueba una llave .p12/.pfx/.bks con su contraseña y, si sirve, la deja como la llave para firmar. */
    static String importKey(Context c, File f, String password) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT);
        String type = n.endsWith(".bks") ? "BKS" : "PKCS12";
        if (n.endsWith(".jks") || n.endsWith(".keystore")) {
            // las .jks de Java no se pueden leer en Android; muchas veces en realidad son PKCS12
            type = "PKCS12";
        }
        Signer s;
        try {
            s = load(f, type, password.toCharArray());
        } catch (java.io.IOException e) {
            if (n.endsWith(".jks") || n.endsWith(".keystore")) {
                throw new Exception(L.t("Android no puede leer llaves .jks de Java. Conviértela a .p12 en el PC con: keytool -importkeystore -srckeystore llave.jks -destkeystore llave.p12 -deststoretype PKCS12"));
            }
            throw new Exception(L.t("La contraseña no es correcta o el archivo está dañado"));
        }
        File dst = new File(c.getFilesDir(), CUSTOM);
        try (InputStream in = new FileInputStream(f); FileOutputStream o = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
        }
        prefs(c).edit().putBoolean("propia", true).putString("tipo", type).putString("clave", Secure.encrypt(password))
                .putString("nombre", f.getName()).apply();
        return s.label;
    }

    static void useOwn(Context c) {
        prefs(c).edit().putBoolean("propia", false).apply();
    }

    /** Copia la llave de Bóveda a un archivo .p12 (para guardarla o usarla en el PC). Contraseña: boveda-firma. */
    static File exportOwn(Context c, File dir) throws Exception {
        File src = ownFile(c);
        File dst = new File(dir, "Boveda-llave-de-firma.p12");
        for (int i = 1; dst.exists(); i++) dst = new File(dir, "Boveda-llave-de-firma (" + i + ").p12");
        try (InputStream in = new FileInputStream(src); FileOutputStream o = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
        }
        return dst;
    }

    static String ownPassword() {
        return new String(OWN_PASS);
    }

    /** Huella SHA-256 del certificado (lo que identifica la firma). */
    static String fingerprint(X509Certificate cert) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", d[i]));
        }
        return sb.toString();
    }

    /** Firma «in» y deja el resultado en «out» (alineado, con firmas v1, v2 y v3). */
    static void sign(Context c, File in, File out) throws Exception {
        sign(signer(c), in, out);
    }

    static void sign(Signer s, File in, File out) throws Exception {
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("CERT", s.key, s.chain).build();
        ApkSigner.Builder b = new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(in)
                .setOutputApk(out)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false)
                .setCreatedBy("Bóveda");
        try {
            b.build().sign();
        } catch (com.android.apksig.apk.MinSdkVersionException e) {
            // el AndroidManifest no se pudo leer (pasa con algunos APK modificados): se firma para Android 7+
            b.setMinSdkVersion(24);
            b.build().sign();
        }
    }

    /** Revisa la firma de un APK: null si es válida; si no, el problema. */
    static String verify(File apk) {
        try {
            ApkVerifier.Result r = new ApkVerifier.Builder(apk).build().verify();
            if (r.isVerified()) return null;
            StringBuilder sb = new StringBuilder();
            for (ApkVerifier.IssueWithParams i : r.getErrors()) sb.append(i).append('\n');
            return sb.length() == 0 ? L.t("La firma no es válida") : sb.toString().trim();
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    // ------------------------------------------------------------------ crear la llave de Bóveda

    private static void createOwn(File f) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048, new SecureRandom());
        KeyPair kp = g.generateKeyPair();
        X509Certificate cert = selfSigned(kp, "Bóveda", 30);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("boveda", kp.getPrivate(), OWN_PASS, new Certificate[]{cert});
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            ks.store(o, OWN_PASS);
        }
        if (!tmp.renameTo(f)) throw new IOException(L.t("No se pudo guardar la llave de firma"));
    }

    /** Certificado X.509 v3 autofirmado (SHA256withRSA), armado a mano en DER. */
    static X509Certificate selfSigned(KeyPair kp, String cn, int years) throws Exception {
        long now = System.currentTimeMillis();
        Date from = new Date(now - 24L * 60 * 60 * 1000), to = new Date(now + years * 365L * 24 * 60 * 60 * 1000);
        byte[] sigAlg = seq(oid(1, 2, 840, 113549, 1, 1, 11), new byte[]{0x05, 0x00});
        byte[] name = seq(set(seq(oid(2, 5, 4, 3), tlv(0x0C, cn.getBytes(StandardCharsets.UTF_8)))),
                set(seq(oid(2, 5, 4, 10), tlv(0x0C, "Forja Estudios".getBytes(StandardCharsets.UTF_8)))));
        byte[] serial = new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray();
        byte[] tbs = seq(
                tlv(0xA0, tlv(0x02, new byte[]{2})),
                tlv(0x02, serial),
                sigAlg,
                name,
                seq(time(from), time(to)),
                name,
                kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        byte[] der = seq(tbs, sigAlg, tlv(0x03, bits));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    private static byte[] time(Date d) {
        // hasta 2049 va UTCTime; desde 2050, GeneralizedTime (así lo pide el estándar)
        SimpleDateFormat utc = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.ROOT), gen = new SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        gen.setTimeZone(TimeZone.getTimeZone("UTC"));
        java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTime(d);
        return cal.get(java.util.Calendar.YEAR) < 2050
                ? tlv(0x17, utc.format(d).getBytes(StandardCharsets.US_ASCII))
                : tlv(0x18, gen.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] oid(int... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(parts[0] * 40 + parts[1]);
        for (int i = 2; i < parts.length; i++) {
            int v = parts[i];
            byte[] tmp = new byte[5];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7F);
                v >>>= 7;
            } while (v != 0);
            for (int j = n - 1; j >= 0; j--) o.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, o.toByteArray());
    }

    private static byte[] seq(byte[]... items) {
        return tlv(0x30, concat(items));
    }

    private static byte[] set(byte[]... items) {
        return tlv(0x31, concat(items));
    }

    private static byte[] concat(byte[]... items) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] b : items) o.write(b, 0, b.length);
        return o.toByteArray();
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int len = value.length;
        if (len < 0x80) o.write(len);
        else {
            int n = len > 0xFFFFFF ? 4 : len > 0xFFFF ? 3 : len > 0xFF ? 2 : 1;
            o.write(0x80 | n);
            for (int i = n - 1; i >= 0; i--) o.write((len >>> (8 * i)) & 0xFF);
        }
        o.write(value, 0, value.length);
        return o.toByteArray();
    }
}
