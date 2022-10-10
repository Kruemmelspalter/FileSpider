package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import java.sql.Timestamp
import java.util.UUID

data class DocumentMeta(
    val id: UUID,
    val title: String,
    val added: Timestamp,
    val created: Timestamp,
    val modified: Timestamp,
    val accessed: Timestamp,
    val tags: List<String>,
) {
    constructor(
        doc: Document,
        created: Timestamp,
        modified: Timestamp,
        accessed: Timestamp,
        tags: List<String>
    ) : this(doc.id, doc.title, doc.added, created, modified, accessed, tags)
}
