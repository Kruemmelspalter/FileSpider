package me.kruemmelspalter.file_spider.backend.database.dao

import java.nio.file.attribute.FileTime
import java.sql.Timestamp
import java.util.*

data class DocumentMeta(
    val id: UUID,
    val title: String,
    val added: Timestamp,
    val created: FileTime,
    val modified: FileTime,
    val accessed: FileTime,
    val tags: Array<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentMeta

        if (id != other.id) return false
        if (title != other.title) return false
        if (added != other.added) return false
        if (created != other.created) return false
        if (modified != other.modified) return false
        if (accessed != other.accessed) return false
        if (!tags.contentEquals(other.tags)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + added.hashCode()
        result = 31 * result + created.hashCode()
        result = 31 * result + modified.hashCode()
        result = 31 * result + accessed.hashCode()
        result = 31 * result + tags.contentHashCode()
        return result
    }
}