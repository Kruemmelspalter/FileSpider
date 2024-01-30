use std::str::FromStr;

use clap::Parser;
use eyre::Result;
use sqlx::mysql::{MySqlConnectOptions, MySqlRow};
use sqlx::{MySqlPool, Row};
use tokio::process::Command;
use uuid::Uuid;

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

    let pool_old = MySqlPool::connect_with(MySqlConnectOptions::from_str(&args.mysql_url)?).await?;

    let mut conn = pool_old.acquire().await?.detach();

    let x = sqlx::query("select id, title, renderer, fileExtension from Document")
        .fetch_all(&pool_old)
        .await?;
    for r in x {
        let tags = sqlx::query("select tag from Tag where document = ?")
            .bind::<String>(r.get("id"))
            .map(|res: MySqlRow| res.get("tag"))
            .fetch_all(&mut conn)
            .await?;
        let id = filespider::document::create(
            &pool_new,
            r.get("title"),
            Some(match r.get("renderer") {
                "markdown" => filespider::types::DocType::Markdown,
                "tex" | "latex" => filespider::types::DocType::LaTeX,
                "xournal" | "xournalpp" => filespider::types::DocType::XournalPP,
                _ => filespider::types::DocType::Plain,
            }),
            tags,
            r.try_get("fileExtension").map(Some).unwrap_or(None),
            File::None,
        )
        .await?;

        let docdir = filespider::document::get_document_directory(&id)?;
        let old_id = Uuid::try_parse(&r.get::<String, &str>("id"))?;
        let ext = match r.get::<Option<String>, &str>("fileExtension") {
            Some(s) => format!(".{}", s),
            None => String::new(),
        };
        if !Command::new("sh")
            .arg("-c")
            .arg(format!(
                "mkdir {}/{} cp -r {}/{}/* {} && mv {}/{}{} {}/{}{}",
                docdir, id, args.document_directory, old_id, docdir, docdir, old_id, ext, docdir, id, ext,
            ))
            .spawn()?
            .wait()
            .await?
            .success()
        {
            return Err(eyre::eyre!(
                "Failed to copy files of document {} to {}",
                old_id,
                id
            ));
        }
    }

    Ok(())
}
