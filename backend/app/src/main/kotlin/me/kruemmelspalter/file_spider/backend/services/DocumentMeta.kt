package me.kruemmelspalter.file_spider.backend.services

import me.kruemmelspalter.file_spider.backend.database.Document
import java.sql.Timestamp
import java.util.UUID

data class DocumentMeta(
    val id: UUID,
    val title: String,
    val added: Timestamp,
    val editor: String,
    val created: Timestamp,
    val modified: Timestamp,
    val accessed: Timestamp,
    val tags: List<String>,
    val fileExtension: String?,
) {
    constructor(
        doc: Document,
        created: Timestamp,
        modified: Timestamp,
        accessed: Timestamp,
        tags: List<String>
    ) : this(doc.id, doc.title, doc.added, doc.editor, created, modified, accessed, tags, doc.fileExtension)
}
