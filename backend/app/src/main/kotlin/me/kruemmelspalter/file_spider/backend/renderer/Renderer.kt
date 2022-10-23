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
        private val xournalppRenderer = CommandRenderer(
            "xournalpp -p out.pdf",
            "out.pdf",
            "pdf",
            "application/pdf",
            1,
        )
        private val markdownRenderer = CommandRenderer(
            "pandoc -f markdown -o out.html %file%",
            "out.html",
            "html",
            "test/html",
            1
        )
        private val mimeSpecificRenderer = MimeSpecificRenderer()
        fun getRenderer(renderer: String): Renderer {
            return when (renderer) {
                "mimeSpecific" -> mimeSpecificRenderer
                "plain" -> plainRenderer
                "latex", "tex" -> latexRenderer
                "xournalpp", "xournal" -> xournalppRenderer
                "markdown", "md" -> markdownRenderer
                else -> plainRenderer
            }
        }
    }

    fun render(document: Document, fsService: FileSystemService): Optional<RenderedDocument>
}
