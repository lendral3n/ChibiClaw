package com.chibiclaw.gateway

import java.util.UUID

data class CommandRequest(
    val id: String = UUID.randomUUID().toString(),
    val source: CommandSource,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Priority = Priority.NORMAL,
    val metadata: Map<String, String> = emptyMap()
)
