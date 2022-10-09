package me.kruemmelspalter.file_spider.backend.database

import java.util.UUID

data class Tag(
    val document: UUID,
    val tag: String,
)
