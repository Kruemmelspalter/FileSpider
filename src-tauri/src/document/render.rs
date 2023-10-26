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
use fasthash::{FastHasher, SpookyHasher};
use sqlx::{query, SqliteConnection, SqlitePool};
use tokio::{sync::Mutex, task::JoinHandle};
use uuid::Uuid;

use crate::{
    document,
    types::{DocType, Meta, RenderType},
};

use super::{get_cache_file, get_document_basename, get_document_directory, get_document_file};

pub type Hash = u64;

pub async fn hash_document_files(id: Uuid) -> Result<Hash> {
    document::document_exists(id).await?;

    let mut hasher = fasthash::SpookyHasher::new();
    hash_file(
        &mut hasher,
        PathBuf::from(document::get_document_directory(id)?),
    )
    .await?;
    Ok(hasher.finish())
}

#[async_recursion]
async fn hash_file(hasher: &mut SpookyHasher, path: PathBuf) -> Result<()> {
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
    document::document_exists(id).await?;
    let hash = hash_document_files(id).await?;
    let hex_hash = format!("{:128x}", hash);

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
            )
            .map(|r| RenderType::from_str(r.render_type.as_str()))
            .fetch_one(pool)
            .await??,
        );
    }

    // check cache

    if let Some(render_type) = query!(
        "select render_type from Cache where document = ? and hash = unhex(?)",
        id,
        hex_hash
    )
    .map(|r| r.render_type)
    .fetch_optional(pool)
    .await?
    {
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
        )
        .map(|r| RenderType::from_str(r.render_type.as_str()))
        .fetch_one(pool)
        .await??,
    )
}

fn get_from_cache(id: Uuid, render_type: RenderType) -> Result<(String, RenderType)> {
    Ok((get_cache_file(id)?, render_type))
}

async fn render_task(meta: Meta, hash: Hash, mut connection: SqliteConnection) {
    let renderer = get_renderer_from_doc_type(&meta.doc_type);
    match renderer.render(meta.id, hash, &mut connection, &meta).await {
        Ok(_) => {}
        Err(e) => {
            tokio::fs::write(get_cache_file(meta.id).unwrap(), e.to_string())
                .await
                .unwrap();
            insert_into_cache(&mut connection, meta.id, hash, RenderType::Plain)
                .await
                .unwrap()
        }
    };
}

async fn insert_into_cache<'a>(
    connection: &mut SqliteConnection,
    id: Uuid,
    hash: Hash,
    render_type: RenderType,
) -> Result<()> {
    let hex_hash = format!("{:128x}", hash);
    let render_str = render_type.to_string();

    query!(
        "insert into Cache (document, hash, render_type) values (?, unhex(?), ?) on conflict(document) do update set hash = unhex(?), render_type = ?",
        id,
        hex_hash,
        render_str,
        hex_hash,
        render_str
    )
    .execute(connection).await?;
    Ok(())
}

async fn copy_into_cache<'a>(
    connection: &mut SqliteConnection,
    id: Uuid,
    hash: Hash,
    path: impl AsRef<Path>,
    render_type: RenderType,
) -> Result<()> {
    tokio::fs::copy(path, get_cache_file(id)?).await?;
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
            get_document_file(meta)?,
            RenderType::Plain,
        )
        .await?;
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
        let tempdir = tempfile::tempdir()?;
        let temppath = tempdir.path();

        tokio::fs::copy(get_document_directory(id)?, temppath).await?; // TODO does this work

        tokio::fs::rename(
            temppath.join(get_document_basename(meta)),
            temppath.join("in.md"),
        )
        .await?;

        if !tokio::process::Command::new("pandoc")
            .args(vec!["in.md", "-o", "out.html", "-s"])
            .current_dir(temppath)
            .spawn()?
            .wait()
            .await?
            .success()
        {
            return Err(eyre!("pandoc failed"));
        }

        copy_into_cache(
            connection,
            id,
            hash,
            temppath.join("out.html"),
            RenderType::Html,
        )
        .await?;

        drop(tempdir);

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
        let tempdir = tempfile::tempdir()?;
        let temppath = tempdir.path();

        tokio::fs::copy(get_document_directory(id)?, temppath).await?; // TODO does this work

        tokio::fs::rename(
            temppath.join(get_document_basename(meta)),
            temppath.join("in.tex"),
        )
        .await?;

        if !tokio::process::Command::new("pdflatex")
            .args(vec!["-draftmode", "-halt-on-error", "in.tex"])
            .current_dir(temppath)
            .spawn()?
            .wait()
            .await?
            .success()
        {
            return Err(eyre!("draftmode latex failed"));
        }

        if !tokio::process::Command::new("pdflatex")
            .args(vec!["-halt-on-error", "in.tex"])
            .current_dir(temppath)
            .spawn()?
            .wait()
            .await?
            .success()
        {
            return Err(eyre!("render latex failed"));
        }

        copy_into_cache(
            connection,
            id,
            hash,
            temppath.join("in.pdf"),
            RenderType::Pdf,
        )
        .await?;

        drop(tempdir);

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
        let tempdir = tempfile::tempdir()?;
        let temppath = tempdir.path();

        tokio::fs::copy(get_document_directory(id)?, temppath).await?; // TODO does this work

        tokio::fs::rename(
            temppath.join(get_document_basename(meta)),
            temppath.join("in.xopp"),
        )
        .await?;

        // if !tokio::process::Command::new("sh")
        //     .arg("-c")
        //     .arg(format!("gunzip -c -S .xopp in.xopp |sed -r -e 's/filename=\".*\\/{}\\/(.*)\" /filename=\"\\1\" /g'|gzip>tmp.xopp", id))
        //     .current_dir(temppath)
        //     .spawn()?
        //     .wait()
        //     .await?
        //     .success()
        // {
        //     return Err(eyre!("xopp export failed"));
        // }

        if !tokio::process::Command::new("xournalpp")
            .args(vec!["-p", "out.pdf", "in.xopp"])
            .current_dir(temppath)
            .spawn()?
            .wait()
            .await?
            .success()
        {
            return Err(eyre!("xopp export failed"));
        }

        copy_into_cache(
            connection,
            id,
            hash,
            temppath.join("out.pdf"),
            RenderType::Pdf,
        )
        .await?;

        Ok(())
    }
}
