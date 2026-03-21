package org.mountaineers.traillog.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment() {

    private var map: MapView? = null
    private lateinit var locationManager: LocationManager

    private var pendingLat = 0.0
    private var pendingLng = 0.0
    private var pendingType = ReportType.LOG
    private var pendingSeverity = "Medium"
    private var pendingQuantity = 0
    private var photoFile: File? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) @androidx.annotation.RequiresPermission(
        allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION]
    ) { granted ->
        if (granted) centerOnUserLocation() else Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_LONG).show()
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) {
            val newReport = TrailReport(
                lat = pendingLat,
                lng = pendingLng,
                description = "New ${pendingType.displayName}",
                type = pendingType,
                severity = pendingSeverity,
                photoPath = photoFile!!.absolutePath,
                quantity = pendingQuantity,
                reporter = getSavedCrewName(),
                isCleared = false
            )

            lifecycleScope.launch {
                TrailLogRepository.addReport(newReport, photoFile)
                Toast.makeText(requireContext(), "Pin + photo synced!", Toast.LENGTH_SHORT).show()
            }
            refreshAllMarkers()
        } else {
            Toast.makeText(requireContext(), "Photo cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        map = view.findViewById(R.id.map)

        if (map == null) {
            Toast.makeText(requireContext(), "Map failed to initialize", Toast.LENGTH_LONG).show()
            return view
        }

        setupMap()
        setupLongPress()
        setupMyLocationButton(view)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TrailLogRepository.reports.collect {
                    refreshAllMarkers()
                }
            }
        }

        arguments?.let { args ->
            val targetLat = args.getDouble("targetLat", 0.0)
            val targetLng = args.getDouble("targetLng", 0.0)
            if (targetLat != 0.0 && targetLng != 0.0) {
                map?.controller?.animateTo(GeoPoint(targetLat, targetLng), 17.0, 1200L)
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshAllMarkers()
    }

    private fun setupMap() {
        Configuration.getInstance().userAgentValue = "org.mountaineers.traillog"
        map?.setTileSource(TileSourceFactory.MAPNIK)
        map?.setMultiTouchControls(true)
        map?.controller?.setZoom(14.0)
        map?.controller?.setCenter(GeoPoint(48.17, -121.69))
    }

    private fun refreshAllMarkers() {
        map?.let { m ->
            m.overlays.clear()
            setupLongPress()
            TrailLogRepository.reports.value.forEach { addMarker(it, m) }
            m.invalidate()
        } ?: run {
            // Map not ready yet — retry in 200ms
            view?.postDelayed({ refreshAllMarkers() }, 200)
        }
    }

    private fun addMarker(report: TrailReport, mapView: MapView) {
        val marker = Marker(mapView).apply {
            position = GeoPoint(report.lat, report.lng)
            title = report.type.displayName
            snippet = report.description
            relatedObject = report

            icon = when {
                report.isCleared -> {
                    resources.getDrawable(android.R.drawable.checkbox_on_background, null) // green check
                }
                report.severity.lowercase() == "low" -> {
                    resources.getDrawable(android.R.drawable.ic_menu_myplaces, null).apply {
                        setTint(0xFFFFFF00.toInt()) // yellow
                    }
                }
                report.severity.lowercase() == "medium" -> {
                    resources.getDrawable(android.R.drawable.ic_menu_myplaces, null).apply {
                        setTint(0xFFFF9800.toInt()) // orange
                    }
                }
                report.severity.lowercase() == "high" -> {
                    resources.getDrawable(android.R.drawable.ic_menu_myplaces, null).apply {
                        setTint(0xFFF44336.toInt()) // red
                    }
                }
                else -> {
                    resources.getDrawable(android.R.drawable.ic_menu_myplaces, null)
                }
            }

            setInfoWindow(CustomInfoWindow(mapView,
                onEdit = { showEditDialog(it) },
                onToggleComplete = { toggleCompleted(it) },
                onDelete = { showDeleteConfirmation(it) }
            ))
        }
        mapView.overlays.add(marker)
    }

    private fun setupLongPress() {
        val overlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint) = false
            override fun longPressHelper(p: GeoPoint): Boolean {
                showAddReportDialog(p.latitude, p.longitude)
                return true
            }
        })
        map?.overlays?.add(0, overlay)
    }

    private fun setupMyLocationButton(view: View) {
        val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_my_location)
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        fab.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                centerOnUserLocation()
            } else {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun centerOnUserLocation() {
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                map?.controller?.animateTo(GeoPoint(location.latitude, location.longitude), 16.0, 800L)
            }
        } catch (e: Exception) {}
    }

    private fun showAddReportDialog(lat: Double, lon: Double) {
        val types = resources.getStringArray(R.array.report_types)
        var selectedType = ReportType.LOG

        AlertDialog.Builder(requireContext())
            .setTitle("New Trail Problem - Type")
            .setSingleChoiceItems(types, 0) { _, which ->
                selectedType = when (which) {
                    0 -> ReportType.LOG
                    1 -> ReportType.BRUSH
                    2 -> ReportType.TREADWORK
                    else -> ReportType.OTHER
                }
            }
            .setPositiveButton("Next - Severity") { _, _ ->
                showSeverityDialog(lat, lon, selectedType)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSeverityDialog(lat: Double, lon: Double, type: ReportType) {
        val severities = arrayOf("Low", "Medium", "High")
        var selectedSeverity = "Medium"

        AlertDialog.Builder(requireContext())
            .setTitle("Severity Level")
            .setSingleChoiceItems(severities, 1) { _, which ->
                selectedSeverity = severities[which]
            }
            .setPositiveButton("Next - Quantity") { _, _ ->
                showQuantityDialog(lat, lon, type, selectedSeverity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuantityDialog(lat: Double, lon: Double, type: ReportType, severity: String) {
        val title = when (type) {
            ReportType.LOG -> "How many logs to clear"
            else -> "Linear feet to clear?"
        }

        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = if (type == ReportType.LOG) "Number of logs" else "Feet"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Take Photo & Add") { _, _ ->
                pendingQuantity = input.text.toString().toIntOrNull() ?: 0
                pendingLat = lat
                pendingLng = lon
                pendingType = type
                pendingSeverity = severity
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageName = "TrailLog_${timeStamp}.jpg"
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        photoFile = File(storageDir, imageName)

        val photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile!!)
        takePhotoLauncher.launch(photoUri)
    }



    private fun toggleCompleted(report: TrailReport) {
        val updated = report.copy(isCleared = !report.isCleared)

        runBlocking {
            TrailLogRepository.updateReport(updated)
        }

        refreshAllMarkers()

        val message = if (updated.isCleared) "Marked as Completed ✓" else "Re-opened"
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showEditDialog(report: TrailReport) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_report, null)
        val etDesc = view.findViewById<EditText>(R.id.et_description)
        val etQty = view.findViewById<EditText>(R.id.et_quantity)

        etDesc.setText(report.description)
        etQty.setText(report.quantity.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Report")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newDesc = etDesc.text.toString().trim()
                val newQty = etQty.text.toString().toIntOrNull() ?: report.quantity
                val updated = report.copy(description = newDesc, quantity = newQty)
                lifecycleScope.launch {
                    TrailLogRepository.updateReport(updated)
                }
                refreshAllMarkers()
                Toast.makeText(requireContext(), "Report updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(report: TrailReport) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Report?")
            .setMessage("This will permanently remove the pin and photo.\n\nAre you sure?")
            .setPositiveButton("Delete") { _, _ ->
                if (report.photoPath.isNotEmpty()) File(report.photoPath).delete()
                lifecycleScope.launch {
                    TrailLogRepository.deleteReport(report.id)
                }
                refreshAllMarkers()
                Toast.makeText(requireContext(), "Report deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getSavedCrewName(): String {
        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        return prefs.getString("crew_name", "You") ?: "You"
    }
}