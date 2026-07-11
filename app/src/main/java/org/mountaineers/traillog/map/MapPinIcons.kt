package org.mountaineers.traillog.map

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailReport

/**
 * Legible teardrop pins colored by severity / completion for outdoor map scanning.
 */
object MapPinIcons {

    private val LOW = Color.parseColor("#F9A825")
    private val MEDIUM = Color.parseColor("#EF6C00")
    private val HIGH = Color.parseColor("#C62828")
    private val COMPLETE = Color.parseColor("#2E7D32")
    private val UNKNOWN = Color.parseColor("#546E7A")

    fun forReport(context: Context, report: TrailReport): Drawable {
        if (report.isCleared) {
            // Pre-colored green pin + check — do not re-tint
            return ContextCompat.getDrawable(context, R.drawable.ic_map_pin_complete)!!.mutate()
        }
        val base = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)!!.mutate()
        val wrapped = DrawableCompat.wrap(base)
        DrawableCompat.setTint(wrapped, colorFor(report))
        return wrapped
    }

    fun colorFor(report: TrailReport): Int {
        if (report.isCleared) return COMPLETE
        return when (report.severity.lowercase()) {
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            else -> UNKNOWN
        }
    }
}
