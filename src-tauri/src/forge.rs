//! Forge. Para tener compatibilidad total se usa el instalador oficial de Forge en modo
//! sin ventana (`--installClient`): así se descargan TODAS sus librerías y se ejecutan
//! los "processors" que parchean el juego exactamente como lo hace Forge. Los Forge
//! antiguos (formato `versionInfo`, hasta 1.12.2) se instalan copiando sus archivos.

use serde_json::{json, Value};
use std::io::Read;
use std::path::Path;
use std::process::Stdio;
use tokio::io::{AsyncBufReadExt, BufReader};

use crate::config::{self, libraries_dir};
use crate::mojang;
use crate::net::{self, Download};
use crate::progress;

const MAVEN: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
const PROMOTIONS: &str = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

/// Versiones de Forge para una versión de Minecraft (la más nueva primero).
pub async fn list_versions(mc: &str) -> Result<Vec<Value>, String> {
    let xml = net::get_text(&format!("{MAVEN}maven-metadata.xml")).await?;
    let promos = net::get_json(PROMOTIONS).await.unwrap_or(json!({}));
    let recommended = promos["promos"][format!("{mc}-recommended")].as_str().map(str::to_string);
    let latest = promos["promos"][format!("{mc}-latest")].as_str().map(str::to_string);
    let prefix = format!("{mc}-");
    let mut out = Vec::new();
    for chunk in xml.split("<version>").skip(1) {
        let Some(v) = chunk.split("</version>").next() else { continue };
        if !v.starts_with(&prefix) {
            continue;
        }
        // "1.20.1-47.2.0" → "47.2.0"; antiguos: "1.7.10-10.13.4.1614-1.7.10" → "10.13.4.1614"
        let forge = v[prefix.len()..].split('-').next().unwrap_or_default().to_string();
        out.push(json!({
            "full": v,
            "forge": forge,
            "recommended": recommended.as_deref() == Some(forge.as_str()),
            "latest": latest.as_deref() == Some(forge.as_str()),
        }));
    }
    out.reverse();
    Ok(out)
}

fn read_zip_entry(jar: &Path, name: &str) -> Option<Vec<u8>> {
    let file = std::fs::File::open(jar).ok()?;
    let mut zip = zip::ZipArchive::new(file).ok()?;
    let mut entry = zip.by_name(name).ok()?;
    let mut buf = Vec::new();
    entry.read_to_end(&mut buf).ok()?;
    Some(buf)
}

fn extract_entry(jar: &Path, name: &str, dest: &Path) -> Result<(), String> {
    let data = read_zip_entry(jar, name).ok_or_else(|| format!("No se encontró {name} en el instalador"))?;
    if let Some(p) = dest.parent() {
        std::fs::create_dir_all(p).map_err(|e| e.to_string())?;
    }
    std::fs::write(dest, data).map_err(|e| e.to_string())
}

