package org.mountaineers.traillog.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TrailLogViewModel : ViewModel() {

    private val _reports = MutableStateFlow<List<TrailReport>>(emptyList())
    val reports: StateFlow<List<TrailReport>> = _reports.asStateFlow()

    fun addReport(report: TrailReport) {
        _reports.update { it + report }
    }

    fun updateReport(updatedReport: TrailReport) {
        _reports.update { list ->
            list.map { if (it.id == updatedReport.id) updatedReport else it }
        }
    }

    fun deleteReport(reportId: String) {
        _reports.update { list -> list.filter { it.id != reportId } }
    }
}