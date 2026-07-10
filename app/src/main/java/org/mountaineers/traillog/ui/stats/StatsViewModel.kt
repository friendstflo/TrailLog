package org.mountaineers.traillog.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport

data class DashboardStats(
    val total: Int = 0,
    val cleared: Int = 0,
    val pending: Int = 0,
    val logsRemoved: Int = 0,
    val brushFeet: Int = 0,
    val treadFeet: Int = 0
)

class StatsViewModel : ViewModel() {

    val reports: StateFlow<List<TrailReport>> = TrailLogRepository.reports

    fun getLandownerFilter(context: Context): String =
        TrailLogRepository.getCurrentLandownerFilter(context)

    fun setLandownerFilter(context: Context, landowner: String) {
        TrailLogRepository.setCurrentLandowner(context, landowner)
    }

    fun computeStats(context: Context, source: List<TrailReport> = reports.value): DashboardStats {
        val filter = getLandownerFilter(context)
        val filtered = if (filter == "All") source else source.filter { it.landowner == filter }

        val total = filtered.size
        val cleared = filtered.count { it.isCleared }
        return DashboardStats(
            total = total,
            cleared = cleared,
            pending = total - cleared,
            logsRemoved = filtered.count { it.type == ReportType.LOG && it.isCleared },
            brushFeet = filtered.filter { it.type == ReportType.BRUSH && it.isCleared }.sumOf { it.quantity },
            treadFeet = filtered.filter { it.type == ReportType.TREADWORK && it.isCleared }.sumOf { it.quantity }
        )
    }
}
