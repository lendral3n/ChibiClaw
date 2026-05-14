package com.chibiclaw.memory.miner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.chibiclaw.agent.cleanup.AgentCleanupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MemoryWorkScheduler — enqueue Phase 7 (memory miner + decay) dan Phase 8
 * (agent cleanup) periodic workers. Re-enqueue idempotent via KEEP policy.
 */
@Singleton
class MemoryWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule() {
        val wm = WorkManager.getInstance(context)

        val patternMinerReq = PeriodicWorkRequestBuilder<PatternMinerWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            PatternMinerWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            patternMinerReq,
        )

        val decayReq = PeriodicWorkRequestBuilder<MemoryDecayWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            MemoryDecayWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            decayReq,
        )

        val cleanupReq = PeriodicWorkRequestBuilder<AgentCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInitialDelay(3, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            AgentCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupReq,
        )

        Timber.i("Workers enqueued: pattern miner (weekly), memory decay (daily), agent cleanup (daily)")
    }
}
