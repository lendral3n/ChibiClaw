package com.chibiclaw.memory.miner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chibiclaw.data.repository.MemoryRepository
import com.chibiclaw.memory.MemoryStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import timber.log.Timber
import kotlin.time.Duration.Companion.days

/**
 * MemoryDecayWorker — daily worker:
 *  1. Records yang tidak diakses > STALE_DAYS: confidence -= DECAY_STEP
 *  2. Records dengan confidence < AUTO_FORGET_THRESHOLD + last_accessed > 30 hari → hapus
 *  3. Cleanup TTL expired + LRU evict via MemoryStore.cleanup
 *
 * Worker idempotent — kalau gagal mid-loop, run berikutnya re-cek dan
 * lanjutkan decay. Tidak ada lock cross-run.
 */
@HiltWorker
class MemoryDecayWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MemoryRepository,
    private val store: MemoryStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Clock.System.now()
        val staleThreshold = now.minus(STALE_DAYS.days)

        val stale = repo.listStaleSince(staleThreshold)
        var decayed = 0
        stale.forEach { record ->
            val newConfidence = (record.confidence - DECAY_STEP).coerceAtLeast(0f)
            if (newConfidence < record.confidence) {
                repo.updateConfidence(record.id, newConfidence)
                decayed++
            }
        }

        val autoForgetThreshold = now.minus(AUTO_FORGET_AFTER_DAYS.days)
        val forgotten = repo.deleteLowConfidenceStale(AUTO_FORGET_BELOW, autoForgetThreshold)

        // Reuse existing cleanup (TTL expired + LRU evict)
        store.cleanup(now)

        Timber.i("MemoryDecayWorker done: $decayed decayed, $forgotten forgotten")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "memory_decay"
        private const val STALE_DAYS = 60
        private const val DECAY_STEP = 0.1f
        private const val AUTO_FORGET_BELOW = 0.2f
        private const val AUTO_FORGET_AFTER_DAYS = 30
    }
}
