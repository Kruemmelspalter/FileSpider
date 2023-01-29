package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RenderService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var fsService: FileSystemService

    @Autowired
    private lateinit var renderCache: RenderCache

    fun renderDocument(document: Document, useCache: Boolean = true): RenderedDocument? {
        return getRenderer(document).render(document, fsService, renderCache, useCache)
    }

    fun getRenderer(document: Document): Renderer {
        logger.debug("Getting renderer from renderer ${document.renderer} and mime type ${document.mimeType}")
        return when (document.renderer) {
            "plain" -> Renderer.plainRenderer
            "markdown", "md" -> Renderer.markdownRenderer
            "tex", "latex" -> Renderer.latexRenderer
            "xournal", "xournalpp" -> Renderer.xournalppRenderer
            "html" -> Renderer.htmlRenderer
            "renderer" -> Renderer.ebookRenderer
            else -> when (document.mimeType) {
                "application/x-tex", "application/x-latex" -> Renderer.latexRenderer
                "text/markdown" -> Renderer.markdownRenderer
                "application/x-xopp" -> Renderer.xournalppRenderer
                "text/html" -> Renderer.htmlRenderer
                "application/zip+epub" -> Renderer.ebookRenderer
                else -> Renderer.plainRenderer
            }
        }
    }
}
