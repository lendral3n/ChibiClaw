package com.chibiclaw.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class DevLogLevel { DEBUG, INFO, WARN, ERROR }

data class DevLog(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: DevLogLevel = DevLogLevel.INFO
) {
    fun formattedTime(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

@Singleton
class DevLogger @Inject constructor() {

    private val _logs = MutableStateFlow<List<DevLog>>(emptyList())
    val logs: StateFlow<List<DevLog>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: DevLogLevel = DevLogLevel.INFO) {
        _logs.update { current ->
            (current + DevLog(tag = tag, message = message, level = level)).takeLast(500)
        }
    }

    fun d(tag: String, message: String) = log(tag, message, DevLogLevel.DEBUG)
    fun i(tag: String, message: String) = log(tag, message, DevLogLevel.INFO)
    fun w(tag: String, message: String) = log(tag, message, DevLogLevel.WARN)
    fun e(tag: String, message: String) = log(tag, message, DevLogLevel.ERROR)

    fun clear() = _logs.update { emptyList() }

    fun export(): String = _logs.value.joinToString("\n") { log ->
        "[${log.formattedTime()}] [${log.level}] [${log.tag}] ${log.message}"
    }
}
