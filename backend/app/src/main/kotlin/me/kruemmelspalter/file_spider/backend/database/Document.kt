package me.kruemmelspalter.file_spider.backend.database

import org.springframework.data.annotation.Id
import java.util.UUID

data class Document(
    @Id val id: UUID,
    val title: String,
    val renderer: String,
    val editor: String,
    val mimeType: String,
)
