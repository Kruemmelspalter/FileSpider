use std::{
    collections::HashMap,
    hash::Hasher,
    path::{Path, PathBuf},
    str::FromStr,
    time::UNIX_EPOCH,
};

use async_recursion::async_recursion;
use async_trait::async_trait;
use eyre::eyre;
use eyre::Result;
use eyre::WrapErr;
use sqlx::{query, SqliteConnection, SqlitePool};
use tokio::{sync::Mutex, task::JoinHandle};
use tokio::io::AsyncWriteExt;
use uuid::Uuid;

use crate::{
    document,
    types::{DocType, Meta, RenderType},
};

use super::{get_cache_file, get_document_basename, get_document_directory, get_document_file};

pub type Hash = u64;

pub async fn hash_document_files(id: Uuid) -> Result<Hash> {
    document::document_exists(&id).await?;

    let mut hasher = fxhash::FxHasher::default();
    hash_file(
        &mut hasher,
        PathBuf::from(get_document_directory(&id)?),
    ).await?;
    Ok(hasher.finish())
}

#[async_recursion]
async fn hash_file(hasher: &mut fxhash::FxHasher, path: PathBuf) -> Result<()> {
    let meta = tokio::fs::metadata(&path).await?;

    if meta.is_dir() {
        let mut entries = tokio::fs::read_dir(&path).await?;
        while let Ok(Some(entry)) = entries.next_entry().await {
            hash_file(hasher, entry.path()).await?;
        }
    } else {
        hasher.write_u128(meta.modified()?.duration_since(UNIX_EPOCH)?.as_millis());
    }

    Ok(())
}

pub async fn render(
    pool: &SqlitePool,
    renderers: &mut HashMap<(Uuid, Hash), Mutex<JoinHandle<()>>>,
    id: Uuid,
) -> Result<(String, RenderType)> {
    document::document_exists(&id).await?;
    let hash = hash_document_files(id).await?;
    let hex_hash = format!("{:016x}", hash);

    if let Some(handle) = renderers.get(&(id, hash)) {
        if !handle.lock().await.is_finished() {
            panic!("weird things happening: original renderer is not being awaited");
        }
        return get_from_cache(
            id,
            query!(
                "select render_type from Cache where document = ? and hash = unhex(?)",
                id,
                hex_hash
            ).map(|r| RenderType::from_str(r.render_type.as_str())).fetch_one(pool).await??,
        );
    }

    // check cache

    if let Some(render_type) = query!(
        "select render_type from Cache where document = ? and hash = unhex(?)",
        id,
        hex_hash
    ).map(|r| r.render_type).fetch_optional(pool).await? {
        return get_from_cache(id, RenderType::from_str(render_type.as_str())?);
    }

    let meta = document::get_meta(pool, id).await?;

    let connection = pool.acquire().await?.detach();

    let handle = tokio::task::spawn(render_task(meta, hash, connection));
    let mutex = Mutex::new(handle);
    renderers.insert((id, hash), mutex);

    let handle_ref = renderers.get(&(id, hash)).unwrap().lock().await;

    // TODO is there really no better way to do this
    while !handle_ref.is_finished() {
        tokio::time::sleep(std::time::Duration::from_millis(200)).await;
    }

    drop(handle_ref);

    get_from_cache(
        id,
        query!(
            "select render_type from Cache where document = ? and hash = unhex(?)",
            id,
            hex_hash
        ).map(|r| RenderType::from_str(r.render_type.as_str())).fetch_one(pool).await
            .wrap_err("renderer didn't insert into cache")??,
    )
}

fn get_from_cache(id: Uuid, render_type: RenderType) -> Result<(String, RenderType)> {
    Ok((get_cache_file(id)?, render_type))
}

#[allow(clippy::unused_io_amount)]
async fn render_task(meta: Meta, hash: Hash, mut connection: SqliteConnection) {
    let renderer = get_renderer_from_doc_type(&meta.doc_type);
    if let Err(e) = renderer.render(meta.id, hash, &mut connection, &meta).await {
        if let Ok(true) = tokio::fs::try_exists(get_cache_file(meta.id).unwrap()).await { tokio::fs::remove_file(get_cache_file(meta.id).unwrap()).await.unwrap() };
        tokio::fs::File::options().create(true).write(true)
            .open(get_cache_file(meta.id).unwrap()).await.unwrap()
            .write(format!("{:?}", e).as_bytes()).await.unwrap();
        insert_into_cache(&mut connection, meta.id, hash, RenderType::Plain).await.unwrap();
    };
}

async fn insert_into_cache<'a>(
    connection: &mut SqliteConnection,
    id: Uuid,
    hash: Hash,
    render_type: RenderType,
) -> Result<()> {
    let hex_hash = format!("{:016x}", hash);
    let render_str = render_type.to_string();

    query!(
        "insert into Cache (document, hash, render_type) values (?, unhex(?), ?) on conflict(document) do update set hash = unhex(?), render_type = ?",
        id,
        hex_hash,
        render_str,
        hex_hash,
        render_str
    ).execute(connection).await?;
    Ok(())
}

async fn copy_into_cache<'a>(
    connection: &mut SqliteConnection,
    id: Uuid,
    hash: Hash,
    path: impl AsRef<Path>,
    render_type: RenderType,
) -> Result<()> {
    tokio::fs::copy(path, get_cache_file(id)?).await.wrap_err("copying into cache failed")?;
    insert_into_cache(connection, id, hash, render_type).await?;
    Ok(())
}

