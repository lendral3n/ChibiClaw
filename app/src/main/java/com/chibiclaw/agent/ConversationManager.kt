package com.chibiclaw.agent

import com.chibiclaw.data.database.TaskChannel
import com.chibiclaw.data.database.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConversationManager — entry untuk user input (text via chat panel atau voice
 * via STT). Buat Task channel=CHAT lalu enqueue.
 *
 * Phase 1: text input only.
 * Phase 2: voice input via Whisper STT.
 */
@Singleton
class ConversationManager @Inject constructor(
    private val taskManager: TaskManager,
) {

    suspend fun handleUserInput(text: String): TaskEntity {
        return taskManager.enqueue(
            goal = text.trim(),
            channel = TaskChannel.CHAT,
            priority = 4,
        )
    }
}
