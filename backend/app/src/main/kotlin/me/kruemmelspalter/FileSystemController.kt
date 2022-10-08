package me.kruemmelspalter.file_spider.backend

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

object FileSystemController {
    private val logger = LoggerFactory.getLogger(FileSystemController.javaClass)
    private val config = ConfigFactory.load()
    val documentDirectory = Paths.get(config.getString("documentDirectory"))
    fun initialize() {
        if (!Files.isDirectory(documentDirectory)) {
            logger.warn("Document Directory doesn't exist; creating")
            Files.createDirectories(documentDirectory)
        }
    }
}
