//! Ajustes del launcher, rutas y almacenamiento en disco.

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::sync::RwLock;

use once_cell::sync::Lazy;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    /// Carpeta donde se guardan versiones, librerías, assets, Java e instancias.
    pub game_dir: String,
    /// Ruta a Java elegida por el usuario. Vacía = Java automático de Mojang.
    pub java_path: String,
    pub min_memory_mb: u32,
    pub max_memory_mb: u32,
    /// Argumentos extra de la JVM, separados por espacios.
    pub extra_jvm_args: String,
    /// Aplica los argumentos de recolección de basura optimizados.
    pub optimized_gc: bool,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    /// "nada" | "minimizar" | "cerrar"
    pub on_launch: String,
    pub concurrent_downloads: usize,
    pub show_snapshots: bool,
    pub curseforge_api_key: String,
    /// ID de cliente de la aplicación de Azure para el inicio de sesión con Microsoft.
    pub ms_client_id: String,
    pub selected_instance: String,
    pub selected_account: String,
}

impl Default for Settings {
    fn default() -> Self {
        let total = system_memory_mb();
        // Mitad de la RAM, entre 2 y 8 GB: lo que mejor rinde para la mayoría.
        let max = (total / 2).clamp(2048, 8192);
        Self {
            game_dir: default_game_dir().display().to_string(),
            java_path: String::new(),
            min_memory_mb: 1024,
            max_memory_mb: max,
            extra_jvm_args: String::new(),
            optimized_gc: true,
            width: 1280,
            height: 720,
            fullscreen: false,
            on_launch: "minimizar".into(),
            concurrent_downloads: 32,
            show_snapshots: false,
            curseforge_api_key: String::new(),
            ms_client_id: String::new(),
            selected_instance: String::new(),
            selected_account: String::new(),
        }
    }
}

static SETTINGS: Lazy<RwLock<Settings>> = Lazy::new(|| RwLock::new(load_settings()));

pub fn settings() -> Settings {
    SETTINGS.read().unwrap_or_else(|e| e.into_inner()).clone()
}

pub fn save_settings(new: Settings) -> Result<(), String> {
    let mut new = new;
    new.concurrent_downloads = new.concurrent_downloads.clamp(1, 64);
    new.max_memory_mb = new.max_memory_mb.max(512);
    new.min_memory_mb = new.min_memory_mb.clamp(256, new.max_memory_mb);
    if new.game_dir.trim().is_empty() {
        new.game_dir = default_game_dir().display().to_string();
    }
    write_json(&config_dir().join("settings.json"), &new)?;
    *SETTINGS.write().unwrap_or_else(|e| e.into_inner()) = new;
    Ok(())
}

fn load_settings() -> Settings {
    read_json(&config_dir().join("settings.json")).unwrap_or_default()
}

/// Carpeta de configuración propia del launcher (cuentas, ajustes).
pub fn config_dir() -> PathBuf {
    let dir = dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("AmatistaLauncher");
    let _ = std::fs::create_dir_all(&dir);
    dir
}

/// Carpeta del juego por defecto: %APPDATA%\.amatista en Windows, ~/.amatista en Linux,
/// ~/Library/Application Support/amatista en macOS. Separada de .minecraft para no
/// interferir con el launcher oficial.
pub fn default_game_dir() -> PathBuf {
    if cfg!(target_os = "macos") {
        dirs::data_dir().unwrap_or_default().join("amatista")
    } else if cfg!(target_os = "windows") {
        dirs::data_dir().unwrap_or_default().join(".amatista")
    } else {
        dirs::home_dir().unwrap_or_default().join(".amatista")
    }
}

pub fn game_dir() -> PathBuf {
    PathBuf::from(settings().game_dir)
}

pub fn versions_dir() -> PathBuf {
    game_dir().join("versions")
}
pub fn libraries_dir() -> PathBuf {
    game_dir().join("libraries")
}
pub fn assets_dir() -> PathBuf {
    game_dir().join("assets")
}
pub fn runtimes_dir() -> PathBuf {
    game_dir().join("runtimes")
}
pub fn instances_dir() -> PathBuf {
    game_dir().join("instances")
}
pub fn cache_dir() -> PathBuf {
    game_dir().join("cache")
}

pub fn read_json<T: serde::de::DeserializeOwned>(path: &Path) -> Option<T> {
    let bytes = std::fs::read(path).ok()?;
    serde_json::from_slice(&bytes).ok()
}

/// Escritura atómica: primero a un .tmp y luego se renombra, para no dejar archivos a medias.
pub fn write_json<T: Serialize>(path: &Path, value: &T) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension("json.tmp");
    let data = serde_json::to_vec_pretty(value).map_err(|e| e.to_string())?;
    std::fs::write(&tmp, data).map_err(|e| e.to_string())?;
    std::fs::rename(&tmp, path).map_err(|e| e.to_string())
}

/// RAM total del equipo en MB (0 si no se puede saber).
pub fn system_memory_mb() -> u32 {
    #[cfg(target_os = "linux")]
    {
        if let Ok(info) = std::fs::read_to_string("/proc/meminfo") {
            for line in info.lines() {
                if let Some(rest) = line.strip_prefix("MemTotal:") {
                    let kb: u64 = rest.trim().trim_end_matches("kB").trim().parse().unwrap_or(0);
                    return (kb / 1024) as u32;
                }
            }
        }
        0
    }
    #[cfg(target_os = "windows")]
    {
        #[repr(C)]
        struct MemoryStatusEx {
            length: u32,
            memory_load: u32,
            total_phys: u64,
            avail_phys: u64,
            total_page_file: u64,
            avail_page_file: u64,
            total_virtual: u64,
            avail_virtual: u64,
            avail_extended_virtual: u64,
        }
        extern "system" {
            fn GlobalMemoryStatusEx(buffer: *mut MemoryStatusEx) -> i32;
        }
        let mut status = MemoryStatusEx {
            length: std::mem::size_of::<MemoryStatusEx>() as u32,
            memory_load: 0,
            total_phys: 0,
            avail_phys: 0,
            total_page_file: 0,
            avail_page_file: 0,
            total_virtual: 0,
            avail_virtual: 0,
            avail_extended_virtual: 0,
        };
        // SAFETY: estructura con el tamaño correcto inicializado, como pide la API.
        if unsafe { GlobalMemoryStatusEx(&mut status) } != 0 {
            return (status.total_phys / 1024 / 1024) as u32;
        }
        0
    }
    #[cfg(target_os = "macos")]
    {
        let out = std::process::Command::new("sysctl").args(["-n", "hw.memsize"]).output();
        if let Ok(out) = out {
            let bytes: u64 = String::from_utf8_lossy(&out.stdout).trim().parse().unwrap_or(0);
            return (bytes / 1024 / 1024) as u32;
        }
        0
    }
    #[cfg(not(any(target_os = "linux", target_os = "windows", target_os = "macos")))]
    {
        0
    }
}

/// Nombre del sistema como lo usa Mojang en las reglas de librerías.
pub fn os_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

pub fn os_arch() -> &'static str {
    if cfg!(target_arch = "x86") {
        "x86"
    } else if cfg!(target_arch = "aarch64") {
        "arm64"
    } else {
        "x86_64"
    }
}

pub fn classpath_separator() -> &'static str {
    if cfg!(target_os = "windows") {
        ";"
    } else {
        ":"
    }
}
