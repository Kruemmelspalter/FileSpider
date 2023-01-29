package me.kruemmelspalter.file_spider.backend.database

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
class DatabaseConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val config = ConfigFactory.load()

    @EventListener(ApplicationReadyEvent::class)
    fun createTablesIfNonexistent() {
        jdbcTemplate().queryForObject(
            "select count(*) from information_schema.tables where table_name in ('Document', 'Tag', 'Cache')"
        ) { rs, _ ->
            if (rs.getInt(1) != 3) {
                logger.warn("Not all tables 'Document', 'Tag' and 'Cache' exist; creating from init script")
                try {
                    ScriptUtils.executeSqlScript(
                        dataSource().connection,
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

    @Bean
    fun jdbcTemplate(): JdbcTemplate {
        return JdbcTemplate(dataSource())
    }

    @Bean
    fun dataSource(): DataSource {
        val dataSource = DriverManagerDataSource()
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver")
        dataSource.url =
            "jdbc:mysql://${config.getString("database.host")}:${config.getString("database.port")}/" +
                    config.getString("database.database")
        dataSource.username = config.getString("database.username")
        dataSource.password = config.getString("database.password")
        return dataSource
    }

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager {
        return DataSourceTransactionManager(dataSource)
    }

    @Bean
    fun namedParameterJdbcOperations(dataSource: DataSource): NamedParameterJdbcOperations {
        return NamedParameterJdbcTemplate(dataSource)
    }
}
