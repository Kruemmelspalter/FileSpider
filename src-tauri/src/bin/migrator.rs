use std::str::FromStr;

use clap::Parser;
use eyre::Result;
use sqlx::sqlite::{SqliteConnectOptions, SqliteRow};
use sqlx::{Row, SqlitePool};
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
        SqlitePool::connect_with(SqliteConnectOptions::from_str(&args.mysql_url)?.read_only(true))
            .await?;

    let mut conn = pool_old.acquire().await?.detach();

    let rt = tokio::runtime::Runtime::new()?;

    sqlx::query("select id, title, renderer, fileExtension from Document").map(move |res: SqliteRow| {

        // this can probably done better using proper async but I couldn't figure out how to do it
        rt.block_on(
        async {
            let tags = sqlx::query("select tag from Tag where document = ?")
                .bind::<String>(res.get("id"))
                .map(|res: SqliteRow| res.get("tag"))
                .fetch_all(&mut conn).await?;

            filespider::document::create(&pool_new, res.get("title"), Some(match res.get("renderer") {
                "markdown" => filespider::types::DocType::Markdown,
                "tex" | "latex" => filespider::types::DocType::LaTeX,
                "xournal" | "xournalpp" => filespider::types::DocType::XournalPP,
                _ => filespider::types::DocType::Plain,
            }), tags, res.try_get("fileExtension").map(|s| Some(s)).unwrap_or(None), File::None).await?;

            Ok(())
        })
    }).fetch_all(&pool_old).await?.into_iter().collect::<Result<_>>()?;

    if !Command::new("cp")
        .arg("-r")
        .arg(format!("{}/*-*-*-*-*", args.document_directory))
        .arg(directories::get_filespider_directory()?)
        .spawn()?
        .wait().await?.success() {
        return Err(eyre::eyre!("Failed to copy files"));
    }

    Ok(())
}
