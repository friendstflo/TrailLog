package org.mountaineers.traillog.map

import org.osmdroid.util.GeoPoint

/**
 * Map jump targets for each land manager / ranger district used by the committee.
 */
data class TrailPreset(
    val landowner: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val zoom: Double = 12.5
) {
    val geoPoint: GeoPoint get() = GeoPoint(latitude, longitude)
}

object TrailPresets {

    val SNOHOMISH_COUNTY = TrailPreset(
        landowner = "Snohomish County",
        displayName = "Snohomish County",
        latitude = 47.849427,
        longitude = -122.047910,
        zoom = 12.5
    )

    val DARRINGTON_RD = TrailPreset(
        landowner = "Darrington RD",
        displayName = "Darrington RD",
        latitude = 48.163689,
        longitude = -121.778883,
        zoom = 12.0
    )

    val GIFFORD_PINCHOT_RD = TrailPreset(
        landowner = "Gifford-Pinchot RD",
        displayName = "Gifford-Pinchot RD",
        latitude = 46.318879,
        longitude = -122.204089,
        zoom = 11.5
    )

    /** Presets with fixed trail centers (excludes All / Other). */
    val all: List<TrailPreset> = listOf(
        SNOHOMISH_COUNTY,
        DARRINGTON_RD,
        GIFFORD_PINCHOT_RD
    )

    fun forLandowner(landowner: String?): TrailPreset? {
        if (landowner.isNullOrBlank() || landowner == "All" || landowner == "Other") return null
        return all.firstOrNull { it.landowner.equals(landowner, ignoreCase = true) }
    }

    /** Fallback when no GPS and no landowner match (Three Fingers area). */
    val FALLBACK = TrailPreset(
        landowner = "All",
        displayName = "Everett Lookouts (default)",
        latitude = 48.17,
        longitude = -121.69,
        zoom = 12.0
    )
}
