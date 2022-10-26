package me.kruemmelspalter.file_spider.backend.services

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

@Service
class FileSystemService {
    private val logger = LoggerFactory.getLogger(FileSystemService::class.java)
    private val config = ConfigFactory.load()
    private val documentDirectory = Paths.get(config.getString("app.documentDirectory")).toAbsolutePath()
    private val tmpDirectory = Paths.get(config.getString("app.tmpDirectory")).toAbsolutePath()

    init {
        if (!Files.isReadable(documentDirectory)) throw Exception("Document Directory isn't readable")
        if (!Files.isWritable(documentDirectory)) throw Exception("Document Directory isn't writable")
        if (!Files.isDirectory(documentDirectory)) {
            logger.warn("Document Directory doesn't exist; creating")
            Files.createDirectories(documentDirectory)
        }
        if (!Files.exists(tmpDirectory)) throw Exception("Temporary Directory doesn't exist")
        if (!Files.isReadable(tmpDirectory)) throw Exception("Temporary Directory isn't readable")
        if (!Files.isWritable(tmpDirectory)) throw Exception("Temporary Directory isn't writable")
    }

    fun getDocumentPathFromID(id: UUID): Path {
        return Paths.get(getDirectoryPathFromID(id).toString(), id.toString())
    }

    fun getDirectoryPathFromID(id: UUID): Path {
        return Paths.get(documentDirectory.toString(), id.toString()).toAbsolutePath()
    }

    fun getFileFromID(id: UUID): File {
        return File(getDocumentPathFromID(id).toUri())
    }

    fun getInputStreamFromID(id: UUID): InputStream {
        return FileInputStream(getFileFromID(id))
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

    fun getFileAttributesFromID(id: UUID): BasicFileAttributes {
        return Files.readAttributes(getDocumentPathFromID(id), BasicFileAttributes::class.java)
    }

    fun getTemporaryDirectory(): Path {
        return tmpDirectory
    }

    fun getLogPathFromID(id: UUID): Path {
        return Paths.get(tmpDirectory.toString(), "$id.log").toAbsolutePath()
    }

    fun readLog(id: UUID): InputStream? {
        val file = File(getLogPathFromID(id).toString())
        return if (!file.exists()) null
        else FileInputStream(file)
    }
}
