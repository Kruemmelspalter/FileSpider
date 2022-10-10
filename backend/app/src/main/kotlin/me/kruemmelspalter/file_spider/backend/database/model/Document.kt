package me.kruemmelspalter.file_spider.backend.database.model

import org.springframework.data.annotation.Id
import java.sql.Timestamp
import java.util.UUID

data class Document(
    @Id val id: UUID,
    val title: String,
    val added: Timestamp,
    val renderer: String,
    val editor: String,
    val mimeType: String,
)
