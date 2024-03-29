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
            std::env::var("XDG_DATA_HOME")
                .unwrap_or(format!("{}/.local/share", std::env::var("HOME")?))
        }
    )))
}

pub fn get_cache_directory() -> Result<String> {
    Ok(format!("{}/{}", get_filespider_directory()?, ".cache"))
}

pub async fn create_directories() -> Result<()> {
    if !tokio::fs::try_exists(get_cache_directory()?).await? {
        tokio::fs::create_dir_all(get_cache_directory()?).await?;
    }
    Ok(())
}

#[cfg(target_os = "windows")]
pub fn get_filespider_directory() -> Result<String> {
    Ok(std::env::var("FILESPIDER_DATA_PATH").unwrap_or("%APPDATA%/filespider".to_string()))
}
