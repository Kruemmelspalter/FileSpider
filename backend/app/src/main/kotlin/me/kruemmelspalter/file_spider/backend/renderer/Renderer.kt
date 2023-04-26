package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.Document
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.slf4j.LoggerFactory
import org.springframework.util.FileSystemUtils
import java.io.FileInputStream
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date
import java.util.concurrent.TimeUnit

class Renderer(private val name: String) {
    private val renderSteps: MutableList<(RenderMeta) -> Unit> = mutableListOf()

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        internal val plainRenderer = Renderer("plain").output {
            RenderedDocument(
                FileInputStream(it.fsService.getFileFromID(it.document.id, it.document.fileExtension)),
                it.document.mimeType,
                it.fsService.getFileAttributesFromID(it.document.id, it.document.fileExtension).size(),
                it.fileName
            )
        }

        internal val htmlRenderer =
            Renderer("html").tempDir().resolveLinks().outputFile("text/html", "html") { it.fileName }

        internal val markdownRenderer = Renderer("markdown").tempDir()
            .command(10) {
                listOf(
                    "pandoc",
                    "-f",
                    "markdown",
                    "-o",
                    "out.html",
                    "-s",
                    "--katex=http://localhost:80/libs/katex/",
                    "--metadata",
                    "title=${it.document.title}",
                    "--self-contained",
                    it.fileName
                )
            }.resolveLinks { "out.html" }.outputFile("text/html", "html") { "out.html" }

        internal val latexRenderer =
            Renderer("latex").tempDir()
                .command(5) { listOf("pdflatex", "-draftmode", "-halt-on-error", it.fileName) }
                .command(5) { listOf("pdflatex", "-halt-on-error", it.fileName) }
                .outputFile("application/pdf", "pdf") { "${it.document.id}.pdf" }

        internal val xournalppRenderer = Renderer("xournalpp").tempDir().command(2) {
            listOf(
                "sh",
                "-c",
                "gunzip -c -S .${it.document.fileExtension} ${it.fileName} |sed -r -e 's/filename=\".*\\/${it.document.id}\\/(.*)\" /filename=\"\\1\" /g'|gzip>tmp.xopp"
            )
        }.command(10) { listOf("xournalpp", "-p", "out.pdf", "tmp.xopp") }
            .outputFile("application/pdf", "pdf") { "out.pdf" }

