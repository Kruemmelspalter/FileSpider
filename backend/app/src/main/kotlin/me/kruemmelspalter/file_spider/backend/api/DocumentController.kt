package me.kruemmelspalter.file_spider.backend.api

import me.kruemmelspalter.file_spider.backend.renderer.RenderingException
import me.kruemmelspalter.file_spider.backend.services.DocumentMeta
import me.kruemmelspalter.file_spider.backend.services.DocumentService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/document")
class DocumentController {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var documentService: DocumentService

    @GetMapping("/")
    fun searchDocuments(@RequestParam("filter") filterString: String): List<DocumentMeta> {
        if (!Pattern.matches("^!?\\p{L}+(?:,!?\\p{L}+)*\$", filterString))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST)

        val filters = filterString.split(",")

        return documentService.filterDocuments(
            filters.filter { !it.startsWith("!") },
            filters.filter { it.startsWith("!") }.map { it.substring(1) }
        )
    }

    @PostMapping("/", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createDocument(
        @RequestParam file: MultipartFile?,
        @RequestParam title: String?,
        @RequestParam renderer: String?,
        @RequestParam editor: String?,
        @RequestParam tags: List<String>?,
        @RequestParam mimeType: String?,
        @RequestParam fileExtension: String?,
    ): String {
        if (mimeType == null && file == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val uuid = if (file != null) documentService.createDocument(
            title, renderer, editor, mimeType ?: file.contentType, tags, fileExtension, file.inputStream
        )
        else documentService.createDocument(title, renderer, editor, mimeType!!, tags, fileExtension, null)
        return uuid.toString()
    }

    @GetMapping("/{id}")
    fun getDocumentMeta(@PathVariable("id") documentId: UUID): DocumentMeta {
        return documentService.getDocumentMeta(documentId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    data class DocumentChange(val addTags: List<String>?, val removeTags: List<String>?, val title: String?)

    @PatchMapping("/{id}")
    fun changeDocumentTags(@PathVariable id: UUID, @RequestBody change: DocumentChange) {
        if (change.addTags != null) documentService.addTags(id, change.addTags)
        if (change.removeTags != null) documentService.removeTags(id, change.removeTags)
        if (change.title != null) documentService.setTitle(id, change.title)
    }

    @DeleteMapping("/{id}")
    fun deleteDocument(@PathVariable id: UUID) {
        documentService.deleteDocument(id)
    }

    @GetMapping("/{id}/rendered")
    fun getRenderedDocument(
        @PathVariable("id") documentId: UUID,
        @RequestParam download: Boolean?,
        @RequestParam cache: Boolean?,
    ): ResponseEntity<Resource> {

        val renderedDocument: RenderedDocument = documentService.renderDocument(documentId, cache ?: true)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(renderedDocument.contentType)
        headers.contentLength = renderedDocument.contentLength
        if (download == true) headers.contentDisposition =
            ContentDisposition.parse("attachment; filename=" + renderedDocument.fileName)

        return ResponseEntity.status(HttpStatus.OK).headers(headers).body(InputStreamResource(renderedDocument.stream))
    }

    @GetMapping("/{id}/rendered/{file}")
    fun getDocumentResource(
        @PathVariable("id") documentId: UUID,
        @PathVariable("file") fileName: String,
        request: HttpServletRequest
    ): Resource? {
        return documentService.getDocumentResource(documentId, fileName, request.servletContext)
    }

    @GetMapping("/{id}/renderlog")
    fun getRenderLog(@PathVariable id: UUID): InputStreamResource {
        return InputStreamResource(
            documentService.readDocumentLog(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        )
    }

    @ExceptionHandler(value = [RenderingException::class])
    fun renderingException(exception: RenderingException?): ResponseEntity<String> {
        return ResponseEntity
            .internalServerError()
            .contentType(MediaType.TEXT_PLAIN)
            .body(exception?.message)
    }
}
