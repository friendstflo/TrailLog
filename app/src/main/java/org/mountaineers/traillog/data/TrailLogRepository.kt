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

    fun addReport(report: TrailReport, photoFile: File? = null, onComplete: () -> Unit = {}) {
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
                            .addOnFailureListener { Log.e("Firebase", "Firestore write failed: ${it.message}") }
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
    suspend fun syncAll() {
        // Pull latest from Firebase and update local state
        val remote = db.collection("reports").get().await().toObjects(TrailReport::class.java)
        _reports.update { remote.filter { !it.isInvalidated } }
    }
    fun deleteReport(reportId: String) {
        _reports.update { current ->
            current.filter { it.id != reportId }
        }

        db.collection("reports").document(reportId).delete()
            .addOnFailureListener { Log.e("Firebase", "Delete failed: ${it.message}") }
    }
}