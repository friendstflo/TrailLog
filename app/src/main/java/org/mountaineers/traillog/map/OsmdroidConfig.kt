package org.mountaineers.traillog.map

import android.content.Context
import android.util.Log
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Configures OSMdroid disk tile cache for offline field use.
 * Tiles are stored under the app's external files dir (no legacy storage permission).
 */
object OsmdroidConfig {

    private const val TAG = "OsmdroidConfig"
    private var configured = false

    fun ensureConfigured(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)

            val base = File(app.getExternalFilesDir(null) ?: app.filesDir, "osmdroid")
            val tileCache = File(base, "tiles")
            if (!tileCache.exists()) {
                tileCache.mkdirs()
            }

            Configuration.getInstance().apply {
                userAgentValue = app.packageName
                osmdroidBasePath = base
                osmdroidTileCache = tileCache

                // Prefer downloading tiles when online so they are available offline later
                isMapTileDownloaderFollowRedirects = true

                // Keep cached tiles a long time for multi-day trips without Wi‑Fi
                expirationOverrideDuration = 1000L * 60 * 60 * 24 * 180 // 180 days

                // Larger in-memory tile counts reduce flicker when panning
                cacheMapTileCount = 18.toShort()
                cacheMapTileOvershoot = 3.toShort()
            }

            Configuration.getInstance().load(app, prefs)
            configured = true
            Log.d(TAG, "OSMdroid tile cache at ${tileCache.absolutePath}")
        }
    }
}
