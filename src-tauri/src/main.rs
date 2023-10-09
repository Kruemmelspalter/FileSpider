// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use eyre::Result;
use sqlx::SqlitePool;
use std::collections::HashMap;
use tokio::{process, sync::Mutex, task::JoinHandle};
use uuid::Uuid;

mod db;
mod directories;
mod document;
mod types;

pub struct FilespiderState {
    pool: Mutex<SqlitePool>,
    editors: Mutex<HashMap<Uuid, process::Child>>,
    renderers: Mutex<HashMap<Uuid, JoinHandle<()>>>,
}

impl FilespiderState {
    fn new(pool: SqlitePool) -> Self {
        Self {
            pool: Mutex::new(pool),
            editors: Mutex::new(HashMap::new()),
            renderers: Mutex::new(HashMap::new()),
        }
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    directories::create_directory().await?;

    let pool = db::init().await?;
    sqlx::migrate!().run(&pool).await?;

    tauri::Builder::default()
        .manage(FilespiderState::new(pool))
        .plugin(document::commands::plugin())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");

    Ok(())
}
