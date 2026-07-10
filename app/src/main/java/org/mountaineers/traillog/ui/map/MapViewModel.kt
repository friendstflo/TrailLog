package org.mountaineers.traillog.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport
import java.io.File

class MapViewModel : ViewModel() {

    val reports: StateFlow<List<TrailReport>> = TrailLogRepository.reports

    fun getLandownerFilter(context: Context): String =
        TrailLogRepository.getCurrentLandownerFilter(context)

    fun addReport(report: TrailReport, photoFile: File?) {
        viewModelScope.launch {
            TrailLogRepository.addReport(report, photoFile)
        }
    }

    fun updateReport(report: TrailReport) {
        viewModelScope.launch {
            TrailLogRepository.updateReport(report)
        }
    }

    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            TrailLogRepository.deleteReport(reportId)
        }
    }

    fun filteredReports(context: Context): List<TrailReport> {
        val filter = getLandownerFilter(context)
        val all = reports.value
        return if (filter == "All") all else all.filter { it.landowner == filter }
    }
}
