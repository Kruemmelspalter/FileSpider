package me.kruemmelspalter.file_spider.backend.database

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class DocumentRepository {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var namedParameterJdbcOperations: NamedParameterJdbcOperations

    fun getDocument(id: UUID): Document? {
        val documents = jdbcTemplate.query(
            "select * from Document where id = ?", { rs, _ -> documentFromResultSet(rs) }, id.toString()
        )
        if (documents.size != 1) return null
        return documents[0]
    }

    fun getTags(id: UUID): List<String> {
        return jdbcTemplate.query(
            "select tag from Tag where document = ?", { rs, _ -> rs.getString(1) }, id.toString()
        )
    }

    fun deleteDocument(id: UUID) {
        jdbcTemplate.update("delete from Document where id=?", id.toString())
        jdbcTemplate.update("delete from Tag where document=?", id.toString())
    }

    fun filterDocuments(posFilter: List<String>, negFilter: List<String>): List<Document> {
        if (posFilter.isEmpty()) return ArrayList()

        return namedParameterJdbcOperations.query(
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

    fun removeTags(id: UUID, removeTags: List<String>) {
        jdbcTemplate.batchUpdate("delete from Tag where document=? and tag=?", removeTags, 100) { ps, s ->
            ps.setString(1, id.toString()); ps.setString(2, s)
        }
    }

    fun addTags(id: UUID, addTags: List<String>) {
        jdbcTemplate.batchUpdate("insert into Tag(document, tag) values (?,?)", addTags, 100) { ps, s ->
            ps.setString(1, id.toString()); ps.setString(2, s)
        }
    }

    fun setTitle(id: UUID, title: String) {
        jdbcTemplate.update("update Document set title=? where id=?", title, id.toString())
    }

    private fun documentFromResultSet(rs: ResultSet) = Document(
        UUID.fromString(rs.getString(1)),
        rs.getString(2),
        rs.getTimestamp(3),
        rs.getString(4),
        rs.getString(5),
        rs.getString(6),
        rs.getString(7)
    )

    fun createDocument(
        uuid: UUID,
        title: String,
        renderer: String,
        editor: String,
        mimeType: String,
        tags: List<String>,
        fileExtension: String?,
    ) {

        if (jdbcTemplate.update(
                "insert into Document (id, title, renderer, editor, mimeType, fileExtension) values (?,?,?,?,?,?)",
                uuid.toString(),
                title,
                renderer,
                editor,
                mimeType,
                fileExtension
            ) != 1
        ) throw RuntimeException("document creation did not work / didn't affect one row")

        jdbcTemplate.batchUpdate("insert into Tag (tag, document) values (?, ?)", tags, 100) { ps, s ->
            ps.setString(1, s); ps.setString(2, uuid.toString())
        }
    }
}
