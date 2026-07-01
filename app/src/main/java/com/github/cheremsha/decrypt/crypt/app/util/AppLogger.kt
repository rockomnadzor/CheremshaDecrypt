package com.github.cheremsha.decrypt.crypt.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val time: String,
    val tag: String,
    val message: String
)

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(tag: String, message: String) {
        val entry = LogEntry(
            time = fmt.format(Date()),
            tag = tag,
            message = message
        )
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
