pub mod commands;
pub use commands::plugin;
pub mod render;

use chrono::{DateTime, FixedOffset, NaiveDateTime};
use eyre::eyre;
use eyre::Result;
use filespider_common::{DocType, Meta, MetaPatch, Render};
use mac_address::get_mac_address;
use sqlx::query;
use sqlx::Row;
use sqlx::SqlitePool;
use std::collections::HashMap;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::process;
use tokio::process::Command;
use tokio::task::JoinHandle;
use uuid::Uuid;

use crate::directories::get_filespider_directory;

async fn document_exists(id: Uuid) -> Result<()> {
    let path = format!("{}/{}/{}", get_filespider_directory()?, id, id);
    match tokio::fs::try_exists(path).await? {
        true => Err(eyre!("document does not exist")),
        false => Ok(()),
    }
}

pub async fn search(
    pool: &SqlitePool,
    pos_filter: Vec<String>,
    neg_filter: Vec<String>,
    crib: String,
    page: u32,
    page_length: u32,
) -> Result<Vec<Meta>> {
    let query_str = format!(
        "select id from Document left join (select document, count(tag) as tagCount from Tag where tag in {} group by document) as posTags on posTags.document = Document.id left join (select document, count(tag) as tagCount from Tag where tag in {} group by document) as negTags on negTags.document = document.id where {} and (negTags.tagCount = 0 or negTags.tagCount is null) and document.title like '%' || ? || '%' limit ? offset ?",
        if pos_filter.len() == 0 {"()".to_string()} else {format!("(?{})",", ?".repeat(pos_filter.len()-1))},
        if neg_filter.len() == 0 {"()".to_string()} else {format!("(?{})",", ?".repeat(neg_filter.len()-1))},
        if pos_filter.len() == 0 {"(posTags.tagCount = ? or posTags.tagCount is null)"} else {"posTags.tagCount = ?"}
    );

    let mut query = sqlx::query(&query_str);

    for pos_tag in pos_filter.iter() {
        query = query.bind(pos_tag);
    }
    for neg_tag in neg_filter.iter() {
        query = query.bind(neg_tag);
    }

    query = query.bind(page_length).bind((page - 1) * page_length);

    let docs = query
        .bind(pos_filter.len() as u32)
        .bind(crib)
        .map(|x| x.get("id"))
        .fetch_all(pool)
        .await?;

    todo!()
}

pub async fn create(
    pool: &SqlitePool,
    title: String,
    doc_type: Option<DocType>,
    tags: Vec<String>,
    file: Option<String>,
) -> Result<Uuid> {
    let id: Uuid = Uuid::now_v1(&get_mac_address()?.map(|x| x.bytes()).unwrap_or([0u8; 6]));

    tokio::fs::create_dir(format!("{}/{}", get_filespider_directory()?, id)).await?;

    match file {
        Some(path) => tokio::fs::copy(
            path,
            format!("{}/{}/{}", get_filespider_directory()?, id, id),
        )
        .await
        .map(|_| ()),
        None => {
            tokio::fs::write(
                format!("{}/{}/{}", get_filespider_directory()?, id, id),
                [0u8; 0],
            )
            .await
        }
    }?;

    let doc_type_str = doc_type.unwrap_or(DocType::Plain).to_string();
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs() as u32;
    query!(
        "insert into Document (id, title, type, added) values (?, ?, ?, ?)",
        id,
        title,
        doc_type_str,
        timestamp
    )
    .execute(pool)
    .await?;

    for tag in tags {
        query!("insert into Tag (document, tag) values (?, ?)", id, tag)
            .execute(pool)
            .await?;
    }
    Ok(id)
}

pub async fn get_meta(pool: &SqlitePool, id: Uuid) -> Result<Meta> {
    document_exists(id).await?;

    let doc_res = query!("select title, type, added from Document where id = ?", id)
        .fetch_one(pool)
        .await?;

    let tags = query!("select tag from Tag where document = ?", id)
        .map(|x| x.tag)
        .fetch_all(pool)
        .await?;

    Ok(Meta {
        title: doc_res.title,
        doc_type: DocType::from_string(doc_res.r#type)?,
        tags,
        created: DateTime::from_local(
            NaiveDateTime::from_timestamp_opt(doc_res.added, 0)
                .unwrap_or(NaiveDateTime::from_timestamp_opt(0, 0).unwrap()),
            FixedOffset::east_opt(0).unwrap(),
        ),
        accessed: tokio::fs::metadata(format!("{}/{}/{}", get_filespider_directory()?, id, id))
            .await?
            .accessed()?
            .into(),
        id,
    })
}

pub async fn render(
    pool: &SqlitePool,
    renderers: &mut HashMap<Uuid, JoinHandle<()>>,
    id: Uuid,
) -> Result<Render> {
    document_exists(id).await?;
    let meta = get_meta(pool, id).await?;

    todo!()
}

/// returns Ok(false) if editor is already running, if editor got spawned it returns Ok(true)
pub async fn open_editor(
    pool: &SqlitePool,
    editors: &mut HashMap<Uuid, process::Child>,
    id: Uuid,
) -> Result<bool> {
    document_exists(id).await?;

    if let Some(editor) = editors.get_mut(&id) {
        if let Ok(Some(_)) = editor.try_wait() {
            editors.remove(&id);
        } else {
            return Ok(false);
        }
    }

    let meta = get_meta(pool, id).await?;

    editors.insert(
        id,
        Command::new(meta.doc_type.get_editor())
            .arg(format!("{}/{}/{}", get_filespider_directory()?, id, id))
            .spawn()?,
    );

    Ok(true)
}

pub async fn alter_meta(pool: &SqlitePool, id: Uuid, patch: MetaPatch) -> Result<()> {
    document_exists(id).await?;

    match patch {
        MetaPatch::Title(title) => {
            match query!("update Document set title = ? where id = ?", title, id)
                .execute(pool)
                .await?
                .rows_affected()
            {
                1 => Ok(()),
                _ => Err(eyre!("Wrong number of rows affected")),
            }
        }
        MetaPatch::AddTag(tag) => {
            match query!("insert into Tag (document, tag) values (?, ?)", id, tag)
                .execute(pool)
                .await?
                .rows_affected()
            {
                0 => Err(eyre!("document already has tag")),
                _ => Ok(()),
            }
        }
        MetaPatch::RemoveTag(tag) => {
            match query!("delete from Tag where tag = ? and document = ?", tag, id)
                .execute(pool)
                .await?
                .rows_affected()
            {
                1 => Ok(()),
                _ => Err(eyre!("failed to delete tag")),
            }
        }
    }
}

pub async fn delete(pool: &SqlitePool, id: Uuid) -> Result<()> {
    document_exists(id).await?;

    if query!(
        "delete from Document where id = ?; delete from Tag where document = ?",
        id,
        id
    )
    .execute(pool)
    .await?
    .rows_affected()
        == 0
    {
        return Err(eyre!("no rows affected"));
    }

    tokio::fs::remove_dir_all(format!("{}/{}", get_filespider_directory()?, id)).await?;

    Ok(())
}

pub async fn get_tags(pool: &SqlitePool, crib: String) -> Result<Vec<String>> {
    Ok(query!(
        "select distinct tag from Tag where tag like '%' || ? || '%'",
        crib
    )
    .map(|x| x.tag)
    .fetch_all(pool)
    .await?)
}