/// Extrae la carpeta `maven/` del instalador (librerías de Forge incluidas en él).
fn extract_maven_dir(jar: &Path) -> Result<(), String> {
    let file = std::fs::File::open(jar).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        let name = entry.name().to_string();
        let Some(rel) = name.strip_prefix("maven/") else { continue };
        if rel.is_empty() || name.ends_with('/') {
            continue;
        }
        let dest = libraries_dir().join(rel);
        if let Some(p) = dest.parent() {
            std::fs::create_dir_all(p).map_err(|e| e.to_string())?;
        }
        let mut f = std::fs::File::create(&dest).map_err(|e| e.to_string())?;
        std::io::copy(&mut entry, &mut f).map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// Instala Forge (`full` = "1.20.1-47.2.0"). Devuelve el id de la versión instalada.
pub async fn install(full: &str) -> Result<String, String> {
    let mc = full.split('-').next().unwrap_or(full).to_string();
    let task = format!("Instalando Forge {full}");

    // 1. Minecraft vanilla de base.
    mojang::install_vanilla(&mc).await?;
    let vanilla = mojang::load_merged(&mc)?;
    let java = crate::java::ensure_for_version(&vanilla).await?;

    // 2. Instalador oficial.
    progress::status(&task, "Descargando el instalador de Forge…");
    let installer = config::cache_dir().join("forge").join(format!("forge-{full}-installer.jar"));
    let d = Download {
        url: format!("{MAVEN}{full}/forge-{full}-installer.jar"),
        path: installer.clone(),
        sha1: net::get_text(&format!("{MAVEN}{full}/forge-{full}-installer.jar.sha1")).await.ok().map(|s| s.trim().to_string()),
        size: None,
    };
    if !net::is_valid(&d) {
        net::download_one(&d, None).await.map_err(|e| format!("No se pudo descargar el instalador de Forge: {e}"))?;
    }

    let profile: Value = read_zip_entry(&installer, "install_profile.json")
        .and_then(|b| serde_json::from_slice(&b).ok())
        .ok_or("El instalador de Forge no tiene install_profile.json")?;

    let id = if profile.get("versionInfo").is_some() {
        install_legacy(&installer, &profile, &mc)?
    } else {
        install_modern(&installer, &profile, &mc, &java, &task).await?
    };

    // 3. Librerías restantes (comprobación de todo con SHA-1).
    mojang::install_files(&id).await?;
    crate::launch::invalidate(&id);
    progress::emit(&task, 1, 1, "Forge instalado");
    Ok(id)
}

/// Forge hasta 1.12.2 (formato antiguo): el JSON de la versión viene en `versionInfo`
/// y el jar "universal" dentro del instalador.
fn install_legacy(installer: &Path, profile: &Value, mc: &str) -> Result<String, String> {
    let mut info = profile["versionInfo"].clone();
    let id = info["id"].as_str().ok_or("versionInfo sin id")?.to_string();
    if info.get("inheritsFrom").is_none() {
        info["inheritsFrom"] = json!(mc);
    }
    if info.get("jar").is_none() {
        info["jar"] = json!(mc);
    }
    // Las librerías marcadas solo para servidor no hacen falta en el cliente.
    if let Some(libs) = info["libraries"].as_array_mut() {
        // "clientreq": true → la necesita el cliente; solo "serverreq": true → solo servidor.
        libs.retain(|l| match l["clientreq"].as_bool() {
            Some(c) => c,
            None => l["serverreq"].as_bool() != Some(true),
        });
    }
    config::write_json(&mojang::version_json_path(&id), &info)?;

    let file_path = profile["install"]["filePath"].as_str().ok_or("install.filePath ausente")?;
    let lib_name = profile["install"]["path"].as_str().ok_or("install.path ausente")?;
    let rel = mojang::maven_path(lib_name).ok_or("Ruta de librería inválida")?;
    extract_entry(installer, file_path, &libraries_dir().join(rel))?;
    Ok(id)
}

/// Forge 1.12.2 reciente y 1.13+: instalador oficial sin ventana.
async fn install_modern(installer: &Path, profile: &Value, mc: &str, java: &Path, task: &str) -> Result<String, String> {
    let version_json: Value = profile["json"]
        .as_str()
        .map(|p| p.trim_start_matches('/'))
        .and_then(|p| read_zip_entry(installer, p))
        .or_else(|| read_zip_entry(installer, "version.json"))
        .and_then(|b| serde_json::from_slice(&b).ok())
        .ok_or("El instalador de Forge no tiene version.json")?;
    let id = version_json["id"].as_str().ok_or("version.json sin id")?.to_string();

    let game_dir = config::game_dir();
    mojang::ensure_launcher_profiles(&game_dir);

    progress::status(task, "Ejecutando el instalador oficial de Forge…");
    let ok = run_installer(java, installer, &game_dir, task).await;
    let installed = mojang::version_json_path(&id).exists();
    if ok && installed {
        return Ok(id);
    }

    // Respaldo: instaladores sin --installClient pero sin processors se pueden copiar.
    let has_processors = profile["processors"].as_array().is_some_and(|p| !p.is_empty());
    if has_processors {
        return Err("El instalador de Forge falló. Revisa tu conexión e inténtalo de nuevo.".into());
    }
    progress::status(task, "Instalando Forge manualmente…");
    let mut v = version_json.clone();
    if v.get("inheritsFrom").is_none() {
        v["inheritsFrom"] = json!(mc);
    }
    config::write_json(&mojang::version_json_path(&id), &v)?;
    extract_maven_dir(installer)?;
    Ok(id)
}

async fn run_installer(java: &Path, installer: &Path, game_dir: &Path, task: &str) -> bool {
    let mut cmd = tokio::process::Command::new(java);
    cmd.arg("-jar")
        .arg(installer)
        .arg("--installClient")
        .arg(game_dir)
        .current_dir(installer.parent().unwrap_or(Path::new(".")))
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null());
    #[cfg(target_os = "windows")]
    {
        cmd.creation_flags(0x0800_0000);
    }
    let Ok(mut child) = cmd.spawn() else { return false };
    if let Some(out) = child.stdout.take() {
        let task = task.to_string();
        tokio::spawn(async move {
            let mut lines = BufReader::new(out).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let short: String = line.chars().take(90).collect();
                progress::status(&task, &format!("Forge: {short}"));
            }
        });
    }
    if let Some(err) = child.stderr.take() {
        tokio::spawn(async move {
            let mut lines = BufReader::new(err).lines();
            while let Ok(Some(_)) = lines.next_line().await {}
        });
    }
    child.wait().await.map(|s| s.success()).unwrap_or(false)
}
