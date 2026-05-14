package com.chibiclaw.agent.scheduler

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResourceScheduler — sentralisasi lock untuk shared resource yang
 * tidak bisa di-acquire paralel. Tools acquire via `withResource(name) { ... }`.
 *
 * Resource taxonomy Phase 8:
 *  - MIC: 1 (Mutex) — STT can't double-acquire
 *  - SCREEN_CAPTURE: 1 (Mutex) — MediaProjection single-frame
 *  - TTS: 1 (Mutex) — AudioTrack playback exclusif
 *  - SHIZUKU_BINDER: 3 (Semaphore) — UserService dispatch, parallel ok
 *  - CLOUD_CALL: 3 (Semaphore) — per-adapter rate inheritance Phase 4
 *
 * Timeout default 10 detik. Deadlock detection sederhana via timeout
 * (kalau gagal acquire → return null, caller mark error).
 */
@Singleton
class ResourceScheduler @Inject constructor() {

    private val mic = Mutex()
    private val screenCapture = Mutex()
    private val tts = Mutex()
    private val shizukuBinder = Semaphore(SHIZUKU_PERMITS)
    private val cloudCall = Semaphore(CLOUD_PERMITS)

    /**
     * Acquire resource lock dengan timeout, run block, release.
     * Return null kalau gagal acquire (deadlock atau resource busy lama).
     */
    suspend fun <T> withResource(
        kind: ResourceKind,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> T,
    ): T? {
        return try {
            withTimeout(timeoutMs) {
                when (kind) {
                    ResourceKind.MIC -> mic.withLockOrTimeout(block)
                    ResourceKind.SCREEN_CAPTURE -> screenCapture.withLockOrTimeout(block)
                    ResourceKind.TTS -> tts.withLockOrTimeout(block)
                    ResourceKind.SHIZUKU_BINDER -> shizukuBinder.withPermit { block() }
                    ResourceKind.CLOUD_CALL -> cloudCall.withPermit { block() }
                }
            }
        } catch (t: TimeoutCancellationException) {
            Timber.w("ResourceScheduler timeout for $kind after ${timeoutMs}ms")
            null
        }
    }

    /** Snapshot state untuk debug UI. */
    fun snapshot(): Map<ResourceKind, ResourceState> = mapOf(
        ResourceKind.MIC to ResourceState(mic.isLocked, 1),
        ResourceKind.SCREEN_CAPTURE to ResourceState(screenCapture.isLocked, 1),
        ResourceKind.TTS to ResourceState(tts.isLocked, 1),
        ResourceKind.SHIZUKU_BINDER to ResourceState(
            inUse = SHIZUKU_PERMITS - shizukuBinder.availablePermits,
            total = SHIZUKU_PERMITS,
        ),
        ResourceKind.CLOUD_CALL to ResourceState(
            inUse = CLOUD_PERMITS - cloudCall.availablePermits,
            total = CLOUD_PERMITS,
        ),
    )

    private suspend inline fun <T> Mutex.withLockOrTimeout(crossinline block: suspend () -> T): T {
        lock()
        return try {
            block()
        } finally {
            unlock()
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val SHIZUKU_PERMITS = 3
        private const val CLOUD_PERMITS = 3
    }
}

enum class ResourceKind { MIC, SCREEN_CAPTURE, TTS, SHIZUKU_BINDER, CLOUD_CALL }

data class ResourceState(val inUse: Any, val total: Int) {
    val busy: Boolean get() = when (inUse) {
        is Boolean -> inUse
        is Int -> inUse > 0
        else -> false
    }
    val usageText: String get() = when (inUse) {
        is Boolean -> if (inUse) "busy" else "free"
        is Int -> "$inUse/$total"
        else -> "?"
    }
}
