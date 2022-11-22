package me.kruemmelspalter.file_spider.backend

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.springframework.boot.web.server.ConfigurableWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import javax.sql.DataSource

@Suppress("SpringComponentScan")
@Configuration
@EnableJdbcRepositories(basePackages = ["com.springdata"])
class FileSpiderConfiguration : WebMvcConfigurer, WebServerFactoryCustomizer<ConfigurableWebServerFactory>,
    AbstractJdbcConfiguration() {
    val config: Config = ConfigFactory.load()

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
    fun namedParameterJdbcOperations(dataSource: DataSource): NamedParameterJdbcOperations {
        return NamedParameterJdbcTemplate(dataSource)
    }

    @Bean
    fun transactionManager(dataSource: DataSource): PlatformTransactionManager {
        return DataSourceTransactionManager(dataSource)
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
    }

    override fun customize(factory: ConfigurableWebServerFactory) {
        factory.setPort(80)
    }

    @Bean
    fun jdbcTemplate(): JdbcTemplate {
        return JdbcTemplate(dataSource())
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**").allowedOrigins("*").allowedHeaders("*").allowedMethods("*")
        // TODO proper CORS, this is just for debugging / developing
    }
}
