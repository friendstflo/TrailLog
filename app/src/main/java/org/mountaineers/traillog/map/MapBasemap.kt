package org.mountaineers.traillog.map

import android.content.Context
import androidx.core.content.edit
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Selectable basemaps for the trail map.
 * Tiles are cached on disk by OSMdroid (see [OsmdroidConfig]).
 */
enum class MapBasemap(val prefValue: String, val displayName: String) {
    OPEN_STREET_MAP("osm", "OpenStreetMap"),
    USGS_TOPO("usgs_topo", "USGS Topo");

    companion object {
        fun fromPref(value: String?): MapBasemap =
            entries.firstOrNull { it.prefValue == value } ?: OPEN_STREET_MAP

        val displayNames: List<String> = entries.map { it.displayName }
    }
}

/**
 * OpenStreetMap (Mapnik-compatible) with a tile policy that **allows bulk download**
 * for offline field prep.
 *
 * Stock [org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK] sets FLAG_NO_BULK,
 * which crashes CacheManager with TileSourcePolicyException.
 *
 * Name stays "Mapnik" so tiles share the same disk-cache folder as classic Mapnik.
 * Concurrency is limited to be polite to OSM tile servers — use Wi‑Fi only.
 */
object OpenStreetMapTileSource : XYTileSource(
    "Mapnik",
    0,
    19,
    256,
    ".png",
    arrayOf(
        "https://tile.openstreetmap.org/",
        "https://a.tile.openstreetmap.org/",
        "https://b.tile.openstreetmap.org/",
        "https://c.tile.openstreetmap.org/"
    ),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        /* maxConcurrent */ 2,
        TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
            TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
        // Intentionally omit FLAG_NO_BULK so "Cache this area" works offline prep
    )
)

/**
 * USGS National Map Topo tiles (ArcGIS tile scheme: z/y/x).
 * https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer
 */
object UsgsTopoTileSource : OnlineTileSourceBase(
    "USGSTopo",
    0,
    16,
    256,
    "",
    arrayOf("https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/"),
    "USGS The National Map"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "tile/$z/$y/$x"
    }
}

object MapBasemapPreferences {
    private const val PREFS = "traillog_prefs"
    private const val KEY_BASEMAP = "map_basemap"

    fun get(context: Context): MapBasemap {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MapBasemap.fromPref(prefs.getString(KEY_BASEMAP, MapBasemap.OPEN_STREET_MAP.prefValue))
    }

    fun set(context: Context, basemap: MapBasemap) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_BASEMAP, basemap.prefValue)
        }
    }

    fun tileSource(basemap: MapBasemap): ITileSource = when (basemap) {
        MapBasemap.OPEN_STREET_MAP -> OpenStreetMapTileSource
        MapBasemap.USGS_TOPO -> UsgsTopoTileSource
    }

    fun maxZoom(basemap: MapBasemap): Double = when (basemap) {
        MapBasemap.OPEN_STREET_MAP -> 19.0
        MapBasemap.USGS_TOPO -> 16.0
    }

    /** True when CacheManager bulk download is allowed for this basemap. */
    fun supportsBulkCache(tileSource: ITileSource): Boolean {
        return if (tileSource is OnlineTileSourceBase) {
            try {
                tileSource.tileSourcePolicy.acceptsBulkDownload()
            } catch (_: Exception) {
                true
            }
        } else {
            false
        }
    }
}
