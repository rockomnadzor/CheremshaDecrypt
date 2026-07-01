package com.github.cheremsha.decrypt.crypt.app.util

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, SUCCESS, ERROR }

data class LogEntry(
    val time: String,
    val tag: String,
    val message: String,
    val level: LogLevel
)

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var logFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(dir, "app_${fileFmt.format(Date())}.log")
        loadFromFile()
        startLogcat()
    }

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(fmt.format(Date()), tag, message, level)
        _logs.value = (_logs.value + entry).takeLast(1000)
        scope.launch {
            logFile?.appendText("${entry.time} [${entry.level}] [${entry.tag}] ${entry.message}\n")
        }
    }

    fun clear() {
        _logs.value = emptyList()
        scope.launch { logFile?.writeText("") }
    }

    private fun loadFromFile() {
        scope.launch {
            val file = logFile ?: return@launch
            if (!file.exists()) return@launch
            val entries = file.readLines().mapNotNull { line ->
                runCatching {
                    val time = line.substring(0, 8)
                    val level = when {
                        line.contains("[ERROR]")   -> LogLevel.ERROR
                        line.contains("[SUCCESS]") -> LogLevel.SUCCESS
                        else                       -> LogLevel.INFO
                    }
                    val rest = line.substringAfter("] ").substringAfter("] ")
                    val tag = rest.substringBefore("]").removePrefix("[")
                    val msg = rest.substringAfter("] ")
                    LogEntry(time, tag, msg, level)
                }.getOrNull()
            }.takeLast(1000)
            if (entries.isNotEmpty()) {
                _logs.value = entries
            }
        }
    }

    private fun startLogcat() {
        scope.launch {
            runCatching {
                val pid = android.os.Process.myPid().toString()
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "--pid=$pid", "-T", "100")
                )
                process.inputStream.bufferedReader().forEachLine { line ->
                    val level = when {
                        line.contains(" E ") || line.contains(" E/") -> LogLevel.ERROR
                        line.contains(" I ") || line.contains(" I/") -> LogLevel.INFO
                        else -> LogLevel.INFO
                    }
                    val msg = line.substringAfter("): ").ifBlank { line }.trim()
                    if (msg.isNotBlank() && msg.length > 2) {
                        val tag = line.substringAfter("/").substringBefore("(").trim()
                            .take(12).ifBlank { "SYS" }
                        val entry = LogEntry(
                            time = fmt.format(Date()),
                            tag = tag,
                            message = msg,
                            level = level
                        )
                        _logs.value = (_logs.value + entry).takeLast(1000)
                    }
                }
            }
        }
    }
}
