package me.kruemmelspalter.file_spider.backend.services

import me.kruemmelspalter.file_spider.backend.database.dao.DocumentRepository
import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.renderer.Renderer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.InputStream
import java.sql.Timestamp
import java.util.Optional
import java.util.UUID

@Service
class DocumentService {
    @Autowired
    val documentRepository: DocumentRepository? = null

    @Autowired
    val fsService: FileSystemService? = null

    fun getDocumentMeta(id: UUID): DocumentMeta? {
        val document = documentRepository!!.getDocument(id)
        return documentToMeta(document ?: return null)
    }

    private fun documentToMeta(document: Document): DocumentMeta {
        val attributes = fsService!!.getFileAttributesFromID(document.id)
        return DocumentMeta(
            document,
            Timestamp(attributes.creationTime().toMillis()),
            Timestamp(attributes.lastModifiedTime().toMillis()),
            Timestamp(attributes.lastAccessTime().toMillis()),
            documentRepository!!.getTags(document.id)
        )
    }

    fun filterDocuments(posFilter: List<String>, negFilter: List<String>): List<DocumentMeta> {
        return documentRepository!!.filterDocuments(posFilter, negFilter).map { documentToMeta(it) }
    }

    fun renderDocument(id: UUID): RenderedDocument? {
        val document = documentRepository!!.getDocument(id)
        return if (document == null) null else Renderer.getRenderer(document.renderer).render(document, fsService!!)
    }

    fun readDocumentLog(id: UUID): InputStream? {
        documentRepository!!.getDocument(id) ?: return null

        return fsService!!.readLog(id)
    }

    fun createDocument(
        title: String?,
        renderer: String?,
        editor: String?,
        mimeType: String,
        tags: List<String>?,
        content: InputStream?,
    ): UUID {
        return documentRepository!!.createDocument(
            title ?: "Untitled",
            renderer ?: "plain",
            editor ?: "text",
            mimeType,
            tags ?: listOf(),
        )
    }
}
