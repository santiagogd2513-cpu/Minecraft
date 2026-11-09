package com.boveda.archivos;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * La Bóveda: una carpeta secreta donde todo queda cifrado con AES-256.
 *
 * Hay una llave maestra al azar (32 bytes) que cifra los archivos y sus nombres. Esa llave se guarda
 * envuelta (cifrada) con el PIN, en el archivo «llave» dentro de la carpeta de la Bóveda, así que si se
 * reinstala la app se puede volver a entrar con el mismo PIN. Con la huella, la llave también se guarda
 * envuelta con una clave del almacén de Android que solo se puede usar después de poner la huella.
 *
 * Mientras está abierta, la llave vive solo en la memoria. Se cierra sola cuando sales de la app.
 */
final class Vault {
    /** Donde están los archivos cifrados (en el almacenamiento interno, pero ilegibles sin la llave). */
    static final String DIR = Paths.ROOT + "/.Boveda-segura";
    private static final String KEY_FILE = "llave";
    private static final String BIO_ALIAS = "boveda_huella";
    private static final int ITER = 120_000;
    /** Cuánto espera para cerrarse después de salir de la app (girar la pantalla no la cierra). */
    private static final long LOCK_DELAY = 15_000;

    private static byte[] dataKey, nameKey;
    private static boolean watching;
    private static int started;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Context appContext;
    /** No se cierra mientras se están moviendo archivos hacia o desde la Bóveda. */
    private static final Runnable autoLock = new Runnable() {
        @Override
        public void run() {
            if (appContext != null) {
                for (TransferEngine.Job j : TransferEngine.get(appContext).jobs()) {
                    if (j.state <= 1) {
                        main.postDelayed(this, LOCK_DELAY);
                        return;
                    }
                }
            }
            lock();
        }
    };
    private static final SecureRandom random = new SecureRandom();

    private Vault() {}

    static File dir() {
        return new File(DIR);
    }

    static boolean exists() {
        return new File(DIR, KEY_FILE).isFile();
    }

    static synchronized boolean unlocked() {
        return dataKey != null;
    }

    static synchronized byte[] dataKey() {
        return dataKey;
    }

    static synchronized byte[] nameKey() {
        return nameKey;
    }

    static synchronized void lock() {
        if (dataKey != null) Arrays.fill(dataKey, (byte) 0);
        if (nameKey != null) Arrays.fill(nameKey, (byte) 0);
        dataKey = null;
        nameKey = null;
        VaultFs.forget();
        Thumbs.clearAll();
    }

    private static synchronized void open(byte[] master) throws Exception {
        dataKey = derive(master, "datos");
        nameKey = derive(master, "nombres");
        Arrays.fill(master, (byte) 0);
    }

