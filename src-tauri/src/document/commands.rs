use crate::types::{DocType, Meta, MetaPatch, RenderType};
use eyre::Result;

use tauri::State;

use tauri::{
    plugin::{Builder as PluginBuilder, TauriPlugin},
    Runtime,
};
use uuid::Uuid;

use crate::document;
use crate::FilespiderState;

#[tauri::command]
pub async fn search(
    state: State<'_, FilespiderState>,
    pos_filter: Vec<String>,
    neg_filter: Vec<String>,
    crib: String,
    page: u32,
    page_length: u32,
) -> Result<Vec<Meta>, String> {
    document::search(
        &*state.pool.lock().await,
        pos_filter,
        neg_filter,
        crib,
        page,
        page_length,
    )
    .await
    .map_err(|x| x.to_string())
}

#[tauri::command]
pub async fn create(
    state: State<'_, FilespiderState>,
    title: String,
    doc_type: Option<DocType>,
    tags: Vec<String>,
    file: Option<String>,
) -> Result<Uuid, String> {
    document::create(&*state.pool.lock().await, title, doc_type, tags, file)
        .await
        .map_err(|x| x.to_string())
}

#[tauri::command]
pub async fn get_meta(state: State<'_, FilespiderState>, id: Uuid) -> Result<Meta, String> {
    document::get_meta(&*state.pool.lock().await, id)
        .await
        .map_err(|x| x.to_string())
}

#[tauri::command]
pub async fn render(
    state: State<'_, FilespiderState>,
    id: Uuid,
) -> Result<(String, RenderType), String> {
    document::render::render(
        &*state.pool.lock().await,
        &mut *state.renderers.lock().await,
        id,
    )
    .await
    .map_err(|x| x.to_string())
}

/// returns Ok(false) if editor is already running, if editor got spawned it returns Ok(true)
#[tauri::command]
pub async fn open_editor(state: State<'_, FilespiderState>, id: Uuid) -> Result<bool, String> {
    document::open_editor(
        &*state.pool.lock().await,
        &mut *state.editors.lock().await,
        id,
    )
    .await
    .map_err(|x| x.to_string())
}

#[tauri::command]
pub async fn alter_meta(
    state: State<'_, FilespiderState>,
    id: Uuid,
    patch: MetaPatch,
) -> Result<(), String> {
    document::patch_meta(&*state.pool.lock().await, id, patch)
        .await
        .map_err(|x| x.to_string())
}

#[tauri::command]
pub async fn delete(state: State<'_, FilespiderState>, id: Uuid) -> Result<(), String> {
    document::delete(&*state.pool.lock().await, id)
        .await
        .map_err(|x| x.to_string())
}

#[tauri::command]
pub async fn get_tags(
    state: State<'_, FilespiderState>,
    crib: String,
) -> Result<Vec<String>, String> {
    document::get_tags(&*state.pool.lock().await, crib)
        .await
        .map_err(|x| x.to_string())
}

pub fn plugin<R: Runtime>() -> TauriPlugin<R> {
    PluginBuilder::new("document")
        .invoke_handler(tauri::generate_handler![
            search,
            create,
            get_meta,
            render,
            open_editor,
            alter_meta,
            delete,
            get_tags
        ])
        .build()
}
