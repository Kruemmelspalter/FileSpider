package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import java.util.*

class DocumentService: DocumentDao {
    override fun getDocumentMetaById(id: UUID): DocumentMeta {
        TODO("Not yet implemented")
    }

    override fun insertDocument(id: UUID, document: Document) {
        TODO("Not yet implemented")
    }

    override fun deleteDocument(id: UUID) {
        TODO("Not yet implemented")
    }

    override fun filterDocuments(filter: String): List<DocumentMeta> {
        TODO("Not yet implemented")
    }
}