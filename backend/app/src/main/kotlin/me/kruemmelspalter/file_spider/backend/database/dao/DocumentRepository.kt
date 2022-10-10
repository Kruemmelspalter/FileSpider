package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import me.kruemmelspalter.file_spider.backend.services.DocumentMeta
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
class DocumentRepository : DocumentDao {

    @Autowired
    val jdbcTemplate: JdbcTemplate? = null

    override fun getDocument(id: UUID): Optional<Document> {
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
        return Optional.of(documents[0])
    }

    override fun getTags(id: UUID): List<String> {
        return jdbcTemplate!!.query(
            "select tag from Tag where document = ?",
            { rs, _ ->
                rs.getString(1)
            }, id.toString()
        )
    }

    override fun insertDocument(document: Document) {
        TODO("Not yet implemented")
    }

    override fun deleteDocument(id: UUID) {
        TODO("Not yet implemented")
    }

    override fun filterDocuments(filter: String): List<DocumentMeta> {
        TODO("Not yet implemented")
    }
}
