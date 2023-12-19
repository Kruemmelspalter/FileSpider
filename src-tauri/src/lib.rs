use std::collections::HashMap;

#[cfg(target_os = "linux")]
use std::sync::Arc;

use sqlx::SqlitePool;
use tokio::process;
use tokio::sync::Mutex;
use tokio::task::JoinHandle;
use uuid::Uuid;

pub mod db;
pub mod directories;
pub mod document;
pub mod types;

pub struct FilespiderState {
    pool: Mutex<SqlitePool>,
    editors: Mutex<HashMap<Uuid, process::Child>>,
    renderers: Mutex<HashMap<(Uuid, document::render::Hash), Mutex<JoinHandle<()>>>>,
    #[cfg(target_os = "linux")] dbus: Mutex<Arc<dbus::nonblock::SyncConnection>>,
}

impl FilespiderState {
    pub fn new(
        pool: SqlitePool,
        #[cfg(target_os = "linux")] dbus: Arc<dbus::nonblock::SyncConnection>,
    ) -> Self {
        Self {
            pool: Mutex::new(pool),
            editors: Mutex::new(HashMap::new()),
            renderers: Mutex::new(HashMap::new()),
            #[cfg(target_os = "linux")] dbus: Mutex::new(dbus),
        }
    }
}