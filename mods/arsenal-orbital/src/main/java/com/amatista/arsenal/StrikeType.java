package com.amatista.arsenal;

/** Armas que puede usar el Designador orbital (se eligen con la tecla H). */
public enum StrikeType {
    SATELLITE("satellite", Timeline.BEAM_WORLD_START, Timeline.IMPACT, Timeline.IMPACT),
    PHOBOS("phobos", PhobosTimeline.WORLD_START, PhobosTimeline.IMPACT, PhobosTimeline.IMPACT),
    BLACKHOLE("blackhole", BlackHoleTimeline.WORLD_START, BlackHoleTimeline.IMPACT, BlackHoleTimeline.PULL_START),
    STAR("star", StarTimeline.WORLD_START, StarTimeline.IMPACT, StarTimeline.BURN_START),
    GENESIS("genesis", GenesisTimeline.WORLD_START, GenesisTimeline.IMPACT, GenesisTimeline.BLOOM_START);

    public final String id;
    /** Cuándo empieza el efecto visible en el mundo para todos (fracción de la cinemática). */
    public final float worldVisualStart;
    /** Cuándo ocurre el impacto real (fracción de la cinemática). */
    public final float impact;
    /** Cuándo empieza a actuar en el mundo antes del impacto (la singularidad absorbe); igual a impact si no aplica. */
    public final float pullStart;

    StrikeType(String id, float worldVisualStart, float impact, float pullStart) {
        this.id = id;
        this.worldVisualStart = worldVisualStart;
        this.impact = impact;
        this.pullStart = pullStart;
    }

    public String nameKey() {
        return "weapon.arsenalorbital." + id;
    }

    public String descKey() {
        return "weapon.arsenalorbital." + id + ".desc";
    }

    /** Multiplica la duración configurada (el Protocolo Prometeo dura 3 min 30 s). */
    public float durationScale() {
        if (this == GENESIS) return GenesisTimeline.DURATION_SCALE;
        return this == STAR ? StarTimeline.DURATION_SCALE : 1f;
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
