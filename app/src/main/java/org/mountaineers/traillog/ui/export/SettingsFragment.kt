package org.mountaineers.traillog.ui.export

import android.content.Context
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Locale

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val etCrewName = view.findViewById<EditText>(R.id.et_crew_name)
        val btnSave = view.findViewById<Button>(R.id.btn_save_crew)
        val btnExport = view.findViewById<Button>(R.id.btn_export_csv)
        val tvLastSync = view.findViewById<TextView>(R.id.tv_last_sync)

        // Load saved crew name
        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        etCrewName.setText(prefs.getString("crew_name", "You"))

        btnSave.setOnClickListener {
            val name = etCrewName.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.edit().putString("crew_name", name).apply()
                Toast.makeText(requireContext(), "Crew name saved", Toast.LENGTH_SHORT).show()
            }
        }

        btnExport.setOnClickListener {
            val csv = buildCsv()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "TrailLog Reports for Landowner")
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            startActivity(Intent.createChooser(intent, "Export CSV for Landowner"))
        }

        // Live last successful sync time
        lifecycleScope.launch {
            TrailLogRepository.lastSyncTime.collect { date ->
                if (date != null) {
                    val formatted = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.US).format(date)
                    tvLastSync.text = "Last successful sync: $formatted"
                } else {
                    tvLastSync.text = "Last successful sync: Never"
                }
            }
        }

        return view
    }

    private fun buildCsv(): String {
        val header = "Date,Type,Description,Quantity,Severity,Reporter,Cleared\n"
        val rows = TrailLogRepository.reports.value.joinToString("\n") { r ->
            "${r.timestamp},${r.type.displayName},${r.description},${r.quantity},${r.severity},${r.reporter},${r.isCleared}"
        }
        return header + rows
    }
}