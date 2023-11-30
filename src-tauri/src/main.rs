// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::collections::HashMap;
#[cfg(target_os = "linux")]
use std::sync::Arc;

use eyre::Result;
use sqlx::SqlitePool;
use tokio::{process, sync::Mutex, task::JoinHandle};
use uuid::Uuid;

mod db;
mod directories;
mod document;
mod types;

pub struct FilespiderState {
    pool: Mutex<SqlitePool>,
    editors: Mutex<HashMap<Uuid, process::Child>>,
    renderers: Mutex<HashMap<(Uuid, document::render::Hash), Mutex<JoinHandle<()>>>>,
    #[cfg(target_os = "linux")] dbus: Mutex<Arc<dbus::nonblock::SyncConnection>>,
}

impl FilespiderState {
    fn new(
        pool: SqlitePool,
        #[cfg(target_os = "linux")] dbus: Arc<dbus::nonblock::SyncConnection>,
    ) -> Self {
        Self {
            pool: Mutex::new(pool),
            editors: Mutex::new(HashMap::new()),
            renderers: Mutex::new(HashMap::new()),
            #[cfg(target_os = "linux")] dbus: Mutex::new(dbus),
        }
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    env_logger::Builder::new()
        .filter_level(log::LevelFilter::Trace)
        .init();

    directories::create_directories().await?;

    let pool = db::init().await?;
    sqlx::migrate!().run(&pool).await?;
    #[cfg(target_os = "linux")]
        let (resource, conn) = dbus_tokio::connection::new_session_sync()?;
    #[cfg(target_os = "linux")]
    tokio::spawn(async {
        let err = resource.await;
        error!("Lost connection to D-Bus: {}", err);
    });

    tauri::Builder::default()
        .manage(FilespiderState::new(pool, #[cfg(target_os = "linux")] conn))
        .plugin(document::commands::plugin())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");

    Ok(())
}
