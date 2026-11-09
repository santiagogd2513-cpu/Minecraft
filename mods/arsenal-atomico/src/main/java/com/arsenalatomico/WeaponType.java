package com.arsenalatomico;

/** Las cinco armas del Marcador de objetivos, de la más pequeña a la más grande (tecla J). */
public enum WeaponType {
    /** Lluvia de Acero: 24 misiles tácticos desde un camión lanzacohetes. */
    SALVO("salvo", true, 210),
    /** Cóndor Doble: dos intercontinentales desde un submarino, o desde dos lanzadoras móviles si no hay mar. */
    TWIN("twin", true, 210),
    /** Cóndor: un misil intercontinental (no nuclear) desde un búnker subterráneo. */
    ICBM("icbm", true, 210),
    /** Protocolo Hongo: bomba nuclear pequeña desde el búnker más detallado (silo, cuarto de control, pasillo, caseta). */
    NUKE("nuke", true, 210),
    /**
     * Protocolo Sol Negro: termonuclear. Es una misión: un camión lleva al jugador a la Base Sol
     * Negro y él mismo oprime el botón (la cinemática dura desde el segundo 90 hasta el 210).
     */
    THERMO("thermo", true, 210);

    public final String id;
    /** Ya está hecha (las demás llegan en las siguientes fases). */
    public final boolean ready;
    /** Duración de la cinemática en segundos (todas duran 3 min 30 s). */
    public final int seconds;

    WeaponType(String id, boolean ready, int seconds) {
        this.id = id;
        this.ready = ready;
        this.seconds = seconds;
    }

    public int ticks() {
        return seconds * 20;
    }

    public String nameKey() {
        return "weapon.arsenalatomico." + id;
    }

    public String descKey() {
        return "weapon.arsenalatomico." + id + ".desc";
    }

    public static WeaponType byId(String id) {
        for (WeaponType t : values()) if (t.id.equals(id)) return t;
        return SALVO;
    }

    public static WeaponType byOrdinal(int i) {
        WeaponType[] v = values();
        return i >= 0 && i < v.length ? v[i] : SALVO;
    }
}
