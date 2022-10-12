package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.springframework.util.FileSystemUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Date
import java.util.Optional

interface TempFileRenderer : Renderer {

    override fun render(document: Document, fsService: FileSystemService): Optional<RenderedDocument> {
        val tmpPath = Paths.get(
            fsService.getTemporaryDirectory().toString(),
            "filespider-${document.id}-${Date().time}"
        )
        Files.createDirectories(tmpPath)
        Files.copy(fsService.getPathFromID(document.id), Paths.get(tmpPath.toString(), document.id.toString()))

        val renderedDocument = render(document, fsService, tmpPath)

        FileSystemUtils.deleteRecursively(tmpPath)
        FileSystemUtils.deleteRecursively(tmpPath)

        return renderedDocument
    }

    fun render(document: Document, fsService: FileSystemService, tmpPath: Path): Optional<RenderedDocument>
}
