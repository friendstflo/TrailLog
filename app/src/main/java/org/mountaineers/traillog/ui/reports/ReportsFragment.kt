package org.mountaineers.traillog.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport

class ReportsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReportsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)

        recyclerView = view.findViewById(R.id.recycler_reports)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ReportsAdapter { report ->
            val bundle = Bundle().apply {
                putDouble("targetLat", report.lat)
                putDouble("targetLng", report.lng)
            }
            findNavController().navigate(R.id.mapFragment, bundle)
        }
        recyclerView.adapter = adapter

        // Pull-to-refresh
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
            Toast.makeText(requireContext(), "Refreshed", Toast.LENGTH_SHORT).show()
        }

        // Initial load + live updates
        lifecycleScope.launch {
            // First load immediately
            val initialReports = TrailLogRepository.reports.value
            adapter.submitList(initialReports.sortedWith(compareBy({ it.isCleared }, { -it.timestamp.time })))

            // Then live collect
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TrailLogRepository.reports.collect { updatedList ->
                    adapter.submitList(updatedList.sortedWith(compareBy({ it.isCleared }, { -it.timestamp.time })))
                }
            }
        }

        return view
    }
}

class ReportsAdapter(private val onClick: (TrailReport) -> Unit) :
    RecyclerView.Adapter<ReportsAdapter.ViewHolder>() {

    private var currentList = emptyList<TrailReport>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = currentList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = currentList[position]
        val itemView = holder.itemView

        itemView.findViewById<android.widget.TextView>(R.id.tv_type).text = report.type.displayName
        itemView.findViewById<android.widget.TextView>(R.id.tv_date).text = report.timestamp.toString().take(10)
        itemView.findViewById<android.widget.TextView>(R.id.tv_description).text = report.description

        val qtyText = when (report.type) {
            org.mountaineers.traillog.data.ReportType.LOG -> "${report.quantity} logs"
            else -> "${report.quantity} ft"
        }
        itemView.findViewById<android.widget.TextView>(R.id.tv_quantity).text = qtyText

        val severityTv = itemView.findViewById<android.widget.TextView>(R.id.tv_severity)
        if (report.isCleared) {
            severityTv.text = "COMPLETE"
            severityTv.setBackgroundColor(0xFF4CAF50.toInt()) // green
        } else {
            severityTv.text = report.severity.uppercase()
            severityTv.setBackgroundColor(when (report.severity.lowercase()) {
                "low" -> 0xFFFFFF00.toInt()   // yellow
                "medium" -> 0xFFFF9800.toInt() // orange
                "high" -> 0xFFF44336.toInt()   // red
                else -> 0xFFFF8C00.toInt()     // default orange
            })
        }

        val iv = itemView.findViewById<android.widget.ImageView>(R.id.iv_thumbnail)
        if (report.photoPath.isNotEmpty()) {
            Glide.with(iv.context)
                .load(report.photoPath)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(iv)
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        itemView.setOnClickListener { onClick(report) }
    }

    fun submitList(newList: List<TrailReport>) {
        currentList = newList
        notifyDataSetChanged()
    }
}