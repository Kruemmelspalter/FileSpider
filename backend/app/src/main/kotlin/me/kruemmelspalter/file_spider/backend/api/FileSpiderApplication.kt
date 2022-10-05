package me.kruemmelspalter.file_spider.backend.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FileSpiderApplication {
    companion object {
        fun run(args: Array<String>) {
            runApplication<FileSpiderApplication>(*args)
        }
    }
}
