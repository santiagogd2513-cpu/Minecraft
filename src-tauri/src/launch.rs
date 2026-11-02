//! Lanzamiento del juego: verificación rápida de archivos, nativos, argumentos de la JVM
//! y del juego (formatos nuevo y antiguo) y el proceso de Java con su registro.

use serde_json::{Map, Value};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::Mutex;
use tokio::io::{AsyncBufReadExt, BufReader};

use crate::accounts::{self, Account};
use crate::config::{self, assets_dir, libraries_dir, versions_dir};
use crate::instances::{self, Instance};
use crate::java;
use crate::mojang;
use crate::progress;

static RUNNING: once_cell::sync::Lazy<Mutex<HashMap<String, u32>>> =
    once_cell::sync::Lazy::new(|| Mutex::new(HashMap::new()));

pub fn running_instances() -> Vec<String> {
    RUNNING.lock().map(|m| m.keys().cloned().collect()).unwrap_or_default()
}

/// Argumentos de recolección de basura que mejor rinden con Minecraft (estilo Aikar).
const OPTIMIZED_GC: &[&str] = &[
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseG1GC",
    "-XX:G1NewSizePercent=20",
    "-XX:G1ReservePercent=20",
    "-XX:MaxGCPauseMillis=50",
    "-XX:G1HeapRegionSize=32M",
    "-XX:+ParallelRefProcEnabled",
    "-XX:+DisableExplicitGC",
    "-XX:+PerfDisableSharedMem",
];

/// Marca de que la versión ya se verificó completa: los siguientes inicios son inmediatos.
fn verified_marker(id: &str) -> PathBuf {
    versions_dir().join(id).join(".amatista-verified")
}

pub fn invalidate(id: &str) {
    let _ = std::fs::remove_file(verified_marker(id));
}

/// Descomprime los nativos antiguos (LWJGL 2/3 hasta 1.18) en una carpeta propia.
fn extract_natives(version: &Value, dir: &Path) -> Result<(), String> {
    std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    for lib in mojang::resolve_libraries(version).into_iter().filter(|l| l.is_native) {
        let Ok(file) = std::fs::File::open(&lib.path) else { continue };
        let Ok(mut zip) = zip::ZipArchive::new(file) else { continue };
        for i in 0..zip.len() {
            let Ok(mut entry) = zip.by_index(i) else { continue };
            let name = entry.name().to_string();
            if name.starts_with("META-INF") || name.ends_with('/') {
                continue;
            }
            let Some(rel) = entry.enclosed_name() else { continue };
            let out = dir.join(rel);
            if out.exists() {
                continue;
            }
            if let Some(p) = out.parent() {
                let _ = std::fs::create_dir_all(p);
            }
            if let Ok(mut f) = std::fs::File::create(&out) {
                let _ = std::io::copy(&mut entry, &mut f);
            }
        }
    }
    Ok(())
}

fn classpath(version: &Value) -> String {
    let mut seen = std::collections::HashSet::new();
    let mut parts: Vec<String> = Vec::new();
    for lib in mojang::resolve_libraries(version).into_iter().filter(|l| !l.is_native) {
        let p = lib.path.display().to_string();
        if seen.insert(p.clone()) {
            parts.push(p);
        }
    }
    parts.push(mojang::client_jar(version).display().to_string());
    parts.join(config::classpath_separator())
}

fn substitute(arg: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = arg.to_string();
    if out.contains("${") {
        for (k, v) in vars {
            let key = format!("${{{k}}}");
            if out.contains(&key) {
                out = out.replace(&key, v);
            }
        }
    }
    out
}

