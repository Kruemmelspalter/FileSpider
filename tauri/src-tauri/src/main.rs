// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

// use tauri::{CustomMenuItem, Menu, MenuItem, Submenu};

fn main() {
    // let menu = Menu::new()
    //     .add_submenu(Submenu::new(
    //         "File",
    //         Menu::new()
    //             .add_item(CustomMenuItem::new("test", "Test"))
    //             .add_native_item(MenuItem::Quit),
    //     ))
    //     .add_submenu(Submenu::new(
    //         "View",
    //         Menu::new().add_native_item(MenuItem::EnterFullScreen),
    //     ));

    tauri::Builder::default()
        // .menu(menu)
        .run(tauri::generate_context!())
        .expect("error while running tauri application")
}
