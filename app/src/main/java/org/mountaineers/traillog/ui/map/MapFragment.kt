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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.data.TrailReport
import org.mountaineers.traillog.map.MapAreaCacheHelper
import org.mountaineers.traillog.map.MapBasemapPreferences
import org.mountaineers.traillog.map.MapPinIcons
import org.mountaineers.traillog.map.OsmdroidConfig
import org.mountaineers.traillog.map.TrailPreset
import org.mountaineers.traillog.map.TrailPresets
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment() {

    private val viewModel: MapViewModel by viewModels()

    private var map: MapView? = null
    private lateinit var locationManager: LocationManager

    private var pendingLat = 0.0
    private var pendingLng = 0.0
    private var pendingType = ReportType.LOG
    private var pendingSeverity = "Medium"
    private var pendingQuantity = 0
    private var pendingLandowner = "Snohomish County"
    private var photoFile: File? = null
    /** Avoid re-centering every onResume (preserves pan/zoom + preset jumps). */
    private var didInitialCenter = false

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

            viewModel.addReport(newReport, photoFile)
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

        view.findViewById<FloatingActionButton>(R.id.fab_trail_preset)?.setOnClickListener {
            showTrailPresetPicker()
        }

        view.findViewById<FloatingActionButton>(R.id.fab_cache_area)?.setOnClickListener {
            val m = map
            if (m != null) {
                MapAreaCacheHelper.cacheVisibleArea(requireContext(), m, zoomLevelsBelow = 2)
            } else {
                Toast.makeText(requireContext(), R.string.cache_need_network, Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reports.collect {
                    refreshAllMarkers()
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        applySelectedBasemap()
        setupLongPress()
        setupMyLocationButton(requireView())
        if (!didInitialCenter) {
            centerMapOnStartup()
            didInitialCenter = true
        }
        refreshAllMarkers()
        map?.onResume()
    }

    override fun onPause() {
        map?.onPause()
        super.onPause()
    }

    private fun setupMapBasic() {
        OsmdroidConfig.ensureConfigured(requireContext())
        map?.setMultiTouchControls(true)
        map?.isTilesScaledToDpi = true
        // Allow network downloads so panned areas are cached for offline use later
        map?.setUseDataConnection(true)
        applySelectedBasemap()
        map?.controller?.setZoom(14.0)
    }

    /** Applies OpenStreetMap or USGS Topo from Settings; tiles cache to disk automatically. */
    private fun applySelectedBasemap() {
        val basemap = MapBasemapPreferences.get(requireContext())
        val source = MapBasemapPreferences.tileSource(basemap)
        map?.setTileSource(source)
        map?.maxZoomLevel = MapBasemapPreferences.maxZoom(basemap)
        map?.minZoomLevel = 3.0
        // Clamp current zoom if switching to USGS (max 16)
        val currentZoom = map?.zoomLevelDouble ?: 14.0
        val maxZ = MapBasemapPreferences.maxZoom(basemap)
        if (currentZoom > maxZ) {
            map?.controller?.setZoom(maxZ)
        }
        map?.invalidate()
    }

    private fun centerMapOnStartup() {
        // Prefer land-manager preset from Settings filter when no fresh GPS fix is available
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                if (!::locationManager.isInitialized) {
                    locationManager =
                        requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                }
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (location != null) {
                    map?.controller?.setCenter(GeoPoint(location.latitude, location.longitude))
                    map?.controller?.setZoom(16.0)
                    return
                }
            } catch (_: Exception) {
                // fall through to preset
            }
        }
        centerOnLandManagerOrFallback()
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
                centerOnLandManagerOrFallback()
            }
        } catch (_: Exception) {
            centerOnLandManagerOrFallback()
        }
    }

    private fun centerOnLandManagerOrFallback() {
        val filter = viewModel.getLandownerFilter(requireContext())
        val preset = TrailPresets.forLandowner(filter) ?: TrailPresets.FALLBACK
        applyTrailPreset(preset, animate = false)
    }

    private fun showTrailPresetPicker() {
        val presets = TrailPresets.all
        val labels = presets.map { it.displayName }.toTypedArray()
        val filter = viewModel.getLandownerFilter(requireContext())
        val checked = presets.indexOfFirst { it.landowner == filter }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.trail_preset_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val preset = presets[which]
                // Align landowner filter with selected trail area
                TrailLogRepository.setCurrentLandowner(requireContext(), preset.landowner)
                applyTrailPreset(preset, animate = true)
                refreshAllMarkers()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.trail_preset_jumped, preset.displayName),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyTrailPreset(preset: TrailPreset, animate: Boolean) {
        val point = preset.geoPoint
        val zoom = preset.zoom.coerceAtMost(map?.maxZoomLevel ?: preset.zoom)
        if (animate) {
            map?.controller?.animateTo(point, zoom, 900L)
        } else {
            map?.controller?.setCenter(point)
            map?.controller?.setZoom(zoom)
        }
    }

    private fun refreshAllMarkers() {
        map?.let { m ->
            m.overlays.clear()
            setupLongPress()

            val filteredReports = viewModel.filteredReports(requireContext())
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
            snippet = buildString {
                append(report.severity)
                if (report.isCleared) append(" · Complete")
                if (report.description.isNotBlank()) append("\n").append(report.description)
            }
            relatedObject = report

            // Teardrop pin: amber / orange / red by severity, green when complete
            icon = MapPinIcons.forReport(requireContext(), report)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

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
        val currentFilter = viewModel.getLandownerFilter(requireContext())
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
        viewModel.updateReport(updated)
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

                viewModel.updateReport(updatedReport)
                refreshAllMarkers()
                Toast.makeText(requireContext(), "Report updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(report: TrailReport) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Report?")
            .setMessage("This will permanently remove the pin, photo, and all data.\n\nAre you sure?")
            .setPositiveButton("Delete") { _, _ ->
                if (report.photoPath.isNotEmpty()) {
                    // Delete local file only if path is a local filesystem path
                    val local = File(report.photoPath)
                    if (local.exists()) local.delete()
                }

                viewModel.deleteReport(report.id)

                map?.overlays?.forEach { overlay ->
                    if (overlay is Marker && (overlay.relatedObject as? TrailReport)?.id == report.id) {
                        overlay.closeInfoWindow()
                    }
                }

                refreshAllMarkers()
                Toast.makeText(requireContext(), "Report and photo deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getSavedCrewName(): String {
        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        return prefs.getString("crew_name", "You") ?: "You"
    }
}
