package org.mountaineers.traillog.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import org.mountaineers.traillog.data.TrailLogRepository

/**
 * Background sync: push pending Room writes (and deletes), then pull remote into Room.
 * Requires network (enforced by [SyncScheduler] constraints) and a signed-in user.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "No signed-in user — skipping sync")
            return Result.success()
        }

        return try {
            TrailLogRepository.initialize(applicationContext)
            TrailLogRepository.syncAll()
            Log.d(TAG, "Background sync succeeded (attempt $runAttemptCount)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
