package net.kdt.pojavlaunch.modloaders.modpacks.models;

public class Constants {
    private Constants(){}

    /** Types of modpack apis */
    public static final int SOURCE_MODRINTH = 0x0;
    public static final int SOURCE_CURSEFORGE = 0x1;
    public static final int SOURCE_TECHNIC = 0x2;

    /* Tipos de contenido (Amatista) */
    public static final int TYPE_MOD = 0;
    public static final int TYPE_MODPACK = 1;
    public static final int TYPE_RESOURCEPACK = 2;
    public static final int TYPE_SHADER = 3;

    /** Carpeta del juego donde va cada tipo de contenido. */
    public static String folderFor(int type) {
        switch (type) {
            case TYPE_RESOURCEPACK: return "resourcepacks";
            case TYPE_SHADER: return "shaderpacks";
            default: return "mods";
        }
    }

    /** Modrinth api, file environments */
    public static final String MODRINTH_FILE_ENV_REQUIRED = "required";
    public static final String MODRINTH_FILE_ENV_OPTIONAL = "optional";
    public static final String MODRINTH_FILE_ENV_UNSUPPORTED = "unsupported";

}
