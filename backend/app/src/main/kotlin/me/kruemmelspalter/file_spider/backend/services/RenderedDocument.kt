package me.kruemmelspalter.file_spider.backend.services

import java.io.InputStream

data class RenderedDocument(
    val stream: InputStream,
    val contentType: String,
    val contentLength: Long,
    val fileName: String,
)
