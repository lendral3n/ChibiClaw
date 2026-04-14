package com.chibiclaw.gateway.source

import com.chibiclaw.gateway.CommandNormalizer
import com.chibiclaw.gateway.CommandQueue
import com.chibiclaw.gateway.CommandRequest
import com.chibiclaw.gateway.CommandSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AidlCommandSource @Inject constructor(
    private val normalizer: CommandNormalizer,
    private val queue: CommandQueue
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun submit(jsonCommand: String) {
        val text = try {
            val obj = json.parseToJsonElement(jsonCommand).jsonObject
            obj["text"]?.jsonPrimitive?.content ?: jsonCommand
        } catch (_: Exception) {
            jsonCommand
        }
        val request = normalizer.normalize(text, CommandSource.AIDL)
        queue.enqueue(request)
    }
}
