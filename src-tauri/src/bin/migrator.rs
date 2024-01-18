use std::str::FromStr;

use clap::Parser;
use eyre::Result;
use sqlx::{MySqlPool, Row};
use sqlx::mysql::{MySqlConnectOptions, MySqlRow};
use tokio::process::Command;

use filespider::{db, directories, document::File};


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
        MySqlPool::connect_with(MySqlConnectOptions::from_str(&args.mysql_url)?)
            .await?;

    let mut conn = pool_old.acquire().await?.detach();

    let x = sqlx::query("select id, title, renderer, fileExtension from Document").fetch_all(&pool_old).await?;
    for r in x {
            let tags = sqlx::query("select tag from Tag where document = ?")
                .bind::<String>(r.get("id"))
                .map(|res: MySqlRow| res.get("tag"))
                .fetch_all(&mut conn).await?;
            filespider::document::create(&pool_new, r.get("title"), Some(match r.get("renderer") {
                "markdown" => filespider::types::DocType::Markdown,
                "tex" | "latex" => filespider::types::DocType::LaTeX,
                "xournal" | "xournalpp" => filespider::types::DocType::XournalPP,
                _ => filespider::types::DocType::Plain,
            }), tags, r.try_get("fileExtension").map(Some).unwrap_or(None), File::None).await?;
    }

    if !Command::new("sh")
        .arg("-c")
        .arg(format!("cp -r {}/*-*-*-*-* {}", args.document_directory, directories::get_filespider_directory()?))
        .spawn()?
        .wait().await?.success() {
        return Err(eyre::eyre!("Failed to copy files"));
    }

    Ok(())
}
