package com.newbieking.springtestgen.utils

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 诊断日志工具：将关键执行路径写入用户主目录下的日志文件，
 * 方便在插件调试时排查问题。
 */
object DiagnosticLogger {

    private val log = Logger.getInstance(DiagnosticLogger::class.java)
    private val LOG_FILE: File by lazy {
        val dir = File(System.getProperty("user.home"), ".spring-test-gen")
        dir.mkdirs()
        File(dir, "diagnostic.log")
    }
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Synchronized
    fun log(message: String) {
        val time = LocalDateTime.now().format(TIME_FORMAT)
        val line = "[$time] $message"
        // 同时输出到 IDE 内置日志和文件
        log.info("DIAG: $message")
        try {
            PrintWriter(FileWriter(LOG_FILE, true)).use { it.println(line) }
        } catch (_: Exception) { }
    }

    fun getLogPath(): String = LOG_FILE.absolutePath

    fun clear() {
        try {
            LOG_FILE.writeText("")
        } catch (_: Exception) { }
    }
}
