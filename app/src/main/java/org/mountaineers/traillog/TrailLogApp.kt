package org.mountaineers.traillog

import android.app.Application
import android.util.Log
import com.google.android.material.color.DynamicColors
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.map.OsmdroidConfig
import org.mountaineers.traillog.sync.SyncScheduler
import org.mountaineers.traillog.util.NetworkMonitor

/**
 * Process-wide setup:
 * - Material 3 dynamic color (API 31+)
 * - Firestore disk persistence (must run before any Firestore use)
 * - OSMdroid tile disk cache (offline maps)
 * - Room repository
 * - WorkManager periodic background sync
 */
class TrailLogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Material You / dynamic color on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
        configureFirestorePersistence()
        OsmdroidConfig.ensureConfigured(this)
        NetworkMonitor.start(this)
        TrailLogRepository.initialize(this)
        SyncScheduler.schedulePeriodic(this)
        Log.d(TAG, "TrailLogApp initialized")
    }

    private fun configureFirestorePersistence() {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        // Plenty of room for crew reports offline in the field
                        .setSizeBytes(100L * 1024L * 1024L)
                        .build()
                )
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            Log.d(TAG, "Firestore persistent cache enabled (100 MB)")
        } catch (e: Exception) {
            // Settings can only be applied once per process, before other use
            Log.w(TAG, "Firestore persistence settings already applied or failed", e)
        }
    }

    companion object {
        private const val TAG = "TrailLogApp"
    }
}
