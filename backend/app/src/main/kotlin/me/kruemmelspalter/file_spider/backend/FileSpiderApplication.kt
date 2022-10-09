package me.kruemmelspalter.file_spider.backend

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import javax.sql.DataSource

@SpringBootApplication
class FileSpiderApplication {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val config = ConfigFactory.load()

    @Autowired private val dataSource: DataSource? = null
    @Autowired private val jdbcTemplate: JdbcTemplate? = null

    @EventListener(ApplicationReadyEvent::class)
    fun initializeFSController() {
        FileSystemController.initialize()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun createTablesIfNonexistent() {
        jdbcTemplate!!.queryForObject(
            "select count(*) from information_schema.tables where table_name='Document' or table_name='Tag'"
        ) { rs, _ ->
            if (rs.getInt(1) != 2) {
                logger.warn("Not all tables 'Document' and 'Tag' exist; creating from init script")
                try {
                    ScriptUtils.executeSqlScript(dataSource!!.connection, ClassPathResource(config.getString("app.initFilePath")))
                } catch (e: Exception) {
                    logger.error("could not execute init script")
                    e.printStackTrace()
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<FileSpiderApplication>(*args)
}
