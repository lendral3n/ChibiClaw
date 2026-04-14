package com.chibiclaw.gateway

import com.chibiclaw.executor.KillSwitch
import com.chibiclaw.gateway.source.AidlCommandSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandGateway @Inject constructor(
    private val aidlSource: AidlCommandSource,
    private val queue: CommandQueue,
    private val killSwitch: KillSwitch
) {
    suspend fun submitFromAidl(jsonCommand: String) {
        killSwitch.reset()
        aidlSource.submit(jsonCommand)
    }

    suspend fun submitDirect(text: String, source: CommandSource = CommandSource.WIDGET) {
        killSwitch.reset()
        val request = CommandRequest(source = source, rawText = text)
        queue.enqueue(request)
    }

    suspend fun nextCommand(): CommandRequest = queue.dequeue()

    fun stopCurrent() = killSwitch.activate()
}
