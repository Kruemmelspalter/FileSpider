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
                FileInputStream(it.fsService.getFileFromID(it.document.id, it.document.fileExtension)),
                it.document.mimeType,
                it.fsService.getFileAttributesFromID(it.document.id, it.document.fileExtension).size(),
                it.fileName
            )
        }

        private val htmlRenderer =
            Renderer().tempDir().resolveLinks().outputFile("text/html", "html") { it.fileName }

        private val markdownRenderer =
            Renderer().tempDir().command(10) {
                listOf(
                    "pandoc",
                    "-f", "markdown",
                    "-o", "out.html",
                    "-s",
                    "--katex=/libs/katex/",
                    "--metadata", "title=${it.document.title}",
                    it.fileName
                )
            }
                .resolveLinks { "out.html" }.outputFile("text/html", "html") { "out.html" }

        private val latexRenderer = Renderer().tempDir().command(10) {
            listOf(
                "bash",
                "-c",
                "pdflatex -draftmode -halt-on-error ${it.document.id} && pdflatex -halt-on-error ${it.document.id}"
            )
        }.outputFile("application/pdf", "pdf") { "${it.document.id}.pdf" }

        private val xournalppRenderer =
            Renderer().tempDir().command(10) { listOf("xournalpp", "-p", "out.pdf", it.fileName) }
                .outputFile("application/pdf", "pdf") { "out.pdf" }

        private val mimeSpecificRenderer = Renderer().useRenderer {
            when (it.document.mimeType) {
                "application/x-tex", "application/x-latex" -> latexRenderer
                "text/markdown" -> markdownRenderer
                "application/x-xopp" -> xournalppRenderer
                "text/html" -> htmlRenderer
                else -> plainRenderer
            }
        }

        fun getRenderer(rendererName: String): Renderer {
            return when (rendererName) {
                "plain" -> plainRenderer
                "markdown", "md" -> markdownRenderer
                "tex", "latex" -> latexRenderer
                "xournal", "xournalpp" -> xournalppRenderer
                "html" -> htmlRenderer
                else -> mimeSpecificRenderer
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
        commandProvider: (RenderMeta) -> List<String>
    ): Renderer {
        return addStep {
            val process = ProcessBuilder(commandProvider(it)).redirectErrorStream(true)
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

    fun replace(
        replacements: Map<String, String>,
        filenameProvider: (RenderMeta) -> String = { it.fileName }
    ): Renderer {
        return command(1) { meta ->
            listOf("sed", "-i", "-E") + replacements.map { listOf("-e", "s/${it.key}/${it.value}/g") }.flatten() +
                listOf(Paths.get(meta.workingDirectory.toString(), filenameProvider(meta)).toString())
        }
    }

    fun useRenderer(rendererProvider: (RenderMeta) -> Renderer): Renderer {
        return addStep {
            it.output = rendererProvider(it).render(it.document, it.fsService)
        }
    }

    fun resolveLinks(filenameProvider: (RenderMeta) -> String = { it.fileName }): Renderer {
        return replace(
            mapOf(
                "<a href=\"%5E([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})\">(.+)<\\/a>" to "<a href=\"..\\/..\\/\\1\" target=\"_parent\">\\2<\\/a>",
                "\\\"\\%5E([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})(\\/?.*\\\")"
                    to "\"..\\/\\1\\/rendered\\2"
            ),
            filenameProvider
        )
    }

    fun render(document: Document, fsService: FileSystemService): RenderedDocument? {
        return render(
            RenderMeta(
                document,
                fsService.getDirectoryPathFromID(document.id),
                fsService,
                document.id.toString() + if (document.fileExtension != null) "." + document.fileExtension else ""
            )
        )
    }

    fun render(meta: RenderMeta): RenderedDocument? {
        for (step in renderSteps) step(meta)
        for (step in meta.cleanup) step(meta)
        return meta.output
    }

    data class RenderMeta(
        val document: Document,
        var workingDirectory: Path,
        val fsService: FileSystemService,
        val fileName: String,
        var output: RenderedDocument? = null,
        val cleanup: MutableList<(RenderMeta) -> Unit> = mutableListOf(),
    )
}
