package org.mountaineers.traillog.ui.reports

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
    private lateinit var spinnerLandowner: Spinner

    private val landowners = listOf("All", "Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)

        recyclerView = view.findViewById(R.id.recycler_reports)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        spinnerLandowner = view.findViewById(R.id.spinner_landowner)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ReportsAdapter { report ->
            val bundle = Bundle().apply {
                putDouble("targetLat", report.lat)
                putDouble("targetLng", report.lng)
            }
            findNavController().navigate(R.id.mapFragment, bundle)
        }
        recyclerView.adapter = adapter

        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, landowners)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLandowner.adapter = spinnerAdapter

        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("default_landowner", "All") ?: "All"
        spinnerLandowner.setSelection(landowners.indexOf(saved).takeIf { it >= 0 } ?: 0)

        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = landowners[position]
                prefs.edit().putString("default_landowner", selected).apply()
                Toast.makeText(requireContext(), "Filtering by: $selected", Toast.LENGTH_SHORT).show()
                applyFilter(TrailLogRepository.reports.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
        }

        lifecycleScope.launch {
            TrailLogRepository.reports.collect { reports ->
                applyFilter(reports)
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        applyFilter(TrailLogRepository.reports.value)
    }

    private fun applyFilter(reports: List<TrailReport>) {
        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        val filter = prefs.getString("default_landowner", "All") ?: "All"

        val filtered = if (filter == "All") reports else reports.filter { it.landowner == filter }

        adapter.submitList(filtered.sortedWith(compareBy({ it.isCleared }, { -it.timestamp.time })))
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
            org.mountaineers.traillog.data.ReportType.LOG -> "${report.quantity} inches"
            else -> "${report.quantity} ft"
        }
        itemView.findViewById<android.widget.TextView>(R.id.tv_quantity).text = qtyText

        itemView.findViewById<android.widget.TextView>(R.id.tv_landowner).text = "Landowner: ${report.landowner}"

        val severityTv = itemView.findViewById<android.widget.TextView>(R.id.tv_severity)
        if (report.isCleared) {
            severityTv.text = "COMPLETE"
            severityTv.setBackgroundColor(0xFF4CAF50.toInt())
        } else {
            severityTv.text = report.severity.uppercase()
            severityTv.setBackgroundColor(when (report.severity.lowercase()) {
                "low" -> 0xFFFFFF00.toInt()
                "medium" -> 0xFFFF9800.toInt()
                "high" -> 0xFFF44336.toInt()
                else -> 0xFFFF8C00.toInt()
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