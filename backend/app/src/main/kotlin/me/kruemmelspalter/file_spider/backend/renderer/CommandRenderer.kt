package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.RenderingException
import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.Optional
import java.util.concurrent.TimeUnit

class CommandRenderer(
    private val commandTemplate: String,
    private val generatedFileNameTemplate: String,
    private val fileExtension: String,
    private val mimeType: String,
    private val timeout: Long,
) : TempFileRenderer {
    override fun render(document: Document, fsService: FileSystemService, tmpPath: Path): Optional<RenderedDocument> {
        var command = commandTemplate
        if (command.contains("%file%")) command = command.replace("%file%", document.id.toString())
        else command += " " + document.id.toString()

        var generatedFileName = generatedFileNameTemplate
        if (generatedFileName.contains("%file%")) generatedFileName =
            generatedFileName.replace("%file%", document.id.toString())

        val process = ProcessBuilder("bash", "-c", command).redirectErrorStream(true)
            .redirectOutput(File(fsService.getLogPathFromID(document.id).toUri())).directory(File(tmpPath.toString()))
            .start()

        if (!process.waitFor(
                timeout, TimeUnit.SECONDS
            ) || process.exitValue() != 0
        ) throw RenderingException(fsService.readLog(document.id).map { String(it.readAllBytes()) }.orElseGet { "" })

        return Optional.of(
            RenderedDocument(
                FileInputStream(File(tmpPath.toString(), generatedFileName)),
                mimeType,
                Files.readAttributes(Paths.get(tmpPath.toString(), generatedFileName), BasicFileAttributes::class.java)
                    .size(),
                document.id.toString() + "." + fileExtension
            )
        )
    }
}
