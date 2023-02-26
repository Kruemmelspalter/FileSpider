package me.kruemmelspalter.file_spider.backend.renderer

import me.kruemmelspalter.file_spider.backend.database.CacheRepository
import me.kruemmelspalter.file_spider.backend.services.FileSystemService
import me.kruemmelspalter.file_spider.backend.services.RenderedDocument
import org.slf4j.LoggerFactory
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

typealias Hash = ByteArray
@Service
class RenderCache {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var fsService: FileSystemService

    @Autowired
    private lateinit var cacheRepository: CacheRepository

    fun isCacheValid(id: UUID): Boolean {
        logger.debug("validating cache for id $id")
        val computedHash = computeHash(id)
        val cachedHash = cacheRepository.getCacheEntry(id)?.hash
        val isValid = computedHash.contentEquals(cachedHash)
        logger.debug("computed: ${computedHash.toHex()} cached: ${cachedHash?.toHex()} valid: $isValid")
        return isValid
    }

    fun getCachedRender(id: UUID): RenderedDocument? {
        val cacheEntry = cacheRepository.getCacheEntry(id) ?: return null
        logger.trace("serving cached file ${Paths.get(fsService.getCacheDirectory().toString(), id.toString())}")
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

    fun cacheRender(id: UUID, hash: Hash, renderedDocument: RenderedDocument?): RenderedDocument? {
        logger.debug("setting cache entry for id $id")
        if (renderedDocument == null) return null
        val cacheFile = Paths.get(fsService.getCacheDirectory().toString(), id.toString()).toFile()
        val outStream = FileOutputStream(cacheFile)
        renderedDocument.stream.transferTo(outStream)
        outStream.close()
        cacheRepository.setCacheEntry(id, hash, renderedDocument.contentType, renderedDocument.fileName)
        return RenderedDocument(
            FileInputStream(cacheFile),
            renderedDocument.contentType,
            Files.readAttributes(cacheFile.toPath(), BasicFileAttributes::class.java).size(),
            renderedDocument.fileName
        )
    }

    fun computeHash(id: UUID): Hash {
        logger.debug("computing hash for id $id")
        val md = MessageDigest.getInstance("MD5")
        val directory = fsService.getDirectoryPathFromID(id)
        val files = Files.find(directory, 5, { _, _ -> true })
        files.forEach { path ->
            logger.trace("adding file $path")
            md.update(path.toString().toByteArray())
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            md.update(longToByteArray(attributes.size()))
            md.update(longToByteArray(attributes.lastModifiedTime().toMillis()))
        }
        return md.digest()
    }

    private fun longToByteArray(value: Long): Hash {
        val bytes = Hash(8)
        ByteBuffer.wrap(bytes).putLong(value)
        return bytes.copyOfRange(4, 8)
    }

    private fun Hash.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}
