//! Descargas: un cliente HTTP compartido, verificación SHA-1, reintentos y descargas en
//! paralelo con progreso. Los archivos que ya existen y están bien no se vuelven a bajar.

use futures::stream::{self, StreamExt};
use once_cell::sync::Lazy;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::io::AsyncWriteExt;

use crate::progress;

pub static CLIENT: Lazy<reqwest::Client> = Lazy::new(|| {
    reqwest::Client::builder()
        .user_agent(concat!("AmatistaLauncher/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(300))
        .pool_max_idle_per_host(64)
        .tcp_nodelay(true)
        .build()
        .expect("no se pudo crear el cliente HTTP")
});

#[derive(Clone, Debug)]
pub struct Download {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

pub fn sha1_file(path: &Path) -> Option<String> {
    let data = std::fs::read(path).ok()?;
    Some(sha1_smol::Sha1::from(&data).digest().to_string())
}

/// ¿El archivo ya está descargado y es correcto? Con SHA-1 se comprueba el contenido;
/// sin él basta con que exista (y tenga el tamaño esperado si se conoce).
pub fn is_valid(d: &Download) -> bool {
    let Ok(meta) = std::fs::metadata(&d.path) else {
        return false;
    };
    if let Some(size) = d.size {
        if size > 0 && meta.len() != size {
            return false;
        }
    }
    match &d.sha1 {
        Some(expected) => sha1_file(&d.path).is_some_and(|h| h.eq_ignore_ascii_case(expected)),
        None => meta.len() > 0,
    }
}

pub async fn get_json(url: &str) -> Result<serde_json::Value, String> {
    let resp = CLIENT
        .get(url)
        .send()
        .await
        .map_err(|e| format!("No se pudo conectar ({url}): {e}"))?;
    if !resp.status().is_success() {
        return Err(format!("El servidor respondió {} para {url}", resp.status()));
    }
    resp.json().await.map_err(|e| format!("Respuesta inválida de {url}: {e}"))
}

pub async fn get_text(url: &str) -> Result<String, String> {
    let resp = CLIENT.get(url).send().await.map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("El servidor respondió {} para {url}", resp.status()));
    }
    resp.text().await.map_err(|e| e.to_string())
}

/// Descarga un archivo (a .part y luego se renombra), con hasta 4 intentos.
pub async fn download_one(d: &Download, bytes_counter: Option<&AtomicU64>) -> Result<(), String> {
    if let Some(parent) = d.path.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| e.to_string())?;
    }
    let tmp = d.path.with_extension("part");
    let mut last_err = String::new();
    for attempt in 0..4 {
        if attempt > 0 {
            tokio::time::sleep(Duration::from_millis(400 * attempt)).await;
        }
        match try_download(d, &tmp, bytes_counter).await {
            Ok(()) => {
                if let Some(expected) = &d.sha1 {
                    let tmp2 = tmp.clone();
                    let ok = tokio::task::spawn_blocking(move || sha1_file(&tmp2))
                        .await
                        .ok()
                        .flatten()
                        .is_some_and(|h| h.eq_ignore_ascii_case(expected));
                    if !ok {
                        last_err = format!("Suma de verificación incorrecta: {}", d.url);
                        continue;
                    }
                }
                tokio::fs::rename(&tmp, &d.path).await.map_err(|e| e.to_string())?;
                return Ok(());
            }
            Err(e) => last_err = e,
        }
    }
    let _ = tokio::fs::remove_file(&tmp).await;
    Err(last_err)
}

async fn try_download(d: &Download, tmp: &Path, bytes_counter: Option<&AtomicU64>) -> Result<(), String> {
    let resp = CLIENT
        .get(&d.url)
        .send()
        .await
        .map_err(|e| format!("Error de red ({}): {e}", d.url))?;
    if !resp.status().is_success() {
        return Err(format!("{} respondió {}", d.url, resp.status()));
    }
    let mut file = tokio::fs::File::create(tmp).await.map_err(|e| e.to_string())?;
    let mut stream = resp.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| e.to_string())?;
        if let Some(c) = bytes_counter {
            c.fetch_add(chunk.len() as u64, Ordering::Relaxed);
        }
        file.write_all(&chunk).await.map_err(|e| e.to_string())?;
    }
    file.flush().await.map_err(|e| e.to_string())?;
    Ok(())
}

/// Descarga muchos archivos a la vez. Primero descarta (en paralelo) los que ya están
/// bien, luego baja el resto con `concurrency` conexiones simultáneas.
pub async fn download_all(label: &str, items: Vec<Download>, concurrency: usize) -> Result<(), String> {
    if items.is_empty() {
        return Ok(());
    }
    progress::emit(label, 0, items.len() as u64, "Comprobando archivos…");

    // Comprobación de archivos existentes en hilos de bloqueo (lectura + SHA-1).
    let checks = stream::iter(items.into_iter().map(|d| {
        tokio::task::spawn_blocking(move || {
            let ok = is_valid(&d);
            (d, ok)
        })
    }))
    .buffer_unordered(32)
    .collect::<Vec<_>>()
    .await;
    let missing: Vec<Download> = checks
        .into_iter()
        .filter_map(|r| r.ok())
        .filter(|(_, ok)| !ok)
        .map(|(d, _)| d)
        .collect();

    let total = missing.len() as u64;
    if total == 0 {
        progress::emit(label, 1, 1, "Todo al día");
        return Ok(());
    }

    let done = Arc::new(AtomicUsize::new(0));
    let bytes = Arc::new(AtomicU64::new(0));
    let label = label.to_string();
    let results = stream::iter(missing.into_iter().map(|d| {
        let done = done.clone();
        let bytes = bytes.clone();
        let label = label.clone();
        async move {
            let r = download_one(&d, Some(&bytes)).await;
            let n = done.fetch_add(1, Ordering::Relaxed) as u64 + 1;
            if n == total || n % 8 == 0 {
                let mb = bytes.load(Ordering::Relaxed) as f64 / 1_048_576.0;
                progress::emit(&label, n, total, &format!("{n}/{total} archivos · {mb:.1} MB"));
            }
            r
        }
    }))
    .buffer_unordered(concurrency.max(1))
    .collect::<Vec<_>>()
    .await;

    let errors: Vec<String> = results.into_iter().filter_map(|r| r.err()).collect();
    if let Some(first) = errors.first() {
        return Err(format!("Fallaron {} descargas. Primera: {first}", errors.len()));
    }
    Ok(())
}
