package org.mountaineers.traillog.data
import androidx.room.Room
import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Date

object TrailLogRepository {

    @Suppress("StaticFieldLeak")
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference
    private lateinit var dao: TrailReportDao
    private val _reports = MutableStateFlow<List<TrailReport>>(emptyList())
    val reports: StateFlow<List<TrailReport>> = _reports.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Date?>(null)
    val lastSyncTime: StateFlow<Date?> = _lastSyncTime.asStateFlow()

    init {
        db.collection("reports")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Firebase", "Listen failed: ${e.message}")
                    return@addSnapshotListener
                }

                val remoteList = snapshot?.toObjects(TrailReport::class.java) ?: emptyList()
                val localList = _reports.value.toMutableList()

                // Merge: local isCleared ALWAYS wins
                remoteList.forEach { remote ->
                    val index = localList.indexOfFirst { it.id == remote.id }
                    if (index != -1) {
                        val local = localList[index]
                        // Keep local isCleared, take everything else from server
                        val merged = local.copy(
                            description = remote.description,
                            type = remote.type,
                            severity = remote.severity,
                            quantity = remote.quantity,
                            photoPath = remote.photoPath,
                            timestamp = remote.timestamp,
                            reporter = remote.reporter
                        )
                        localList[index] = merged
                    } else {
                        localList.add(remote)
                    }
                }

                // Remove local pins deleted by others
                localList.removeAll { local ->
                    remoteList.none { it.id == local.id }
                }

                _reports.update { localList.filter { !it.isInvalidated } }
                _lastSyncTime.value = Date()
            }
    }
    fun initialize(context: Context) {
        val database = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "traillog_database"
        )
            .fallbackToDestructiveMigrationOnDowngrade()  // or use .fallbackToDestructiveMigration() if you want to drop on any version change
            .build()

        dao = database.trailReportDao()
    }
    fun addReport(report: TrailReport, photoFile: File? = null, onComplete: () -> Unit = {}) {
        // Optimistic local update
        _reports.update { it + report }

        val docRef = db.collection("reports").document(report.id)

        if (photoFile != null && photoFile.exists()) {
            val photoRef = storage.child("photos/${report.id}.jpg")
            photoRef.putFile(android.net.Uri.fromFile(photoFile))
                .addOnSuccessListener {
                    photoRef.downloadUrl.addOnSuccessListener { uri ->
                        val reportWithUrl = report.copy(photoPath = uri.toString())
                        docRef.set(reportWithUrl)
                            .addOnSuccessListener { onComplete() }
                            .addOnFailureListener {
                                Log.e(
                                    "Firebase",
                                    "Firestore write failed: ${it.message}"
                                )
                            }
                    }
                }
                .addOnFailureListener { Log.e("Firebase", "Photo upload failed: ${it.message}") }
        } else {
            docRef.set(report)
                .addOnSuccessListener { onComplete() }
                .addOnFailureListener { Log.e("Firebase", "Firestore write failed: ${it.message}") }
        }
    }

    fun updateReport(report: TrailReport) {
        _reports.update { current ->
            current.map { if (it.id == report.id) report else it }
        }

        db.collection("reports").document(report.id).set(report)
            .addOnFailureListener { Log.e("Firebase", "Update failed: ${it.message}") }
    }
    fun getAllReports(): Flow<List<TrailReport>> {
        return dao.getAll()
    }
    fun deleteReport(reportId: String) {
        _reports.update { current ->
            current.filter { it.id != reportId }
        }

        db.collection("reports").document(reportId).delete()
            .addOnFailureListener { Log.e("Firebase", "Delete failed: ${it.message}") }
    }

    fun syncAll() {
        try {
            val snapshot =
                db.collection("reports").get().result   // ← Fixed: .result instead of .get()
            val remote = snapshot.toObjects(TrailReport::class.java)
            _reports.update { remote.filter { !it.isInvalidated } }
            Log.d("Firebase", "syncAll pulled ${remote.size} reports")
        } catch (e: Exception) {
            Log.e("Firebase", "syncAll failed: ${e.message}")
        }
    }
}