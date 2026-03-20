package org.mountaineers.traillog.data

import java.util.Date
import java.util.UUID

data class TrailReport(
    val id: String = UUID.randomUUID().toString(),
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val description: String = "",
    val type: ReportType = ReportType.LOG,
    val severity: String = "Medium",
    val photoPath: String = "",
    val quantity: Int = 0,
    val isCleared: Boolean = false,
    val isOfflineCreated: Boolean = false,
    val isInvalidated: Boolean = false,
    val timestamp: Date = Date(),
    val reporter: String = "Anonymous Crew"
)