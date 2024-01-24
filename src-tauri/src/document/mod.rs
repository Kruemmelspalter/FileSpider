use std::collections::HashMap;
use std::io::Write;
use std::str::FromStr;
#[cfg(target_os = "linux")]
use std::sync::Arc;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

use base64::prelude::*;
use chrono::NaiveDateTime;
use eyre::eyre;
use eyre::Result;
use flate2::Compression;
use flate2::write::GzEncoder;
use mac_address::get_mac_address;
use pdf::file::FileOptions;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use sqlx::{query, Row, SqlitePool};
use sqlx::sqlite::SqliteRow;
use tokio::process::Command;
use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use uuid::Uuid;

use crate::directories::get_cache_directory;
use crate::directories::get_filespider_directory;
use crate::document::render::Hash;
use crate::settings::Settings;
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

pub fn get_document_directory(id: &Uuid) -> Result<String> {
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
    use SearchSortCriterium::*;

    let query_str = format!(
        "select id from Document left join (select document, count(tag) as tagCount from Tag where tag in {} group by document) as posTags on posTags.document = Document.id left join (select document, count(tag) as tagCount from Tag where tag in {} group by document) as negTags on negTags.document = document.id where {} and (negTags.tagCount = 0 or negTags.tagCount is null) and Document.title like ?  order by {} {} limit ?, ?",
        if pos_filter.is_empty() { "()".to_string() } else { format!("(?{})", ", ?".repeat(pos_filter.len() - 1)) },
        if neg_filter.is_empty() { "()".to_string() } else { format!("(?{})", ", ?".repeat(neg_filter.len() - 1)) },
        if pos_filter.is_empty() { "(posTags.tagCount = ? or posTags.tagCount is null)" } else { "posTags.tagCount = ?" },
        match sort.0 {
            CreationTime => "Document.added",
            AccessTime => "Document.accessed",
            Title => "Document.title",
        },
        if sort.1 { "asc" } else { "desc" }
    );

    let mut query = sqlx::query(&query_str);

    for pos_tag in pos_filter.iter() {
        query = query.bind(pos_tag);
    }
    for neg_tag in neg_filter.iter() {
        query = query.bind(neg_tag);
    }

    query = query
        .bind(pos_filter.len() as u32)
        .bind(format!("%{}%", crib))
        .bind(page * page_length)
        .bind(page_length);

    let docs: Vec<Uuid> = query.map(|x: SqliteRow| x.get("id")).fetch_all(pool).await?;

    futures::future::join_all(docs.into_iter().map(|id| get_meta(pool, id)))
        .await
        .into_iter()
        .collect()
}

fn as_base64<T, S>(key: &T, serializer: S) -> Result<<S as Serializer>::Ok, S::Error>
    where T: AsRef<[u8]>,
          S: Serializer
{
    serializer.serialize_str(&BASE64_STANDARD.encode(key.as_ref()))
}

fn from_base64<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
    where D: Deserializer<'de>
{
    let s = String::deserialize(deserializer)?;
    BASE64_STANDARD.decode(s.as_bytes()).map_err(serde::de::Error::custom)
}

#[derive(Serialize, Deserialize, Clone)]
pub enum File {
    None,
    Path(String),
    #[serde(serialize_with = "as_base64", deserialize_with = "from_base64")]
    Blob(Vec<u8>),
}

