use std::str::FromStr;

use chrono::DateTime;
use chrono::{offset::Local, NaiveDateTime};
use eyre::eyre;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Serialize, Deserialize)]
pub struct Meta {
    pub title: String,
    pub doc_type: DocType,
    pub tags: Vec<String>,
    pub created: NaiveDateTime,
    pub accessed: NaiveDateTime,
    pub id: Uuid,
}

#[derive(Serialize, Deserialize)]
pub enum MetaPatch {
    ChangeTitle(String),
    AddTag(String),
    RemoveTag(String),
}

#[derive(Serialize, Deserialize)]
pub enum RenderType {
    Plain,
    Html,
    Pdf,
}

#[derive(Serialize, Deserialize)]
pub enum DocType {
    Plain,
    Markdown,
    XournalPP,
    LaTeX,
}

impl FromStr for DocType {
    type Err = eyre::Report;

    fn from_str(s: &str) -> std::result::Result<Self, Self::Err> {
        Ok(match s {
            "plain" => Self::Plain,
            "markdown" | "md" => Self::Markdown,
            "xournalpp" | "xournal" | "xopp" => Self::XournalPP,
            "latex" | "tex" => Self::LaTeX,
            _ => return Err(eyre!("invalid doctype")),
        })
    }
}

impl ToString for DocType {
    fn to_string(&self) -> String {
        match self {
            DocType::Plain => "plain",
            DocType::Markdown => "md",
            DocType::XournalPP => "xopp",
            DocType::LaTeX => "tex",
        }
        .to_string()
    }
}

impl DocType {
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
