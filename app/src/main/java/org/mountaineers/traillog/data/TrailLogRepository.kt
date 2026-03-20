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

                val remoteList = snapshot?.toObjects(TrailReport::class.java) ?: emptyList()
                val localList = _reports.value.toMutableList()

                // Merge: Local isCleared state wins until server confirms
                remoteList.forEach { remote ->
                    val index = localList.indexOfFirst { it.id == remote.id }
                    if (index != -1) {
                        val local = localList[index]
                        // Use remote data unless local isCleared is different (local wins)
                        val merged = if (local.isCleared != remote.isCleared) local else remote
                        localList[index] = merged
                    } else {
                        localList.add(remote)
                    }
                }

                // Remove any local pins deleted by other crew
                localList.removeAll { local ->
                    remoteList.none { it.id == local.id }
                }

                _reports.update { localList.filter { !it.isInvalidated } }
                _lastSyncTime.value = Date()
            }
    }

    suspend fun addReport(report: TrailReport, photoFile: File? = null) {
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
        _reports.update { current ->
            current.map { if (it.id == report.id) report else it }
        }
        db.collection("reports").document(report.id).set(report).await()
    }

    suspend fun deleteReport(reportId: String) {
        _reports.update { current ->
            current.filter { it.id != reportId }
        }
        db.collection("reports").document(reportId).delete().await()
    }

    suspend fun syncAll() {
        val snapshot = db.collection("reports").get().await()
        val remote = snapshot.toObjects(TrailReport::class.java)
        _reports.update { remote.filter { !it.isInvalidated } }
    }
}