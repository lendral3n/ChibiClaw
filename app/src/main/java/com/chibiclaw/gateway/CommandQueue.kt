package com.chibiclaw.gateway

import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandQueue @Inject constructor() {
    private val channel = Channel<CommandRequest>(capacity = Channel.UNLIMITED)

    suspend fun enqueue(request: CommandRequest) = channel.send(request)

    suspend fun dequeue(): CommandRequest = channel.receive()

    fun tryDequeue(): CommandRequest? = channel.tryReceive().getOrNull()
}
