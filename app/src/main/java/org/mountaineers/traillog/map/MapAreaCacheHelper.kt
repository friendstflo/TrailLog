package org.mountaineers.traillog.map

import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import org.mountaineers.traillog.R
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException
import org.osmdroid.views.MapView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Downloads map tiles for the current viewport into the OSMdroid disk cache
 * so crews can use the basemap offline after pre-caching on Wi‑Fi.
 *
 * OpenStreetMap: uses [OpenStreetMapTileSource] (bulk allowed, polite concurrency).
 * Stock osmdroid MAPNIK sets FLAG_NO_BULK and throws [TileSourcePolicyException].
 */
object MapAreaCacheHelper {

    private const val TAG = "MapAreaCache"

    /**
     * Caches [zoomLevelsBelow] extra detail levels under the current zoom
     * (capped by the map's max zoom / tile source).
     */
    @Suppress("DEPRECATION") // ProgressDialog is fine for a simple field tool progress UI
    fun cacheVisibleArea(
        context: Context,
        map: MapView,
        zoomLevelsBelow: Int = 2
    ) {
        if (!map.useDataConnection()) {
            Toast.makeText(context, R.string.cache_need_network, Toast.LENGTH_LONG).show()
            return
        }

        // Ensure bulk-capable sources (re-apply if user still on stock MAPNIK somehow)
        ensureBulkCapableTileSource(map)

        val source = map.tileProvider?.tileSource
        if (source is OnlineTileSourceBase &&
            !source.tileSourcePolicy.acceptsBulkDownload()
        ) {
            android.app.AlertDialog.Builder(context)
                .setTitle(R.string.cache_area_title)
                .setMessage(R.string.cache_bulk_not_allowed)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val bbox = map.boundingBox
        val zoomMin = map.zoomLevelDouble.roundToInt().coerceAtLeast(map.minZoomLevel.toInt())
        val zoomMax = min(
            map.maxZoomLevel.toInt(),
            zoomMin + zoomLevelsBelow.coerceAtLeast(0)
        ).coerceAtLeast(zoomMin)

        val cacheManager = try {
            CacheManager(map)
        } catch (e: TileSourcePolicyException) {
            Log.e(TAG, "Tile source rejects bulk download", e)
            Toast.makeText(context, R.string.cache_bulk_not_allowed, Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Could not create CacheManager", e)
            Toast.makeText(context, R.string.cache_failed_generic, Toast.LENGTH_LONG).show()
            return
        }

        val possible = try {
            cacheManager.possibleTilesInArea(bbox, zoomMin, zoomMax)
        } catch (e: Exception) {
            Log.e(TAG, "Could not estimate tiles", e)
            -1
        }

        val message = if (possible > 0) {
            context.getString(R.string.cache_confirm_message, possible, zoomMin, zoomMax)
        } else {
            context.getString(R.string.cache_confirm_message_unknown, zoomMin, zoomMax)
        }

        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.cache_area_title)
            .setMessage(message)
            .setPositiveButton(R.string.cache_start) { _, _ ->
                startDownload(context, map, cacheManager, bbox, zoomMin, zoomMax)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * If the map is still on stock MAPNIK (no bulk), switch to our bulk-capable OSM source
     * with the same cache name ("Mapnik") so offline tiles still apply.
     */
    private fun ensureBulkCapableTileSource(map: MapView) {
        val current = map.tileProvider?.tileSource
        val name = current?.name().orEmpty()
        val allowsBulk = (current as? OnlineTileSourceBase)
            ?.tileSourcePolicy
            ?.acceptsBulkDownload() == true

        if (!allowsBulk && (name.equals("Mapnik", ignoreCase = true) ||
                name.contains("OpenStreet", ignoreCase = true) ||
                name.contains("OSM", ignoreCase = true))
        ) {
            Log.d(TAG, "Switching $name → OpenStreetMapTileSource for bulk cache support")
            map.setTileSource(OpenStreetMapTileSource)
        }
    }

    @Suppress("DEPRECATION")
    private fun startDownload(
        context: Context,
        map: MapView,
        cacheManager: CacheManager,
        bbox: org.osmdroid.util.BoundingBox,
        zoomMin: Int,
        zoomMax: Int
    ) {
        val progress = ProgressDialog(context).apply {
            setTitle(context.getString(R.string.cache_progress_title))
            setMessage(context.getString(R.string.cache_progress_message))
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(true)
            setButton(ProgressDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { d, _ ->
                d.dismiss()
            }
            show()
        }

        try {
            cacheManager.downloadAreaAsync(
                context,
                bbox,
                zoomMin,
                zoomMax,
                object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        safeDismiss(progress)
                        Toast.makeText(context, R.string.cache_complete, Toast.LENGTH_LONG).show()
                        Log.d(TAG, "Tile cache complete z=$zoomMin..$zoomMax")
                    }

                    override fun updateProgress(
                        progressValue: Int,
                        currentZoomLevel: Int,
                        zoomMinLevel: Int,
                        zoomMaxLevel: Int
                    ) {
                        if (!progress.isShowing) return
                        val pct = if (progress.max > 0) {
                            min(100, max(0, progressValue))
                        } else {
                            progressValue
                        }
                        progress.progress = pct.coerceIn(0, 100)
                        progress.setMessage(
                            context.getString(
                                R.string.cache_progress_zoom,
                                currentZoomLevel,
                                zoomMinLevel,
                                zoomMaxLevel
                            )
                        )
                    }

                    override fun downloadStarted() {
                        Log.d(TAG, "Tile download started")
                    }

                    override fun setPossibleTilesInArea(total: Int) {
                        if (!progress.isShowing) return
                        progress.max = total.coerceAtLeast(1)
                        progress.progress = 0
                    }

                    override fun onTaskFailed(errors: Int) {
                        safeDismiss(progress)
                        Toast.makeText(
                            context,
                            context.getString(R.string.cache_failed, errors),
                            Toast.LENGTH_LONG
                        ).show()
                        Log.w(TAG, "Tile cache finished with $errors error(s)")
                    }
                }
            )
        } catch (e: TileSourcePolicyException) {
            safeDismiss(progress)
            Log.e(TAG, "Bulk download blocked by tile source policy", e)
            Toast.makeText(context, R.string.cache_bulk_not_allowed, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            safeDismiss(progress)
            Log.e(TAG, "Bulk download failed to start", e)
            Toast.makeText(context, R.string.cache_failed_generic, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun safeDismiss(progress: ProgressDialog) {
        try {
            if (progress.isShowing) progress.dismiss()
        } catch (_: Exception) {
            // Dialog may already be gone if activity was destroyed
        }
    }
}
