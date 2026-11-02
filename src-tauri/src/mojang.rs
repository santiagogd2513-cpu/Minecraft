//! Versiones de Minecraft: lista oficial de Mojang, lectura y fusión de los JSON de
//! versión (`inheritsFrom`), reglas de librerías e instalación de vanilla (jar del
//! cliente, librerías, nativos y assets).

use serde_json::{json, Map, Value};
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use crate::config::{self, assets_dir, libraries_dir, versions_dir};
use crate::net::{self, Download};
use crate::progress;

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const LIBRARIES_URL: &str = "https://libraries.minecraft.net/";
const RESOURCES_URL: &str = "https://resources.download.minecraft.net/";

/// Lista de versiones de Mojang, con caché en disco de 15 minutos.
pub async fn manifest() -> Result<Value, String> {
    let cache = config::cache_dir().join("version_manifest_v2.json");
    let fresh = std::fs::metadata(&cache)
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| SystemTime::now().duration_since(t).ok())
        .is_some_and(|age| age < Duration::from_secs(15 * 60));
    if fresh {
        if let Some(v) = config::read_json::<Value>(&cache) {
            return Ok(v);
        }
    }
    match net::get_json(MANIFEST_URL).await {
        Ok(v) => {
            let _ = config::write_json(&cache, &v);
            Ok(v)
        }
        // Sin internet: usar la copia vieja si existe.
        Err(e) => config::read_json::<Value>(&cache).ok_or(e),
    }
}

pub fn version_json_path(id: &str) -> PathBuf {
    versions_dir().join(id).join(format!("{id}.json"))
}

pub fn read_version(id: &str) -> Result<Value, String> {
    config::read_json(&version_json_path(id)).ok_or_else(|| format!("La versión {id} no está instalada"))
}

/// Descarga el JSON de una versión de vanilla si aún no está.
pub async fn ensure_version_json(id: &str) -> Result<Value, String> {
    let path = version_json_path(id);
    let manifest = manifest().await?;
    let entry = manifest["versions"]
        .as_array()
        .and_then(|vs| vs.iter().find(|v| v["id"] == id))
        .cloned();
    if let Some(entry) = entry {
        let d = Download {
            url: entry["url"].as_str().unwrap_or_default().to_string(),
            path: path.clone(),
            sha1: entry["sha1"].as_str().map(str::to_string),
            size: None,
        };
        if !net::is_valid(&d) {
            net::download_one(&d, None).await?;
        }
    } else if !path.exists() {
        return Err(format!("La versión {id} no existe en la lista de Mojang"));
    }
    read_version(id)
}

// ---------------------------------------------------------------------------
// Reglas y librerías
// ---------------------------------------------------------------------------

/// Evalúa las reglas `rules` de una librería o argumento para este sistema.
/// `features` son las opciones del launcher (resolución personalizada, demo…).
pub fn rules_allow(rules: Option<&Value>, features: &Map<String, Value>) -> bool {
    let Some(rules) = rules.and_then(Value::as_array) else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        let mut matches = true;
        if let Some(os) = rule.get("os") {
            if let Some(name) = os.get("name").and_then(Value::as_str) {
                if name != config::os_name() {
                    matches = false;
                }
            }
            if let Some(arch) = os.get("arch").and_then(Value::as_str) {
                let current = if cfg!(target_arch = "x86") { "x86" } else { "x86_64" };
                if arch != current && !(arch == "arm64" && cfg!(target_arch = "aarch64")) {
                    matches = false;
                }
            }
        }
        if let Some(feats) = rule.get("features").and_then(Value::as_object) {
            for (k, v) in feats {
                let have = features.get(k).and_then(Value::as_bool).unwrap_or(false);
                if Some(have) != v.as_bool() {
                    matches = false;
                }
            }
        }
        if matches {
            allowed = rule["action"] == "allow";
        }
    }
    allowed
}

/// "grupo:artefacto:versión[:clasificador][@ext]" → ruta relativa en el repositorio Maven.
pub fn maven_path(name: &str) -> Option<String> {
    let (coords, ext) = match name.split_once('@') {
        Some((c, e)) => (c, e),
        None => (name, "jar"),
    };
    let parts: Vec<&str> = coords.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let file = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    Some(format!("{group}/{artifact}/{version}/{file}"))
}

