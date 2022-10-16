package me.kruemmelspalter.file_spider.backend.api

import me.kruemmelspalter.file_spider.backend.RenderingException
import me.kruemmelspalter.file_spider.backend.services.DocumentMeta
import me.kruemmelspalter.file_spider.backend.services.DocumentService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import java.util.regex.Pattern

@RestController
@RequestMapping("/document")
class DocumentController {

    @Autowired
    val documentService: DocumentService? = null

    @GetMapping("/")
    fun searchDocuments(@RequestParam("filter") filterString: String): List<DocumentMeta> {
        if (!Pattern.matches(
                "^!?\\w+(?:,!?\\w+)*\$",
                filterString
            )
        ) throw ResponseStatusException(HttpStatus.BAD_REQUEST)

        val filters = filterString.split(",")

        return documentService!!.filterDocuments(
            filters.filter { !it.startsWith("!") },
            filters.filter { it.startsWith("!") }.map { it.substring(1) }
        )
    }

    @GetMapping("/{id}")
    fun getDocumentMeta(@PathVariable("id") documentId: UUID): DocumentMeta {
        return documentService!!.getDocumentMeta(documentId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND)
            }
    }

    @GetMapping("/{id}/rendered")
    fun getRenderedDocument(
        @PathVariable("id") documentId: UUID,
        @RequestParam("download") download: Optional<Boolean>
    ): ResponseEntity<Resource> {

        val renderedDocument: RenderedDocument = documentService!!.renderDocument(documentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(renderedDocument.contentType)
        headers.contentLength = renderedDocument.contentLength
        if (download.orElseGet { false }) headers.contentDisposition =
            ContentDisposition.parse("attachment; filename=" + renderedDocument.fileName)

        return ResponseEntity
            .status(HttpStatus.OK)
            .headers(headers)
            .body(InputStreamResource(renderedDocument.stream))
    }

    @GetMapping("/{id}/renderlog")
    fun getRenderLog(@PathVariable id: UUID): InputStreamResource {
        val log = documentService!!.readDocumentLog(id)
        if (log.isEmpty) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return InputStreamResource(log.get())
    }

    @ExceptionHandler(value = [RenderingException::class])
    fun renderingException(exception: RenderingException?): ResponseEntity<String> {
        return ResponseEntity(exception!!.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
