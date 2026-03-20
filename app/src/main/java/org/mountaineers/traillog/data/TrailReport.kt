package org.mountaineers.traillog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import java.util.Date  // Consider replacing Date with Long later

@Entity(tableName = "TrailReport")
data class TrailReport(
    @PrimaryKey  // UUID as PK – no autoGenerate needed
    val id: String = UUID.randomUUID().toString(),  // Generate only on new reports

    val lat: Double = 0.0,
    val lng: Double = 0.0,

    val description: String = "",
    val type: ReportType = ReportType.LOG,
    val severity: String = "Medium",  // Consider enum: Low/Medium/High

    val photoPath: String = "",      // Local path/URI for offline photo
    val photoUrl: String? = null,    // Firebase Storage URL after sync (nullable)

    val quantity: Int = 0,
    val isCleared: Boolean = false,
    val isOfflineCreated: Boolean = false,
    val isInvalidated: Boolean = false,

    val timestamp: Date = Date(),    // Creation time – consider Long millis instead
    val reporter: String = "Anonymous Crew"
)