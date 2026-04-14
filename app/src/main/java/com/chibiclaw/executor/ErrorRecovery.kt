package com.chibiclaw.executor

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorRecovery @Inject constructor() {

    private val MAX_RETRIES = 3

    suspend fun <T> withRetry(
        tag: String,
        block: suspend (attempt: Int) -> T
    ): Result<T> {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                return Result.success(block(attempt))
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "[$tag] Attempt $attempt/$MAX_RETRIES failed: ${e.message}")
            }
        }
        return Result.failure(lastException ?: Exception("Max retries exceeded for $tag"))
    }

    companion object {
        private const val TAG = "ErrorRecovery"
    }
}
