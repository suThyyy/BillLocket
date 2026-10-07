package com.billlocket.app.media

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.MediaStorePort
import com.billlocket.app.domain.result.Outcome
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class MediaReconciliationWorker(appContext: Context, parameters: WorkerParameters) :
    CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, MediaWorkerEntryPoint::class.java)
        return when (dependencies.mediaStore().reconcile(dependencies.clock().nowEpochMillis())) {
            is Outcome.Success -> Result.success()
            is Outcome.Failure -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaWorkerEntryPoint {
    fun mediaStore(): MediaStorePort
    fun clock(): Clock
}

object MediaWorkScheduler {
    private const val UNIQUE_WORK = "billlocket-media-reconciliation"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MediaReconciliationWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
