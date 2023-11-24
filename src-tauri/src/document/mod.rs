use std::collections::HashMap;
use std::str::FromStr;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

use chrono::NaiveDateTime;
use eyre::eyre;
use eyre::Result;
use mac_address::get_mac_address;
use sqlx::{
    query,
    Row,
    SqlitePool,
};
use tokio::process::Command;
use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use uuid::Uuid;

use crate::directories::get_cache_directory;
use crate::directories::get_filespider_directory;
use crate::document::render::Hash;
use crate::types::*;

pub mod commands;
pub mod render;

#[cfg(test)]
mod tests;

async fn document_exists(id: &Uuid) -> Result<()> {
    match tokio::fs::try_exists(get_document_directory(id)?).await {
        Ok(true) => Ok(()),
        _ => Err(eyre!("document does not exist")),
    }
}

fn get_document_directory(id: &Uuid) -> Result<String> {
    Ok(format!("{}/{}", get_filespider_directory()?, id))
}

fn get_document_basename(id: &Uuid, extension: &Option<String>) -> String {
    match extension {
        Some(s) => format!("{}.{}", id, s),
        None => id.to_string(),
    }
}

fn get_document_file(id: &Uuid, extension: &Option<String>) -> Result<String> {
    Ok(format!(
        "{}/{}",
        get_document_directory(id)?,
        get_document_basename(id, extension)
    ))
}

fn get_cache_file(id: Uuid) -> Result<String> {
    Ok(format!("{}/{}", get_cache_directory()?, id))
}

/// page starts at 0
pub async fn search(
    pool: &SqlitePool,
    pos_filter: Vec<String>,
    neg_filter: Vec<String>,
    crib: String,
    page: u32,
    page_length: u32,
    sort: SearchSorting,
) -> Result<Vec<Meta>> {
// TODO sort
    let query_str = format!(
        "select id from Document left join (select document, count(tag) as tagCount from Tag where tag in {} group by document) as posTags on posTags.document = Document.id left join (select document, count(tag) as tagCount from Tag where tag in {} group by document) as negTags on negTags.document = document.id where {} and (negTags.tagCount = 0 or negTags.tagCount is null) and Document.title like ?  order by {} {} limit ?, ?",
        if pos_filter.is_empty() { "()".to_string() } else { format!("(?{})", ", ?".repeat(pos_filter.len() - 1)) },
        if neg_filter.is_empty() { "()".to_string() } else { format!("(?{})", ", ?".repeat(neg_filter.len() - 1)) },
        if pos_filter.is_empty() { "(posTags.tagCount = ? or posTags.tagCount is null)" } else { "posTags.tagCount = ?" },
        match sort.0 { SearchSortCriterium::CreationTime => "Document.added" },
        if sort.1 { "asc" } else { "desc" }
    );

    let mut query = sqlx::query(&query_str);

    for pos_tag in pos_filter.iter() {
        query = query.bind(pos_tag);
    }
    for neg_tag in neg_filter.iter() {
        query = query.bind(neg_tag);
    }

    query = query.bind(pos_filter.len() as u32).bind(format!("%{}%", crib)).bind(page * page_length).bind(page_length);

    let docs: Vec<Uuid> = query.map(|x| x.get("id")).fetch_all(pool).await?;

    futures::future::join_all(docs.into_iter().map(|id| get_meta(pool, id))).await.into_iter().collect()
}

pub async fn create(
    pool: &SqlitePool,
    title: String,
    doc_type: Option<DocType>,
    tags: Vec<String>,
    extension: Option<String>,
    file: Option<String>,
) -> Result<Uuid> {
    let id: Uuid = Uuid::now_v1(&get_mac_address()?.map(|x| x.bytes()).unwrap_or([0x69u8; 6]));

    tokio::fs::create_dir(get_document_directory(&id)?).await?;

    match file {
        Some(path) => tokio::fs::copy(
            path,
            get_document_file(&id, &extension)?,
        ).await.map(|_| ()),
        None => {
            tokio::fs::write(
                get_document_file(&id, &extension)?,
                [0u8; 0],
            ).await
        }
    }?;

    let doc_type_str = doc_type.unwrap_or(DocType::Plain).to_string();
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u32;
    query!(
        "insert into Document (id, title, type, added, file_extension) values (?, ?, ?, ?, ?)",
        id,
        title,
        doc_type_str,
        timestamp,
        extension
    ).execute(pool).await?;

    for tag in tags {
        query!("insert into Tag (document, tag) values (?, ?)", id, tag).execute(pool).await?;
    }
    Ok(id)
}