pub async fn create(
    pool: &SqlitePool,
    title: String,
    doc_type: Option<DocType>,
    tags: Vec<String>,
    extension: Option<String>,
    file: File,
) -> Result<Uuid> {
    let id: Uuid = Uuid::now_v1(&get_mac_address()?.map(|x| x.bytes()).unwrap_or([0x69u8; 6]));

    tokio::fs::create_dir(get_document_directory(&id)?).await?;

    match file {
        File::Path(path) => tokio::fs::copy(path, get_document_file(&id, &extension)?)
            .await
            .map(|_| ()),
        File::Blob(b) => tokio::fs::write(get_document_file(&id, &extension)?, b).await,
        File::None => tokio::fs::write(get_document_file(&id, &extension)?, [0u8; 0]).await,
    }?;

    let doc_type_str = doc_type.unwrap_or(DocType::Plain).to_string();
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u32;
    query!(
        "insert into Document (id, title, type, added, file_extension, accessed) values (?, ?, ?, ?, ?, ?)",
        id,
        title,
        doc_type_str,
        timestamp,
        extension,
        timestamp,
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

pub async fn import_pdf(
    pool: &SqlitePool,
    title: String,
    tags: Vec<String>,
    file: String,
) -> Result<Uuid> {
    let pdf = FileOptions::cached().open(&file)?;

    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());

    encoder.write_all(b"<xournal fileversion=\"4\">")?;

    for (i, page) in pdf.pages().enumerate() {
        let page = page?;
        let crop_box = page.crop_box()?;

        let w = if page.rotate == 0 || page.rotate == 180 {
            crop_box.right - crop_box.left
        } else {
            crop_box.top - crop_box.bottom
        }
            .abs();
        let h = if page.rotate == 0 || page.rotate == 180 {
            crop_box.top - crop_box.bottom
        } else {
            crop_box.right - crop_box.left
        }
            .abs();

        encoder.write_all(format!("<page width=\"{w}\" height=\"{h}\"><background type=\"pdf\" pageno=\"{}\" {}/><layer/></page>",
                                  i + 1, if i == 0 { "domain=\"absolute\" filename=\"bg.pdf\"" } else { "" },
        ).as_bytes())?;
    }

    encoder.write_all(b"</xournal>")?;

    let xopp_contents = encoder.finish()?;

    let extension = Some("xopp".to_string());

    let id: Uuid = Uuid::now_v1(&get_mac_address()?.map(|x| x.bytes()).unwrap_or([0x69u8; 6]));
    tokio::fs::create_dir(get_document_directory(&id)?).await?;

    tokio::fs::copy(
        file,
        format!("{}/{}", get_document_directory(&id)?, "bg.pdf"),
    )
        .await?;

    tokio::fs::write(get_document_file(&id, &extension)?, xopp_contents).await?;

    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u32;
    let doc_type = DocType::XournalPP.to_string();
    query!(
        "insert into Document (id, title, type, added, file_extension, accessed) values (?, ?, ?, ?, ?, ?)",
        id,
        title,
        doc_type,
        timestamp,
        extension,
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
    document_exists(&id).await?;

    let doc_res = query!(
        "select title, type, added, file_extension, accessed from Document where id = ?",
        id
    )
        .fetch_one(pool)
        .await?;

    let tags = query!("select tag from Tag where document = ?", id)
        .map(|x| x.tag)
        .fetch_all(pool)
        .await?;

    Ok(Meta {
        title: doc_res.title,
        doc_type: DocType::from_str(&doc_res.r#type)?,
        tags,
        created: doc_res.added,
        accessed: doc_res.accessed,
        id,
        extension: doc_res.file_extension,
    })
}

/// returns Ok(false) if editor is already running, if editor got spawned it returns Ok(true)
pub async fn open_editor(
    pool: &SqlitePool,
    settings: &Settings,
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
        Command::new(meta.doc_type.get_editor(settings).0)
            .args(meta.doc_type.get_editor(settings).1.iter().map(|s| s.replace("%FILE%", &get_document_file(&meta.id, &meta.extension).unwrap())).collect::<Vec<_>>())
            .spawn()?,
    );

    Ok(true)
}

pub async fn patch_meta(pool: &SqlitePool, id: Uuid, patch: MetaPatch) -> Result<()> {
    document_exists(&id).await?;

    match patch {
        MetaPatch::ChangeTitle(title) => {
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
                1 => Ok(()),
                _ => panic!(),
            }
        }
        MetaPatch::RemoveTag(tag) => {
            match query!("delete from Tag where tag = ? and document = ?", tag, id)
                .execute(pool)
                .await?
                .rows_affected()
            {
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
    )
        .execute(pool)
        .await?
        .rows_affected()
        == 0
    {
        return Err(eyre!("no rows affected"));
    }

    tokio::fs::remove_dir_all(get_document_directory(&id)?).await?;

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

pub async fn show_render_in_explorer(
    pool: &SqlitePool,
    renderers: &mut HashMap<(Uuid, Hash), Mutex<JoinHandle<()>>>,
    id: Uuid,
    #[cfg(target_os = "linux")] dbus: Arc<dbus::nonblock::SyncConnection>,
) -> Result<()> {
    document_exists(&id).await?;

    let render = render::render(pool, renderers, id).await?;

    #[cfg(target_os = "linux")]
    {
        use std::time::Duration;

        let proxy = dbus::nonblock::Proxy::new(
            "org.freedesktop.FileManager1",
            "/org/freedesktop/FileManager1",
            Duration::from_secs(5),
            dbus,
        );

        proxy
            .method_call(
                "org.freedesktop.FileManager1",
                "ShowItems",
                (vec![format!("file://{}", render.0)], ""),
            )
            .await?;
    }

    #[cfg(target_os = "windows")]
    unsafe {
        use windows::{
            core::{PCSTR, s},
            Win32::UI::{Shell::ShellExecuteA, WindowsAndMessaging::SW_SHOW},
        };

        let encoded = render
            .0
            .as_bytes()
            .into_iter()
            .map(|v| *v)
            .chain([0u8])
            .collect::<Vec<u8>>();

        ShellExecuteA(
            None,
            s!("explore"),
            PCSTR(encoded.as_ptr()),
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

pub async fn update_accessed(pool: &SqlitePool, id: Uuid) -> Result<()> {
    document_exists(&id).await?;

    let time = match NaiveDateTime::from_timestamp_millis(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as i64) {
        Some(t) => t,
        None => return Err(eyre!("failed to get time")),
    };

    query!(
        "update Document set accessed = ? where id = ?",
        time,
        id
    )
        .execute(pool)
        .await?;

    Ok(())
}