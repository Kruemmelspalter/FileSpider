package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.RenderingException
import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.springframework.util.FileSystemUtils
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date
import java.util.concurrent.TimeUnit

class Renderer {
    private val renderSteps: MutableList<(RenderMeta) -> Unit> = mutableListOf()

    companion object {
        private val plainRenderer = Renderer().output {
            RenderedDocument(
                FileInputStream(it.fsService.getFileFromID(it.document.id)),
                it.document.mimeType,
                it.fsService.getFileAttributesFromID(it.document.id).size(),
                it.document.id.toString() + if (it.document.fileExtension != "") "." + it.document.fileExtension else ""
            )
        }
        private val markdownRenderer =
            Renderer().tempDir().replace(mapOf("->" to "$\\rightarrow$", "=>" to "$\\Rightarrow$"))
                .command(10) { "pandoc -f markdown -o out.html ${it.document.id}" }
                .outputFile("text/html", "html") { "out.html" }
        private val latexRenderer = Renderer().tempDir().command(10) {
            "pdflatex -draftmode -halt-on-error ${it.document.id} && pdflatex -halt-on-error ${it.document.id}"
        }.outputFile("application/pdf", "pdf") { "${it.document.id}.pdf" }
        private val xournalppRenderer = Renderer().tempDir().command(10) { "xournalpp -p out.pdf ${it.document.id}" }
            .outputFile("application/pdf", "pdf") { "${it.document.id}.pdf" }

        fun getRenderer(rendererName: String): Renderer {
            return when (rendererName) {
                "plain" -> plainRenderer
                "markdown", "md" -> markdownRenderer
                "tex", "latex" -> latexRenderer
                "xournal", "xournalpp" -> xournalppRenderer
                else -> plainRenderer
            }
        }
    }

    fun addStep(step: (RenderMeta) -> Unit): Renderer {
        renderSteps.add(step)
        return this
    }

    fun output(stream: InputStream, mimeType: String, contentLength: Long, fileName: String): Renderer {
        return output { RenderedDocument(stream, mimeType, contentLength, fileName) }
    }

    fun output(document: RenderedDocument): Renderer {
        return output { document }
    }

    fun output(documentProvider: (RenderMeta) -> RenderedDocument): Renderer {
        return addStep { it.output = documentProvider(it) }
    }

    fun outputFile(mimeType: String, fileExtension: String, filenameProvider: (RenderMeta) -> String): Renderer {
        return output {
            val filename = filenameProvider(it)
            RenderedDocument(
                FileInputStream(Paths.get(it.workingDirectory.toString(), filename).toString()),
                mimeType,
                Files.readAttributes(
                    Paths.get(it.workingDirectory.toString(), filename), BasicFileAttributes::class.java
                ).size(),
                "${it.document.id}.$fileExtension"
            )
        }
    }

    fun command(
        timeout: Long,
        timeUnit: TimeUnit = TimeUnit.SECONDS,
        commandProvider: (RenderMeta) -> String
    ): Renderer {
        return addStep {
            val command = commandProvider(it)
            val process = ProcessBuilder("bash", "-c", command).redirectErrorStream(true)
                .redirectOutput(it.fsService.getLogPathFromID(it.document.id).toFile())
                .directory(it.workingDirectory.toFile()).start()
            if (!process.waitFor(timeout, timeUnit) || process.exitValue() != 0)
                throw RenderingException(String(it.fsService.readLog(it.document.id)?.readAllBytes() ?: byteArrayOf()))
        }
    }

    fun tempDir(): Renderer {
        return addStep {
            val tmpPath = Paths.get(
                it.fsService.getTemporaryDirectory().toString(), "filespider-${it.document.id}-${Date().time}"
            )
            Files.createDirectories(tmpPath)
            FileSystemUtils.copyRecursively(it.fsService.getDirectoryPathFromID(it.document.id), tmpPath)
            it.workingDirectory = tmpPath
            it.cleanup.add { FileSystemUtils.deleteRecursively(tmpPath) }
        }
    }

    fun replace(replacements: Map<String, String>): Renderer {
        return command(1) {
            val stringBuilder = StringBuilder("sed ")
            for (r in replacements) {
                stringBuilder.append("-e 's/${r.key}/${r.value}/g' ")
            }
            stringBuilder.append(Paths.get(it.workingDirectory.toString(), it.document.id.toString()).toString())
            stringBuilder.toString()
        }
    }

    fun render(document: Document, fsService: FileSystemService): RenderedDocument {
        val meta = RenderMeta(document, fsService.getDirectoryPathFromID(document.id), fsService)
        for (step in renderSteps) step(meta)
        for (step in meta.cleanup) step(meta)
        return meta.output ?: TODO()
    }

    data class RenderMeta(
        val document: Document,
        var workingDirectory: Path,
        val fsService: FileSystemService,
        var output: RenderedDocument? = null,
        val cleanup: MutableList<(RenderMeta) -> Unit> = mutableListOf(),
    )
}
