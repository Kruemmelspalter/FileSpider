package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
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
                documentFromResultSet(rs)
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

    override fun filterDocuments(filter: List<String>): List<Document> {
        if (filter.isEmpty()) return ArrayList()

        val reqBuilder = StringBuilder().append(
            "select Document.*, Tags.tagCount from Document " +
                    "inner join (select document, count(tag) as tagCount from Tag where tag=? "
        )
        for (i in 2..filter.size) reqBuilder.append("or tag=? ")
        reqBuilder.append("group by document) as Tags where Tags.document = Document.id;")

        val documents = jdbcTemplate!!.query(
            {
                val stmt = it.prepareStatement(reqBuilder.toString())
                for (i in filter.indices) {
                    stmt.setString(i + 1, filter[i])
                }
                return@query stmt
            },
            { rs, _ ->
                if (rs.getInt(7) != filter.size) return@query null
                documentFromResultSet(rs)
            }
        )
        return documents.filterNotNull()
    }

    private fun documentFromResultSet(rs: ResultSet) = Document(
        UUID.fromString(rs.getString(1)),
        rs.getString(2),
        rs.getTimestamp(3),
        rs.getString(4),
        rs.getString(5),
        rs.getString(6)
    )
}
