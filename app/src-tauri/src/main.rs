// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use eyre::Result;
use filespider_common::Render;
use sqlx::SqlitePool;
use std::collections::HashMap;
use tokio::process;
use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use uuid::Uuid;

mod db;
mod directories;
mod document;

pub struct FilespiderState {
    pub pool: Mutex<SqlitePool>,
    pub editors: Mutex<HashMap<Uuid, process::Child>>,
    pub renderers: Mutex<HashMap<Uuid, JoinHandle<Result<Render>>>>,
}

#[tokio::main]
async fn main() {
    tauri::Builder::default()
        .manage(FilespiderState {
            pool: db::init().await.unwrap(),
            editors: Mutex::new(HashMap::new()),
            renderers: Mutex::new(HashMap::new()),
        })
        .plugin(document::plugin())
        .run(tauri::generate_context!())
        .expect("error while running tauri application")
}
