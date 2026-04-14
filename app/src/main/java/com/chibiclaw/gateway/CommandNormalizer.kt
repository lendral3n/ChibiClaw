package com.chibiclaw.gateway

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandNormalizer @Inject constructor() {

    fun normalize(rawText: String, source: CommandSource, metadata: Map<String, String> = emptyMap()): CommandRequest {
        val cleaned = rawText.trim().replace(Regex("\\s+"), " ")
        val priority = when (source) {
            CommandSource.CRON -> Priority.LOW
            CommandSource.NOTIFICATION -> Priority.HIGH
            CommandSource.WIDGET -> Priority.HIGH
            CommandSource.VOICE -> Priority.NORMAL
            CommandSource.AIDL -> Priority.NORMAL
        }
        return CommandRequest(
            source = source,
            rawText = cleaned,
            priority = priority,
            metadata = metadata
        )
    }
}
