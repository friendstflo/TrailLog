package org.mountaineers.traillog.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Date

object TrailLogRepository {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

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
                val list = snapshot?.toObjects(TrailReport::class.java) ?: emptyList()
                _reports.update { list.filter { !it.isInvalidated } }
                _lastSyncTime.value = Date()
            }
    }

    suspend fun addReport(report: TrailReport, photoFile: File? = null) {
        // Optimistic local update
        _reports.update { it + report }

        val docRef = db.collection("reports").document(report.id)

        if (photoFile != null && photoFile.exists()) {
            val photoRef = storage.child("photos/${report.id}.jpg")
            photoRef.putFile(android.net.Uri.fromFile(photoFile)).await()
            val uri = photoRef.downloadUrl.await().toString()
            val reportWithUrl = report.copy(photoPath = uri)
            docRef.set(reportWithUrl).await()
        } else {
            docRef.set(report).await()
        }
    }

    suspend fun updateReport(report: TrailReport) {
        // Optimistic local update
        _reports.update { current ->
            current.map { if (it.id == report.id) report else it }
        }

        // Write to Firebase
        db.collection("reports").document(report.id).set(report).await()
    }

    suspend fun deleteReport(reportId: String) {
        // Optimistic local delete
        _reports.update { current ->
            current.filter { it.id != reportId }
        }

        // Delete from Firebase
        db.collection("reports").document(reportId).delete().await()
    }

    suspend fun syncAll() {
        // Pull latest from Firebase and update local state
        val snapshot = db.collection("reports").get().await()
        val remote = snapshot.toObjects(TrailReport::class.java)
        _reports.update { remote.filter { !it.isInvalidated } }
    }
}