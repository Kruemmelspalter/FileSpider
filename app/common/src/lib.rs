use chrono::offset::Local;
use chrono::DateTime;
use chrono::FixedOffset;
use chrono::NaiveDateTime;
use eyre::eyre;
use eyre::Result;
use serde::{Deserialize, Serialize};
use sqlx::Database;
use uuid::Uuid;

#[derive(Serialize, Deserialize)]
pub struct Meta {
    pub title: String,
    pub doc_type: DocType,
    pub tags: Vec<String>,
    pub created: DateTime<Local>,
    pub accessed: DateTime<Local>,
    pub id: Uuid,
}

impl Meta {
    pub async fn from_sqlx_row<DB>(row: <DB as Database>::Row) -> Result<Self>
    where
        DB: Database,
    {
        Self {
            title: row.get("title"),
            doc_type: DocType::from_string(row.get("type"))?,
            tags: vec![],
            created: DateTime::from_local(
                NaiveDateTime::from_timestamp_opt(row.get("added"), 0)
                    .unwrap_or(NaiveDateTime::from_timestamp_opt(0, 0).unwrap()),
                FixedOffset::east_opt(0).unwrap(),
            ),
            accessed: tokio::fs::metadata(format!("{}/{}/{}", get_filespider_directory()?, id, id))
                .await?
                .accessed()?
                .into(),
            id: row.get("id"),
        };
        todo!()
    }
}

#[derive(Serialize, Deserialize)]
pub enum MetaPatch {
    Title(String),
    AddTag(String),
    RemoveTag(String),
}

#[derive(Serialize, Deserialize)]
pub struct Render {
    pub source: RenderSource,
    pub render_type: RenderType,
}

#[derive(Serialize, Deserialize)]
pub enum RenderSource {
    File(String),
    Buffer(Vec<u8>),
}

#[derive(Serialize, Deserialize)]
pub enum RenderType {
    Plain,
    Html,
    PDF,
}

#[derive(Serialize, Deserialize)]
pub enum DocType {
    Plain,
    Markdown,
    XournalPP,
    LaTeX,
}

impl DocType {
    pub fn from_str(str: &str) -> Result<Self> {
        Ok(match str {
            "plain" => Self::Plain,
            "markdown" | "md" => Self::Markdown,
            "xournalpp" | "xournal" | "xopp" => Self::XournalPP,
            "latex" | "tex" => Self::LaTeX,
            _ => return Err(eyre!("invalid doctype")),
        })
    }

    pub fn from_string(str: String) -> Result<Self> {
        Self::from_str(&str)
    }
    pub fn to_str(&self) -> &str {
        match self {
            DocType::Plain => "plain",
            DocType::Markdown => "md",
            DocType::XournalPP => "xopp",
            DocType::LaTeX => "tex",
        }
    }
    pub fn to_string(&self) -> String {
        self.to_str().to_string()
    }

    pub fn get_editor(&self) -> String {
        match self {
            DocType::Plain => "kate",
            DocType::Markdown => "kate",
            DocType::XournalPP => "xournalpp",
            DocType::LaTeX => "kate",
        }
        .to_string()
    }
}
