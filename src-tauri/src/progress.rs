//! Envío de eventos a la interfaz (progreso, registro del juego).

use once_cell::sync::OnceCell;
use serde::Serialize;
use tauri::{AppHandle, Emitter};

static APP: OnceCell<AppHandle> = OnceCell::new();

pub fn init(app: AppHandle) {
    let _ = APP.set(app);
}

#[derive(Clone, Serialize)]
struct Progress<'a> {
    task: &'a str,
    done: u64,
    total: u64,
    message: &'a str,
}

/// Progreso de una tarea larga (instalación, descarga…).
pub fn emit(task: &str, done: u64, total: u64, message: &str) {
    if let Some(app) = APP.get() {
        let _ = app.emit("progress", Progress { task, done, total, message });
    }
}

/// Mensaje de estado sin porcentaje.
pub fn status(task: &str, message: &str) {
    emit(task, 0, 0, message);
}

pub fn emit_raw<S: Serialize + Clone>(event: &str, payload: S) {
    if let Some(app) = APP.get() {
        let _ = app.emit(event, payload);
    }
}