/// Clave "grupo:artefacto[:clasificador]" para evitar duplicados con distinta versión.
pub fn library_key(name: &str) -> String {
    let coords = name.split('@').next().unwrap_or(name);
    let parts: Vec<&str> = coords.split(':').collect();
    match parts.len() {
        0..=2 => coords.to_string(),
        3 => format!("{}:{}", parts[0], parts[1]),
        _ => format!("{}:{}:{}", parts[0], parts[1], parts[3]),
    }
}

/// Clasificador de nativos para este sistema en librerías antiguas (`natives`).
fn natives_classifier(lib: &Value) -> Option<String> {
    let c = lib.get("natives")?.get(config::os_name())?.as_str()?;
    let bits = if cfg!(target_pointer_width = "64") { "64" } else { "32" };
    Some(c.replace("${arch}", bits))
}

pub struct ResolvedLibrary {
    pub path: PathBuf,
    pub download: Option<Download>,
    /// Jar de nativos antiguo que hay que descomprimir (no va en el classpath).
    pub is_native: bool,
}

/// Lista de archivos de librerías que necesita una versión (ya fusionada).
pub fn resolve_libraries(version: &Value) -> Vec<ResolvedLibrary> {
    let features = Map::new();
    let mut out = Vec::new();
    let Some(libs) = version["libraries"].as_array() else {
        return out;
    };
    for lib in libs {
        if !rules_allow(lib.get("rules"), &features) {
            continue;
        }
        let name = lib["name"].as_str().unwrap_or_default();
        let downloads = lib.get("downloads");

        // Artefacto principal.
        let artifact = downloads.and_then(|d| d.get("artifact"));
        let has_classifiers_only = artifact.is_none() && lib.get("natives").is_some();
        if !has_classifiers_only {
            if let Some(a) = artifact {
                let rel = a["path"]
                    .as_str()
                    .map(str::to_string)
                    .or_else(|| maven_path(name))
                    .unwrap_or_default();
                let url = a["url"].as_str().unwrap_or_default();
                let path = libraries_dir().join(&rel);
                out.push(ResolvedLibrary {
                    download: (!url.is_empty()).then(|| Download {
                        url: url.to_string(),
                        path: path.clone(),
                        sha1: a["sha1"].as_str().map(str::to_string),
                        size: a["size"].as_u64(),
                    }),
                    path,
                    is_native: false,
                });
            } else if let Some(rel) = maven_path(name) {
                // Formato antiguo (Forge legacy, OptiFine): solo nombre y quizá "url" base.
                let base = lib["url"].as_str().unwrap_or(LIBRARIES_URL);
                let base = if base.ends_with('/') { base.to_string() } else { format!("{base}/") };
                // Forge antiguo apunta a direcciones viejas; el repositorio actual es este.
                let base = base
                    .replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
                    .replace("https://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
                    .replace("http://", "https://");
                let path = libraries_dir().join(&rel);
                let local_only = lib.get("url").is_none() && name.starts_with("optifine:");
                out.push(ResolvedLibrary {
                    download: (!local_only).then(|| Download {
                        url: format!("{base}{rel}"),
                        path: path.clone(),
                        sha1: lib["checksums"][0].as_str().map(str::to_string),
                        size: None,
                    }),
                    path,
                    is_native: false,
                });
            }
        }

        // Nativos del formato antiguo (classifiers).
        if let Some(classifier) = natives_classifier(lib) {
            let info = downloads.and_then(|d| d.get("classifiers")).and_then(|c| c.get(&classifier));
            let (rel, url, sha1, size) = match info {
                Some(i) => (
                    i["path"].as_str().unwrap_or_default().to_string(),
                    i["url"].as_str().unwrap_or_default().to_string(),
                    i["sha1"].as_str().map(str::to_string),
                    i["size"].as_u64(),
                ),
                None => {
                    let rel = maven_path(&format!("{name}:{classifier}")).unwrap_or_default();
                    (rel.clone(), format!("{LIBRARIES_URL}{rel}"), None, None)
                }
            };
            if !rel.is_empty() {
                let path = libraries_dir().join(&rel);
                out.push(ResolvedLibrary {
                    download: (!url.is_empty()).then(|| Download { url, path: path.clone(), sha1, size }),
                    path,
                    is_native: true,
                });
            }
        }
    }
    out
}

// ---------------------------------------------------------------------------
// Fusión de versiones (inheritsFrom)
// ---------------------------------------------------------------------------

/// Carga una versión y la fusiona con su padre (Forge, OptiFine heredan de vanilla).
/// El resultado tiene "id" del hijo y "jar" con la versión cuyo .jar se usa.
pub fn load_merged(id: &str) -> Result<Value, String> {
    let mut chain = Vec::new();
    let mut current = id.to_string();
    for _ in 0..8 {
        let v = read_version(&current)?;
        let parent = v["inheritsFrom"].as_str().map(str::to_string);
        chain.push(v);
        match parent {
            Some(p) => current = p,
            None => break,
        }
    }
    // chain[0] = hijo, último = vanilla base.
    let mut merged = chain.pop().ok_or("Versión vacía")?;
    let base_jar = merged["jar"].as_str().unwrap_or(merged["id"].as_str().unwrap_or(id)).to_string();
    while let Some(child) = chain.pop() {
        merged = merge(merged, child);
    }
    merged["jar"] = json!(base_jar);
    merged["id"] = json!(id);
    Ok(merged)
}

fn merge(parent: Value, child: Value) -> Value {
    let mut out = parent.clone();
    let Some(obj) = out.as_object_mut() else {
        return child;
    };
    for (k, v) in child.as_object().cloned().unwrap_or_default() {
        match k.as_str() {
            "libraries" => {
                // Librerías del hijo primero; las del padre solo si no están repetidas.
                let mut libs: Vec<Value> = v.as_array().cloned().unwrap_or_default();
                let mut seen: std::collections::HashSet<String> = libs
                    .iter()
                    .filter_map(|l| l["name"].as_str().map(library_key))
                    .collect();
                for l in parent["libraries"].as_array().cloned().unwrap_or_default() {
                    let key = l["name"].as_str().map(library_key).unwrap_or_default();
                    if seen.insert(key) {
                        libs.push(l);
                    }
                }
                obj.insert(k, Value::Array(libs));
            }
            "arguments" => {
                let mut args = parent["arguments"].clone();
                if !args.is_object() {
                    args = json!({});
                }
                for side in ["game", "jvm"] {
                    let mut list = args[side].as_array().cloned().unwrap_or_default();
                    list.extend(v[side].as_array().cloned().unwrap_or_default());
                    args[side] = Value::Array(list);
                }
                obj.insert(k, args);
            }
            "inheritsFrom" | "jar" => {}
            _ => {
                obj.insert(k, v);
            }
        }
    }
    out
}

// ---------------------------------------------------------------------------
// Instalación
// ---------------------------------------------------------------------------

/// Instala (o repara) todo lo que necesita una versión ya fusionada: jar del cliente,
/// librerías, assets y configuración de registro. Devuelve la versión fusionada.
pub async fn install_files(id: &str) -> Result<Value, String> {
    let task = format!("Instalando {id}");
    let version = load_merged(id)?;
    let concurrency = config::settings().concurrent_downloads;
    let mut downloads = Vec::new();

    // Jar del cliente (de la versión base).
    let jar_id = version["jar"].as_str().unwrap_or(id).to_string();
    if let Some(client) = version["downloads"]["client"].as_object() {
        downloads.push(Download {
            url: client["url"].as_str().unwrap_or_default().to_string(),
            path: versions_dir().join(&jar_id).join(format!("{jar_id}.jar")),
            sha1: client["sha1"].as_str().map(str::to_string),
            size: client["size"].as_u64(),
        });
    }

    // Librerías.
    for lib in resolve_libraries(&version) {
        if let Some(d) = lib.download {
            downloads.push(d);
        }
    }

    // Configuración de log4j.
    if let Some(file) = version["logging"]["client"]["file"].as_object() {
        let fid = file["id"].as_str().unwrap_or("client.xml");
        downloads.push(Download {
            url: file["url"].as_str().unwrap_or_default().to_string(),
            path: assets_dir().join("log_configs").join(fid),
            sha1: file["sha1"].as_str().map(str::to_string),
            size: file["size"].as_u64(),
        });
    }

    // Índice de assets.
    let index_id = version["assetIndex"]["id"]
        .as_str()
        .or(version["assets"].as_str())
        .unwrap_or("legacy")
        .to_string();
    let index_path = assets_dir().join("indexes").join(format!("{index_id}.json"));
    if let Some(ai) = version["assetIndex"].as_object() {
        let d = Download {
            url: ai["url"].as_str().unwrap_or_default().to_string(),
            path: index_path.clone(),
            sha1: ai["sha1"].as_str().map(str::to_string),
            size: ai["size"].as_u64(),
        };
        if !net::is_valid(&d) {
            net::download_one(&d, None).await?;
        }
    }

    // Objetos de assets.
    let index: Value = config::read_json(&index_path).unwrap_or(json!({}));
    let mut asset_objects = Vec::new();
    if let Some(objects) = index["objects"].as_object() {
        for (name, obj) in objects {
            let Some(hash) = obj["hash"].as_str() else { continue };
            let sub = &hash[..2];
            let path = assets_dir().join("objects").join(sub).join(hash);
            asset_objects.push((name.clone(), path.clone()));
            downloads.push(Download {
                url: format!("{RESOURCES_URL}{sub}/{hash}"),
                path,
                sha1: Some(hash.to_string()),
                size: obj["size"].as_u64(),
            });
        }
    }

    net::download_all(&task, downloads, concurrency).await?;

    // Versiones muy antiguas leen los assets con su nombre real.
    let is_virtual = index["virtual"].as_bool().unwrap_or(false);
    let map_to_resources = index["map_to_resources"].as_bool().unwrap_or(false);
    if is_virtual || map_to_resources {
        progress::status(&task, "Preparando recursos antiguos…");
        let target = assets_dir().join("virtual").join(&index_id);
        tokio::task::spawn_blocking(move || {
            for (name, src) in asset_objects {
                let dest = target.join(&name);
                if !dest.exists() {
                    if let Some(p) = dest.parent() {
                        let _ = std::fs::create_dir_all(p);
                    }
                    let _ = std::fs::copy(&src, &dest);
                }
            }
        })
        .await
        .map_err(|e| e.to_string())?;
    }

    progress::emit(&task, 1, 1, "Listo");
    Ok(version)
}

/// Instala vanilla completo: JSON, archivos y el Java que pide.
pub async fn install_vanilla(id: &str) -> Result<(), String> {
    progress::status(&format!("Instalando {id}"), "Leyendo la versión…");
    ensure_version_json(id).await?;
    let version = install_files(id).await?;
    crate::java::ensure_for_version(&version).await?;
    Ok(())
}

/// Ruta al jar del cliente de la versión base.
pub fn client_jar(version: &Value) -> PathBuf {
    let jar_id = version["jar"].as_str().or(version["id"].as_str()).unwrap_or_default();
    versions_dir().join(jar_id).join(format!("{jar_id}.jar"))
}

pub fn is_installed(id: &str) -> bool {
    version_json_path(id).exists()
}

/// Lista ordenada de versiones para la interfaz.
pub async fn list_versions(include_snapshots: bool) -> Result<Vec<Value>, String> {
    let m = manifest().await?;
    let latest = m["latest"]["release"].as_str().unwrap_or_default().to_string();
    let out = m["versions"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .into_iter()
        .filter(|v| include_snapshots || v["type"] == "release")
        .map(|v| {
            let id = v["id"].as_str().unwrap_or_default();
            json!({
                "id": id,
                "type": v["type"],
                "releaseTime": v["releaseTime"],
                "installed": is_installed(id),
                "latest": id == latest,
            })
        })
        .collect();
    Ok(out)
}

/// Asegura que un archivo de launcher_profiles.json exista (lo exige el instalador de Forge).
pub fn ensure_launcher_profiles(dir: &Path) {
    let p = dir.join("launcher_profiles.json");
    if !p.exists() {
        let _ = config::write_json(&p, &json!({ "profiles": {}, "selectedProfile": "", "authenticationDatabase": {} }));
    }
}
