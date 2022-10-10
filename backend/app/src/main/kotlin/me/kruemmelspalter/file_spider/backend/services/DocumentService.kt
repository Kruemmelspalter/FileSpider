package me.kruemmelspalter.file_spider.backend.services

import me.kruemmelspalter.file_spider.backend.FileSystemService
import me.kruemmelspalter.file_spider.backend.database.dao.DocumentRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
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
        return if (document.isEmpty) Optional.empty()
        else {
            val attributes = fsService!!.getFileAttributesFromID(id)
            Optional.of(
                DocumentMeta(
                    document.get(),
                    Timestamp(attributes.creationTime().toMillis()),
                    Timestamp(attributes.lastModifiedTime().toMillis()),
                    Timestamp(attributes.lastAccessTime().toMillis()),
                    documentRepository!!.getTags(id)
                )
            )
        }
    }
}
