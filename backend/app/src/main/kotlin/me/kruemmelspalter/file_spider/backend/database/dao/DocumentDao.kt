package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import java.util.UUID

interface DocumentDao {

    fun getDocument(id: UUID): Document?

    fun getTags(id: UUID): List<String>

    fun createDocument(
        uuid: UUID,
        title: String,
        renderer: String,
        editor: String,
        mimeType: String,
        tags: List<String>,
        fileExtension: String?,
    )

    fun deleteDocument(id: UUID)

    fun filterDocuments(posFilter: List<String>, negFilter: List<String>): List<Document>
    fun removeTags(id: UUID, removeTags: List<String>)
    fun addTags(id: UUID, addTags: List<String>)

    fun setTitle(id: UUID, title: String)
}
