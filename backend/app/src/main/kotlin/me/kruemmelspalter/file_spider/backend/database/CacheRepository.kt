package me.kruemmelspalter.file_spider.backend.database

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.support.DataAccessUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CacheRepository {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    fun getCacheEntry(id: UUID): CacheEntry? {
        return DataAccessUtils.singleResult(
            jdbcTemplate.query("select * from Cache where document=?", { rs, _ ->
                CacheEntry(
                    UUID.fromString(rs.getString(1)),
                    rs.getBytes(2),
                    rs.getString(3),
                    rs.getString(4)
                )
            }, id.toString())
        )
    }

    fun setCacheEntry(id: UUID, hash: ByteArray, mimeType: String, fileName: String) {
        jdbcTemplate.update(
            "insert into Cache(document, hash, mimeType, fileName) values (?,?,?,?) on duplicate key update hash=?, mimeType=?, fileName=?",
            id.toString(),
            hash,
            mimeType,
            fileName,
            hash,
            mimeType,
            fileName
        )
    }

    data class CacheEntry(val document: UUID, val hash: ByteArray, val mimeType: String, val fileName: String)
}
