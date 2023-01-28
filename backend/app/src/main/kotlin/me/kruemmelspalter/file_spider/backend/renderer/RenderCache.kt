package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.CacheRepository
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

@Service
class RenderCache {

    @Autowired
    private lateinit var fsService: FileSystemService

    @Autowired
    private lateinit var cacheRepository: CacheRepository

    fun isCacheValid(id: UUID): Boolean {
        return computeHash(id).contentEquals(cacheRepository.getCacheEntry(id)?.hash)
    }

    fun getCachedRender(id: UUID): RenderedDocument? {
        val cacheEntry = cacheRepository.getCacheEntry(id) ?: return null
        return RenderedDocument(
            FileInputStream(Paths.get(fsService.getCacheDirectory().toString(), id.toString()).toFile()),
            cacheEntry.mimeType,
            Files.readAttributes(
                Paths.get(fsService.getCacheDirectory().toString(), id.toString()),
                BasicFileAttributes::class.java
            ).size(),
            cacheEntry.fileName
        )
    }

    fun cacheRender(id: UUID, renderedDocument: RenderedDocument?): RenderedDocument? {
        if (renderedDocument == null) return null
        val cacheFile = Paths.get(fsService.getCacheDirectory().toString(), id.toString()).toFile()
        val outStream = FileOutputStream(cacheFile)
        renderedDocument.stream.transferTo(outStream)
        outStream.close()
        cacheRepository.setCacheEntry(id, computeHash(id), renderedDocument.contentType, renderedDocument.fileName)
        return RenderedDocument(
            FileInputStream(cacheFile),
            renderedDocument.contentType,
            renderedDocument.contentLength,
            renderedDocument.fileName
        )
    }

    private fun computeHash(id: UUID): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        val directory = fsService.getDirectoryPathFromID(id)
        val files = Files.find(directory, 5, { _, _ -> true })
        files.forEach { path ->
            md.update(path.toString().toByteArray())
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            md.update(longToByteArray(attributes.size()))
            md.update(longToByteArray(attributes.lastModifiedTime().toMillis()))
        }
        return md.digest()
    }

    private fun longToByteArray(value: Long): ByteArray {
        val bytes = ByteArray(8)
        ByteBuffer.wrap(bytes).putLong(value)
        return bytes.copyOfRange(4, 8)
    }
}
