package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import java.util.Optional

class PlainRenderer : Renderer {
    override fun render(document: Document, fsService: FileSystemService): Optional<RenderedDocument> {
        return Optional.of(
            RenderedDocument(
                fsService.getInputStreamFromID(document.id),
                document.mimeType,
                fsService.getFileAttributesFromID(document.id).size(),
                document.id.toString() + if (document.fileExtension != "") "." + document.fileExtension else ""
            )
        )
    }
}
