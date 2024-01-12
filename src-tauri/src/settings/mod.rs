use eyre::{eyre, Result};
use serde::{Deserialize, Serialize};

use crate::directories::get_filespider_directory;
use crate::document;
use crate::document::File;
use crate::document::File::Blob;
use crate::types::DocType;

pub mod commands;

#[derive(Serialize, Deserialize)]
pub struct Settings {
    pub text_editor: (String, Vec<String>),
    pub presets: Vec<DocumentPreset>,
    pub file_watcher: bool,
}

impl Settings {
    pub async fn default() -> Result<Self> {
        let editor = if tokio::process::Command::new("which")
            .arg("kate")
            .status().await?.success() {
            ("kate", vec!["-b", "%FILE%"])
        } else if cfg!(linux) {
            ("xdg-open", vec!["%FILE%"])
        } else if cfg!(windows) {
            ("start", vec!["%FILE%"])
        } else {
            return Err(eyre!("no default editor for macos"))
        };

        Ok(Self {
            text_editor: (editor.0.to_string(), editor.1.into_iter().map(|s| s.to_string()).collect()),
            presets: vec![
                DocumentPreset::from_strs(
                    "LaTeX",
                    vec![],
                    Some("tex"),
                    Some(DocType::LaTeX), Some(include_bytes!("../../assets/latex_template.tex"))),
                DocumentPreset::from_strs("XOPP", vec![], Some("xopp"), Some(DocType::XournalPP), Some(include_bytes!("../../assets/xopp_template.xopp"))),
            ],
            file_watcher: false,
        })
    }

    pub async fn save(&self) -> Result<()> {
        save_config(self).await?;
        Ok(())
    }

    pub async fn load() -> Result<Settings> {
        if tokio::fs::try_exists(get_config_file()?).await? {
            let settings: Result<Settings> = try {
                let settings_str = tokio::fs::read_to_string(get_config_file()?).await?;
                let s: Settings = json5::from_str(&settings_str)?;
                s
            };
            if let Ok(s) = settings {
                return Ok(s);
            }
        }
        let settings = Settings::default().await?;

        save_config(&settings).await?;

        Ok(settings)
    }
}

#[derive(Serialize, Deserialize, Clone)]
pub struct DocumentPreset {
    pub name: String,
    pub tags: Vec<String>,
    pub extension: Option<String>,
    pub doc_type: Option<DocType>,
    pub file: document::File,
}

impl DocumentPreset {
    pub fn from_strs(name: &str, tags: Vec<&str>, extension: Option<&str>, doc_type: Option<DocType>, blob: Option<&[u8]>) -> Self {
        Self {
            name: name.to_string(),
            tags: tags.into_iter().map(|s| s.to_string()).collect(),
            extension: extension.map(|s| s.to_string()),
            doc_type,
            file: if let Some(blob) = blob { Blob(blob.to_vec()) } else { File::None },
        }
    }
}

pub fn get_config_file() -> Result<String> {
    Ok(format!("{}/config.json5", get_filespider_directory()?))
}

pub async fn save_config(settings: &Settings) -> Result<()> {
    tokio::fs::write(get_config_file()?,
                     json5::to_string(&settings)?).await?;
    Ok(())
}