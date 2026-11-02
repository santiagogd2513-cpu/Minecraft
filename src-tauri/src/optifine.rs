//! OptiFine. Su autor no permite descargas automáticas, así que el usuario baja el
//! .jar desde optifine.net y el launcher hace lo mismo que el instalador de OptiFine:
//! genera la librería parcheada con `optifine.Patcher`, añade launchwrapper y crea la
//! versión "<mc>-OptiFine_<edición>" que hereda de vanilla.

use serde_json::{json, Value};
use std::io::Read;
use std::path::{Path, PathBuf};

use crate::config::{self, libraries_dir};
use crate::mojang;
use crate::progress;

pub const DOWNLOAD_PAGE: &str = "https://optifine.net/downloads";

/// "OptiFine_1.20.1_HD_U_I6.jar" → ("1.20.1", "HD_U_I6")
pub fn parse_name(file_name: &str) -> Option<(String, String)> {
    let name = file_name.trim_end_matches(".jar");
    let name = name.strip_prefix("preview_").unwrap_or(name);
    let rest = name.strip_prefix("OptiFine_")?;
    let (mc, edition) = rest.split_once("_HD_")?;
    let valid_mc = mc.split('.').all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()));
    if !valid_mc {
        return None;
    }
    Some((mc.to_string(), format!("HD_{edition}")))
}

fn zip_text(jar: &Path, name: &str) -> Option<String> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(jar).ok()?).ok()?;
    let mut e = zip.by_name(name).ok()?;
    let mut s = String::new();
    e.read_to_string(&mut s).ok()?;
    Some(s.trim().to_string())
}

fn zip_extract(jar: &Path, name: &str, dest: &Path) -> Result<(), String> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(jar).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    let mut e = zip.by_name(name).map_err(|_| format!("Falta {name} dentro de OptiFine"))?;
    if let Some(p) = dest.parent() {
        std::fs::create_dir_all(p).map_err(|e| e.to_string())?;
    }
    let mut f = std::fs::File::create(dest).map_err(|e| e.to_string())?;
    std::io::copy(&mut e, &mut f).map_err(|e| e.to_string())?;
    Ok(())
}

/// Instala OptiFine a partir del .jar que descargó el usuario. Devuelve (id, mc, edición).
pub async fn install_from_jar(jar: PathBuf) -> Result<(String, String, String), String> {
    let file_name = jar.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    let (mc, edition) = parse_name(&file_name)
        .ok_or("Ese archivo no parece OptiFine. Debe llamarse como OptiFine_1.20.1_HD_U_I6.jar")?;
    let task = format!("Instalando OptiFine {mc} {edition}");

    mojang::install_vanilla(&mc).await?;
    let vanilla = mojang::load_merged(&mc)?;
    let java = crate::java::ensure_for_version(&vanilla).await?;

    // 1. Librería parcheada.
    progress::status(&task, "Parcheando Minecraft con OptiFine…");
    let lib_version = format!("{mc}_{edition}");
    let out_jar = libraries_dir()
        .join("optifine/OptiFine")
        .join(&lib_version)
        .join(format!("OptiFine-{lib_version}.jar"));
    std::fs::create_dir_all(out_jar.parent().unwrap()).map_err(|e| e.to_string())?;
    let mut cmd = tokio::process::Command::new(&java);
    cmd.arg("-cp")
        .arg(&jar)
        .arg("optifine.Patcher")
        .arg(mojang::client_jar(&vanilla))
        .arg(&jar)
        .arg(&out_jar);
    #[cfg(target_os = "windows")]
    {
        cmd.creation_flags(0x0800_0000);
    }
    let output = cmd.output().await.map_err(|e| format!("No se pudo ejecutar Java: {e}"))?;
    if !output.status.success() || !out_jar.exists() {
        // OptiFine muy antiguo no trae Patcher: se usa el jar tal cual.
        std::fs::copy(&jar, &out_jar).map_err(|e| e.to_string())?;
    }

    // 2. Launchwrapper (el de OptiFine para 1.13+, el de Mojang para versiones viejas).
    let mut libraries = vec![json!({ "name": format!("optifine:OptiFine:{lib_version}") })];
    match zip_text(&jar, "launchwrapper-of.txt") {
        Some(lw) => {
            let dest = libraries_dir()
                .join("optifine/launchwrapper-of")
                .join(&lw)
                .join(format!("launchwrapper-of-{lw}.jar"));
            zip_extract(&jar, &format!("launchwrapper-of-{lw}.jar"), &dest)?;
            libraries.push(json!({ "name": format!("optifine:launchwrapper-of:{lw}") }));
        }
        None => libraries.push(json!({ "name": "net.minecraft:launchwrapper:1.12" })),
    }

    // 3. Versión.
    let id = format!("{mc}-OptiFine_{edition}");
    let parent = mojang::read_version(&mc)?;
    let mut version = json!({
        "id": id,
        "inheritsFrom": mc,
        "type": "release",
        "time": parent["time"],
        "releaseTime": parent["releaseTime"],
        "mainClass": "net.minecraft.launchwrapper.Launch",
        "libraries": libraries,
    });
    match parent["minecraftArguments"].as_str() {
        Some(args) => version["minecraftArguments"] = json!(format!("{args} --tweakClass optifine.OptiFineTweaker")),
        None => version["arguments"] = json!({ "game": ["--tweakClass", "optifine.OptiFineTweaker"] }),
    }
    config::write_json(&mojang::version_json_path(&id), &version)?;

    mojang::install_files(&id).await?;
    crate::launch::invalidate(&id);
    progress::emit(&task, 1, 1, "OptiFine instalado");
    Ok((id, mc, edition))
}

#[cfg(test)]
mod tests {
    use super::parse_name;

    #[test]
    fn parses_optifine_file_names() {
        assert_eq!(parse_name("OptiFine_1.20.1_HD_U_I6.jar"), Some(("1.20.1".into(), "HD_U_I6".into())));
        assert_eq!(parse_name("preview_OptiFine_1.21.4_HD_U_J3_pre9.jar"), Some(("1.21.4".into(), "HD_U_J3_pre9".into())));
        assert_eq!(parse_name("OptiFine_1.8.9_HD_U_M5.jar"), Some(("1.8.9".into(), "HD_U_M5".into())));
        assert_eq!(parse_name("sodium.jar"), None);
    }
}

/// Valor de ayuda para la interfaz.
pub fn info() -> Value {
    json!({ "download_page": DOWNLOAD_PAGE })
}