fn get_renderer_from_doc_type(doc_type: &DocType) -> Box<dyn Renderer + Send + Sync> {
    match doc_type {
        DocType::Plain => Box::new(PlainRenderer),
        DocType::Markdown => Box::new(MarkdownRenderer),
        DocType::XournalPP => Box::new(XournalPPRenderer),
        DocType::LaTeX => Box::new(LaTeXRenderer),
    }
}

async fn copy_into_tempdir(id: &Uuid, temp_path: &Path) -> Result<()> {
    if tokio::process::Command::new("bash").args(vec!["-c", &format!("cp {}/* {}", get_document_directory(id)?, temp_path.to_str().unwrap())]).spawn()?.wait().await?.success() {
        Ok(())
    } else {
        Err(eyre!("copying exited with code != 0"))
    }.wrap_err("copying into tempdir failed")
}

async fn execute_command(command: &str, args: Vec<&str>, current_dir: Option<&Path>) -> Result<()> {
    match tokio::process::Command::new(command).args(args).current_dir(current_dir.unwrap_or(Path::new("/"))).output().await {
        Ok(s) => {
            if s.status.success() {
                Ok(())
            } else {
                Err(eyre!("Command {command} failed with exit code {}\n\nSTDOUT\n{}\n\nSTDERR\nP{}",
                    s.status.code().unwrap_or(-1), unsafe {std::str::from_utf8_unchecked(&s.stdout)}, unsafe{std::str::from_utf8_unchecked(&s.stderr)}))
            }
        }
        Err(e) => Err(e).wrap_err("waiting for command failed"),
    }
}

#[async_trait]
trait Renderer {
    async fn render(
        &self,
        id: Uuid,
        hash: Hash,
        connection: &mut SqliteConnection,
        meta: &Meta,
    ) -> Result<()>;
}

struct PlainRenderer;

#[async_trait]
impl Renderer for PlainRenderer {
    async fn render(
        &self,
        id: Uuid,
        hash: Hash,
        connection: &mut SqliteConnection,
        meta: &Meta,
    ) -> Result<()> {
        copy_into_cache(
            connection,
            id,
            hash,
            get_document_file(&meta.id, &meta.extension)?,
            RenderType::Plain,
        ).await?;
        Ok(())
    }
}

struct MarkdownRenderer;

#[async_trait]
impl Renderer for MarkdownRenderer {
    async fn render(
        &self,
        id: Uuid,
        hash: Hash,
        connection: &mut SqliteConnection,
        meta: &Meta,
    ) -> Result<()> {
        let temp_dir = tempfile::tempdir()?;
        let temp_path = temp_dir.path();

        copy_into_tempdir(&id, temp_path).await?;

        tokio::fs::rename(
            temp_path.join(get_document_basename(&meta.id, &meta.extension)),
            temp_path.join("in.md"),
        ).await?;

        execute_command("pandoc", vec!["in.md", "-o", "out.html", "-s"], Some(temp_path)).await?;

        copy_into_cache(
            connection,
            id,
            hash,
            temp_path.join("out.html"),
            RenderType::Html,
        ).await?;

        drop(temp_dir);

        Ok(())
    }
}

struct LaTeXRenderer;

#[async_trait]
impl Renderer for LaTeXRenderer {
    async fn render(
        &self,
        id: Uuid,
        hash: Hash,
        connection: &mut SqliteConnection,
        meta: &Meta,
    ) -> Result<()> {
        let temp_dir = tempfile::tempdir()?;
        let temp_path = temp_dir.path();

        copy_into_tempdir(&id, temp_path).await?;

        tokio::fs::rename(
            temp_path.join(get_document_basename(&meta.id, &meta.extension)),
            temp_path.join("in.tex"),
        ).await?;

        execute_command("pdflatex", vec!["-draftmode", "--interaction=nonstopmode", "-halt-on-error", "in.tex"], Some(temp_path)).await?;

        execute_command("pdflatex", vec!["-halt-on-error", "--interaction=nonstopmode", "in.tex"], Some(temp_path)).await?;

        copy_into_cache(
            connection,
            id,
            hash,
            temp_path.join("in.pdf"),
            RenderType::Pdf,
        ).await?;

        drop(temp_dir);

        Ok(())
    }
}

struct XournalPPRenderer;

#[async_trait]
impl Renderer for XournalPPRenderer {
    async fn render(
        &self,
        id: Uuid,
        hash: Hash,
        connection: &mut SqliteConnection,
        meta: &Meta,
    ) -> Result<()> {
        let temp_dir = tempfile::tempdir()?;
        let temp_path = temp_dir.path();

        copy_into_tempdir(&id, temp_path).await?;

        tokio::fs::rename(
            temp_path.join(get_document_basename(&meta.id, &meta.extension)),
            temp_path.join("in.xopp"),
        ).await?;

        // if !tokio::process::Command::new("sh")
        //     .arg("-c")
        //     .arg(format!("gunzip -c -S .xopp in.xopp |sed -r -e 's/filename=\".*\\/{}\\/(.*)\" /filename=\"\\1\" /g'|gzip>tmp.xopp", id))
        //     .current_dir(temp_path)
        //     .spawn()?
        //     .wait()
        //     .await?
        //     .success()
        // {
        //
        //     return Err(eyre!("xopp export failed"));
        // }

        execute_command("xournalpp", vec!["-p", "out.pdf", "in.xopp"], Some(temp_path)).await?;

        copy_into_cache(
            connection,
            id,
            hash,
            temp_path.join("out.pdf"),
            RenderType::Pdf,
        ).await?;

        Ok(())
    }
}
