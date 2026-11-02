//! Java: descarga el runtime oficial de Mojang que pide cada versión (Java 8, 17, 21…),
//! así no hace falta tener Java instalado.

use serde_json::Value;
use std::path::PathBuf;

use crate::config::{self, runtimes_dir};
use crate::net::{self, Download};

const RUNTIMES_URL: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

fn platform_key() -> &'static str {
    match (config::os_name(), config::os_arch()) {
        ("windows", "x86") => "windows-x86",
        ("windows", "arm64") => "windows-arm64",
        ("windows", _) => "windows-x64",
        ("osx", "arm64") => "mac-os-arm64",
        ("osx", _) => "mac-os",
        (_, "x86") => "linux-i386",
        _ => "linux",
    }
}

/// Componente de Java que pide la versión (por defecto Java 8 "jre-legacy").
pub fn component_for(version: &Value) -> String {
    version["javaVersion"]["component"].as_str().unwrap_or("jre-legacy").to_string()
}

fn runtime_dir(component: &str) -> PathBuf {
    runtimes_dir().join(component)
}

/// Ejecutable de Java de un componente descargado.
pub fn java_executable(component: &str, windowed: bool) -> PathBuf {
    let dir = runtime_dir(component);
    if cfg!(target_os = "windows") {
        dir.join("bin").join(if windowed { "javaw.exe" } else { "java.exe" })
    } else if cfg!(target_os = "macos") {
        dir.join("jre.bundle/Contents/Home/bin/java")
    } else {
        dir.join("bin/java")
    }
}

/// Ruta de Java a usar para una versión: la elegida en ajustes o la de Mojang.
pub fn java_for(version: &Value, windowed: bool) -> PathBuf {
    let custom = config::settings().java_path;
    if !custom.trim().is_empty() {
        return PathBuf::from(custom.trim());
    }
    java_executable(&component_for(version), windowed)
}

/// Descarga el runtime de un componente si falta (o está incompleto).
pub async fn ensure_component(component: &str) -> Result<PathBuf, String> {
    let exe = java_executable(component, false);
    let marker = runtime_dir(component).join(".amatista-ok");
    if exe.exists() && marker.exists() {
        return Ok(exe);
    }
    let task = format!("Descargando Java ({component})");
    crate::progress::status(&task, "Buscando el runtime de Java…");

    let all = net::get_json(RUNTIMES_URL).await?;
    let key = platform_key();
    let entry = all[key][component]
        .as_array()
        .and_then(|a| a.first())
        .cloned()
        .or_else(|| {
            // Algunas plataformas (p. ej. arm64) no tienen todos los componentes:
            // usar el de x64 si existe (Windows/macOS ARM lo emulan).
            let fallback = match key {
                "windows-arm64" => "windows-x64",
                "mac-os-arm64" => "mac-os",
                _ => return None,
            };
            all[fallback][component].as_array().and_then(|a| a.first()).cloned()
        })
        .ok_or_else(|| format!("Mojang no ofrece {component} para este sistema ({key}). Elige una ruta de Java en Ajustes."))?;

    let manifest_url = entry["manifest"]["url"].as_str().ok_or("Manifiesto de Java inválido")?;
    let files = net::get_json(manifest_url).await?;
    let dir = runtime_dir(component);
    let mut downloads = Vec::new();
    let mut executables = Vec::new();
    let mut links = Vec::new();
    if let Some(map) = files["files"].as_object() {
        for (rel, info) in map {
            let path = dir.join(rel);
            match info["type"].as_str() {
                Some("directory") => {
                    let _ = std::fs::create_dir_all(&path);
                }
                Some("file") => {
                    let raw = &info["downloads"]["raw"];
                    downloads.push(Download {
                        url: raw["url"].as_str().unwrap_or_default().to_string(),
                        path: path.clone(),
                        sha1: raw["sha1"].as_str().map(str::to_string),
                        size: raw["size"].as_u64(),
                    });
                    if info["executable"].as_bool().unwrap_or(false) {
                        executables.push(path);
                    }
                }
                Some("link") => links.push((path, info["target"].as_str().unwrap_or_default().to_string())),
                _ => {}
            }
        }
    }
    net::download_all(&task, downloads, config::settings().concurrent_downloads).await?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for exe in &executables {
            let _ = std::fs::set_permissions(exe, std::fs::Permissions::from_mode(0o755));
        }
        for (path, target) in &links {
            let _ = std::fs::remove_file(path);
            let _ = std::os::unix::fs::symlink(target, path);
        }
    }
    #[cfg(not(unix))]
    {
        let _ = (&executables, &links);
    }

    let _ = std::fs::write(&marker, b"ok");
    Ok(exe)
}

/// Asegura el Java que necesita una versión (salvo que el usuario haya elegido otro).
pub async fn ensure_for_version(version: &Value) -> Result<PathBuf, String> {
    let custom = config::settings().java_path;
    if !custom.trim().is_empty() {
        let p = PathBuf::from(custom.trim());
        if p.exists() {
            return Ok(p);
        }
        return Err(format!("No existe el Java configurado: {}", p.display()));
    }
    ensure_component(&component_for(version)).await
}
