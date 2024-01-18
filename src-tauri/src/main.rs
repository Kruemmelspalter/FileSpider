// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use eyre::Result;
use log::error;
use filespider::*;
use filespider::settings::Settings;

#[tokio::main]
async fn main() -> Result<()> {
    env_logger::Builder::new()
        .filter_level(log::LevelFilter::Trace)
        .init();

    directories::create_directories().await?;

    let pool = db::init().await?;
    sqlx::migrate!().run(&pool).await?;
    #[cfg(target_os = "linux")]
        let (resource, conn) = match dbus_tokio::connection::new_session_sync() {
        Ok((r, c)) => (Some(r), Some(c)),
        Err(e) => {
            error!("Failed to connect to D-Bus, continuing without it: {:?}", e);
            (None, None)
        }
    };
    #[cfg(target_os = "linux")]
    if let Some(resource) = resource {
    tokio::spawn(async {
        let err = resource.await;
        log::error!("Lost connection to D-Bus: {}", err);
    });}

    let settings = Settings::load().await?;

    tauri::Builder::default()
        .manage(FilespiderState::new(pool, settings, #[cfg(target_os = "linux")] conn))
        .plugin(document::commands::plugin())
        .plugin(settings::commands::plugin())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");

    Ok(())
}
