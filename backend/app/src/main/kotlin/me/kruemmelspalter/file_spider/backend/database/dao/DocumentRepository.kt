package me.kruemmelspalter.file_spider.backend.database.dao

import me.kruemmelspalter.file_spider.backend.database.model.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.Optional
import java.util.UUID

@Repository
class DocumentRepository : DocumentDao {

    @Autowired
    val jdbcTemplate: JdbcTemplate? = null

    @Autowired
    val namedParameterJdbcOperations: NamedParameterJdbcOperations? = null

    override fun getDocument(id: UUID): Optional<Document> {
        val documents = jdbcTemplate!!.query(
            "select * from Document where id = ?", { rs, _ -> documentFromResultSet(rs) }, id.toString()
        )
        if (documents.size != 1) return Optional.empty()
        return Optional.of(documents[0])
    }

    override fun getTags(id: UUID): List<String> {
        return jdbcTemplate!!.query(
            "select tag from Tag where document = ?", { rs, _ -> rs.getString(1) }, id.toString()
        )
    }

    override fun insertDocument(document: Document) {
        TODO("Not yet implemented")
    }

    override fun deleteDocument(id: UUID) {
        TODO("Not yet implemented")
    }

    override fun filterDocuments(posFilter: List<String>, negFilter: List<String>): List<Document> {
        if (posFilter.isEmpty()) return ArrayList()

        return namedParameterJdbcOperations!!.query(
            "select Document.* from Document left join (select document, count(tag) as tagCount from Tag where " +
                "tag in (:posTags) group by document) as postags on postags.document = Document.id " +
                (
                    if (negFilter.isEmpty()) "join (select NULL as tagCount) as negtags" else
                        "left join (select document, count(tag) as tagCount from Tag " +
                            "where tag in (:negTags) group by document) as negtags on negtags.document = Document.id"
                    ) +
                " where postags.tagCount = :ptCount and negtags.tagCount IS NULL",
            MapSqlParameterSource(mapOf("posTags" to posFilter, "negTags" to negFilter, "ptCount" to posFilter.size))
        ) { rs, _ -> documentFromResultSet(rs) }
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
