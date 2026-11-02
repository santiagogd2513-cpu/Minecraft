//! Pruebas reales (necesitan internet; en CI se ejecutan con `cargo test -- --ignored`):
//! instalan Minecraft vanilla y Forge, arman el comando con el mismo código que el botón
//! JUGAR y arrancan el juego unos segundos para comprobar que inicia sin errores.

use std::io::{BufRead, BufReader};
use std::process::{Command, Stdio};
use std::sync::mpsc;
use std::time::{Duration, Instant};

use crate::accounts::{self, Account};
use crate::{config, forge, instances, launch, mojang};

fn test_account() -> Account {
    Account {
        uuid: accounts::offline_uuid("Tester"),
        name: "Tester".into(),
        kind: "local".into(),
        ..Default::default()
    }
}

/// Arranca el juego y lee su registro durante `secs` segundos (o hasta que termine).
fn run_and_collect(cmd: launch::GameCommand, secs: u64) -> String {
    let mut child = Command::new(&cmd.java)
        .args(&cmd.args)
        .current_dir(&cmd.cwd)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .expect("no se pudo iniciar Java");
    let (tx, rx) = mpsc::channel::<String>();
    for stream in [Box::new(child.stdout.take().unwrap()) as Box<dyn std::io::Read + Send>, Box::new(child.stderr.take().unwrap())] {
        let tx = tx.clone();
        std::thread::spawn(move || {
            for line in BufReader::new(stream).lines().map_while(Result::ok) {
                let _ = tx.send(line);
            }
        });
    }
    drop(tx);
    let mut log = String::new();
    let start = Instant::now();
    while start.elapsed() < Duration::from_secs(secs) {
        match rx.recv_timeout(Duration::from_millis(500)) {
            Ok(line) => {
                println!("[juego] {line}");
                log.push_str(&line);
                log.push('\n');
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        }
    }
    let _ = child.kill();
    let _ = child.wait();
    log
}

fn assert_boots(log: &str, extra_markers: &[&str]) {
    for bad in ["Could not find or load main class", "ClassNotFoundException", "NoClassDefFoundError", "Error: Unable to access jarfile", "UnsupportedClassVersionError"] {
        assert!(!log.contains(bad), "El juego falló al iniciar: se encontró «{bad}»");
    }
    assert!(log.contains("Setting user: Tester"), "El juego no llegó a iniciar sesión con el jugador de prueba");
    for marker in extra_markers {
        assert!(log.contains(marker), "Falta en el registro: «{marker}»");
    }
}

#[tokio::test(flavor = "multi_thread")]
#[ignore]
async fn live_vanilla_and_forge_boot() {
    let dir = std::env::temp_dir().join("amatista-live-test");
    let mut s = config::settings();
    s.game_dir = dir.display().to_string();
    s.max_memory_mb = 2048;
    s.min_memory_mb = 512;
    config::save_settings(s).unwrap();

    // --- Vanilla ---
    mojang::install_vanilla("1.20.1").await.expect("instalar vanilla");
    let inst = instances::create("Prueba vanilla", "1.20.1", "vanilla", "", "1.20.1").unwrap();
    let cmd = launch::prepare(&inst, &test_account(), "prueba").await.expect("preparar vanilla");
    let log = run_and_collect(cmd, 60);
    assert_boots(&log, &["Backend library: LWJGL"]);

    // --- Forge ---
    let versions = forge::list_versions("1.20.1").await.expect("lista de Forge");
    let full = versions
        .iter()
        .find(|v| v["recommended"] == true)
        .or(versions.first())
        .and_then(|v| v["full"].as_str())
        .expect("hay versiones de Forge")
        .to_string();
    let id = forge::install(&full).await.expect("instalar Forge");
    let inst = instances::create("Prueba Forge", "1.20.1", "forge", "", &id).unwrap();
    let cmd = launch::prepare(&inst, &test_account(), "prueba").await.expect("preparar Forge");
    let log = run_and_collect(cmd, 120);
    assert_boots(&log, &["ModLauncher"]);
}
