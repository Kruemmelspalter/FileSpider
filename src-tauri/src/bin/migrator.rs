use std::str::FromStr;

use clap::Parser;
use eyre::Result;
use sqlx::sqlite::SqliteConnectOptions;
use sqlx::{Row, SqlitePool};
use tokio::process::Command;

use filespider::{db, directories};

#[derive(Parser, Debug)]
#[command()]
struct Args {
    #[arg()]
    mysql_url: String,

    #[arg()]
    document_directory: String,

}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();

    directories::create_directories().await?;

    let pool_new = db::init().await?;
    sqlx::migrate!().run(&pool_new).await?;

    let pool_old =
        SqlitePool::connect_with(SqliteConnectOptions::from_str(&args.mysql_url)?.read_only(true))
            .await?;

    let q = futures::future::join_all(sqlx::query("select id, title, renderer, fileExtension from Document").map(async move |res| {
        let tags = sqlx::query("select tag from Tag where document = ?")
            .bind(res.get("id"))
            .map(|res| res.get("tag"))
            .fetch_all(&pool_old).await?;

        filespider::document::create(&pool_new, res.get("title"), Some(match res.get("renderer") {
            "markdown" => filespider::types::DocType::Markdown,
            "tex" | "latex" => filespider::types::DocType::LaTeX,
            "xournal" | "xournalpp" => filespider::types::DocType::XournalPP,
            _ => filespider::types::DocType::Plain,
        }), tags, res.try_get("fileExtension").map(|s| Some(s)).unwrap_or(None), None)
    }).fetch_all(&pool_old).await?).await?;

    if !Command::new("cp")
        .arg("-r")
        .arg(format!("{}/*-*-*-*-*", args.document_directory))
        .arg(directories::get_filespider_directory()?)
        .spawn()?
        .wait()?.success() {
        return Err(eyre::eyre!("Failed to copy files"));
    }

    Ok(())
}
