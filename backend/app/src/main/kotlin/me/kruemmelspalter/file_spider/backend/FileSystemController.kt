package me.kruemmelspalter.file_spider.backend

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

object FileSystemController {
    private val logger = LoggerFactory.getLogger(FileSystemController.javaClass)
    private val config = ConfigFactory.load()
    val documentDirectory = Paths.get(config.getString("app.documentDirectory")).toAbsolutePath()
    fun initialize() {
        if (!Files.isDirectory(documentDirectory)) {
            logger.warn("Document Directory doesn't exist; creating")
            Files.createDirectories(documentDirectory)
        }
    }

    fun getPathFromID(id: UUID): Path {
        return Paths.get(documentDirectory.toString(), id.toString())
    }

    fun getFileFromID(id: UUID): File {
        return File(getPathFromID(id).toUri())
    }

    fun getContentFromID(id: UUID): String {
        val resultBuilder = StringBuilder()
        val br = BufferedReader(FileReader(getFileFromID(id)))
        br.use {
            var line = br.readLine()
            while (line != null) {
                resultBuilder.append(line).append("\n")
                line = br.readLine()
            }
        }
        return resultBuilder.toString()
    }
}
