package com.boveda.archivos;

/** Bóveda como selector de archivos para otras apps (adjuntar, subir, cargar una ROM…). */
public class PickerActivity extends MainActivity {
    @Override
    protected boolean picker() {
        return true;
    }
}
