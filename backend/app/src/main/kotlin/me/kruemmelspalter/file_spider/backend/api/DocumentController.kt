package me.kruemmelspalter.file_spider.backend.api

import me.kruemmelspalter.file_spider.backend.services.DocumentMeta
import me.kruemmelspalter.file_spider.backend.services.DocumentService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/document")
class DocumentController {

    @Autowired
    val documentService: DocumentService? = null

    @GetMapping("/{id}")
    fun getDocumentMeta(@PathVariable("id") documentId: UUID): DocumentMeta {
        return documentService!!.getDocumentMeta(documentId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND)
            }
    }
}
