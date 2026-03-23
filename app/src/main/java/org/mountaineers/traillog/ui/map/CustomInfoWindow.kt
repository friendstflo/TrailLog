package org.mountaineers.traillog.ui.map

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailReport
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

class CustomInfoWindow(
    mapView: MapView,
    private val onEdit: (TrailReport) -> Unit,
    private val onToggleComplete: (TrailReport) -> Unit,
    private val onDelete: (TrailReport) -> Unit
) : MarkerInfoWindow(R.layout.info_window, mapView) {

    override fun onOpen(item: Any?) {
        val report = (item as Marker).relatedObject as TrailReport

        view.findViewById<TextView>(R.id.tv_title).text = report.type.displayName
        view.findViewById<TextView>(R.id.tv_desc).text = report.description
        view.findViewById<TextView>(R.id.tv_date).text = report.timestamp.toString().take(10)

        val qtyText = when (report.type) {
            org.mountaineers.traillog.data.ReportType.LOG -> "${report.quantity} inches"
            else -> "${report.quantity} ft"
        }
        view.findViewById<TextView>(R.id.tv_quantity).text = qtyText

        val severityTv = view.findViewById<TextView>(R.id.tv_severity)
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

        val iv = view.findViewById<ImageView>(R.id.iv_thumbnail)
        if (report.photoPath.isNotEmpty()) {
            Glide.with(view.context)
                .load(report.photoPath)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(iv)
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Buttons
        view.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            onDelete(report)
            close()
        }

        view.findViewById<Button>(R.id.btn_edit).setOnClickListener {
            onEdit(report)
            close()
        }

        val btnComplete = view.findViewById<Button>(R.id.btn_completed)
        if (report.isCleared) {
            btnComplete.text = "Re-open"
            btnComplete.setBackgroundColor(0xFFFF9800.toInt()) // orange
        } else {
            btnComplete.text = "Complete"
            btnComplete.setBackgroundColor(0xFF1B5E20.toInt()) // dark green
        }
        btnComplete.setOnClickListener {
            onToggleComplete(report)
            close()
        }

        // Force redraw of button
        btnComplete.invalidate()
    }
}