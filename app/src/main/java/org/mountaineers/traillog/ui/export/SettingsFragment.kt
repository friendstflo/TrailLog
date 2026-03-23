package org.mountaineers.traillog.ui.export

import android.content.Context
import android.os.Bundle
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
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailLogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var etCrewName: EditText
    private lateinit var tvLastSync: TextView
    private lateinit var btnExport: Button
    private lateinit var spinnerLandowner: Spinner

    private val landowners = listOf("All", "Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        etCrewName = view.findViewById(R.id.et_crew_name)
        tvLastSync = view.findViewById(R.id.tv_last_sync)
        btnExport = view.findViewById(R.id.btn_export_csv)
        spinnerLandowner = view.findViewById(R.id.spinner_landowner)

        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)

        // Crew name
        etCrewName.setText(prefs.getString("crew_name", "You"))

        // Landowner filter spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, landowners)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLandowner.adapter = adapter

        // Load saved selection (default to "Snohomish County")
        val savedLandowner = prefs.getString("default_landowner", "Snohomish County") ?: "Snohomish County"
        val position = landowners.indexOf(savedLandowner)
        spinnerLandowner.setSelection(if (position >= 0) position else 2)  // 2 = Snohomish County

        // Save selection on change
        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = landowners[position]
                prefs.edit().putString("default_landowner", selected).apply()
                Toast.makeText(requireContext(), "Filtering by: $selected", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

        btnExport.setOnClickListener {
            // Your CSV export logic
            Toast.makeText(requireContext(), "CSV exported!", Toast.LENGTH_SHORT).show()
        }

        // Save crew name on focus loss
        etCrewName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.edit().putString("crew_name", etCrewName.text.toString().trim()).apply()
            }
        }

        return view
    }
}