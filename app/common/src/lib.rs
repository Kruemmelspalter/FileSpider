use chrono::offset::Local;
use chrono::DateTime;
use eyre::eyre;
use eyre::Result;
use serde::{Deserialize, Serialize};
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

#[derive(Serialize, Deserialize)]
pub enum MetaPatch {
    Title(String),
    AddTag(String),
    RemoveTag(String),
}

#[derive(Serialize, Deserialize)]
pub struct Render {}

#[derive(Serialize, Deserialize)]
pub enum DocType {
    Html,
    Plain,
    PDF,
    Markdown,
    XournalPP,
    LaTeX,
}

impl DocType {
    pub fn from_str(str: &str) -> Result<Self> {
        Ok(match str {
            "html" => Self::Html,
            "plain" => Self::Plain,
            "pdf" => Self::PDF,
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
            DocType::Html => "html",
            DocType::Plain => "plain",
            DocType::PDF => "pdf",
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
            DocType::Html => "kate",
            DocType::Plain => "kate",
            DocType::PDF => "okular",
            DocType::Markdown => "kate",
            DocType::XournalPP => "xournalpp",
            DocType::LaTeX => "kate",
        }
        .to_string()
    }
}
