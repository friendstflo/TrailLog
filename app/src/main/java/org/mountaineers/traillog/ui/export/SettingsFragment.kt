package org.mountaineers.traillog.ui.export

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var etCrewName: EditText
    private lateinit var tvLastSync: TextView
    private lateinit var spinnerLandowner: Spinner
    private lateinit var btnExport: Button

    private val landowners = listOf("All", "Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        etCrewName = view.findViewById(R.id.et_crew_name)
        tvLastSync = view.findViewById(R.id.tv_last_sync)
        spinnerLandowner = view.findViewById(R.id.spinner_landowner)
        btnExport = view.findViewById(R.id.btn_export_csv)

        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)

        etCrewName.setText(prefs.getString("crew_name", "You"))

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, landowners)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLandowner.adapter = adapter

        val saved = prefs.getString("default_landowner", "All") ?: "All"
        spinnerLandowner.setSelection(landowners.indexOf(saved).takeIf { it >= 0 } ?: 0)

        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = landowners[position]
                prefs.edit().putString("default_landowner", selected).apply()
                Toast.makeText(requireContext(), "Filter updated: $selected", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Live sync status
        lifecycleScope.launch {
            TrailLogRepository.lastSyncTime.collect { lastSync ->
                val count = TrailLogRepository.reports.value.size
                val text = if (lastSync != null) {
                    val minutes = ((Date().time - lastSync.time) / 60000).toInt()
                    "Last synced: ${minutes} min ago ($count reports)"
                } else {
                    "Last synced: never ($count reports)"
                }
                tvLastSync.text = text
            }
        }

        // Real CSV Export
        btnExport.setOnClickListener {
            exportToCSV()
        }

        etCrewName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.edit().putString("crew_name", etCrewName.text.toString().trim()).apply()
            }
        }

        return view
    }

    private fun exportToCSV() {
        val filter = spinnerLandowner.selectedItem?.toString() ?: "All"
        val reports = TrailLogRepository.reports.value

        val filteredReports = if (filter == "All") reports else reports.filter { it.landowner == filter }

        if (filteredReports.isEmpty()) {
            Toast.makeText(requireContext(), "No reports to export", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                val fileName = "TrailLog_${filter.replace(" ", "_")}_$timestamp.csv"

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                FileWriter(file).use { writer ->
                    writer.append("Date,Landowner,Type,Description,Quantity,Unit,Severity,Status,Reporter,Photo Path\n")

                    filteredReports.forEach { report ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(report.timestamp)
                        val unit = if (report.type == org.mountaineers.traillog.data.ReportType.LOG) "inches" else "ft"
                        val status = if (report.isCleared) "Complete" else "Pending"

                        writer.append("\"$dateStr\",\"${report.landowner}\",\"${report.type.displayName}\",")
                            .append("\"${report.description.replace("\"", "\"\"")}\",")
                            .append("${report.quantity},\"$unit\",\"${report.severity}\",")
                            .append("\"$status\",\"${report.reporter}\",\"${report.photoPath}\"\n")
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "✅ CSV exported!\nSaved to Downloads/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}