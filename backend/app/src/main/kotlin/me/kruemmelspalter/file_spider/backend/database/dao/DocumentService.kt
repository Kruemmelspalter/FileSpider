package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.FileSystemService
import me.kruemmelspalter.file_spider.backend.database.model.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.Optional
import java.util.UUID

@Repository
class DocumentService : DocumentDao {

    @Autowired
    val jdbcTemplate: JdbcTemplate? = null

    @Autowired
    val fsService: FileSystemService? = null

    override fun getDocumentMetaById(id: UUID): Optional<DocumentMeta> {
        val documents = jdbcTemplate!!.query(
            "select * from Document where id = ?",
            { rs, _ ->
                Document(
                    UUID.fromString(rs.getString(1)),
                    rs.getString(2),
                    rs.getTimestamp(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6)
                )
            }, id.toString()
        )
        if (documents.size != 1) return Optional.empty()
        val document = documents[0]

        val tags = jdbcTemplate!!.query(
            "select tag from Tag where document = ?",
            { rs, _ ->
                rs.getString(1)
            }, id.toString()
        )

        val attributes = fsService!!.getFileAttributesFromID(id)

        return Optional.of(
            DocumentMeta(
                document,
                Timestamp(attributes.creationTime().toMillis()),
                Timestamp(attributes.lastModifiedTime().toMillis()),
                Timestamp(attributes.lastAccessTime().toMillis()),
                tags
            )
        )
    }

    override fun insertDocument(id: UUID, document: Document) {
        TODO("Not yet implemented")
    }

    override fun deleteDocument(id: UUID) {
        TODO("Not yet implemented")
    }

    override fun filterDocuments(filter: String): List<DocumentMeta> {
        TODO("Not yet implemented")
    }
}
