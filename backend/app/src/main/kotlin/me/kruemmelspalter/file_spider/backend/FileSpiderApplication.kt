package me.kruemmelspalter.file_spider.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.ConfigurableWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.Properties

@SpringBootApplication
@Suppress("SpringComponentScan")
@EnableJdbcRepositories(basePackages = ["com.springdata"])
class FileSpiderApplication : WebMvcConfigurer, WebServerFactoryCustomizer<ConfigurableWebServerFactory>,
    AbstractJdbcConfiguration() {

    // enable OPTIONS request
    @Bean(name = [DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME])
    fun dispatcherServlet(): DispatcherServlet? {
        val dispatcherServlet = DispatcherServlet()
        dispatcherServlet.setDispatchOptionsRequest(true)
        return dispatcherServlet
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/libs/katex/**").addResourceLocations("classpath:/katex/")
    }

    override fun customize(factory: ConfigurableWebServerFactory) {
        factory.setPort(80)
    }

    // basically disable CORS
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**").allowedOrigins("*").allowedHeaders("*").allowedMethods("*")
    }
}

fun main(args: Array<String>) {
    val props = Properties()

    props["spring.servlet.multipart.max-file-size"] = "1024MB"
    props["spring.servlet.multipart.max-request-size"] = "1024MB"

    SpringApplicationBuilder(FileSpiderApplication::class.java).properties(props).run(*args)
}
