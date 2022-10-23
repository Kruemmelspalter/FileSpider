package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import java.util.Optional

class MimeSpecificRenderer : Renderer {
    override fun render(document: Document, fsService: FileSystemService): Optional<RenderedDocument> {
        return Renderer.getRenderer(
            when (document.mimeType) {
                "application/x-tex" -> "latex"
                else -> "plain"
            }
        ).render(document, fsService)
    }
}
