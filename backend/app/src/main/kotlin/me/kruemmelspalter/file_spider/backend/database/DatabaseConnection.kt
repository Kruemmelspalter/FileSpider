package me.kruemmelspalter.file_spider.backend.database

import org.apache.ibatis.jdbc.ScriptRunner
import java.io.InputStreamReader
import java.io.Reader
import java.sql.Connection
import java.sql.DriverManager

object DatabaseConnection {
    lateinit var conn: Connection

    fun connect(host: String, port: Int, database: String, username: String, password: String) {
        conn = DriverManager.getConnection("jdbc:mysql://$host:$port/$database", username, password)
    }

    fun executeScript(reader: Reader) {
        ScriptRunner(conn).runScript(reader)
    }

    fun executeScriptFromResource(path: String) {
        executeScript(InputStreamReader(javaClass.getClassLoader().getResourceAsStream(path)))
    }
}
