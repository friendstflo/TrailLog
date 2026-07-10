package org.mountaineers.traillog.ui.export

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mountaineers.traillog.R
import org.mountaineers.traillog.map.MapBasemap
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var etCrewName: EditText
    private lateinit var tvLastSync: TextView
    private lateinit var spinnerLandowner: Spinner
    private lateinit var spinnerMapBasemap: Spinner
    private lateinit var btnExport: Button

    private val landowners = listOf("All", "Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")

    /** Suppress spinner callbacks while restoring saved selection. */
    private var basemapSpinnerReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        etCrewName = view.findViewById(R.id.et_crew_name)
        tvLastSync = view.findViewById(R.id.tv_last_sync)
        spinnerLandowner = view.findViewById(R.id.spinner_landowner)
        spinnerMapBasemap = view.findViewById(R.id.spinner_map_basemap)
        btnExport = view.findViewById(R.id.btn_export_csv)

        etCrewName.setText(viewModel.getCrewName(requireContext()))

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, landowners)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLandowner.adapter = adapter

        val saved = viewModel.getLandownerFilter(requireContext())
        spinnerLandowner.setSelection(landowners.indexOf(saved).takeIf { it >= 0 } ?: 0)

        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = landowners[position]
                viewModel.setLandownerFilter(requireContext(), selected)
                Toast.makeText(requireContext(), "Filter updated: $selected", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        setupBasemapSpinner()

        // Live sync status from Room-backed flows
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.lastSyncTime, viewModel.reports) { lastSync, reports ->
                    lastSync to reports.size
                }.collect { (lastSync, count) ->
                    tvLastSync.text = if (lastSync != null) {
                        val minutes = ((Date().time - lastSync.time) / 60000).toInt()
                        getString(R.string.last_synced_minutes, minutes, count)
                    } else {
                        getString(R.string.last_synced_never_count, count)
                    }
                }
            }
        }

        // Tap last-sync row area isn't a button; export + crew name + landowner remain primary.
        // Long-press last sync text forces a sync.
        tvLastSync.setOnLongClickListener {
            viewModel.sync { ok, error ->
                if (ok) {
                    Toast.makeText(requireContext(), "Sync complete", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Sync failed: ${error ?: "unknown"}", Toast.LENGTH_LONG).show()
                }
            }
            true
        }

        btnExport.setOnClickListener {
            exportToCSV()
        }

        etCrewName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.setCrewName(requireContext(), etCrewName.text.toString())
            }
        }

        view.findViewById<Button>(R.id.btn_force_sync)?.setOnClickListener {
            viewModel.sync { ok, error ->
                if (ok) {
                    Toast.makeText(requireContext(), "Sync complete", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Sync failed: ${error ?: "unknown"}", Toast.LENGTH_LONG).show()
                }
            }
        }

        view.findViewById<Button>(R.id.btn_save_crew)?.setOnClickListener {
            viewModel.setCrewName(requireContext(), etCrewName.text.toString())
            Toast.makeText(requireContext(), "Crew name saved", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun setupBasemapSpinner() {
        val basemapAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            MapBasemap.displayNames
        )
        basemapAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMapBasemap.adapter = basemapAdapter

        val current = viewModel.getBasemap(requireContext())
        basemapSpinnerReady = false
        spinnerMapBasemap.setSelection(MapBasemap.entries.indexOf(current).coerceAtLeast(0))
        spinnerMapBasemap.post { basemapSpinnerReady = true }

        spinnerMapBasemap.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!basemapSpinnerReady) return
                val selected = MapBasemap.entries.getOrElse(position) { MapBasemap.OPEN_STREET_MAP }
                if (selected == viewModel.getBasemap(requireContext())) return
                viewModel.setBasemap(requireContext(), selected)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.basemap_updated, selected.displayName),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun exportToCSV() {
        val filter = spinnerLandowner.selectedItem?.toString() ?: "All"
        val filteredReports = viewModel.filteredReports(requireContext(), filter)

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
