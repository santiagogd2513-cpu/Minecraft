//! Amatista Launcher: launcher de Minecraft Java (vanilla, OptiFine y Forge) con cuentas
//! de Microsoft, cuentas locales y descargas de Modrinth/CurseForge.

mod accounts;
mod config;
mod content;
mod forge;
mod instances;
mod java;
mod launch;
#[cfg(test)]
mod live_tests;
mod mojang;
mod net;
mod optifine;
mod progress;

use serde_json::{json, Value};
use tauri::Manager;

type R<T> = Result<T, String>;

// ---------------- Ajustes ----------------

#[tauri::command]
fn get_settings() -> config::Settings {
    config::settings()
}

#[tauri::command]
fn save_settings(settings: config::Settings) -> R<config::Settings> {
    config::save_settings(settings)?;
    Ok(config::settings())
}

#[tauri::command]
fn system_info() -> Value {
    json!({
        "memory_mb": config::system_memory_mb(),
        "default_game_dir": config::default_game_dir().display().to_string(),
        "os": config::os_name(),
        "version": env!("CARGO_PKG_VERSION"),
    })
}

// ---------------- Versiones e instalación ----------------

#[tauri::command]
async fn list_versions(snapshots: bool) -> R<Vec<Value>> {
    mojang::list_versions(snapshots).await
}

#[tauri::command]
async fn install_vanilla(mc: String) -> R<instances::Instance> {
    mojang::install_vanilla(&mc).await?;
    instances::create(&format!("Minecraft {mc}"), &mc, "vanilla", "", &mc)
}

#[tauri::command]
async fn forge_versions(mc: String) -> R<Vec<Value>> {
    forge::list_versions(&mc).await
}

#[tauri::command]
async fn install_forge(full: String) -> R<instances::Instance> {
    let id = forge::install(&full).await?;
    let mc = full.split('-').next().unwrap_or(&full).to_string();
    let fv = full[mc.len()..].trim_start_matches('-').split('-').next().unwrap_or_default().to_string();
    instances::create(&format!("Forge {mc}"), &mc, "forge", &fv, &id)
}

#[tauri::command]
fn optifine_info() -> Value {
    optifine::info()
}

#[tauri::command]
async fn install_optifine(path: String) -> R<instances::Instance> {
    let (id, mc, edition) = optifine::install_from_jar(std::path::PathBuf::from(path)).await?;
    instances::create(&format!("OptiFine {mc}"), &mc, "optifine", &edition, &id)
}

// ---------------- Instancias y juego ----------------

#[tauri::command]
fn instances_list() -> Vec<instances::Instance> {
    instances::list()
}

#[tauri::command]
fn instance_delete(id: String) -> R<()> {
    instances::delete(&id)
}

#[tauri::command]
fn instance_rename(id: String, name: String) -> R<()> {
    instances::rename(&id, &name)
}

#[tauri::command]
fn instance_set_memory(id: String, max_memory_mb: u32) -> R<()> {
    let mut inst = instances::get(&id).ok_or("Instancia no encontrada")?;
    inst.max_memory_mb = max_memory_mb;
    instances::update(&inst)
}

#[tauri::command]
fn instance_dir(id: String) -> R<String> {
    let inst = instances::get(&id).ok_or("Instancia no encontrada")?;
    let dir = inst.dir();
    let _ = std::fs::create_dir_all(&dir);
    Ok(dir.display().to_string())
}

#[tauri::command]
fn game_dir() -> String {
    let d = config::game_dir();
    let _ = std::fs::create_dir_all(&d);
    d.display().to_string()
}

#[tauri::command]
async fn launch_instance(app: tauri::AppHandle, id: String, account: String) -> R<()> {
    launch::launch(&id, &account).await?;
    if let Some(win) = app.get_webview_window("main") {
        match config::settings().on_launch.as_str() {
            "minimizar" => {
                let _ = win.minimize();
            }
            "cerrar" => app.exit(0),
            _ => {}
        }
    }
    Ok(())
}

#[tauri::command]
fn kill_instance(id: String) -> R<()> {
    launch::kill(&id)
}

#[tauri::command]
fn running_instances() -> Vec<String> {
    launch::running_instances()
}

#[tauri::command]
fn repair_instance(id: String) -> R<()> {
    let inst = instances::get(&id).ok_or("Instancia no encontrada")?;
    launch::invalidate(&inst.version_id);
    Ok(())
}

// ---------------- Cuentas ----------------

#[tauri::command]
fn accounts_list() -> Value {
    json!({
        "accounts": accounts::load().iter().map(|a| a.public()).collect::<Vec<_>>(),
        "can_create_local": accounts::has_verified_owner(),
    })
}

#[tauri::command]
async fn ms_login_start() -> R<Value> {
    accounts::start_device_code().await
}

#[tauri::command]
async fn ms_login_finish(device_code: String, interval: u64, expires_in: u64) -> R<Value> {
    accounts::finish_device_code(device_code, interval, expires_in).await
}

#[tauri::command]
fn account_create_local(name: String) -> R<Value> {
    accounts::create_local(&name)
}

#[tauri::command]
fn account_remove(uuid: String, kind: String) -> R<()> {
    accounts::remove(&uuid, &kind)
}

// ---------------- Contenido ----------------

#[tauri::command]
async fn content_search(source: String, kind: String, query: String, mc: String, loader: String, offset: u32) -> R<Value> {
    content::search(&source, &kind, &query, &mc, &loader, offset).await
}

#[tauri::command]
async fn content_game_versions() -> R<Vec<String>> {
    content::game_versions().await
}

#[tauri::command]
async fn content_install(source: String, kind: String, project: String, instance: String) -> R<String> {
    content::install_into(&source, &kind, &project, &instance).await
}

#[tauri::command]
async fn modpack_install(source: String, project: String, title: String, mc: String) -> R<instances::Instance> {
    content::install_modpack(&source, &project, &title, &mc).await
}

#[tauri::command]
fn content_installed(instance: String, kind: String) -> Vec<Value> {
    content::list_installed(&instance, &kind)
}

#[tauri::command]
fn content_toggle(instance: String, kind: String, name: String) -> R<()> {
    content::toggle_installed(&instance, &kind, &name)
}

#[tauri::command]
fn content_delete(instance: String, kind: String, name: String) -> R<()> {
    content::delete_installed(&instance, &kind, &name)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            progress::init(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_settings,
            save_settings,
            system_info,
            list_versions,
            install_vanilla,
            forge_versions,
            install_forge,
            optifine_info,
            install_optifine,
            instances_list,
            instance_delete,
            instance_rename,
            instance_set_memory,
            instance_dir,
            game_dir,
            launch_instance,
            kill_instance,
            running_instances,
            repair_instance,
            accounts_list,
            ms_login_start,
            ms_login_finish,
            account_create_local,
            account_remove,
            content_search,
            content_game_versions,
            content_install,
            modpack_install,
            content_installed,
            content_toggle,
            content_delete,
        ])
        .run(tauri::generate_context!())
        .expect("error al iniciar Amatista Launcher");
}
