package me.kruemmelspalter.file_spider.backend.services

import com.fasterxml.uuid.Generators
import me.kruemmelspalter.file_spider.backend.database.Document
import me.kruemmelspalter.file_spider.backend.database.DocumentRepository
import me.kruemmelspalter.file_spider.backend.renderer.RenderService
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Paths
import java.sql.Timestamp
import java.util.UUID
import java.util.zip.GZIPOutputStream
import javax.servlet.ServletContext

@Service
class DocumentService {

    private val uuidGenerator = Generators.timeBasedGenerator()

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    @Autowired
    private lateinit var renderService: RenderService

    @Autowired
    private lateinit var fsService: FileSystemService

    fun getDocumentMeta(id: UUID): DocumentMeta? {
        val document = documentRepository.getDocument(id)
        return documentToMeta(document ?: return null)
    }

    private fun documentToMeta(document: Document): DocumentMeta {
        val attributes = fsService.getFileAttributesFromID(document.id, document.fileExtension)
        return DocumentMeta(
            document,
            Timestamp(attributes.creationTime().toMillis()),
            Timestamp(attributes.lastModifiedTime().toMillis()),
            Timestamp(attributes.lastAccessTime().toMillis()),
            documentRepository.getTags(document.id)
        )
    }

    fun filterDocuments(posFilter: List<String>, negFilter: List<String>): List<DocumentMeta> {
        return documentRepository.filterDocuments(posFilter, negFilter).map { documentToMeta(it) }
    }

    fun renderDocument(id: UUID, useCache: Boolean = true): RenderedDocument? {
        return renderService.renderDocument(documentRepository.getDocument(id) ?: return null, useCache)
    }

    fun readDocumentLog(id: UUID): InputStream? {
        documentRepository.getDocument(id) ?: return null

        return fsService.readLog(id)
    }

    fun setTitle(id: UUID, title: String) {
        documentRepository.setTitle(id, title)
    }

    fun createDocument(
        title: String?,
        renderer: String?,
        editor: String?,
        mimeType: String,
        tags: List<String>?,
        fileExtension: String?,
        content: InputStream?,
    ): UUID {
        val uuid = uuidGenerator.generate()

        documentRepository.createDocument(
            uuid,
            title ?: "Untitled",
            renderer ?: "plain",
            editor ?: "text",
            mimeType,
            tags ?: listOf(),
            fileExtension,
        )

        fsService.createDocument(uuid, fileExtension)
        if (content != null) {
            fsService.writeToDocument(uuid, fileExtension, content)
            content.close()
        }

        return uuid
    }

    fun addTags(id: UUID, addTags: List<String>) {
        documentRepository.addTags(id, addTags)
    }

    fun removeTags(id: UUID, removeTags: List<String>) {
        documentRepository.removeTags(id, removeTags)
    }

    fun deleteDocument(id: UUID) {
        documentRepository.deleteDocument(id)
        fsService.deleteDocument(id)
    }

    fun getDocumentResource(id: UUID, fileName: String, servletContext: ServletContext): Resource? {
        return fsService.getDocumentResource(id, fileName, servletContext)
    }

    fun importPdfToXopp(stream: InputStream, title: String, tags: List<String>): UUID {
        val id = createDocument(title, null, null, "application/x-xopp", tags, "xopp", null)

        fsService.writeCustomFileToDocument(id, "bg.pdf", stream)

        var xournalData = "<xournal fileversion=\"4\">"

        val doc = PDDocument.load(Paths.get(fsService.getDirectoryPathFromID(id).toString(), "bg.pdf").toFile())
        doc.pages.iterator().withIndex().forEach { (i, v) ->
            val w = (if (v.rotation == 0 || v.rotation == 180) v.cropBox.width else v.cropBox.height) * v.userUnit
            val h = (if (v.rotation == 0 || v.rotation == 180) v.cropBox.height else v.cropBox.width) * v.userUnit

            xournalData += "<page width=\"$w\" height=\"$h\"><background type=\"pdf\" pageno=\"${i + 1}\" " +
                (if (i == 0) "domain=\"absolute\" filename=\"bg.pdf\"" else "") +
                "/><layer/></page>"
        }
        doc.close()

        xournalData += "</xournal>"

        val bos = ByteArrayOutputStream()
        val gos = GZIPOutputStream(bos)
        IOUtils.copy(xournalData.byteInputStream(), gos)
        gos.flush()
        gos.close()

        fsService.writeToDocument(id, "xopp", ByteArrayInputStream(bos.toByteArray()))

        bos.close()

        return id
    }
}