    /** Dos llaves distintas a partir de la maestra: una para el contenido y otra para los nombres. */
    private static byte[] derive(byte[] master, String label) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(master, "HmacSHA256"));
        return m.doFinal(("boveda-" + label).getBytes(StandardCharsets.UTF_8));
    }

    static byte[] random(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    // ------------------------------------------------------------------ PIN

    private static byte[] pinKey(String pin, byte[] salt, int iter) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return f.generateSecret(new PBEKeySpec(pin.toCharArray(), salt, iter, 256)).getEncoded();
    }

    private static byte[] gcm(int mode, byte[] key, byte[] iv, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(data);
    }

    /** Crea la Bóveda con este PIN y la deja abierta. */
    static void create(Context c, String pin) throws Exception {
        File d = dir();
        if (!d.isDirectory() && !d.mkdirs()) throw new Exception(L.t("No se pudo crear la carpeta de la Bóveda"));
        // que la galería y los reproductores no la revisen
        //noinspection ResultOfMethodCallIgnored
        new File(d, ".nomedia").createNewFile();
        byte[] master = random(32);
        writeKeyFile(master.clone(), pin);
        forgetBiometric(c);
        open(master);
    }

    private static void writeKeyFile(byte[] master, String pin) throws Exception {
        byte[] salt = random(16), iv = random(12);
        byte[] wrapped = gcm(Cipher.ENCRYPT_MODE, pinKey(pin, salt, ITER), iv, master);
        Arrays.fill(master, (byte) 0);
        JSONObject j = new JSONObject();
        j.put("v", 1);
        j.put("iter", ITER);
        j.put("salt", b64(salt));
        j.put("iv", b64(iv));
        j.put("llave", b64(wrapped));
        File tmp = new File(dir(), KEY_FILE + ".nueva"), f = new File(dir(), KEY_FILE);
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(j.toString().getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new Exception(L.t("No se pudo guardar la llave de la Bóveda"));
    }

    private static byte[] readMaster(String pin) throws Exception {
        JSONObject j = new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(dir(), KEY_FILE).toPath()), StandardCharsets.UTF_8));
        byte[] key = pinKey(pin, unb64(j.getString("salt")), j.optInt("iter", ITER));
        try {
            return gcm(Cipher.DECRYPT_MODE, key, unb64(j.getString("iv")), unb64(j.getString("llave")));
        } catch (javax.crypto.AEADBadTagException e) {
            return null;
        }
    }

    // intentos fallidos: después de 5 seguidos hay que esperar, cada vez más
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("boveda_segura", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ autoborrado

    /** Intentos de PIN fallidos seguidos que borran todo (si el autoborrado está encendido). */
    static final int WIPE_AFTER = 7;
    private static boolean wiped;

    static boolean autoWipe(Context c) {
        return prefs(c).getBoolean("autoborrado", false);
    }

    static void setAutoWipe(Context c, boolean on) {
        prefs(c).edit().putBoolean("autoborrado", on).putInt("fallos", 0).putLong("espera_hasta", 0).apply();
    }

    static int fails(Context c) {
        return prefs(c).getInt("fallos", 0);
    }

    /** ¿El último intento con el PIN acabó borrando La Bóveda? */
    static boolean justWiped() {
        boolean w = wiped;
        wiped = false;
        return w;
    }

    /**
     * Borra La Bóveda para siempre. Primero se destruye la llave: sin ella, lo cifrado ya es ruido aunque
     * quede en el disco; después se borran los archivos.
     */
    static void wipe(Context c) {
        lock();
        //noinspection ResultOfMethodCallIgnored
        new File(DIR, KEY_FILE).delete();
        deleteTree(dir());
        forgetBiometric(c);
        prefs(c).edit().putInt("fallos", 0).putLong("espera_hasta", 0).apply();
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Segundos que faltan para poder volver a intentar con el PIN (0 = ya se puede). */
    static long waitSeconds(Context c) {
        if (autoWipe(c)) return 0;   // con autoborrado no hay esperas: el séptimo error borra todo
        long until = prefs(c).getLong("espera_hasta", 0);
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    /** Abre con el PIN. false si el PIN no es. */
    static boolean unlockWithPin(Context c, String pin) throws Exception {
        if (waitSeconds(c) > 0) return false;
        byte[] master = readMaster(pin);
        SharedPreferences p = prefs(c);
        if (master == null) {
            int fails = p.getInt("fallos", 0) + 1;
            if (autoWipe(c) && fails >= WIPE_AFTER) {
                wipe(c);
                wiped = true;
                return false;
            }
            SharedPreferences.Editor e = p.edit().putInt("fallos", fails);
            if (autoWipe(c)) {
                e.apply();
                return false;
            }
            if (fails >= 5) e.putLong("espera_hasta", System.currentTimeMillis() + 30_000L * (1L << Math.min(fails - 5, 6)));
            e.apply();
            return false;
        }
        p.edit().putInt("fallos", 0).putLong("espera_hasta", 0).apply();
        open(master);
        return true;
    }

    /** Cambia el PIN (la Bóveda tiene que estar abierta y hay que saber el PIN actual). */
    static boolean changePin(String oldPin, String newPin) throws Exception {
        byte[] master = readMaster(oldPin);
        if (master == null) return false;
        writeKeyFile(master, newPin);
        return true;
    }

    // ------------------------------------------------------------------ huella

    static boolean biometricOn(Context c) {
        return !prefs(c).getString("huella", "").isEmpty();
    }

    private static SecretKey bioKey(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(BIO_ALIAS) && !create) return ((KeyStore.SecretKeyEntry) ks.getEntry(BIO_ALIAS, null)).getSecretKey();
        if (!create) return null;
        if (ks.containsAlias(BIO_ALIAS)) ks.deleteEntry(BIO_ALIAS);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= 30) b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(b.build());
        return g.generateKey();
    }

    /** Cifrador listo para guardar la llave con la huella (hay que pasarlo por el aviso de huella). */
    static Cipher bioEncryptCipher() throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, bioKey(true));
        return c;
    }

    /** Después de poner la huella: guarda la llave maestra envuelta. Necesita el PIN para sacar la maestra. */
    static boolean enableBiometric(Context c, Cipher authed, String pin) throws Exception {
        byte[] master = readMaster(pin);
        if (master == null) return false;
        byte[] wrapped = authed.doFinal(master);
        Arrays.fill(master, (byte) 0);
        prefs(c).edit().putString("huella", b64(authed.getIV()) + ":" + b64(wrapped)).apply();
        return true;
    }

    /** Descifrador para abrir con la huella; null si la huella ya no sirve (por ejemplo, se registró otra). */
    static Cipher bioDecryptCipher(Context c) {
        try {
            String s = prefs(c).getString("huella", "");
            if (s.isEmpty()) return null;
            SecretKey k = bioKey(false);
            if (k == null) {
                forgetBiometric(c);
                return null;
            }
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, unb64(s.split(":")[0])));
            return ci;
        } catch (android.security.keystore.KeyPermanentlyInvalidatedException e) {
            forgetBiometric(c);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean unlockWithBiometric(Context c, Cipher authed) {
        try {
            String s = prefs(c).getString("huella", "");
            byte[] master = authed.doFinal(unb64(s.split(":")[1]));
            prefs(c).edit().putInt("fallos", 0).putLong("espera_hasta", 0).apply();
            open(master);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void forgetBiometric(Context c) {
        prefs(c).edit().remove("huella").apply();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(BIO_ALIAS)) ks.deleteEntry(BIO_ALIAS);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ cierre automático

    /** Se cierra sola cuando la app queda en segundo plano (todas sus pantallas ocultas). */
    static void watch(Application app) {
        if (watching) return;
        watching = true;
        appContext = app;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                started++;
                main.removeCallbacks(autoLock);
            }
            @Override public void onActivityStopped(Activity a) {
                if (--started <= 0) {
                    started = 0;
                    main.postDelayed(autoLock, LOCK_DELAY);
                }
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
