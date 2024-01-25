use std::fmt::Display;
use std::str::FromStr;

use chrono::NaiveDateTime;
use eyre::eyre;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::settings::Settings;

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug)]
pub struct Meta {
    pub title: String,
    pub doc_type: DocType,
    pub tags: Vec<String>,
    pub created: NaiveDateTime,
    pub accessed: NaiveDateTime,
    pub id: Uuid,
    pub extension: Option<String>,
}

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug)]
pub enum MetaPatch {
    ChangeTitle(String),
    AddTag(String),
    RemoveTag(String),
}

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug)]
pub enum RenderType {
    Plain,
    Html,
    Pdf,
}

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug, Clone)]
pub enum DocType {
    Plain,
    Markdown,
    XournalPP,
    LaTeX,
}

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug)]
pub enum SearchSortCriterium {
    CreationTime,
    AccessTime,
    Title,
}

/// bool is true for ascending, false for descending
pub type SearchSorting = (SearchSortCriterium, bool);

impl FromStr for RenderType {
    type Err = eyre::Report;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "plain" => Ok(RenderType::Plain),
            "html" => Ok(RenderType::Html),
            "pdf" => Ok(RenderType::Pdf),
            _ => Err(eyre!("unknown render type {}", s)),
        }
    }
}

impl Display for RenderType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let str = match self {
            RenderType::Plain => "plain".to_string(),
            RenderType::Html => "html".to_string(),
            RenderType::Pdf => "pdf".to_string(),
        };
        write!(f, "{}", str)
    }
}

impl FromStr for DocType {
    type Err = eyre::Report;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(match s {
            "plain" => Self::Plain,
            "markdown" | "md" => Self::Markdown,
            "xournalpp" | "xournal" | "xopp" => Self::XournalPP,
            "latex" | "tex" => Self::LaTeX,
            _ => return Err(eyre!("invalid doctype")),
        })
    }
}

impl Display for DocType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let str = match self {
            DocType::Plain => "plain",
            DocType::Markdown => "md",
            DocType::XournalPP => "xopp",
            DocType::LaTeX => "tex",
        }
        .to_string();
        write!(f, "{}", str)
    }
}

impl DocType {
    pub fn get_editor(&self, settings: &Settings) -> (String, Vec<String>) {
        match self {
            DocType::Plain => settings.text_editor.clone(),
            DocType::Markdown => settings.text_editor.clone(),
            DocType::XournalPP => ("xournalpp".to_string(), vec![]),
            DocType::LaTeX => settings.text_editor.clone(),
        }
    }
}
