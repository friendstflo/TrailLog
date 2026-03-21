package org.mountaineers.traillog.data

import androidx.room.TypeConverter

class ReportTypeConverter {

    @TypeConverter
    fun fromReportType(type: ReportType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toReportType(name: String?): ReportType? {
        return name?.let { ReportType.valueOf(it) }
    }
}