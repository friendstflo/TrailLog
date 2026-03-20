package org.mountaineers.traillog.data

import org.mountaineers.traillog.R

enum class ReportType(val displayName: String, val colorRes: Int) {
    LOG("Log", R.color.log_orange),
    BRUSH("Brush", R.color.brush_green),
    TREADWORK("Treadwork", R.color.treadwork_blue),
    OTHER("Other", R.color.other_gray)
}