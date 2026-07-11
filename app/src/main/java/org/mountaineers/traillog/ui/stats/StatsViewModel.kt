package org.mountaineers.traillog.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport
import org.mountaineers.traillog.util.CsvExporter

data class DashboardStats(
    val total: Int = 0,
    val cleared: Int = 0,
    val pending: Int = 0,
    val logsRemoved: Int = 0,
    val brushFeet: Int = 0,
    val treadFeet: Int = 0
)

data class ExportUiEvent(
    val message: String,
    val isError: Boolean = false
)

class StatsViewModel : ViewModel() {

    val reports: StateFlow<List<TrailReport>> = TrailLogRepository.reports

    private val _exportEvent = MutableStateFlow<ExportUiEvent?>(null)
    val exportEvent: StateFlow<ExportUiEvent?> = _exportEvent.asStateFlow()

    fun getLandownerFilter(context: Context): String =
        TrailLogRepository.getCurrentLandownerFilter(context)

    fun setLandownerFilter(context: Context, landowner: String) {
        TrailLogRepository.setCurrentLandowner(context, landowner)
    }

    fun computeStats(context: Context, source: List<TrailReport> = reports.value): DashboardStats =
        computeStatsForFilter(getLandownerFilter(context), source)

    fun computeStatsForFilter(
        filter: String,
        source: List<TrailReport> = reports.value
    ): DashboardStats {
        val filtered = filteredReports(filter, source)
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

    fun filteredReports(
        filter: String,
        source: List<TrailReport> = reports.value
    ): List<TrailReport> {
        return if (filter == "All") source else source.filter { it.landowner == filter }
    }

    fun exportCsv(context: Context, landownerFilter: String) {
        viewModelScope.launch {
            val filtered = filteredReports(landownerFilter, reports.value)
            val result = withContext(Dispatchers.IO) {
                CsvExporter.export(context.applicationContext, filtered, landownerFilter)
            }
            _exportEvent.value = if (result.success) {
                ExportUiEvent(
                    message = "CSV exported to Downloads/${result.fileName}",
                    isError = false
                )
            } else {
                ExportUiEvent(
                    message = result.errorMessage ?: "Export failed",
                    isError = true
                )
            }
        }
    }

    fun consumeExportEvent() {
        _exportEvent.value = null
    }
}
