package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import java.util.Optional

interface Renderer {
    companion object {
        private val plainRenderer = PlainRenderer()
        private val latexRenderer = CommandRenderer(
            "pdflatex -draftmode -halt-on-error %file% && pdflatex -halt-on-error %file%",
            "%file%.pdf",
            "pdf",
            "application/pdf",
            10,
        )
        private val mimeSpecificRenderer = MimeSpecificRenderer()
        fun getRenderer(renderer: String): Renderer {
            return when (renderer) {
                "mimeSpecific" -> mimeSpecificRenderer
                "plain" -> plainRenderer
                "latex" -> latexRenderer
                else -> plainRenderer
            }
        }
    }

    fun render(document: Document, fsService: FileSystemService): Optional<RenderedDocument>
}
