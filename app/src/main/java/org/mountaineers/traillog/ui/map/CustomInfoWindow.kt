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
import java.io.File

class CustomInfoWindow(
    mapView: MapView,
    private val onEdit: (TrailReport) -> Unit,
    private val onToggleComplete: (TrailReport) -> Unit,
    private val onDelete: (TrailReport) -> Unit
) : MarkerInfoWindow(R.layout.info_window, mapView) {

    override fun onOpen(item: Any?) {
        val report = (item as Marker).relatedObject as TrailReport

        // Title
        view.findViewById<TextView>(R.id.tv_title).text = report.type.displayName

        // Description
        view.findViewById<TextView>(R.id.tv_desc).text = report.description

        // Date
        view.findViewById<TextView>(R.id.tv_date).text = report.timestamp.toString().take(10)

        // Quantity
        val qtyText = when (report.type) {
            org.mountaineers.traillog.data.ReportType.LOG -> "${report.quantity} logs"
            else -> "${report.quantity} ft"
        }
        view.findViewById<TextView>(R.id.tv_quantity).text = qtyText

        // Photo (Firebase URL)
        val iv = view.findViewById<ImageView>(R.id.iv_thumbnail)
        if (report.photoPath.isNotEmpty()) {
            Glide.with(view.context)
                .load(report.photoPath)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(iv)
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Delete button (top right)
        view.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            onDelete(report)
            close()
        }

        // Edit button
        view.findViewById<Button>(R.id.btn_edit).setOnClickListener {
            onEdit(report)
            close()
        }

        // Completed / Re-open button - correct toggle logic
        val btnComplete = view.findViewById<Button>(R.id.btn_completed)
        if (report.isCleared) {
            btnComplete.text = "Re-open"
            btnComplete.setBackgroundColor(0xFFFF9800.toInt()) // orange for re-open
        } else {
            btnComplete.text = "Completed"
            btnComplete.setBackgroundColor(0xFF4CAF50.toInt()) // green for complete
        }
        btnComplete.setOnClickListener {
            onToggleComplete(report)
            close()
        }
    }
}