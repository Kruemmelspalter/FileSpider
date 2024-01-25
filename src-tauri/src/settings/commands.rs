use tauri::plugin::{Builder as PluginBuilder, TauriPlugin};
use tauri::{Runtime, State};

use crate::settings::DocumentPreset;
use crate::FilespiderState;

#[tauri::command]
pub async fn get_presets(state: State<'_, FilespiderState>) -> Result<Vec<DocumentPreset>, String> {
    Ok(state.settings.lock().await.presets.clone())
}

pub fn plugin<R: Runtime>() -> TauriPlugin<R> {
    PluginBuilder::new("settings")
        .invoke_handler(tauri::generate_handler![get_presets])
        .build()
}
