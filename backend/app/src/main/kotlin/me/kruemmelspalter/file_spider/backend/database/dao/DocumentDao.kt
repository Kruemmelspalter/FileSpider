package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import java.util.Optional
import java.util.UUID

interface DocumentDao {
    fun getDocumentMetaById(id: UUID): Optional<DocumentMeta>

    fun insertDocument(document: Document): UUID {
        val id = UUID.randomUUID()
        insertDocument(id, document)
        return id
    }

    fun insertDocument(id: UUID, document: Document)

    fun deleteDocument(id: UUID)

    fun filterDocuments(filter: String): List<DocumentMeta>
}
