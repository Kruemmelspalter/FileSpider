use eyre::Result;

#[cfg(target_os = "linux")]
extern "C" {
    fn geteuid() -> u32;
}

#[cfg(target_os = "linux")]
pub fn get_filespider_directory() -> Result<String> {
    Ok(std::env::var("FILESPIDER_DATA_PATH").unwrap_or(format!(
        "{}/filespider",
        if unsafe { geteuid() } == 0 {
            "/var/lib".to_string()
        } else {
            std::env::var("XDG_CONFIG_HOME")
                .unwrap_or(format!("{}/.config", std::env::var("HOME")?))
        }
    )))
}

#[cfg(target_os = "windows")]
pub fn get_filespider_directory() -> Result<String> {
    Ok(std::env::var("FILESPIDER_DATA_PATH").unwrap_or("%APPDATA%/filespider".to_string()))
}
