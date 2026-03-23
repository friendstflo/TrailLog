package org.mountaineers.traillog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName
import java.util.Date
import java.util.UUID

@Entity(tableName = "reports")
data class TrailReport(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val description: String = "",
    val type: ReportType = ReportType.LOG,
    val severity: String = "Medium",
    val photoPath: String = "",
    val quantity: Int = 0,

    @PropertyName("cleared")
    val isCleared: Boolean = false,

    @PropertyName("offlineCreated")
    val isOfflineCreated: Boolean = false,

    @PropertyName("invalidated")
    val isInvalidated: Boolean = false,

    val timestamp: Date = Date(),
    val reporter: String = "Anonymous Crew",
    val landowner: String = "Unknown"
)