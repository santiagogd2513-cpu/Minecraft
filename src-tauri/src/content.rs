//! Contenido: búsqueda e instalación de mods, paquetes de recursos, modpacks y shaders
//! desde Modrinth (sin clave) y CurseForge (requiere clave de API gratuita).
//! El filtro por cargador (Forge) solo se aplica a mods y modpacks.

use serde_json::{json, Value};
use std::collections::HashSet;
use std::io::Read;
use std::path::{Path, PathBuf};

use crate::config;
use crate::instances::{self, Instance};
use crate::net::{self, Download, CLIENT};
use crate::progress;

const MODRINTH: &str = "https://api.modrinth.com/v2";
const CURSEFORGE: &str = "https://api.curseforge.com/v1";
const CF_GAME_MINECRAFT: u32 = 432;
const CF_LOADER_FORGE: u32 = 1;

fn cf_class(kind: &str) -> u32 {
    match kind {
        "resourcepack" => 12,
        "modpack" => 4471,
        "shader" => 6552,
        _ => 6,
    }
}

fn folder_for(kind: &str) -> &'static str {
    match kind {
        "resourcepack" => "resourcepacks",
        "shader" => "shaderpacks",
        _ => "mods",
    }
}

fn uses_loader_filter(kind: &str) -> bool {
    kind == "mod" || kind == "modpack"
}

fn cf_key() -> Result<String, String> {
    let k = config::settings().curseforge_api_key.trim().to_string();
    if k.is_empty() {
        Err("CurseForge necesita una clave de API gratuita. Pégala en Ajustes → CurseForge (console.curseforge.com).".into())
    } else {
        Ok(k)
    }
}

async fn cf_get(path: &str, query: &[(&str, String)]) -> Result<Value, String> {
    let resp = CLIENT
        .get(format!("{CURSEFORGE}{path}"))
        .header("x-api-key", cf_key()?)
        .query(query)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if resp.status().as_u16() == 403 {
        return Err("CurseForge rechazó la clave de API. Revísala en Ajustes.".into());
    }
    resp.json().await.map_err(|e| e.to_string())
}

// ---------------------------------------------------------------------------
// Búsqueda
// ---------------------------------------------------------------------------