/// Expande una lista de argumentos del formato nuevo (cadenas o {rules, value}).
fn expand(list: &[Value], features: &Map<String, Value>, vars: &HashMap<&str, String>) -> Vec<String> {
    let mut out = Vec::new();
    for item in list {
        match item {
            Value::String(s) => out.push(substitute(s, vars)),
            Value::Object(o) => {
                if !mojang::rules_allow(o.get("rules"), features) {
                    continue;
                }
                match o.get("value") {
                    Some(Value::String(s)) => out.push(substitute(s, vars)),
                    Some(Value::Array(a)) => {
                        out.extend(a.iter().filter_map(Value::as_str).map(|s| substitute(s, vars)))
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
    out
}

/// Comando listo para ejecutar: Java, argumentos y carpeta de trabajo.
pub struct GameCommand {
    pub java: PathBuf,
    pub args: Vec<String>,
    pub cwd: PathBuf,
}

/// Prepara todo lo necesario (archivos, Java, nativos) y arma el comando del juego.
pub async fn prepare(inst: &Instance, account: &Account, task: &str) -> Result<GameCommand, String> {

    // Verificación completa solo la primera vez (o tras reparar).
    let version = if verified_marker(&inst.version_id).exists() {
        mojang::load_merged(&inst.version_id)?
    } else {
        if inst.loader == "vanilla" {
            mojang::ensure_version_json(&inst.mc_version).await?;
        }
        let v = mojang::install_files(&inst.version_id).await?;
        let _ = std::fs::write(verified_marker(&inst.version_id), b"ok");
        v
    };
    progress::status(task, "Preparando Java…");
    java::ensure_for_version(&version).await?;
    let java_exe = java::java_for(&version, cfg!(target_os = "windows"));

    let settings = config::settings();
    let game_dir = inst.dir();
    std::fs::create_dir_all(&game_dir).map_err(|e| e.to_string())?;
    let natives_dir = versions_dir().join(&inst.version_id).join("natives");
    {
        let v = version.clone();
        let nd = natives_dir.clone();
        tokio::task::spawn_blocking(move || extract_natives(&v, &nd))
            .await
            .map_err(|e| e.to_string())??;
    }

    let assets_index = version["assetIndex"]["id"].as_str().or(version["assets"].as_str()).unwrap_or("legacy").to_string();
    let virtual_dir = assets_dir().join("virtual").join(&assets_index);
    let game_assets = if virtual_dir.exists() { virtual_dir } else { assets_dir() };
    let user_type = if account.kind == "microsoft" { "msa" } else { "legacy" };
    let access_token = if account.access_token.is_empty() { "0".to_string() } else { account.access_token.clone() };

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", account.name.clone());
    vars.insert("version_name", inst.version_id.clone());
    vars.insert("game_directory", game_dir.display().to_string());
    vars.insert("assets_root", assets_dir().display().to_string());
    vars.insert("game_assets", game_assets.display().to_string());
    vars.insert("assets_index_name", assets_index.clone());
    vars.insert("auth_uuid", account.uuid.clone());
    vars.insert("auth_access_token", access_token.clone());
    vars.insert("auth_session", format!("token:{}:{}", access_token, account.uuid));
    vars.insert("clientid", settings.ms_client_id.clone());
    vars.insert("auth_xuid", account.xuid.clone());
    vars.insert("user_type", user_type.into());
    vars.insert("user_properties", "{}".into());
    vars.insert("version_type", version["type"].as_str().unwrap_or("release").to_string());
    vars.insert("natives_directory", natives_dir.display().to_string());
    vars.insert("launcher_name", "AmatistaLauncher".into());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").into());
    vars.insert("classpath", classpath(&version));
    vars.insert("library_directory", libraries_dir().display().to_string());
    vars.insert("classpath_separator", config::classpath_separator().into());
    vars.insert("resolution_width", settings.width.to_string());
    vars.insert("resolution_height", settings.height.to_string());

    let mut features = Map::new();
    features.insert("has_custom_resolution".into(), Value::Bool(!settings.fullscreen));
    features.insert("is_demo_user".into(), Value::Bool(false));

    // --- JVM ---
    let max_mem = if inst.max_memory_mb > 0 { inst.max_memory_mb } else { settings.max_memory_mb };
    let mut jvm: Vec<String> = vec![
        format!("-Xms{}M", settings.min_memory_mb.min(max_mem)),
        format!("-Xmx{max_mem}M"),
    ];
    if settings.optimized_gc {
        jvm.extend(OPTIMIZED_GC.iter().map(|s| s.to_string()));
    }
    jvm.push("-Dfile.encoding=UTF-8".into());
    if let Some(log_arg) = version["logging"]["client"]["argument"].as_str() {
        if let Some(fid) = version["logging"]["client"]["file"]["id"].as_str() {
            let path = assets_dir().join("log_configs").join(fid);
            if path.exists() {
                jvm.push(log_arg.replace("${path}", &path.display().to_string()));
            }
        }
    }
    jvm.extend(settings.extra_jvm_args.split_whitespace().map(str::to_string));
    match version["arguments"]["jvm"].as_array() {
        Some(list) => jvm.extend(expand(list, &features, &vars)),
        None => {
            jvm.push(format!("-Djava.library.path={}", natives_dir.display()));
            jvm.push("-cp".into());
            jvm.push(vars["classpath"].clone());
        }
    }

    // --- Juego ---
    let main_class = version["mainClass"].as_str().ok_or("La versión no indica la clase principal")?.to_string();
    let mut game: Vec<String> = match version["arguments"]["game"].as_array() {
        Some(list) => expand(list, &features, &vars),
        None => version["minecraftArguments"]
            .as_str()
            .unwrap_or_default()
            .split_whitespace()
            .map(|a| substitute(a, &vars))
            .collect(),
    };
    if version["arguments"]["game"].is_null() && !settings.fullscreen {
        game.extend(["--width".into(), settings.width.to_string(), "--height".into(), settings.height.to_string()]);
    }
    if settings.fullscreen {
        game.push("--fullscreen".into());
    }

    let mut args = jvm;
    args.push(main_class);
    args.extend(game);
    Ok(GameCommand { java: java_exe, args, cwd: game_dir })
}

/// Prepara y lanza una instancia con una cuenta.
pub async fn launch(instance_id: &str, account_uuid: &str) -> Result<(), String> {
    let inst: Instance = instances::get(instance_id).ok_or("La instancia ya no existe")?;
    if RUNNING.lock().map(|m| m.contains_key(&inst.id)).unwrap_or(false) {
        return Err("Esta instancia ya se está ejecutando.".into());
    }
    let task = format!("Iniciando {}", inst.name);
    let account: Account = accounts::find(account_uuid).ok_or("Primero añade una cuenta en Cuentas.")?;
    progress::status(&task, "Comprobando la cuenta…");
    let account = accounts::ensure_fresh(account).await?;
    let game_cmd = prepare(&inst, &account, &task).await?;
    let java_exe = game_cmd.java.clone();

    progress::status(&task, "Abriendo Minecraft…");
    let mut cmd = tokio::process::Command::new(&game_cmd.java);
    cmd.args(&game_cmd.args)
        .current_dir(&game_cmd.cwd)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null());
    #[cfg(target_os = "windows")]
    {
        // Sin ventana de consola.
        cmd.creation_flags(0x0800_0000);
    }
    let mut child = cmd
        .spawn()
        .map_err(|e| format!("No se pudo iniciar Java ({}): {e}", java_exe.display()))?;
    let pid = child.id().unwrap_or(0);
    if let Ok(mut m) = RUNNING.lock() {
        m.insert(inst.id.clone(), pid);
    }
    instances::touch_played(&inst.id);
    progress::emit(&task, 1, 1, "Minecraft se está abriendo");
    progress::emit_raw("game-started", inst.id.clone());

    // Registro del juego hacia la interfaz.
    let id = inst.id.clone();
    if let Some(out) = child.stdout.take() {
        let id = id.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(out).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                progress::emit_raw("game-log", serde_json::json!({ "instance": id, "line": line }));
            }
        });
    }
    if let Some(err) = child.stderr.take() {
        let id = id.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(err).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                progress::emit_raw("game-log", serde_json::json!({ "instance": id, "line": line, "error": true }));
            }
        });
    }
    tokio::spawn(async move {
        let status = child.wait().await;
        if let Ok(mut m) = RUNNING.lock() {
            m.remove(&id);
        }
        let code = status.ok().and_then(|s| s.code()).unwrap_or(-1);
        progress::emit_raw("game-exit", serde_json::json!({ "instance": id, "code": code }));
    });
    Ok(())
}

/// Cierra a la fuerza una instancia en ejecución.
pub fn kill(instance_id: &str) -> Result<(), String> {
    let pid = RUNNING.lock().ok().and_then(|m| m.get(instance_id).copied()).ok_or("No se está ejecutando")?;
    #[cfg(target_os = "windows")]
    let r = std::process::Command::new("taskkill").args(["/PID", &pid.to_string(), "/T", "/F"]).status();
    #[cfg(not(target_os = "windows"))]
    let r = std::process::Command::new("kill").args(["-9", &pid.to_string()]).status();
    r.map(|_| ()).map_err(|e| e.to_string())
}