pub async fn get_meta(pool: &SqlitePool, id: Uuid) -> Result<Meta> {
    document_exists(&id).await?;

    let doc_res = query!(
        "select title, type, added, file_extension from Document where id = ?",
        id
    ).fetch_one(pool).await?;

    let tags = query!("select tag from Tag where document = ?", id).map(|x| x.tag).fetch_all(pool).await?;

    Ok(Meta {
        title: doc_res.title,
        doc_type: DocType::from_str(&doc_res.r#type)?,
        tags,
        created: NaiveDateTime::from_timestamp_millis(doc_res.added).unwrap(),
        accessed: NaiveDateTime::from_timestamp_millis(
            tokio::fs::metadata(get_document_file(&id, &doc_res.file_extension)?).await?.accessed()?.duration_since(UNIX_EPOCH)?.as_millis().try_into().unwrap(),
        ).unwrap(),
        id,
        extension: doc_res.file_extension,
    })
}

/// returns Ok(false) if editor is already running, if editor got spawned it returns Ok(true)
pub async fn open_editor(
    pool: &SqlitePool,
    editors: &mut HashMap<Uuid, tokio::process::Child>,
    id: Uuid,
) -> Result<bool> {
    document_exists(&id).await?;

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
        Command::new(meta.doc_type.get_editor().0).args(meta.doc_type.get_editor().1).arg(get_document_file(&meta.id, &meta.extension)?).spawn()?,
    );

    Ok(true)
}

pub async fn patch_meta(pool: &SqlitePool, id: Uuid, patch: MetaPatch) -> Result<()> {
    document_exists(&id).await?;

    match patch {
        MetaPatch::ChangeTitle(title) => {
            match query!("update Document set title = ? where id = ?", title, id).execute(pool).await?.rows_affected() {
                1 => Ok(()),
                _ => Err(eyre!("Wrong number of rows affected")),
            }
        }
        MetaPatch::AddTag(tag) => {
            match query!("insert into Tag (document, tag) values (?, ?)", id, tag).execute(pool).await?.rows_affected() {
                0 => Err(eyre!("document already has tag")),
                1 => Ok(()),
                _ => panic!(),
            }
        }
        MetaPatch::RemoveTag(tag) => {
            match query!("delete from Tag where tag = ? and document = ?", tag, id).execute(pool).await?.rows_affected() {
                1 => Ok(()),
                0 => Err(eyre!("failed to delete tag")),
                _ => panic!(),
            }
        }
    }
}

pub async fn delete(pool: &SqlitePool, id: Uuid) -> Result<()> {
    document_exists(&id).await?;

    if query!(
        "delete from Document where id = ?; delete from Tag where document = ?",
        id,
        id
    ).execute(pool).await?.rows_affected() == 0 {
        return Err(eyre!("no rows affected"));
    }

    tokio::fs::remove_dir_all(get_document_directory(&id)?).await?;

    Ok(())
}

pub async fn get_tags(pool: &SqlitePool, crib: String) -> Result<Vec<String>> {
    Ok(query!(
        "select distinct tag from Tag where tag like '%' || ? || '%'",
        crib
    ).map(|x| x.tag).fetch_all(pool).await?)
}

pub async fn show_render_in_explorer(
    pool: &SqlitePool,
    renderers: &mut HashMap<(Uuid, Hash), Mutex<JoinHandle<()>>>,
    id: Uuid,
) -> Result<()> {
    document_exists(&id).await?;

    // let meta = get_meta(pool, id).await?;

    let render = render::render(pool, renderers, id).await?;

    #[cfg(target_os = "linux")]
    {
        use dbus_tokio::connection;
        use dbus::nonblock;
        use std::time::Duration;

        let (resource, conn) = connection::new_session_sync()?;

        let _handle = tokio::spawn(async {
            let err = resource.await;
            panic!("Lost connection to D-Bus: {}", err);
        });

        let proxy = nonblock::Proxy::new("org.freedesktop.FileManager1", "/org/freedesktop/FileManager1", Duration::from_secs(2), conn);

        proxy.method_call("org.freedesktop.FileManager1",
                          "ShowItems",
                          (vec![format!("file://{}", render.0)], ""),
        ).await?;
    }

    #[cfg(target_os = "windows")]
    unsafe {
        use windows::Win32::{Foundation::PCSTR, UI::Shell::{shellExecuteA, SW_SHOW}};
        ShellExecuteA(
            None,
            PCSTR::null(),
            PCSTR::from_raw(&render.0.as_bytes()),
            PCSTR::null(),
            PCSTR::null(),
            SW_SHOW,
        );
    }

    #[cfg(target_os = "macos")]
    {
        Command::new("open").args(vec!["-R", render.0]).spawn()?;
    }

    Ok(())
}