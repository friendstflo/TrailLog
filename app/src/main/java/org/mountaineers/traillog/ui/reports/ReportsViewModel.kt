package org.mountaineers.traillog.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport

class ReportsViewModel : ViewModel() {

    val reports: StateFlow<List<TrailReport>> = TrailLogRepository.reports

    fun getLandownerFilter(context: Context): String =
        TrailLogRepository.getCurrentLandownerFilter(context)

    fun setLandownerFilter(context: Context, landowner: String) {
        TrailLogRepository.setCurrentLandowner(context, landowner)
    }

    fun filteredSorted(context: Context, source: List<TrailReport> = reports.value): List<TrailReport> {
        val filter = getLandownerFilter(context)
        val filtered = if (filter == "All") source else source.filter { it.landowner == filter }
        return filtered.sortedWith(compareBy({ it.isCleared }, { -it.timestamp.time }))
    }

    /** Pull-to-refresh: push pending + pull remote (WorkManager + in-process). */
    fun sync() {
        TrailLogRepository.requestForceSync()
    }
}
