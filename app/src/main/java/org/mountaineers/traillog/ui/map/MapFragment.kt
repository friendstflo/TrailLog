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
    private var pendingLandowner = "Snohomish County"
    private var photoFile: File? = null

    private val landowners = listOf("All", "Darrington RD", "Gifford-Pinchot RD", "Snohomish County", "Other")

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) centerOnUserLocation() else Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_LONG).show()
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
                TrailLogRepository.addReport(newReport, photoFile)
            }
            view?.postDelayed({ refreshAllMarkers() }, 300)
            Toast.makeText(requireContext(), "Pin added successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Photo capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        map = view.findViewById(R.id.map)

        if (map == null) {
            Toast.makeText(requireContext(), "Map failed to initialize", Toast.LENGTH_LONG).show()
            return view
        }

        setupMapBasic()

        // Handle target pin from Reports
        val args = arguments
        if (args != null) {
            val targetLat = args.getDouble("targetLat", 0.0)
            val targetLng = args.getDouble("targetLng", 0.0)
            if (targetLat != 0.0 && targetLng != 0.0) {
                arguments = null
                view.postDelayed({
                    val point = GeoPoint(targetLat, targetLng)
                    map?.controller?.animateTo(point, 18.0, 1200L)
                }, 500)
            }
        }

        view.findViewById<FloatingActionButton>(R.id.fab_pin_here)?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@setOnClickListener
            }
            centerOnUserLocation()
            view.postDelayed({
                val center = map?.mapCenter as? GeoPoint
                if (center != null) {
                    showAddReportDialog(center.latitude, center.longitude)
                }
            }, 300)
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TrailLogRepository.reports.collect {
                    refreshAllMarkers()
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        setupLongPress()
        setupMyLocationButton(requireView())
        centerMapOnStartup()
        refreshAllMarkers()
    }

    private fun setupMapBasic() {
        Configuration.getInstance().userAgentValue = "org.mountaineers.traillog"
        map?.setTileSource(TileSourceFactory.MAPNIK)
        map?.setMultiTouchControls(true)
        map?.controller?.setZoom(14.0)
    }

    private fun centerMapOnStartup() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            centerOnUserLocation()
        } else {
            centerOnDefaultLocation()
        }
    }

    private fun centerOnUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val point = GeoPoint(location.latitude, location.longitude)
                map?.controller?.setCenter(point)
                map?.controller?.setZoom(19.0)
            } else {
                centerOnDefaultLocation()
            }
        } catch (e: Exception) {
            centerOnDefaultLocation()
        }
    }

    private fun centerOnDefaultLocation() {
        val defaultPoint = GeoPoint(48.17, -121.69)
        map?.controller?.setCenter(defaultPoint)
        map?.controller?.setZoom(14.0)
    }

    private fun refreshAllMarkers() {
        map?.let { m ->
            m.overlays.clear()
            setupLongPress()

            val filter = TrailLogRepository.getCurrentLandownerFilter(requireContext())

            val filteredReports = if (filter == "All") {
                TrailLogRepository.reports.value
            } else {
                TrailLogRepository.reports.value.filter { it.landowner == filter }
            }

            filteredReports.forEach { addMarker(it, m) }
            m.invalidate()
        } ?: run {
            view?.postDelayed({ refreshAllMarkers() }, 250)
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
            centerOnUserLocation()
        }
    }

    // ==================== Add Report Flow ====================

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
            .setPositiveButton("Next") { _, _ ->
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
            .setPositiveButton("Next") { _, _ ->
                showLandownerDialog(lat, lon, type, selectedSeverity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLandownerDialog(lat: Double, lon: Double, type: ReportType, severity: String) {
        val currentFilter = TrailLogRepository.getCurrentLandownerFilter(requireContext())
        val defaultIndex = if (currentFilter != "All") landowners.indexOf(currentFilter) else 3

        AlertDialog.Builder(requireContext())
            .setTitle("Landowner / Team")
            .setSingleChoiceItems(landowners.toTypedArray(), defaultIndex) { _, which ->
                pendingLandowner = landowners[which]
            }
            .setPositiveButton("Next") { _, _ ->
                showQuantityDialog(lat, lon, type, severity, pendingLandowner)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuantityDialog(lat: Double, lon: Double, type: ReportType, severity: String, landowner: String) {
        val title = when (type) {
            ReportType.LOG -> "Log diameter in inches"
            else -> "Linear feet to clear"
        }

        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Take Photo") { _, _ ->
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

        val photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile!!
        )
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

        val etDescription = view.findViewById<EditText>(R.id.et_description)
        val etQuantity = view.findViewById<EditText>(R.id.et_quantity)

        etDescription.setText(report.description)
        etQuantity.setText(report.quantity.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Report")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newDescription = etDescription.text.toString().trim()
                val newQuantity = etQuantity.text.toString().toIntOrNull() ?: report.quantity

                val updatedReport = report.copy(
                    description = newDescription,
                    quantity = newQuantity
                )

                lifecycleScope.launch {
                    TrailLogRepository.updateReport(updatedReport)
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

                map?.overlays?.forEach { overlay ->
                    if (overlay is Marker && (overlay.relatedObject as? TrailReport)?.id == report.id) {
                        overlay.closeInfoWindow()
                    }
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