use tempfile::tempdir;
use tokio::test;

use crate::directories;
use crate::document::*;
use crate::document::render::render;

#[test]
async fn tests() {
    let dtmp = tempdir().unwrap();
    let tempdir = dtmp.path();

    if let Err(e) = async {
        env_logger::Builder::new()
            .filter_level(log::LevelFilter::Trace)
            .init();

        std::env::set_var("FILESPIDER_DATA_PATH", tempdir);

        let pool = crate::db::init().await?;

        directories::create_directories().await?;

        assert!(document_exists(&uuid::uuid!("a346b1e3-2c11-4c72-87b1-122bfcc43560")).await.is_err(), "random document exists");

        let id = create(
            &pool,
            "Test".to_string(),
            Some(DocType::Plain),
            vec!["test".to_string(), "abc".to_string()],
            None,
            File::None,
        )
            .await?;

        let meta = get_meta(&pool, id).await?;

        assert!(meta.title == "Test"
                    && meta.doc_type == DocType::Plain
                    && meta.tags.contains(&"test".to_string())
                    && meta.tags.contains(&"abc".to_string()),
                "meta {:?} does not match", meta);

        patch_meta(&pool, id, MetaPatch::AddTag("amogus".to_string())).await?;

        let meta = get_meta(&pool, id).await?;

        assert!(meta.tags.contains(&"amogus".to_string()), "failed to add tag");


        patch_meta(&pool, id, MetaPatch::RemoveTag("abc".to_string())).await?;

        let meta = get_meta(&pool, id).await?;

        assert!(!meta.tags.contains(&"abc".to_string()), "failed to remove tag");


        patch_meta(&pool, id, MetaPatch::ChangeTitle("exam".to_string())).await?;

        let meta = get_meta(&pool, id).await?;

        assert_eq!(meta.title, "exam", "failed to changte title");

        let res = search(
            &pool,
            vec!["test".to_string()],
            vec!["d".to_string()],
            "ex".to_string(),
            0,
            1,
            (SearchSortCriterium::CreationTime, false),
        )
            .await?;

        assert_ne!(res.first(), None, "search didn't return anything");

        assert_eq!(res[0].title, "exam", "search returned something else");

        let tags_res = get_tags(&pool, "te".to_string()).await?;
        assert!(tags_res.len() == 1 && tags_res[0] == "test", "tag search failed");

        tokio::fs::write(get_document_file(&meta.id, &meta.extension)?, "testogus").await?;

        let path = render(&pool, &mut HashMap::new(), id).await?;

        assert_eq!(path.1, RenderType::Plain);
        assert_eq!(tokio::fs::read_to_string(path.0).await?, "testogus");

        delete(&pool, id).await?;

        Ok::<(), eyre::Report>(())
    }
        .await {
        panic!("Error: {}", e);
    }
    drop(dtmp);
}
