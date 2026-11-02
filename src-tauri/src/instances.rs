//! Instancias: cada juego instalado (vanilla, Forge u OptiFine) con su propia carpeta
//! (mods, paquetes de recursos, shaders, mundos). Versiones, librerías y assets se
//! comparten entre todas para no duplicar descargas.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::config::{self, instances_dir};

#[derive(Clone, Debug, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct Instance {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    /// "vanilla" | "forge" | "optifine"
    pub loader: String,
    pub loader_version: String,
    /// Versión a lanzar (p. ej. "1.20.1-forge-47.2.0").
    pub version_id: String,
    pub created: u64,
    pub last_played: u64,
    /// Memoria máxima propia (0 = la de Ajustes).
    pub max_memory_mb: u32,
}

impl Instance {
    pub fn dir(&self) -> PathBuf {
        instances_dir().join(&self.id)
    }
}

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

fn store_path() -> PathBuf {
    config::game_dir().join("instances.json")
}

pub fn list() -> Vec<Instance> {
    let mut v: Vec<Instance> = config::read_json(&store_path()).unwrap_or_default();
    v.sort_by(|a, b| b.last_played.max(b.created).cmp(&a.last_played.max(a.created)));
    v
}

fn save(list: &[Instance]) -> Result<(), String> {
    config::write_json(&store_path(), &list)
}

pub fn get(id: &str) -> Option<Instance> {
    list().into_iter().find(|i| i.id == id)
}

fn slug(name: &str) -> String {
    let mut s: String = name
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c.to_ascii_lowercase() } else { '-' })
        .collect();
    while s.contains("--") {
        s = s.replace("--", "-");
    }
    let s = s.trim_matches('-').to_string();
    if s.is_empty() {
        "instancia".into()
    } else {
        s
    }
}

/// Crea (o reutiliza, si ya existe una igual) una instancia.
pub fn create(name: &str, mc_version: &str, loader: &str, loader_version: &str, version_id: &str) -> Result<Instance, String> {
    let mut all = list();
    if let Some(existing) = all.iter().find(|i| i.version_id == version_id && i.name == name) {
        return Ok(existing.clone());
    }
    let base = slug(name);
    let mut id = base.clone();
    let mut n = 2;
    while all.iter().any(|i| i.id == id) || instances_dir().join(&id).exists() {
        id = format!("{base}-{n}");
        n += 1;
    }
    let inst = Instance {
        id,
        name: name.to_string(),
        mc_version: mc_version.to_string(),
        loader: loader.to_string(),
        loader_version: loader_version.to_string(),
        version_id: version_id.to_string(),
        created: now(),
        ..Default::default()
    };
    for sub in ["mods", "resourcepacks", "shaderpacks", "saves", "config"] {
        std::fs::create_dir_all(inst.dir().join(sub)).map_err(|e| e.to_string())?;
    }
    all.push(inst.clone());
    save(&all)?;
    Ok(inst)
}

pub fn update(inst: &Instance) -> Result<(), String> {
    let mut all = list();
    if let Some(slot) = all.iter_mut().find(|i| i.id == inst.id) {
        *slot = inst.clone();
    }
    save(&all)
}

pub fn touch_played(id: &str) {
    if let Some(mut i) = get(id) {
        i.last_played = now();
        let _ = update(&i);
    }
}

/// Borra la instancia y su carpeta (mundos y mods incluidos).
pub fn delete(id: &str) -> Result<(), String> {
    let mut all = list();
    if let Some(i) = all.iter().find(|i| i.id == id) {
        let dir = i.dir();
        if dir.starts_with(instances_dir()) && dir.exists() {
            std::fs::remove_dir_all(&dir).map_err(|e| e.to_string())?;
        }
    }
    all.retain(|i| i.id != id);
    save(&all)
}

pub fn rename(id: &str, name: &str) -> Result<(), String> {
    let mut all = list();
    for i in all.iter_mut().filter(|i| i.id == id) {
        i.name = name.trim().to_string();
    }
    save(&all)
}
