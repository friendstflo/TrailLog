package org.mountaineers.traillog.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules WorkManager jobs for TrailLog background sync.
 *
 * - Immediate: after local write (create / update / delete)
 * - Periodic: while the app is installed (min interval 15 minutes)
 */
object SyncScheduler {

    private const val TAG = "SyncScheduler"
    const val UNIQUE_IMMEDIATE = "traillog_sync_immediate"
    const val UNIQUE_PERIODIC = "traillog_sync_periodic"

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /** Enqueue a one-shot sync as soon as network is available. */
    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("immediate")
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                // If a sync is already queued/running, keep it (avoid stampede on multi-pin)
                ExistingWorkPolicy.KEEP,
                request
            )
        Log.d(TAG, "Enqueued immediate sync")
    }

    /**
     * Force a new immediate sync even if one is already pending
     * (pull-to-refresh / Settings force sync).
     */
    fun enqueueImmediateReplace(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("immediate_force")
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request
            )
        Log.d(TAG, "Enqueued immediate sync (replace)")
    }

    /** Recurring background sync (Android minimum period is 15 minutes). */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .addTag("periodic")
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        Log.d(TAG, "Scheduled periodic sync (15 min)")
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(UNIQUE_IMMEDIATE)
        wm.cancelUniqueWork(UNIQUE_PERIODIC)
        Log.d(TAG, "Cancelled all TrailLog sync work")
    }
}
