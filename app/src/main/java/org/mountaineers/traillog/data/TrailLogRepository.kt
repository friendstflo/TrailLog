package org.mountaineers.traillog.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Date

object TrailLogRepository {

    private lateinit var dao: TrailReportDao

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    private val _reports = MutableStateFlow<List<TrailReport>>(emptyList())
    val reports: StateFlow<List<TrailReport>> = _reports.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Date?>(null)
    val lastSyncTime: StateFlow<Date?> = _lastSyncTime.asStateFlow()

    // ==================== Initialization ====================
    fun initialize(context: Context) {
        if (::dao.isInitialized) return

        Thread {
            try {
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "traillog_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                dao = database.trailReportDao()
                Log.d("TrailLogRepo", "Room initialized successfully")
            } catch (e: Exception) {
                Log.e("TrailLogRepo", "Room initialization failed", e)
            }
        }.start()
    }

    // ==================== Start Firebase Listener (Call after login) ====================
    // ==================== Firebase Listener ====================
    fun startFirebaseListener() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("TrailLogRepo", "No authenticated user - cannot start listener")
            return
        }

        Log.d("TrailLogRepo", "=== STARTING FIREBASE LISTENER for user: ${currentUser.uid} ===")

        db.collection("reports")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("TrailLogRepo", "Listener error", e)
                    return@addSnapshotListener
                }

                val list = snapshot?.toObjects(TrailReport::class.java) ?: emptyList()
                val filtered = list.filter { !it.isInvalidated }

                _reports.update { filtered }
                _lastSyncTime.value = Date()

                Log.d("TrailLogRepo", "Loaded ${filtered.size} reports from Firestore")
            }
    }

    // ==================== Landowner Helpers ====================
    fun getCurrentLandownerFilter(context: Context): String {
        val prefs = context.getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        return prefs.getString("default_landowner", "All") ?: "All"
    }

    fun setCurrentLandowner(context: Context, landowner: String) {
        val prefs = context.getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("default_landowner", landowner).apply()
    }

    // ==================== Data Operations ====================
    fun addReport(report: TrailReport, photoFile: File? = null) {
        _reports.update { it + report }   // optimistic update

        val docRef = db.collection("reports").document(report.id)

        if (photoFile != null && photoFile.exists()) {
            val photoRef = storage.child("photos/${report.id}.jpg")
            photoRef.putFile(android.net.Uri.fromFile(photoFile))
                .addOnSuccessListener {
                    photoRef.downloadUrl.addOnSuccessListener { uri ->
                        val reportWithUrl = report.copy(photoPath = uri.toString())
                        docRef.set(reportWithUrl)
                            .addOnSuccessListener { Log.d("TrailLogRepo", "Report with photo saved") }
                            .addOnFailureListener { Log.e("TrailLogRepo", "Firestore write failed", it) }
                    }
                }
                .addOnFailureListener { Log.e("TrailLogRepo", "Photo upload failed", it) }
        } else {
            docRef.set(report)
                .addOnSuccessListener { Log.d("TrailLogRepo", "Report saved") }
                .addOnFailureListener { Log.e("TrailLogRepo", "Firestore write failed", it) }
        }
    }

    fun updateReport(report: TrailReport) {
        _reports.update { current ->
            current.map { if (it.id == report.id) report else it }
        }
        db.collection("reports").document(report.id).set(report)
    }

    fun deleteReport(reportId: String) {
        _reports.update { current -> current.filter { it.id != reportId } }
        db.collection("reports").document(reportId).delete()
    }

    // Force sync (can be called from Settings)
    fun syncAll() {
        db.collection("reports")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.toObjects(TrailReport::class.java)
                _reports.update { list.filter { !it.isInvalidated } }
                _lastSyncTime.value = Date()
                Log.d("TrailLogRepo", "Manual sync completed: ${list.size} reports")
            }
            .addOnFailureListener { e ->
                Log.e("TrailLogRepo", "Manual sync failed", e)
            }
    }
}