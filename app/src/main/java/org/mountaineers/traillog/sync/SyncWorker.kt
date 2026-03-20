package org.mountaineers.traillog.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.mountaineers.traillog.data.TrailLogRepository

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        TrailLogRepository.syncAll()
        return Result.success()
    }
}