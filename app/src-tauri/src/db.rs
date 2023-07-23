use std::fs;

use std::str::FromStr;
use tokio::sync::Mutex;

use eyre::Result;
use sqlx::sqlite::SqliteConnectOptions;
use sqlx::SqlitePool;

use crate::directories::get_filespider_directory;

pub async fn init() -> Result<Mutex<SqlitePool>> {
    fs::create_dir_all(get_filespider_directory()?)?;

    let db_path = format!("{}/filespider.sqlite", get_filespider_directory()?);
    println!("using DB {}", db_path);
    let pool =
        SqlitePool::connect_with(SqliteConnectOptions::from_str(&db_path)?.create_if_missing(true))
            .await?;

    sqlx::migrate!().run(&pool).await?;

    Ok(Mutex::new(pool))
}
