package me.kruemmelspalter.file_spider.backend.services

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.util.FileSystemUtils
import org.springframework.web.server.ResponseStatusException
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import javax.servlet.ServletContext

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

    fun getDocumentPathFromID(id: UUID, fileExtension: String?): Path {
        return Paths.get(getDirectoryPathFromID(id).toString(), id.toString() + if (fileExtension != null) ".$fileExtension" else "")
    }

    fun getDirectoryPathFromID(id: UUID): Path {
        return Paths.get(documentDirectory.toString(), id.toString()).toAbsolutePath()
    }

    fun getFileFromID(id: UUID, fileExtension: String?): File {
        return getDocumentPathFromID(id, fileExtension).toFile()
    }

    fun getInputStreamFromID(id: UUID, fileExtension: String?): InputStream {
        return FileInputStream(getFileFromID(id, fileExtension))
    }

    fun getContentFromID(id: UUID, fileExtension: String?): String {
        val resultBuilder = StringBuilder()
        val br = BufferedReader(FileReader(getFileFromID(id, fileExtension)))
        br.use {
            var line = br.readLine()
            while (line != null) {
                resultBuilder.append(line).append("\n")
                line = br.readLine()
            }
        }
        return resultBuilder.toString()
    }

    fun getFileAttributesFromID(id: UUID, fileExtension: String?): BasicFileAttributes {
        return Files.readAttributes(getDocumentPathFromID(id, fileExtension), BasicFileAttributes::class.java)
    }

    fun getTemporaryDirectory(): Path {
        return tmpDirectory
    }

    fun getLogPathFromID(id: UUID): Path {
        return Paths.get(tmpDirectory.toString(), "$id.log").toAbsolutePath()
    }

    fun readLog(id: UUID): InputStream? {
        val file = getLogPathFromID(id).toFile()
        return if (!file.exists()) null
        else FileInputStream(file)
    }

    fun createDocument(id: UUID, fileExtension: String?) {
        val directory = getDirectoryPathFromID(id).toFile()
        if (!directory.mkdir()) throw Error("could not create document directory")
        val file = getFileFromID(id, fileExtension)
        if (!file.createNewFile()) throw Error("could not create document file")
    }

    fun writeToDocument(id: UUID, fileExtension: String?, stream: InputStream) {
        val outStream = FileOutputStream(getFileFromID(id, fileExtension))
        stream.transferTo(outStream)
        outStream.close()
    }

    fun deleteDocument(id: UUID) {
        FileSystemUtils.deleteRecursively(getDirectoryPathFromID(id))
    }

    fun getDocumentResource(id: UUID, fileName: String, servletContext: ServletContext): Resource? {
        var resource: Resource? = null
        if (fileName.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        try {
            resource = FileSystemResource(Paths.get(getDirectoryPathFromID(id).toString(), fileName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resource
    }
}
