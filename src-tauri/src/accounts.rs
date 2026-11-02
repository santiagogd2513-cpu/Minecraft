//! Cuentas: inicio de sesión con Microsoft (código de dispositivo → Xbox Live → XSTS →
//! servicios de Minecraft) y cuentas locales.
//!
//! Las cuentas locales solo se pueden crear después de haber verificado una cuenta de
//! Microsoft que tenga Minecraft (igual que Prism Launcher), para no permitir jugar sin
//! haber comprado el juego.

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::config;
use crate::net::CLIENT;

const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const SCOPE: &str = "XboxLive.signin offline_access";

#[derive(Clone, Debug, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct Account {
    /// UUID del jugador (sin guiones).
    pub uuid: String,
    pub name: String,
    /// "microsoft" | "local"
    pub kind: String,
    pub access_token: String,
    pub refresh_token: String,
    /// Momento (segundos Unix) en que caduca el token de Minecraft.
    pub expires_at: u64,
    pub xuid: String,
    pub owns_game: bool,
}

impl Account {
    /// Versión segura para enviar a la interfaz (sin tokens).
    pub fn public(&self) -> Value {
        json!({ "uuid": self.uuid, "name": self.name, "kind": self.kind, "owns_game": self.owns_game })
    }
}

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

fn accounts_path() -> std::path::PathBuf {
    config::config_dir().join("accounts.json")
}

pub fn load() -> Vec<Account> {
    config::read_json(&accounts_path()).unwrap_or_default()
}

fn save(list: &[Account]) -> Result<(), String> {
    config::write_json(&accounts_path(), &list)
}

fn upsert(acc: Account) -> Result<(), String> {
    let mut list = load();
    list.retain(|a| !(a.uuid == acc.uuid && a.kind == acc.kind));
    list.insert(0, acc);
    save(&list)
}

pub fn remove(uuid: &str, kind: &str) -> Result<(), String> {
    let mut list = load();
    list.retain(|a| !(a.uuid == uuid && a.kind == kind));
    save(&list)
}

pub fn has_verified_owner() -> bool {
    load().iter().any(|a| a.kind == "microsoft" && a.owns_game)
}

fn client_id() -> Result<String, String> {
    let id = config::settings().ms_client_id.trim().to_string();
    if id.is_empty() {
        return Err("Falta el ID de cliente de Azure. Ve a Ajustes → Cuenta de Microsoft y pega el ID de tu aplicación (allí se explica cómo crearla).".into());
    }
    Ok(id)
}

// ---------------------------------------------------------------------------
// Microsoft: código de dispositivo
// ---------------------------------------------------------------------------

pub async fn start_device_code() -> Result<Value, String> {
    let resp = CLIENT
        .post(DEVICE_CODE_URL)
        .form(&[("client_id", client_id()?.as_str()), ("scope", SCOPE)])
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let body: Value = resp.json().await.map_err(|e| e.to_string())?;
    if body.get("device_code").is_none() {
        return Err(format!(
            "Microsoft rechazó la solicitud: {}",
            body["error_description"].as_str().unwrap_or("error desconocido")
        ));
    }
    Ok(json!({
        "device_code": body["device_code"],
        "user_code": body["user_code"],
        "verification_uri": body["verification_uri"],
        "interval": body["interval"].as_u64().unwrap_or(5),
        "expires_in": body["expires_in"].as_u64().unwrap_or(900),
    }))
}

/// Espera a que el usuario autorice en el navegador y completa el inicio de sesión.
pub async fn finish_device_code(device_code: String, interval: u64, expires_in: u64) -> Result<Value, String> {
    let id = client_id()?;
    let deadline = now() + expires_in;
    let mut wait = interval.max(2);
    loop {
        if now() > deadline {
            return Err("El código caducó. Vuelve a intentarlo.".into());
        }
        tokio::time::sleep(std::time::Duration::from_secs(wait)).await;
        let body: Value = CLIENT
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", id.as_str()),
                ("device_code", device_code.as_str()),
            ])
            .send()
            .await
            .map_err(|e| e.to_string())?
            .json()
            .await
            .map_err(|e| e.to_string())?;
        match body["error"].as_str() {
            None => {
                let ms_access = body["access_token"].as_str().unwrap_or_default().to_string();
                let refresh = body["refresh_token"].as_str().unwrap_or_default().to_string();
                let acc = minecraft_login(&ms_access, refresh).await?;
                upsert(acc.clone())?;
                return Ok(acc.public());
            }
            Some("authorization_pending") => {}
            Some("slow_down") => wait += 5,
            Some("authorization_declined") => return Err("Cancelaste el inicio de sesión.".into()),
            Some("expired_token") => return Err("El código caducó. Vuelve a intentarlo.".into()),
            Some(other) => {
                return Err(format!(
                    "Error de Microsoft ({other}): {}",
                    body["error_description"].as_str().unwrap_or_default()
                ))
            }
        }
    }
}

