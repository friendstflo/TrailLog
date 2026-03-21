package org.mountaineers.traillog.ui.export

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailLogRepository
import java.util.Date

class SettingsFragment : Fragment() {

    private lateinit var etCrewName: EditText
    private lateinit var tvLastSync: TextView
    private lateinit var btnExport: Button
    private lateinit var btnForceSync: Button   // ← New

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        etCrewName = view.findViewById(R.id.et_crew_name)
        tvLastSync = view.findViewById(R.id.tv_last_sync)
        btnExport = view.findViewById(R.id.btn_export_csv)
        btnForceSync = view.findViewById(R.id.btn_force_sync)   // ← New

        // Load crew name
        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        etCrewName.setText(prefs.getString("crew_name", "Everett Crew"))

        // Live sync status + count
        lifecycleScope.launch {
            TrailLogRepository.lastSyncTime.collect { lastSync ->
                val count = TrailLogRepository.reports.value.size
                val timeText = if (lastSync != null) {
                    val minutes = ((Date().time - lastSync.time) / 60000).toInt()
                    "Last synced: ${minutes} min ago ($count reports)"
                } else {
                    "Last synced: never (0 reports)"
                }
                tvLastSync.text = timeText
            }
        }

        btnExport.setOnClickListener { exportCsv() }

        // NEW: Force Sync button
        btnForceSync.setOnClickListener {
            lifecycleScope.launch {
                try {
                    TrailLogRepository.syncAll()
                    Toast.makeText(requireContext(), "✅ Sync forced — pulling latest data", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Save crew name
        etCrewName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.edit().putString("crew_name", etCrewName.text.toString().trim()).apply()
            }
        }

        return view
    }

    private fun exportCsv() {
        // Your existing CSV logic here
        Toast.makeText(requireContext(), "CSV exported!", Toast.LENGTH_SHORT).show()
    }
}