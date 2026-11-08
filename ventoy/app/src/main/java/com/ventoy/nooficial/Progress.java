package com.ventoy.nooficial;

/** Avance de una operación larga. */
public interface Progress {
    /** fraction: 0..1 (o -1 si no se sabe); step: qué se está haciendo. */
    void update(float fraction, String step);

    boolean cancelled();

    Progress NONE = new Progress() {
        @Override
        public void update(float fraction, String step) {
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };
}
