package com.boveda.archivos;

/** Cómo se va a comprimir: formato, nivel, contraseña y si se borra el original (que va a la papelera). */
final class CompressOptions {
    enum Format {
        ZIP(".zip", "ZIP"), SEVEN_Z(".7z", "7Z"), TAR_GZ(".tar.gz", "TAR.GZ"), TAR_XZ(".tar.xz", "TAR.XZ");

        final String ext, label;

        Format(String ext, String label) {
            this.ext = ext;
            this.label = label;
        }

        /** ¿Admite contraseña? (el .tar no tiene cómo cifrar) */
        boolean password() {
            return this == ZIP || this == SEVEN_Z;
        }

        /** Los .tar.* comprimen todo como un solo bloque: ya son «sólidos». */
        boolean solid() {
            return this == TAR_GZ || this == TAR_XZ;
        }
    }

    /** 0 sin compresión, 1 rápida, 2 normal, 3 alta, 4 ultra. */
    static final String[] LEVELS = {"Sin compresión", "Rápida", "Normal", "Alta", "Ultra"};

    Format format = Format.ZIP;
    int level = 2;
    char[] password;
    boolean deleteOriginal;

    boolean encrypted() {
        return password != null && password.length > 0 && format.password();
    }

    /** Nivel de deflate (0 a 9) para .zip y .tar.gz. */
    int deflateLevel() {
        return new int[]{0, 1, 6, 8, 9}[level];
    }

    /**
     * Preajuste de LZMA2 (0 a 9) para .7z y .tar.xz. El «ultra» no llega al 9 de siempre: ese pide más de 600 MB de
     * memoria para comprimir y un celular no lo aguanta.
     */
    int lzmaPreset() {
        return new int[]{0, 1, 4, 6, 7}[level];
    }
}
