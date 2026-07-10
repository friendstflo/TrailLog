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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailReport

class StatsFragment : Fragment() {

    private val viewModel: StatsViewModel by viewModels()

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

        val saved = viewModel.getLandownerFilter(requireContext())
        spinnerLandowner.setSelection(landowners.indexOf(saved).takeIf { it >= 0 } ?: 0)

        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = landowners[position]
                viewModel.setLandownerFilter(requireContext(), selected)
                updateStats(viewModel.reports.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reports.collect { reports: List<TrailReport> ->
                    updateStats(reports)
                }
            }
        }

        view.post {
            updateStats(viewModel.reports.value)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        updateStats(viewModel.reports.value)
    }

    private fun updateStats(reports: List<TrailReport>) {
        if (!isAdded) return
        val stats = viewModel.computeStats(requireContext(), reports)

        tvTotal.text = getString(R.string.stats_total, stats.total)
        tvCleared.text = getString(R.string.stats_cleared, stats.cleared)
        tvPending.text = getString(R.string.stats_pending, stats.pending)
        tvLogs.text = getString(R.string.stats_logs_removed, stats.logsRemoved)
        tvBrush.text = getString(R.string.stats_brushing, stats.brushFeet)
        tvTread.text = getString(R.string.stats_treadwork, stats.treadFeet)
    }
}
