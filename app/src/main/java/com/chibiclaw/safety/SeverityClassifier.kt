package com.chibiclaw.safety

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeverityClassifier @Inject constructor() {

    private val blockedPatterns = listOf(
        "rm -rf", "factory reset", "wipe", "format"
    )
    private val highPatterns = listOf(
        "delete", "hapus", "uninstall", "transfer", "bayar", "payment",
        "send money", "kirim uang", "share password", "bagikan password"
    )
    private val mediumPatterns = listOf(
        "install", "setting", "setting wifi", "bluetooth", "location"
    )

    fun classify(command: String): Severity {
        val lower = command.lowercase()
        return when {
            blockedPatterns.any { lower.contains(it) } -> Severity.BLOCKED
            highPatterns.any { lower.contains(it) } -> Severity.HIGH
            mediumPatterns.any { lower.contains(it) } -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }
}