/// Microsoft → Xbox Live → XSTS → Minecraft, y comprueba que la cuenta tenga el juego.
async fn minecraft_login(ms_access: &str, refresh_token: String) -> Result<Account, String> {
    // Xbox Live
    let xbl: Value = CLIENT
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .json(&json!({
            "Properties": { "AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={ms_access}") },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|_| "Xbox Live no respondió correctamente".to_string())?;
    let xbl_token = xbl["Token"].as_str().ok_or("No se obtuvo el token de Xbox Live")?;

    // XSTS
    let xsts_resp = CLIENT
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let status = xsts_resp.status();
    let xsts: Value = xsts_resp.json().await.unwrap_or(json!({}));
    if !status.is_success() {
        let msg = match xsts["XErr"].as_u64() {
            Some(2148916233) => "Esta cuenta de Microsoft no tiene perfil de Xbox. Créalo en xbox.com y vuelve a intentarlo.",
            Some(2148916235) => "Xbox Live no está disponible en tu país.",
            Some(2148916236) | Some(2148916237) => "La cuenta necesita verificación de edad en xbox.com.",
            Some(2148916238) => "Es una cuenta de menor de edad: un adulto debe añadirla a una familia de Microsoft.",
            _ => "Xbox Live rechazó el inicio de sesión.",
        };
        return Err(msg.into());
    }
    let xsts_token = xsts["Token"].as_str().ok_or("No se obtuvo el token XSTS")?;
    let uhs = xsts["DisplayClaims"]["xui"][0]["uhs"].as_str().unwrap_or_default();
    let xuid = xsts["DisplayClaims"]["xui"][0]["xid"].as_str().unwrap_or_default().to_string();

    // Minecraft
    let mc_resp = CLIENT
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") }))
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if mc_resp.status().as_u16() == 403 {
        return Err("Minecraft rechazó la aplicación: tu ID de cliente de Azure aún no está aprobado por Mojang. Mira Ajustes → Cuenta de Microsoft para pedir la aprobación.".into());
    }
    let mc: Value = mc_resp.json().await.map_err(|e| e.to_string())?;
    let mc_token = mc["access_token"].as_str().ok_or("Minecraft no devolvió un token")?.to_string();
    let expires_at = now() + mc["expires_in"].as_u64().unwrap_or(86400);

    // ¿Tiene el juego?
    let ent: Value = CLIENT
        .get("https://api.minecraftservices.com/entitlements/mcstore")
        .bearer_auth(&mc_token)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .unwrap_or(json!({}));
    let owns = ent["items"].as_array().is_some_and(|i| !i.is_empty());

    // Perfil (nombre y UUID).
    let prof_resp = CLIENT
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&mc_token)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if prof_resp.status().as_u16() == 404 {
        return Err(if owns {
            "Tu cuenta tiene Minecraft pero aún no tiene nombre de jugador. Créalo en minecraft.net y vuelve a intentarlo.".into()
        } else {
            "Esta cuenta de Microsoft no tiene Minecraft Java Edition.".into()
        });
    }
    let profile: Value = prof_resp.json().await.map_err(|e| e.to_string())?;

    Ok(Account {
        uuid: profile["id"].as_str().unwrap_or_default().to_string(),
        name: profile["name"].as_str().unwrap_or_default().to_string(),
        kind: "microsoft".into(),
        access_token: mc_token,
        refresh_token,
        expires_at,
        xuid,
        owns_game: owns || profile["id"].is_string(),
    })
}

/// Renueva el token si va a caducar en menos de 10 minutos.
pub async fn ensure_fresh(acc: Account) -> Result<Account, String> {
    if acc.kind != "microsoft" || acc.expires_at > now() + 600 {
        return Ok(acc);
    }
    let id = client_id()?;
    let body: Value = CLIENT
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", id.as_str()),
            ("refresh_token", acc.refresh_token.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;
    let Some(ms_access) = body["access_token"].as_str() else {
        return Err("La sesión de Microsoft caducó. Vuelve a iniciar sesión en Cuentas.".into());
    };
    let refresh = body["refresh_token"].as_str().unwrap_or(&acc.refresh_token).to_string();
    let fresh = minecraft_login(ms_access, refresh).await?;
    upsert(fresh.clone())?;
    Ok(fresh)
}

// ---------------------------------------------------------------------------
// Cuentas locales
// ---------------------------------------------------------------------------

/// UUID que Minecraft usa para jugadores sin conexión: MD5("OfflinePlayer:" + nombre), v3.
pub fn offline_uuid(name: &str) -> String {
    use md5::{Digest, Md5};
    let hash: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}").as_bytes()).into();
    uuid::Builder::from_md5_bytes(hash).into_uuid().simple().to_string()
}

pub fn create_local(name: &str) -> Result<Value, String> {
    if !has_verified_owner() {
        return Err("Para crear una cuenta local primero inicia sesión con una cuenta de Microsoft que tenga Minecraft Java.".into());
    }
    let name = name.trim();
    let valid = (3..=16).contains(&name.len()) && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
    if !valid {
        return Err("El nombre debe tener de 3 a 16 caracteres: letras, números o _.".into());
    }
    let acc = Account {
        uuid: offline_uuid(name),
        name: name.to_string(),
        kind: "local".into(),
        owns_game: false,
        ..Default::default()
    };
    upsert(acc.clone())?;
    Ok(acc.public())
}

pub fn find(uuid: &str) -> Option<Account> {
    let list = load();
    list.iter().find(|a| a.uuid == uuid).cloned().or_else(|| list.first().cloned())
}

#[cfg(test)]
mod tests {
    #[test]
    fn offline_uuid_matches_java() {
        // UUID.nameUUIDFromBytes("OfflinePlayer:Notch") en Java.
        assert_eq!(super::offline_uuid("Notch"), "b50ad385829d3141a2167e7d7539ba7f");
    }
}
