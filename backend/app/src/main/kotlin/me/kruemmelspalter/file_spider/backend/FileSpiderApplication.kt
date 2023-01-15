package me.kruemmelspalter.file_spider.backend

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.web.servlet.DispatcherServlet
import java.util.Properties
import javax.sql.DataSource

@SpringBootApplication
class FileSpiderApplication {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val config = ConfigFactory.load()

    @Autowired
    private val dataSource: DataSource? = null

    @Autowired
    private val jdbcTemplate: JdbcTemplate? = null

    @EventListener(ApplicationReadyEvent::class)
    fun createTablesIfNonexistent() {
        jdbcTemplate!!.queryForObject(
            "select count(*) from information_schema.tables where table_name in ('Document', 'Tag', 'Cache')"
        ) { rs, _ ->
            if (rs.getInt(1) != 3) {
                logger.warn("Not all tables 'Document', 'Tag' and 'Cache' exist; creating from init script")
                try {
                    ScriptUtils.executeSqlScript(
                        dataSource!!.connection,
                        EncodedResource(ClassPathResource(config.getString("app.initFilePath"))),
                        true,
                        false,
                        "--",
                        ";",
                        "##/*",
                        "*/##"
                    )
                } catch (e: Exception) {
                    logger.error("could not execute init script")
                    e.printStackTrace()
                }
            }
        }
    }

    @Bean(name = [DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME])
    fun dispatcherServlet(): DispatcherServlet? {
        val dispatcherServlet = DispatcherServlet()
        dispatcherServlet.setDispatchOptionsRequest(true)
        return dispatcherServlet
    }
}

fun main(args: Array<String>) {
    val props = Properties()

    props["spring.servlet.multipart.max-file-size"] = "1024MB"
    props["spring.servlet.multipart.max-request-size"] = "1024MB"

    SpringApplicationBuilder(FileSpiderApplication::class.java).properties(props).run(*args)
}
