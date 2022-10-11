package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import java.util.Optional
import java.util.UUID

interface DocumentDao {

    fun getDocument(id: UUID): Optional<Document>

    fun getTags(id: UUID): List<String>

    fun insertDocument(document: Document)

    fun deleteDocument(id: UUID)

    fun filterDocuments(filter: List<String>): List<Document>
}
