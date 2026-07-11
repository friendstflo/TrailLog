package org.mountaineers.traillog.ui.export

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport
import org.mountaineers.traillog.map.MapBasemap
import org.mountaineers.traillog.map.MapBasemapPreferences
import java.util.Date

class SettingsViewModel : ViewModel() {

    val reports: StateFlow<List<TrailReport>> = TrailLogRepository.reports
    val lastSyncTime: StateFlow<Date?> = TrailLogRepository.lastSyncTime

    fun getLandownerFilter(context: Context): String =
        TrailLogRepository.getCurrentLandownerFilter(context)

    fun setLandownerFilter(context: Context, landowner: String) {
        TrailLogRepository.setCurrentLandowner(context, landowner)
    }

    fun getBasemap(context: Context): MapBasemap =
        MapBasemapPreferences.get(context)

    fun setBasemap(context: Context, basemap: MapBasemap) {
        MapBasemapPreferences.set(context, basemap)
    }

    fun getCrewName(context: Context): String {
        val prefs = context.getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        return prefs.getString("crew_name", "You") ?: "You"
    }

    fun setCrewName(context: Context, name: String) {
        context.getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE).edit {
            putString("crew_name", name.trim())
        }
    }

    val isSyncing = TrailLogRepository.isSyncing

    fun sync(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                TrailLogRepository.syncAll()
                onResult(true, null)
            } catch (e: Exception) {
                // Ensure WorkManager retries when connectivity returns
                TrailLogRepository.requestForceSync()
                onResult(false, e.message)
            }
        }
    }
}
