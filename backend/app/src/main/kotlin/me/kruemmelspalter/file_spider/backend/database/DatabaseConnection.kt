package me.kruemmelspalter.file_spider.backend.database

import com.typesafe.config.ConfigFactory
import org.apache.ibatis.jdbc.ScriptRunner
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.io.Reader
import java.sql.Connection
import java.sql.DriverManager

object DatabaseConnection {
    private val logger = LoggerFactory.getLogger(DatabaseConnection.javaClass)
    private val config = ConfigFactory.load()
    lateinit var conn: Connection

    fun connect(host: String, port: Int, database: String, username: String, password: String) {
        conn = DriverManager.getConnection("jdbc:mysql://$host:$port/$database", username, password)

        val stmt = conn.createStatement()

        val rs = stmt.executeQuery("select count(*) from information_schema.tables where table_name='Document' or table_name='Tag'")
        rs.next()
        if (rs.getInt(1) != 2) {
            logger.warn("Not all tables 'Document' and 'Tag' exist; creating from init script")
            try {
                executeScriptFromResource(config.getString("app.initFilePath"))
            } catch (e: Exception) {
                logger.error("could not execute init script")
                e.printStackTrace()
            }
        }
        stmt.close()
    }

    fun executeScript(reader: Reader) {
        ScriptRunner(conn).runScript(reader)
    }

    fun executeScriptFromResource(path: String) {
        executeScript(InputStreamReader(javaClass.getClassLoader().getResourceAsStream(path)))
    }
}
