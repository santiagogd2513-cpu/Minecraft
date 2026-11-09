package com.arsenalatomico;

/** Las cinco armas del Marcador de objetivos, de la más pequeña a la más grande (tecla J). */
public enum WeaponType {
    /** Lluvia de Acero: 24 misiles tácticos desde un camión lanzacohetes. */
    SALVO("salvo", true, 210),
    /** Cóndor Doble: dos intercontinentales desde un submarino, o desde dos lanzadoras móviles si no hay mar. */
    TWIN("twin", true, 210),
    /** Cóndor: un misil intercontinental (no nuclear) desde un búnker subterráneo. */
    ICBM("icbm", false, 210),
    /** Protocolo Hongo: misil nuclear desde un silo subterráneo. */
    NUKE("nuke", false, 210),
    /** Protocolo Sol Negro: termonuclear desde el búnker más profundo. */
    THERMO("thermo", false, 210);

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
