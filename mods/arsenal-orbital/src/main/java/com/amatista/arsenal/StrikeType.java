package com.amatista.arsenal;

/** Armas que puede usar el Designador orbital (se eligen con la tecla H). */
public enum StrikeType {
    SATELLITE("satellite", Timeline.BEAM_WORLD_START, Timeline.IMPACT),
    PHOBOS("phobos", PhobosTimeline.WORLD_START, PhobosTimeline.IMPACT);

    public final String id;
    /** Cuándo empieza el efecto visible en el mundo para todos (fracción de la cinemática). */
    public final float worldVisualStart;
    /** Cuándo ocurre el impacto real (fracción de la cinemática). */
    public final float impact;

    StrikeType(String id, float worldVisualStart, float impact) {
        this.id = id;
        this.worldVisualStart = worldVisualStart;
        this.impact = impact;
    }

    public String nameKey() {
        return "weapon.arsenalorbital." + id;
    }

    public String descKey() {
        return "weapon.arsenalorbital." + id + ".desc";
    }

    public static StrikeType byId(String id) {
        for (StrikeType t : values()) if (t.id.equals(id)) return t;
        return SATELLITE;
    }

    public static StrikeType byOrdinal(int i) {
        StrikeType[] v = values();
        return i >= 0 && i < v.length ? v[i] : SATELLITE;
    }
}
