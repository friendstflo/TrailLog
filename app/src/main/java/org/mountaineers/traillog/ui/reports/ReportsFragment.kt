package org.mountaineers.traillog.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailReport

class ReportsFragment : Fragment() {

    private val viewModel: ReportsViewModel by viewModels()

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

        val saved = viewModel.getLandownerFilter(requireContext())
        spinnerLandowner.setSelection(landowners.indexOf(saved).takeIf { it >= 0 } ?: 0)

        spinnerLandowner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = landowners[position]
                viewModel.setLandownerFilter(requireContext(), selected)
                Toast.makeText(requireContext(), "Filtering by: $selected", Toast.LENGTH_SHORT).show()
                applyFilter(viewModel.reports.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.sync()
            swipeRefresh.isRefreshing = false
            Toast.makeText(requireContext(), "Sync requested", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reports.collect { reports ->
                    applyFilter(reports)
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        applyFilter(viewModel.reports.value)
    }

    private fun applyFilter(reports: List<TrailReport>) {
        adapter.submitList(viewModel.filteredSorted(requireContext(), reports))
    }
}

class ReportsAdapter(
    private val onClick: (TrailReport) -> Unit
) : ListAdapter<TrailReport, ReportsAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = getItem(position)
        val itemView = holder.itemView
        val ctx = itemView.context

        itemView.findViewById<android.widget.TextView>(R.id.tv_type).text = report.type.displayName
        itemView.findViewById<android.widget.TextView>(R.id.tv_date).text =
            report.timestamp.toString().take(10)
        itemView.findViewById<android.widget.TextView>(R.id.tv_description).text = report.description

        val qtyText = when (report.type) {
            ReportType.LOG -> ctx.getString(R.string.report_quantity_inches, report.quantity)
            else -> ctx.getString(R.string.report_quantity_feet, report.quantity)
        }
        itemView.findViewById<android.widget.TextView>(R.id.tv_quantity).text = qtyText

        itemView.findViewById<android.widget.TextView>(R.id.tv_landowner).text =
            ctx.getString(R.string.report_landowner, report.landowner)

        val severityTv = itemView.findViewById<android.widget.TextView>(R.id.tv_severity)
        if (report.isCleared) {
            severityTv.text = ctx.getString(R.string.report_status_complete)
            severityTv.setBackgroundColor(0xFF4CAF50.toInt())
        } else {
            severityTv.text = report.severity.uppercase()
            severityTv.setBackgroundColor(
                when (report.severity.lowercase()) {
                    "low" -> 0xFFFFFF00.toInt()
                    "medium" -> 0xFFFF9800.toInt()
                    "high" -> 0xFFF44336.toInt()
                    else -> 0xFFFF8C00.toInt()
                }
            )
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

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TrailReport>() {
            override fun areItemsTheSame(oldItem: TrailReport, newItem: TrailReport): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TrailReport, newItem: TrailReport): Boolean =
                oldItem == newItem
        }
    }
}
