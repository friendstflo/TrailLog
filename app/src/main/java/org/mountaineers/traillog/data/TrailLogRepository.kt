package org.mountaineers.traillog.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.mountaineers.traillog.sync.SyncScheduler
import java.io.File
import java.util.Date

/**
 * Offline-first repository.
 *
 * Flow for mutations:
 * 1. Write Room immediately → UI updates optimistically (StateFlow from DAO)
 * 2. Enqueue WorkManager → background upload when network is available
 * 3. Firestore disk cache + snapshot listener keep multi-device data fresh when online
 */
object TrailLogRepository {

    private const val TAG = "TrailLogRepo"
    private const val PREFS = "traillog_prefs"
    private const val KEY_LANDOWNER = "default_landowner"

    private lateinit var appContext: Context
    private lateinit var dao: TrailReportDao

    // Access via getters so we don't hold static Firebase instances (lint: context leak)
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()
    private val storage: StorageReference
        get() = FirebaseStorage.getInstance().reference
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var listenerRegistration: ListenerRegistration? = null
    private var roomCollectStarted = false

    private val _reports = MutableStateFlow<List<TrailReport>>(emptyList())
    val reports: StateFlow<List<TrailReport>> = _reports.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Date?>(null)
    val lastSyncTime: StateFlow<Date?> = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // ==================== Initialization ====================

    fun initialize(context: Context) {
        if (::dao.isInitialized) return
        synchronized(this) {
            if (::dao.isInitialized) return
            appContext = context.applicationContext
            dao = AppDatabase.getDatabase(appContext).trailReportDao()
            startRoomCollection()
            Log.d(TAG, "Room initialized (source of truth)")
        }
    }

    private fun startRoomCollection() {
        if (roomCollectStarted) return
        roomCollectStarted = true
        scope.launch {
            dao.getAll().collect { list ->
                // Soft-deleted rows stay in Room until remote delete succeeds
                _reports.value = list.filter { !it.isInvalidated }
            }
        }
    }

    private fun ensureDao() {
        check(::dao.isInitialized) {
            "TrailLogRepository.initialize(context) must be called before use"
        }
    }

    private fun requestBackgroundSync(force: Boolean = false) {
        if (!::appContext.isInitialized) return
        if (force) {
            SyncScheduler.enqueueImmediateReplace(appContext)
        } else {
            SyncScheduler.enqueueImmediate(appContext)
        }
    }

    // ==================== Firebase Listener ====================

