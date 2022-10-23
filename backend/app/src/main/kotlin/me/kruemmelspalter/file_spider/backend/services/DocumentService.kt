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

    fun getDocumentMeta(id: UUID): Optional<DocumentMeta> {
        val document = documentRepository!!.getDocument(id)
        return if (document.isEmpty) Optional.empty() else Optional.of(documentToMeta(document.get()))
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

    fun renderDocument(id: UUID): Optional<RenderedDocument> {
        val document = documentRepository!!.getDocument(id)
        return if (document.isEmpty) Optional.empty()
        else Renderer.getRenderer(document.get().renderer).render(document.get(), fsService!!)
    }

    fun readDocumentLog(id: UUID): Optional<InputStream> {
        val document = documentRepository!!.getDocument(id)
        return if (document.isEmpty) Optional.empty()
        else fsService!!.readLog(id)
    }
}
