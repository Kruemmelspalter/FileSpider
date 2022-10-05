package me.kruemmelspalter.file_spider.backend.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/document")
class DocumentController {

    @GetMapping("/{id}")
    fun getIndex(@PathVariable("id") documentId: UUID): String {
        // TODO implement
        return ""
    }
}
