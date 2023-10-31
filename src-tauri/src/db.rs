use std::str::FromStr;

use eyre::Result;
use sqlx::sqlite::SqliteConnectOptions;
use sqlx::SqlitePool;

use crate::directories::get_filespider_directory;

pub async fn init() -> Result<SqlitePool> {
    std::fs::create_dir_all(get_filespider_directory()?)?;

    let db_path = std::env::var("DATABASE_URL")
        .unwrap_or(format!("{}/filespider.sqlite", get_filespider_directory()?));
    println!("using DB {}", db_path);
    let pool =
        SqlitePool::connect_with(SqliteConnectOptions::from_str(&db_path)?.create_if_missing(true))
            .await?;

    sqlx::migrate!().run(&pool).await?;

    Ok(pool)
}
