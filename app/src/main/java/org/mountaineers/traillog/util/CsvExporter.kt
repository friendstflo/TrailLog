package org.mountaineers.traillog.util

import android.content.Context
import android.os.Environment
import org.mountaineers.traillog.data.ReportType
import org.mountaineers.traillog.data.TrailReport
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Landowner-oriented CSV export for trail reports.
 */
object CsvExporter {

    data class Result(
        val success: Boolean,
        val fileName: String? = null,
        val absolutePath: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Writes filtered reports to Downloads.
     * Includes latitude/longitude for GIS / landowner follow-up.
     */
    fun export(
        context: Context,
        reports: List<TrailReport>,
        landownerFilter: String
    ): Result {
        if (reports.isEmpty()) {
            return Result(success = false, errorMessage = "No reports to export")
        }

        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            val safeFilter = landownerFilter.replace(" ", "_")
            val fileName = "TrailLog_${safeFilter}_$timestamp.csv"

            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.append(
                    "Date,Latitude,Longitude,Landowner,Type,Description," +
                        "Quantity,Unit,Severity,Status,Reporter,Photo Path\n"
                )

                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                reports.forEach { report ->
                    val dateStr = dateFmt.format(report.timestamp)
                    val unit = if (report.type == ReportType.LOG) "inches" else "ft"
                    val status = if (report.isCleared) "Complete" else "Pending"
                    val desc = report.description.replace("\"", "\"\"")
                    val photo = report.photoPath.replace("\"", "\"\"")

                    writer.append("\"$dateStr\",")
                        .append("${report.lat},")
                        .append("${report.lng},")
                        .append("\"${report.landowner}\",")
                        .append("\"${report.type.displayName}\",")
                        .append("\"$desc\",")
                        .append("${report.quantity},")
                        .append("\"$unit\",")
                        .append("\"${report.severity}\",")
                        .append("\"$status\",")
                        .append("\"${report.reporter}\",")
                        .append("\"$photo\"\n")
                }
            }

            Result(
                success = true,
                fileName = fileName,
                absolutePath = file.absolutePath
            )
        } catch (e: Exception) {
            Result(success = false, errorMessage = e.message ?: "Export failed")
        }
    }
}