    fun startFirebaseListener() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user — cannot start listener")
            return
        }
        if (listenerRegistration != null) {
            Log.d(TAG, "Listener already active")
            return
        }
        if (!::dao.isInitialized) {
            Log.w(TAG, "Room not ready yet — listener deferred until after init")
            return
        }

        Log.d(TAG, "Starting Firestore listener for uid=${currentUser.uid}")

        listenerRegistration = db.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Listener error", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val fromCache = snapshot.metadata.isFromCache
                scope.launch {
                    for (change in snapshot.documentChanges) {
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                val report = change.document.toObject(TrailReport::class.java)
                                mergeRemoteIntoRoom(report, fromCache)
                            }
                            DocumentChange.Type.REMOVED -> {
                                // Only drop local row if it isn't a pending local create
                                val local = dao.getById(change.document.id)
                                if (local == null || !local.isOfflineCreated) {
                                    dao.deleteById(change.document.id)
                                }
                            }
                        }
                    }
                    if (!fromCache) {
                        _lastSyncTime.value = Date()
                    }
                    Log.d(
                        TAG,
                        "Listener applied ${snapshot.documentChanges.size} change(s) " +
                            "(fromCache=$fromCache)"
                    )
                }
            }

        // Kick a background push/pull once signed in
        requestBackgroundSync()
    }

    /**
     * Never clobber a local pending create/update with a stale cache/server copy.
     */
    private suspend fun mergeRemoteIntoRoom(report: TrailReport, fromCache: Boolean) {
        if (report.isInvalidated) {
            val local = dao.getById(report.id)
            if (local?.isOfflineCreated == true && !local.isInvalidated) {
                // Local still has unsynced edit — keep it
                return
            }
            dao.deleteById(report.id)
            return
        }

        val local = dao.getById(report.id)
        if (local != null && local.isOfflineCreated && !local.isInvalidated) {
            // Pending local write wins until WorkManager clears the flag
            Log.d(TAG, "Skip remote merge for pending local ${report.id} (cache=$fromCache)")
            return
        }
        if (local != null && local.isInvalidated) {
            // Pending local delete — don't resurrect from remote cache
            return
        }

        dao.insert(report.copy(isOfflineCreated = false, isInvalidated = false))
    }

    fun stopFirebaseListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.d(TAG, "Firestore listener stopped")
    }

    // ==================== Landowner Helpers ====================

    fun getCurrentLandownerFilter(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANDOWNER, "All") ?: "All"
    }

    fun setCurrentLandowner(context: Context, landowner: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LANDOWNER, landowner)
        }
    }

    // ==================== Local-first mutations (optimistic UI) ====================

    /**
     * 1) Insert into Room immediately (UI updates).
     * 2) Enqueue WorkManager for photo + Firestore upload when online.
     */
    suspend fun addReport(report: TrailReport, photoFile: File? = null) = withContext(Dispatchers.IO) {
        ensureDao()

        val localPath = when {
            photoFile != null && photoFile.exists() -> photoFile.absolutePath
            else -> report.photoPath
        }
        val localReport = report.copy(
            photoPath = localPath,
            isOfflineCreated = true,
            isInvalidated = false
        )
        dao.insert(localReport)
        Log.d(TAG, "Local create ${localReport.id} (optimistic); scheduling upload")
        requestBackgroundSync()
    }

    suspend fun updateReport(report: TrailReport) = withContext(Dispatchers.IO) {
        ensureDao()

        val local = report.copy(isOfflineCreated = true, isInvalidated = false)
        dao.insert(local)
        Log.d(TAG, "Local update ${local.id} (optimistic); scheduling upload")
        requestBackgroundSync()
    }

    /**
     * Soft-delete locally so the pin disappears immediately; remote delete runs in WorkManager.
     * Survives offline (hard delete would lose the id before Firestore is reachable).
     */
    suspend fun deleteReport(reportId: String) = withContext(Dispatchers.IO) {
        ensureDao()

        val existing = dao.getById(reportId)
        if (existing != null) {
            // Drop local photo file soon if path is local (optional cleanup)
            val path = existing.photoPath
            if (path.isNotEmpty() && !isRemoteUrl(path)) {
                runCatching { File(path).delete() }
            }
            dao.insert(
                existing.copy(
                    isInvalidated = true,
                    isOfflineCreated = false
                )
            )
        } else {
            // Still try remote cleanup in case Room was empty
            dao.insert(
                TrailReport(
                    id = reportId,
                    isInvalidated = true,
                    isOfflineCreated = false
                )
            )
        }
        Log.d(TAG, "Local soft-delete $reportId (optimistic); scheduling remote delete")
        requestBackgroundSync()
    }

    // ==================== Background sync (WorkManager / manual) ====================

    /**
     * Push pending deletes + uploads, then pull server/cache into Room.
     * Safe to call from [org.mountaineers.traillog.sync.SyncWorker] or UI force-sync.
     */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        ensureDao()
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.w(TAG, "syncAll skipped — not signed in")
            return@withContext
        }

        _isSyncing.value = true
        try {
            pushPendingDeletes()
            pushPendingUploads()
            pullRemote()
            _lastSyncTime.value = Date()
            Log.d(TAG, "syncAll completed")
        } finally {
            _isSyncing.value = false
        }
    }

    /** UI force-sync: run now and also ensure WorkManager will retry if this fails. */
    fun requestForceSync() {
        requestBackgroundSync(force = true)
        scope.launch {
            try {
                syncAll()
            } catch (e: Exception) {
                Log.e(TAG, "Force sync failed; WorkManager will retry if network returns", e)
            }
        }
    }

    private suspend fun pushPendingDeletes() {
        val pending = dao.getPendingDeletes()
        for (report in pending) {
            try {
                db.collection("reports").document(report.id).delete().await()
                try {
                    storage.child("photos/${report.id}.jpg").delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Storage photo already gone for ${report.id}", e)
                }
                dao.deleteById(report.id)
                Log.d(TAG, "Remote delete completed ${report.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Remote delete deferred for ${report.id}", e)
            }
        }
    }

    private suspend fun pushPendingUploads() {
        val pending = dao.getPendingUploads()
        for (report in pending) {
            try {
                val remote = uploadPhotoIfNeeded(report)
                val synced = remote.copy(isOfflineCreated = false, isInvalidated = false)
                db.collection("reports").document(report.id).set(synced).await()
                dao.insert(synced)
                Log.d(TAG, "Pushed report ${report.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Upload deferred for ${report.id}", e)
            }
        }
    }

    private suspend fun uploadPhotoIfNeeded(report: TrailReport): TrailReport {
        val path = report.photoPath
        if (path.isEmpty() || isRemoteUrl(path)) return report

        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "Local photo missing for ${report.id}: $path")
            return report.copy(photoPath = "")
        }

        val photoRef = storage.child("photos/${report.id}.jpg")
        photoRef.putFile(Uri.fromFile(file)).await()
        val downloadUri = photoRef.downloadUrl.await()
        Log.d(TAG, "Uploaded photo for ${report.id}")
        return report.copy(photoPath = downloadUri.toString())
    }

    private suspend fun pullRemote() {
        // Prefer server; Firestore falls back to cache when offline (throws if nothing usable)
        val snapshot = try {
            db.collection("reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get(Source.DEFAULT)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Pull from server failed, trying cache", e)
            db.collection("reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get(Source.CACHE)
                .await()
        }

        val list = snapshot.toObjects(TrailReport::class.java)
        for (report in list) {
            mergeRemoteIntoRoom(report, fromCache = snapshot.metadata.isFromCache)
        }
        Log.d(TAG, "Pulled ${list.size} reports (fromCache=${snapshot.metadata.isFromCache})")
    }

    private fun isRemoteUrl(path: String): Boolean =
        path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
}
