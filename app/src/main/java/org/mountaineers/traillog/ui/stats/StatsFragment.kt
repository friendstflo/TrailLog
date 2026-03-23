package org.mountaineers.traillog.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.content.Context
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport

class StatsFragment : Fragment() {

    private lateinit var spinnerLandowner: Spinner
    private lateinit var tvTotal: TextView
    private lateinit var tvCleared: TextView
    private lateinit var tvPending: TextView
    private lateinit var tvLogs: TextView
    private lateinit var tvBrush: TextView
    private lateinit var tvTread: TextView

    private val landowners = listOf("All", "Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)

        spinnerLandowner = view.findViewById(R.id.spinner_landowner)
        tvTotal = view.findViewById(R.id.tv_total)
        tvCleared = view.findViewById(R.id.tv_cleared)
        tvPending = view.findViewById(R.id.tv_pending)
        tvLogs = view.findViewById(R.id.tv_logs)
        tvBrush = view.findViewById(R.id.tv_brush)
        tvTread = view.findViewById(R.id.tv_tread)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, landowners)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLandowner.adapter = adapter

        // Load saved filter
        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        val savedLandowner = prefs.getString("default_landowner", "All") ?: "All"
        val position = landowners.indexOf(savedLandowner)
        spinnerLandowner.setSelection(if (position >= 0) position else 0)

        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateStats(requireView(), TrailLogRepository.reports.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Live collect
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TrailLogRepository.reports.collect { reports ->
                    updateStats(requireView(), reports)
                }
            }
        }

        // Initial update after view is fully laid out
        view.post {
            updateStats(view, TrailLogRepository.reports.value)
        }

        return view
    }

    private fun updateStats(view: View, reports: List<TrailReport>) {
        val filter = spinnerLandowner.selectedItem?.toString() ?: "All"
        val filtered = if (filter == "All") reports else reports.filter { it.landowner == filter }

        val total = filtered.size
        val cleared = filtered.count { it.isCleared }
        val pending = total - cleared

        val logsRemoved = filtered.filter { it.type == ReportType.LOG && it.isCleared }.size
        val brushFeet = filtered.filter { it.type == ReportType.BRUSH && it.isCleared }.sumOf { it.quantity }
        val treadFeet = filtered.filter { it.type == ReportType.TREADWORK && it.isCleared }.sumOf { it.quantity }

        view.findViewById<TextView>(R.id.tv_total).text = "Total Reports\n$total"
        view.findViewById<TextView>(R.id.tv_cleared).text = "Cleared\n$cleared"
        view.findViewById<TextView>(R.id.tv_pending).text = "Pending\n$pending"

        view.findViewById<TextView>(R.id.tv_logs).text = "Logs Removed\n$logsRemoved"
        view.findViewById<TextView>(R.id.tv_brush).text = "Brushing\n${brushFeet} ft"
        view.findViewById<TextView>(R.id.tv_tread).text = "Treadwork\n${treadFeet} ft"
    }
}