/// `loader`: "forge" o "" (todos). Solo se usa con mods y modpacks.
pub async fn search(source: &str, kind: &str, query: &str, mc: &str, loader: &str, offset: u32) -> Result<Value, String> {
    let loader = if uses_loader_filter(kind) { loader } else { "" };
    if source == "curseforge" {
        let mut q: Vec<(&str, String)> = vec![
            ("gameId", CF_GAME_MINECRAFT.to_string()),
            ("classId", cf_class(kind).to_string()),
            ("searchFilter", query.to_string()),
            ("sortField", "2".into()),
            ("sortOrder", "desc".into()),
            ("pageSize", "24".into()),
            ("index", offset.to_string()),
        ];
        if !mc.is_empty() {
            q.push(("gameVersion", mc.to_string()));
        }
        if loader == "forge" {
            q.push(("modLoaderType", CF_LOADER_FORGE.to_string()));
        }
        let r = cf_get("/mods/search", &q).await?;
        let hits: Vec<Value> = r["data"]
            .as_array()
            .cloned()
            .unwrap_or_default()
            .iter()
            .map(|m| {
                json!({
                    "id": m["id"].to_string(),
                    "title": m["name"],
                    "description": m["summary"],
                    "author": m["authors"][0]["name"],
                    "icon": m["logo"]["thumbnailUrl"],
                    "downloads": m["downloadCount"],
                    "url": m["links"]["websiteUrl"],
                    "source": "curseforge",
                    "kind": kind,
                })
            })
            .collect();
        return Ok(json!({ "hits": hits, "total": r["pagination"]["totalCount"] }));
    }

    let mut facets = vec![vec![format!("project_type:{kind}")]];
    if !mc.is_empty() {
        facets.push(vec![format!("versions:{mc}")]);
    }
    if loader == "forge" {
        facets.push(vec!["categories:forge".to_string()]);
    }
    let r: Value = CLIENT
        .get(format!("{MODRINTH}/search"))
        .query(&[
            ("query", query.to_string()),
            ("facets", serde_json::to_string(&facets).unwrap_or_default()),
            ("limit", "24".into()),
            ("offset", offset.to_string()),
            ("index", if query.is_empty() { "downloads".into() } else { "relevance".to_string() }),
        ])
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let hits: Vec<Value> = r["hits"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .iter()
        .map(|h| {
            json!({
                "id": h["project_id"],
                "title": h["title"],
                "description": h["description"],
                "author": h["author"],
                "icon": h["icon_url"],
                "downloads": h["downloads"],
                "url": format!("https://modrinth.com/{}/{}", h["project_type"].as_str().unwrap_or("mod"), h["slug"].as_str().unwrap_or_default()),
                "source": "modrinth",
                "kind": kind,
            })
        })
        .collect();
    Ok(json!({ "hits": hits, "total": r["total_hits"] }))
}

/// Versiones de Minecraft que se pueden elegir en los filtros.
pub async fn game_versions() -> Result<Vec<String>, String> {
    let list = crate::mojang::list_versions(false).await?;
    Ok(list.iter().filter_map(|v| v["id"].as_str().map(str::to_string)).collect())
}

// ---------------------------------------------------------------------------
// Instalación en una instancia
// ---------------------------------------------------------------------------

struct FileChoice {
    url: String,
    file_name: String,
    sha1: Option<String>,
    deps: Vec<String>,
}

async fn modrinth_pick(project: &str, mc: &str, loader: Option<&str>) -> Result<Option<FileChoice>, String> {
    let mut q: Vec<(&str, String)> = vec![("game_versions", serde_json::to_string(&[mc]).unwrap_or_default())];
    if let Some(l) = loader {
        q.push(("loaders", serde_json::to_string(&[l]).unwrap_or_default()));
    }
    let versions: Value = CLIENT
        .get(format!("{MODRINTH}/project/{project}/version"))
        .query(&q)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let Some(v) = versions.as_array().and_then(|a| a.first()) else {
        return Ok(None);
    };
    let files = v["files"].as_array().cloned().unwrap_or_default();
    let file = files.iter().find(|f| f["primary"] == true).or(files.first()).ok_or("Versión sin archivos")?;
    let deps = v["dependencies"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .into_iter()
        .filter(|d| d["dependency_type"] == "required")
        .filter_map(|d| d["project_id"].as_str().map(str::to_string))
        .collect();
    Ok(Some(FileChoice {
        url: file["url"].as_str().unwrap_or_default().to_string(),
        file_name: file["filename"].as_str().unwrap_or("archivo.jar").to_string(),
        sha1: file["hashes"]["sha1"].as_str().map(str::to_string),
        deps,
    }))
}

fn cf_file_url(file: &Value) -> String {
    match file["downloadUrl"].as_str() {
        Some(u) if !u.is_empty() => u.to_string(),
        _ => {
            // Algunos autores desactivan la descarga por API; el CDN público sigue
            // sirviendo el archivo en esta ruta.
            let id = file["id"].as_u64().unwrap_or(0);
            let name = file["fileName"].as_str().unwrap_or_default();
            format!("https://mediafilez.forgecdn.net/files/{}/{}/{}", id / 1000, id % 1000, name)
        }
    }
}

fn cf_sha1(file: &Value) -> Option<String> {
    file["hashes"]
        .as_array()?
        .iter()
        .find(|h| h["algo"] == 1)
        .and_then(|h| h["value"].as_str())
        .map(str::to_string)
}

async fn curseforge_pick(project: &str, mc: &str, forge: bool) -> Result<Option<FileChoice>, String> {
    let mut q: Vec<(&str, String)> = vec![("gameVersion", mc.to_string()), ("pageSize", "50".into())];
    if forge {
        q.push(("modLoaderType", CF_LOADER_FORGE.to_string()));
    }
    let r = cf_get(&format!("/mods/{project}/files"), &q).await?;
    let mut files = r["data"].as_array().cloned().unwrap_or_default();
    files.sort_by(|a, b| b["fileDate"].as_str().cmp(&a["fileDate"].as_str()));
    let Some(f) = files.first() else { return Ok(None) };
    let deps = f["dependencies"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .into_iter()
        .filter(|d| d["relationType"] == 3)
        .filter_map(|d| d["modId"].as_u64().map(|i| i.to_string()))
        .collect();
    Ok(Some(FileChoice { url: cf_file_url(f), file_name: f["fileName"].as_str().unwrap_or("archivo.jar").to_string(), sha1: cf_sha1(f), deps }))
}

/// Instala un mod / paquete de recursos / shader en una instancia (con dependencias).
pub async fn install_into(source: &str, kind: &str, project: &str, instance_id: &str) -> Result<String, String> {
    let inst = instances::get(instance_id).ok_or("Elige una instancia")?;
    if kind == "mod" && inst.loader != "forge" {
        return Err("Los mods necesitan una instancia de Forge. Instala Forge en la sección Instalar.".into());
    }
    let task = format!("Instalando en {}", inst.name);
    let dir = inst.dir().join(folder_for(kind));
    let mut visited = HashSet::new();
    let mut queue = vec![project.to_string()];
    let mut installed = Vec::new();
    while let Some(p) = queue.pop() {
        if !visited.insert(p.clone()) {
            continue;
        }
        progress::status(&task, "Buscando la versión compatible…");
        let choice = if source == "curseforge" {
            curseforge_pick(&p, &inst.mc_version, kind == "mod").await?
        } else {
            let loader = (kind == "mod").then_some("forge");
            modrinth_pick(&p, &inst.mc_version, loader).await?
        };
        let Some(choice) = choice else {
            if p == project {
                return Err(format!("No hay una versión para Minecraft {} {}.", inst.mc_version, if kind == "mod" { "con Forge" } else { "" }));
            }
            continue; // dependencia opcionalmente incompatible: se omite
        };
        let d = Download { url: choice.url, path: dir.join(&choice.file_name), sha1: choice.sha1, size: None };
        if !net::is_valid(&d) {
            progress::status(&task, &format!("Descargando {}…", choice.file_name));
            net::download_one(&d, None).await?;
        }
        installed.push(choice.file_name);
        if kind == "mod" {
            queue.extend(choice.deps);
        }
    }
    progress::emit(&task, 1, 1, "Instalado");
    Ok(installed.join(", "))
}

// ---------------------------------------------------------------------------
// Modpacks
// ---------------------------------------------------------------------------

fn read_zip_json(zip_path: &Path, name: &str) -> Option<Value> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(zip_path).ok()?).ok()?;
    let mut e = zip.by_name(name).ok()?;
    let mut buf = Vec::new();
    e.read_to_end(&mut buf).ok()?;
    serde_json::from_slice(&buf).ok()
}

/// Copia las carpetas de "overrides" del modpack a la instancia.
fn extract_overrides(zip_path: &Path, prefixes: &[&str], dest: &Path) -> Result<(), String> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(zip_path).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    for i in 0..zip.len() {
        let mut e = zip.by_index(i).map_err(|e| e.to_string())?;
        let name = e.name().to_string();
        for prefix in prefixes {
            let Some(rel) = name.strip_prefix(&format!("{prefix}/")) else { continue };
            if rel.is_empty() || name.ends_with('/') || rel.contains("..") {
                continue;
            }
            let out = dest.join(rel);
            if let Some(p) = out.parent() {
                std::fs::create_dir_all(p).map_err(|e| e.to_string())?;
            }
            let mut f = std::fs::File::create(&out).map_err(|e| e.to_string())?;
            std::io::copy(&mut e, &mut f).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

async fn ensure_loader(mc: &str, forge: Option<&str>) -> Result<(String, String, String), String> {
    match forge {
        None => {
            crate::mojang::install_vanilla(mc).await?;
            Ok(("vanilla".into(), String::new(), mc.to_string()))
        }
        Some(fv) => {
            // Buscar el nombre completo de la versión de Forge en su repositorio.
            let list = crate::forge::list_versions(mc).await?;
            let full = list
                .iter()
                .find(|v| v["forge"] == fv)
                .and_then(|v| v["full"].as_str())
                .map(str::to_string)
                .unwrap_or_else(|| format!("{mc}-{fv}"));
            let id = crate::forge::install(&full).await?;
            Ok(("forge".into(), fv.to_string(), id))
        }
    }
}

/// Descarga e instala un modpack completo como instancia nueva.
pub async fn install_modpack(source: &str, project: &str, title: &str, mc_filter: &str) -> Result<Instance, String> {
    let task = format!("Modpack {title}");
    progress::status(&task, "Buscando la versión del modpack…");
    let pack_path: PathBuf = config::cache_dir().join("modpacks").join(format!("{source}-{project}.zip"));

    if source == "curseforge" {
        let mut q: Vec<(&str, String)> = vec![("pageSize", "50".into())];
        if !mc_filter.is_empty() {
            q.push(("gameVersion", mc_filter.to_string()));
        }
        let r = cf_get(&format!("/mods/{project}/files"), &q).await?;
        let mut files = r["data"].as_array().cloned().unwrap_or_default();
        files.sort_by(|a, b| b["fileDate"].as_str().cmp(&a["fileDate"].as_str()));
        let f = files.first().ok_or("El modpack no tiene archivos para esa versión")?;
        net::download_one(&Download { url: cf_file_url(f), path: pack_path.clone(), sha1: cf_sha1(f), size: None }, None).await?;
        let manifest = read_zip_json(&pack_path, "manifest.json").ok_or("El modpack no tiene manifest.json")?;
        let mc = manifest["minecraft"]["version"].as_str().ok_or("Modpack sin versión de Minecraft")?.to_string();
        let loader_id = manifest["minecraft"]["modLoaders"][0]["id"].as_str().unwrap_or_default().to_string();
        let forge = if loader_id.is_empty() {
            None
        } else if let Some(v) = loader_id.strip_prefix("forge-") {
            Some(v.to_string())
        } else {
            return Err(format!("Este modpack usa {loader_id}; el launcher solo admite Forge."));
        };
        let (loader, loader_version, version_id) = ensure_loader(&mc, forge.as_deref()).await?;
        let inst = instances::create(title, &mc, &loader, &loader_version, &version_id)?;

        // Archivos del modpack (resueltos en bloque).
        let ids: Vec<u64> = manifest["files"].as_array().cloned().unwrap_or_default().iter().filter_map(|f| f["fileID"].as_u64()).collect();
        let resp: Value = CLIENT
            .post(format!("{CURSEFORGE}/mods/files"))
            .header("x-api-key", cf_key()?)
            .json(&json!({ "fileIds": ids }))
            .send()
            .await
            .map_err(|e| e.to_string())?
            .json()
            .await
            .map_err(|e| e.to_string())?;
        let downloads: Vec<Download> = resp["data"]
            .as_array()
            .cloned()
            .unwrap_or_default()
            .iter()
            .map(|f| {
                let name = f["fileName"].as_str().unwrap_or("archivo.jar");
                let folder = if name.ends_with(".zip") && f["modId"].is_number() { "resourcepacks" } else { "mods" };
                Download { url: cf_file_url(f), path: inst.dir().join(folder).join(name), sha1: cf_sha1(f), size: None }
            })
            .collect();
        net::download_all(&task, downloads, config::settings().concurrent_downloads).await?;
        let overrides = manifest["overrides"].as_str().unwrap_or("overrides").to_string();
        let (p, d) = (pack_path.clone(), inst.dir());
        tokio::task::spawn_blocking(move || extract_overrides(&p, &[&overrides], &d)).await.map_err(|e| e.to_string())??;
        progress::emit(&task, 1, 1, "Modpack instalado");
        return Ok(inst);
    }

    // Modrinth (.mrpack)
    let mut q: Vec<(&str, String)> = Vec::new();
    if !mc_filter.is_empty() {
        q.push(("game_versions", serde_json::to_string(&[mc_filter]).unwrap_or_default()));
    }
    let versions: Value = CLIENT
        .get(format!("{MODRINTH}/project/{project}/version"))
        .query(&q)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let v = versions
        .as_array()
        .and_then(|a| a.iter().find(|v| v["loaders"].as_array().is_some_and(|l| l.iter().any(|x| x == "forge" || x == "minecraft"))).or(a.first()))
        .ok_or("El modpack no tiene versiones compatibles")?
        .clone();
    let file = v["files"].as_array().and_then(|f| f.iter().find(|x| x["primary"] == true).or(f.first())).ok_or("Versión sin archivo")?;
    net::download_one(
        &Download {
            url: file["url"].as_str().unwrap_or_default().to_string(),
            path: pack_path.clone(),
            sha1: file["hashes"]["sha1"].as_str().map(str::to_string),
            size: None,
        },
        None,
    )
    .await?;
    let index = read_zip_json(&pack_path, "modrinth.index.json").ok_or("El modpack no tiene modrinth.index.json")?;
    let deps = &index["dependencies"];
    let mc = deps["minecraft"].as_str().ok_or("Modpack sin versión de Minecraft")?.to_string();
    for other in ["fabric-loader", "quilt-loader", "neoforge"] {
        if deps.get(other).is_some() {
            return Err(format!("Este modpack usa {other}; el launcher solo admite Forge."));
        }
    }
    let (loader, loader_version, version_id) = ensure_loader(&mc, deps["forge"].as_str()).await?;
    let inst = instances::create(title, &mc, &loader, &loader_version, &version_id)?;

    let downloads: Vec<Download> = index["files"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .iter()
        .filter(|f| f["env"]["client"] != "unsupported")
        .filter_map(|f| {
            let rel = f["path"].as_str()?;
            if rel.contains("..") {
                return None;
            }
            Some(Download {
                url: f["downloads"][0].as_str()?.to_string(),
                path: inst.dir().join(rel),
                sha1: f["hashes"]["sha1"].as_str().map(str::to_string),
                size: f["fileSize"].as_u64(),
            })
        })
        .collect();
    net::download_all(&task, downloads, config::settings().concurrent_downloads).await?;
    let (p, d) = (pack_path.clone(), inst.dir());
    tokio::task::spawn_blocking(move || extract_overrides(&p, &["overrides", "client-overrides"], &d)).await.map_err(|e| e.to_string())??;
    progress::emit(&task, 1, 1, "Modpack instalado");
    Ok(inst)
}

/// Archivos instalados en una carpeta de la instancia (mods, resourcepacks, shaderpacks).
pub fn list_installed(instance_id: &str, kind: &str) -> Vec<Value> {
    let Some(inst) = instances::get(instance_id) else { return vec![] };
    let dir = inst.dir().join(folder_for(kind));
    let mut out: Vec<Value> = std::fs::read_dir(&dir)
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.path().is_file())
        .map(|e| {
            let name = e.file_name().to_string_lossy().to_string();
            json!({ "name": name, "enabled": !name.ends_with(".disabled"), "size": e.metadata().map(|m| m.len()).unwrap_or(0) })
        })
        .collect();
    out.sort_by(|a, b| a["name"].as_str().cmp(&b["name"].as_str()));
    out
}

/// Activa/desactiva un mod renombrándolo a .disabled (como hacen los launchers populares).
pub fn toggle_installed(instance_id: &str, kind: &str, name: &str) -> Result<(), String> {
    let inst = instances::get(instance_id).ok_or("Instancia no encontrada")?;
    let dir = inst.dir().join(folder_for(kind));
    let from = dir.join(name);
    let to = match name.strip_suffix(".disabled") {
        Some(base) => dir.join(base),
        None => dir.join(format!("{name}.disabled")),
    };
    std::fs::rename(from, to).map_err(|e| e.to_string())
}

pub fn delete_installed(instance_id: &str, kind: &str, name: &str) -> Result<(), String> {
    let inst = instances::get(instance_id).ok_or("Instancia no encontrada")?;
    if name.contains("..") || name.contains('/') || name.contains('\\') {
        return Err("Nombre inválido".into());
    }
    std::fs::remove_file(inst.dir().join(folder_for(kind)).join(name)).map_err(|e| e.to_string())
}
