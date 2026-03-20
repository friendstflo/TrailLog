package org.mountaineers.traillog.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport

class StatsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)

        // Live updates from Firebase (explicit type fixes inference)
        lifecycleScope.launch {
            TrailLogRepository.reports.collect { reports: List<TrailReport> ->
                updateStats(view, reports)
            }
        }

        return view
    }

    private fun updateStats(view: View, reports: List<TrailReport>) {
        val total = reports.size
        val cleared = reports.count { it.isCleared }
        val pending = total - cleared

        val logsRemoved = reports.filter { it.type == ReportType.LOG && it.isCleared }.sumOf { it.quantity }
        val brushFeet = reports.filter { it.type == ReportType.BRUSH && it.isCleared }.sumOf { it.quantity }
        val treadFeet = reports.filter { it.type == ReportType.TREADWORK && it.isCleared }.sumOf { it.quantity }

        view.findViewById<TextView>(R.id.tv_total).text = "Total Reports\n$total"
        view.findViewById<TextView>(R.id.tv_cleared).text = "Cleared\n$cleared"
        view.findViewById<TextView>(R.id.tv_pending).text = "Pending\n$pending"

        view.findViewById<TextView>(R.id.tv_logs).text = "Logs Removed\n$logsRemoved"
        view.findViewById<TextView>(R.id.tv_brush).text = "Brushing\n${brushFeet} ft"
        view.findViewById<TextView>(R.id.tv_tread).text = "Treadwork\n${treadFeet} ft"
    }
}