        internal val ebookRenderer =
            Renderer("ebook").tempDir().command(15, mapOf("QTWEBENGINE_CHROMIUM_FLAGS" to "--no-sandbox")) {
                listOf("ebook-convert", it.fileName, "out.pdf")
            }.outputFile("application/pdf", "pdf") { "out.pdf" }
    }

    fun addStep(step: (RenderMeta) -> Unit): Renderer {
        renderSteps.add(step)
        return this
    }

    fun output(documentProvider: (RenderMeta) -> RenderedDocument): Renderer {
        return addStep {
            it.output = documentProvider(it)
            buildLog(
                it,
                "=> Exporting file with name ${it.output!!.fileName} of type ${it.output!!.contentType} with length ${it.output!!.contentLength}"
            )
        }
    }

    fun outputFile(mimeType: String, fileExtension: String, filenameProvider: (RenderMeta) -> String): Renderer {
        return output {
            val filepath = Paths.get(it.workingDirectory.toString(), filenameProvider(it))
            logger.trace("serving file $filepath")
            buildLog(
                it,
                "=> Exporting file $filepath with name ${it.document.id}.$fileExtension of MIME type $mimeType"
            )
            RenderedDocument(
                FileInputStream(filepath.toString()), mimeType,
                Files.readAttributes(filepath, BasicFileAttributes::class.java).size(),
                "${it.document.id}.$fileExtension"
            )
        }
    }

    fun command(
        timeout: Long,
        environment: Map<String, String> = mapOf(),
        timeUnit: TimeUnit = TimeUnit.SECONDS,
        commandProvider: (RenderMeta) -> List<String>
    ): Renderer {
        return addStep {
            val command = commandProvider(it)
            buildLog(
                it,
                "=> Executing command $command in directory ${it.workingDirectory} with timeout $timeout $timeUnit"
            )
            val pb = ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(
                    ProcessBuilder.Redirect.appendTo(
                        it.fsService.getLogPathFromID(it.document.id).toFile()
                    )
                )
                .directory(it.workingDirectory.toFile())
            environment.toMap(pb.environment())
            val process = pb.start()
            if (!process.waitFor(
                    timeout,
                    timeUnit
                ) || process.exitValue() != 0
            ) {
                if (process.exitValue() != 0) buildLog(it, "=> Command returned exit code ${process.exitValue()}")
                else buildLog(it, "=> Command timed out (took longer than $timeout $timeUnit")
                throw RenderingException(String(it.fsService.readLog(it.document.id)!!.readAllBytes()))
            }
        }
    }

    fun tempDir(): Renderer {
        return addStep {
            val tmpPath = Paths.get(
                it.fsService.getTemporaryDirectory().toString(), "filespider-${it.document.id}-${Date().time}"
            )
            logger.trace("establishing temp dir at $tmpPath")
            buildLog(it, "=> Initializing temp dir at $tmpPath")
            Files.createDirectories(tmpPath)
            FileSystemUtils.copyRecursively(it.fsService.getDirectoryPathFromID(it.document.id), tmpPath)
            it.workingDirectory = tmpPath
            it.cleanup.add { meta ->
                buildLog(meta, "=> cleaning up temp dir $tmpPath")
                FileSystemUtils.deleteRecursively(tmpPath)
            }
        }
    }

    fun replace(
        replacements: Map<String, String>,
        filenameProvider: (RenderMeta) -> String = { it.fileName }
    ): Renderer {
        return command(1) { meta ->
            buildLog(meta, "=> replacing ${replacements.size} mappings")
            listOf("sed", "-i", "-E") + replacements.map { listOf("-e", "s/${it.key}/${it.value}/g") }
                .flatten() + listOf(Paths.get(meta.workingDirectory.toString(), filenameProvider(meta)).toString())
        }
    }

    fun resolveLinks(filenameProvider: (RenderMeta) -> String = { it.fileName }): Renderer {
        return replace(
            mapOf(
                "<a href=\"%5E([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})\">(.+)<\\/a>" to "<a href=\"..\\/..\\/\\1\" target=\"_parent\">\\2<\\/a>",
                "\\\"\\%5E([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})(\\/?.*\\\")" to "\"..\\/\\1\\/rendered\\2"
            ),
            filenameProvider
        )
    }

    fun copy(srcProvider: (RenderMeta) -> String, dstProvider: (RenderMeta) -> String): Renderer {
        return addStep {
            val src = srcProvider(it)
            val dst = dstProvider(it)
            buildLog(it, "=> copying from $src to $dst")
            Files.copy(
                Paths.get(it.workingDirectory.toString(), src),
                Paths.get(it.workingDirectory.toString(), dst)
            )
        }
    }

    fun render(
        document: Document,
        fsService: FileSystemService
    ): RenderedDocument? {
        return render(
            RenderMeta(
                document,
                fsService.getDirectoryPathFromID(document.id),
                fsService,
                document.id.toString() + if (document.fileExtension != null) "." + document.fileExtension else ""
            )
        )
    }

    private fun render(meta: RenderMeta): RenderedDocument? {
        logger.debug("Rendering using renderer $name")
        meta.fsService.getLogPathFromID(meta.document.id).toFile().delete()
        buildLog(
            meta,
            "==> Starting render using renderer $name of document ${meta.document.id}/${meta.fileName} in ${meta.workingDirectory}"
        )
        for ((i, step) in renderSteps.withIndex()) {
            buildLog(meta, "==> Executing step $i/${renderSteps.size}")
            step(meta)
        }
        for (step in meta.cleanup) {
            step(meta)
        }
        return meta.output!!
    }

    data class RenderMeta(
        val document: Document,
        var workingDirectory: Path,
        val fsService: FileSystemService,
        val fileName: String,
        var output: RenderedDocument? = null,
        val cleanup: MutableList<(RenderMeta) -> Unit> = mutableListOf(),
    )

    private fun buildLog(meta: RenderMeta, s: String, end: String = "\n") {
        val writer = FileWriter(meta.fsService.getLogPathFromID(meta.document.id).toFile(), true)
        writer.append(s + end)
        writer.close()
    }
}
