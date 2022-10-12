package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import java.util.Optional

interface Renderer {
    companion object {
        private val plainRenderer = PlainRenderer()
        fun getRenderer(renderer: String): Renderer {
            return when (renderer) {
                "plain" -> plainRenderer
                else -> plainRenderer
            }
        }
    }

    fun render(document: Document, fsService: FileSystemService): Optional<RenderedDocument>
}
