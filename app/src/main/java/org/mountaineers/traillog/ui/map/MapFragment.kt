package org.mountaineers.traillog.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
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
    private var pendingLandowner = "Snohomish County"  // Default
    private var photoFile: File? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
                landowner = pendingLandowner,
                isCleared = false
            )

            lifecycleScope.launch {
                TrailLogRepository.addReport(newReport, photoFile) {
                    Toast.makeText(requireContext(), "Pin + photo synced!", Toast.LENGTH_SHORT).show()
                }
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
        centerMapOnStartup()
        setupLongPress()
        setupMyLocationButton(view)

        // Pin Here button
        view.findViewById<FloatingActionButton>(R.id.fab_pin_here)?.setOnClickListener {
            centerOnUserLocation()
            val center = map?.mapCenter as? GeoPoint
            if (center != null) {
                showAddReportDialog(center.latitude, center.longitude)
            } else {
                Toast.makeText(requireContext(), "No location available to pin", Toast.LENGTH_SHORT).show()
            }
        }

        // Live updates
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

    private fun centerMapOnStartup() {
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val point = GeoPoint(location.latitude, location.longitude)
                map?.controller?.setCenter(point)
                map?.controller?.setZoom(17.0)
            } else {
                val defaultPoint = GeoPoint(48.17, -121.69)
                map?.controller?.setCenter(defaultPoint)
                map?.controller?.setZoom(14.0)
                Toast.makeText(requireContext(), "No location available — centered on Three Fingers", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            val defaultPoint = GeoPoint(48.17, -121.69)
            map?.controller?.setCenter(defaultPoint)
            map?.controller?.setZoom(14.0)
            Toast.makeText(requireContext(), "Location error — centered on Three Fingers", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAllMarkers() {
        map?.let { m ->
            m.overlays.clear()
            setupLongPress()

            val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
            val selectedLandowner = prefs.getString("default_landowner", "All") ?: "All"

            val filteredReports = if (selectedLandowner == "All") {
                TrailLogRepository.reports.value
            } else {
                TrailLogRepository.reports.value.filter { it.landowner == selectedLandowner }
            }

            filteredReports.forEach { addMarker(it, m) }

            m.invalidate()
        } ?: run {
            view?.postDelayed({ refreshAllMarkers() }, 100)
        }
    }

    private fun addMarker(report: TrailReport, mapView: MapView) {
        val marker = Marker(mapView).apply {
            position = GeoPoint(report.lat, report.lng)
            title = report.type.displayName
            snippet = report.description
            relatedObject = report

            icon = if (report.isCleared) {
                resources.getDrawable(android.R.drawable.checkbox_on_background, null)
            } else {
                resources.getDrawable(android.R.drawable.ic_menu_myplaces, null)
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
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_my_location)
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
                val point = GeoPoint(location.latitude, location.longitude)
                map?.controller?.setCenter(point)
                map?.controller?.setZoom(19.0)
            } else {
                Toast.makeText(requireContext(), "No location available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Location error", Toast.LENGTH_SHORT).show()
        }
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
            .setPositiveButton("Next - Landowner") { _, _ ->
                showLandownerDialog(lat, lon, type, selectedSeverity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLandownerDialog(lat: Double, lon: Double, type: ReportType, severity: String) {
        val landowners = listOf("Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")
        var selectedLandowner = "Snohomish County"  // default

        AlertDialog.Builder(requireContext())
            .setTitle("Landowner / Team")
            .setSingleChoiceItems(landowners.toTypedArray(), 2) { _, which ->
                selectedLandowner = landowners[which]
            }
            .setPositiveButton("Next - Quantity") { _, _ ->
                showQuantityDialog(lat, lon, type, severity, selectedLandowner)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuantityDialog(lat: Double, lon: Double, type: ReportType, severity: String, landowner: String) {
        val title = when (type) {
            ReportType.LOG -> "Log diameter in inches"
            else -> "Linear feet to clear?"
        }

        val hint = if (type == ReportType.LOG) "e.g. 12" else "Feet"

        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            this.hint = hint
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
                pendingLandowner = landowner
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
        lifecycleScope.launch {
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